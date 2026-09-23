package ru.krugzaboty.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** docs/04 §7: после перезагрузки/замены пакета/смены времени — пересинхронизация. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<ReminderReconciler>()
            .setInputData(workDataOf("trigger" to intent.action))
            .setInitialDelay(30, TimeUnit.SECONDS) // ждём разблокировки хранилища
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "krug_boot_sync", androidx.work.ExistingWorkPolicy.REPLACE, request,
        )
    }
}
