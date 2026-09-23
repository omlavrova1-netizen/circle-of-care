package ru.krugzaboty.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.krugzaboty.app.reminders.ReminderNotifier
import ru.krugzaboty.app.reminders.ReminderReconciler
import ru.krugzaboty.app.subscription.SubscriptionManager
import javax.inject.Inject

@HiltAndroidApp
class KrugApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var subscriptionManager: SubscriptionManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier(this).ensureChannel()
        ReminderReconciler.schedule(this)
        // docs/04 §7: пересинк при каждом старте приложения (OEM мог снести будильники).
        ReminderReconciler.syncNow(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { subscriptionManager.refresh() }
        }
    }
}
