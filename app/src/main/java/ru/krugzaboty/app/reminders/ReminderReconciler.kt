package ru.krugzaboty.app.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** docs/04 §7: периодический выравниватель будильников и слотов. */
@HiltWorker
class ReminderReconciler @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: ReminderEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = runCatching { engine.syncAll() }
        return when {
            outcome.isSuccess -> Result.success()
            runAttemptCount < 3 -> Result.retry() // иначе сбои синхронизации терялись бы до следующего цикла
            else -> Result.failure()
        }
    }

    companion object {
        private const val NAME = "krug_reminder_reconciler"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderReconciler>(8, TimeUnit.HOURS)
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * docs/04 §7: OneTime-ресинк при каждом старте приложения — OEM-менеджеры
         * (Xiaomi/Huawei) могли снести будильники; без этого напоминания молчали бы
         * до перезагрузки телефона. REPLACE — вытесняет невыполненный boot-sync.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderReconciler>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "krug_app_start_sync", ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
