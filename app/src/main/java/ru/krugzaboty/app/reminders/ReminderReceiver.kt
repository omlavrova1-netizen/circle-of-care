package ru.krugzaboty.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.krugzaboty.app.analytics.Analytics
import ru.krugzaboty.app.data.local.dao.IntakeDao
import ru.krugzaboty.app.data.local.dao.MedicationDao
import ru.krugzaboty.app.data.local.entity.IntakeStatus
import ru.krugzaboty.app.data.local.entity.MissedReason
import ru.krugzaboty.app.data.settings.SettingsStore
import javax.inject.Inject

/**
 * Обработчик срабатываний и действий пользователя (docs/04 §3–5).
 * Терминальные статусы гасят показ — защита от двойного приёма (FR-12).
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var intakeDao: IntakeDao
    @Inject lateinit var medicationDao: MedicationDao
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var analytics: Analytics

    override fun onReceive(context: Context, intent: Intent) {
        val intakeId = intent.getStringExtra(EXTRA_INTAKE_ID) ?: return
        val notifier = ReminderNotifier(context)
        notifier.ensureChannel()
        val scope = CoroutineScope(Dispatchers.Default)
        val pending = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> handleRemind(context, notifier, intakeId)
                    ACTION_MISS_CHECK -> handleMissCheck(context, notifier, intakeId)
                    ACTION_CONFIRM -> {
                        engine.confirm(intakeId)
                        val log = intakeDao.byId(intakeId)
                        if (log != null) analytics.log(Analytics.Events.INTAKE_CONFIRMED,
                            mapOf("source" to "notification", "delay_bucket_min" to Analytics.delayBucket((System.currentTimeMillis() - log.plannedAt) / 60_000)))
                    }
                    ACTION_SKIP -> {
                        val label = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REASON)?.toString()
                        val reason = MissedReason.entries.firstOrNull { it.displayLabel() == label } ?: MissedReason.OTHER
                        intakeDao.byId(intakeId)?.let {
                            intakeDao.update(it.copy(status = IntakeStatus.SKIPPED, actionAt = System.currentTimeMillis(), missedReason = reason))
                            // ВАЖНО: dismiss, а не confirm — confirm перезаписал бы
                            // SKIPPED на CONFIRMED и исказил историю приёмов.
                            engine.dismiss(intakeId)
                            analytics.log(Analytics.Events.INTAKE_SKIPPED, mapOf("reason" to reason.name))
                        }
                    }
                    ACTION_SNOOZE -> {
                        engine.snooze(intakeId, 15)
                        intakeDao.byId(intakeId)?.let { ReminderNotifier(context).cancel(it.id) }
                        analytics.log(Analytics.Events.REMINDER_SNOOZED, mapOf("count" to 1))
                    }
                }
            } finally { pending.finish() }
        }
    }

    private suspend fun handleRemind(context: Context, notifier: ReminderNotifier, intakeId: String) {
        val log = intakeDao.byId(intakeId) ?: return
        if (log.status == IntakeStatus.CONFIRMED || log.status == IntakeStatus.SKIPPED || log.status == IntakeStatus.CANCELLED) return
        val minuteOfDay = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        // Тихие часы: переносим показ к их окончанию (docs/04 §3). Раньше был snooze(+30),
        // из-за которого точный будильник будил телефон каждые 30 минут до утра.
        val postponeMin = settings.minutesUntilQuietEnd(minuteOfDay)
        if (postponeMin != null && postponeMin > 0) {
            engine.snooze(intakeId, postponeMin)
            return
        }
        val med = medicationDao.byId(log.medicationId) ?: return
        intakeDao.update(log.copy(status = IntakeStatus.SHOWN))
        notifier.showReminder(log, med.name, med.doseText)
        engine.scheduleMissCheck(log.id, log.plannedAt + MISSED_AFTER_MS)
        analytics.log(Analytics.Events.REMINDER_FIRED,
            mapOf("delay_bucket_min" to Analytics.delayBucket((System.currentTimeMillis() - log.plannedAt) / 60_000)))
    }

    private suspend fun handleMissCheck(context: Context, notifier: ReminderNotifier, intakeId: String) {
        val log = intakeDao.byId(intakeId) ?: return
        if (log.status == IntakeStatus.CONFIRMED || log.status == IntakeStatus.SKIPPED || log.status == IntakeStatus.CANCELLED) return
        val med = medicationDao.byId(log.medicationId) ?: return
        val nextIndex = log.repeatIndex + 1
        // Один update от свежего объекта — без повторного чтения и `!!`
        // (между чтениями запись могла быть удалена/изменена).
        val missedLog = log.copy(status = IntakeStatus.MISSED)
        if (log.status != IntakeStatus.MISSED) {
            intakeDao.update(missedLog)
            analytics.log(Analytics.Events.INTAKE_MISSED, mapOf("repeat_index" to 0))
        }
        if (nextIndex <= REPEAT_COUNT) {
            val updated = missedLog.copy(repeatIndex = nextIndex)
            intakeDao.update(updated)
            notifier.showRepeat(updated, med.name, nextIndex)
            engine.scheduleMissCheck(log.id, System.currentTimeMillis() + REPEAT_INTERVAL_MS)
        }
    }

    companion object {
        const val EXTRA_INTAKE_ID = "intake_id"
        const val EXTRA_REASON = "reason"
        const val ACTION_REMIND = "ru.krugzaboty.app.action.REMIND"
        const val ACTION_MISS_CHECK = "ru.krugzaboty.app.action.MISS_CHECK"
        const val ACTION_CONFIRM = "ru.krugzaboty.app.action.CONFIRM"
        const val ACTION_SKIP = "ru.krugzaboty.app.action.SKIP"
        const val ACTION_SNOOZE = "ru.krugzaboty.app.action.SNOOZE"
        private const val MISSED_AFTER_MS = 30L * 60 * 1000 // docs/04 §4
        private const val REPEAT_COUNT = 3
        private const val REPEAT_INTERVAL_MS = 15L * 60 * 1000
    }
}
