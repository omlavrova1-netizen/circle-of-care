package ru.krugzaboty.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.krugzaboty.app.data.local.dao.IntakeDao
import ru.krugzaboty.app.data.local.dao.MedicationDao
import ru.krugzaboty.app.data.local.entity.IntakeLog
import ru.krugzaboty.app.data.local.entity.IntakeStatus
import ru.krugzaboty.app.data.local.entity.MedicationSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * docs/04: генерирует слоты на 7 дней, ставит точные будильники,
 * fallback setWindow при отказе SCHEDULE_EXACT_ALARM.
 */
@Singleton
class ReminderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao,
    private val intakeDao: IntakeDao,
) {

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun syncAll() {
        val now = System.currentTimeMillis()
        val notifier = ReminderNotifier(context)
        // догоняем пропущенное при выключенном телефоне (TC-28): будильник REMIND не сработал.
        intakeDao.overdue(now - SLOTS_WINDOW_MS).forEach { log ->
            if (log.status == IntakeStatus.SCHEDULED) {
                intakeDao.update(log.copy(status = IntakeStatus.MISSED))
                notifier.showMissed(log)
            }
        }
        // Баг-фикс (docs/04 §8): слоты, "застрявшие" в SHOWN/SNOOZED — REMIND сработал,
        // но их miss-check будильник не сработал (телефон был выключен/выгружен из памяти).
        // Без этого они висели бы вечно без эскалации до MISSED и без повторных уведомлений.
        intakeDao.stuckShownOrSnoozed(now - MISSED_STUCK_GRACE_MS).forEach { log ->
            cancelAlarm(log.id, ReminderReceiver.ACTION_MISS_CHECK)
            intakeDao.update(log.copy(status = IntakeStatus.MISSED))
            notifier.showMissed(log)
        }
        medicationDao.allActiveSchedules().forEach { syncSchedule(it, keepHistory = true) }
    }

    /** Пересоздаёт будущие слоты расписания и переставляет будильники. */
    suspend fun syncSchedule(schedule: MedicationSchedule, keepHistory: Boolean = true) {
        val now = System.currentTimeMillis()
        if (keepHistory) {
            // Сначала снимаем будильники старых будущих слотов, ПОТОМ удаляем их из БД —
            // иначе PendingIntent с уже несуществующим intakeId продолжит висеть в
            // AlarmManager до 7 дней впустую (утечка будильников при каждом редактировании
            // расписания), а новый слот на тот же HH:mm может не сработать вовремя.
            intakeDao.future(schedule.id, now).filter { it.status == IntakeStatus.SCHEDULED }
                .forEach { cancelAlarm(it.id, ReminderReceiver.ACTION_REMIND) }
            intakeDao.deleteFuture(schedule.id, now)
        }
        val slots = upcomingEpochs(schedule, daysAhead = 7)
        if (slots.isEmpty()) return
        intakeDao.insertAll(slots.map { IntakeLog(id = UUID.randomUUID().toString(), medicationId = schedule.medicationId, scheduleId = schedule.id, plannedAt = it, zoneId = zone.id) })
        // ставим будильники на только что созданные SCHEDULED-слоты
        intakeDao.future(schedule.id, now).filter { it.status == IntakeStatus.SCHEDULED }.forEach { setAlarm(it.id, it.plannedAt, ReminderReceiver.ACTION_REMIND) }
    }

    suspend fun cancelSchedule(scheduleId: String) {
        intakeDao.future(scheduleId, System.currentTimeMillis()).forEach { cancelAlarm(it.id, ReminderReceiver.ACTION_REMIND) }
        intakeDao.deleteFuture(scheduleId, System.currentTimeMillis())
    }

    suspend fun confirm(intakeId: String) {
        intakeDao.byId(intakeId)?.let {
            if (it.status != IntakeStatus.CONFIRMED)
                intakeDao.update(it.copy(status = IntakeStatus.CONFIRMED, actionAt = System.currentTimeMillis()))
        }
        cancelAlarm(intakeId, ReminderReceiver.ACTION_REMIND)
        cancelAlarm(intakeId, ReminderReceiver.ACTION_MISS_CHECK)
        ReminderNotifier(context).cancel(intakeId)
    }

    /**
     * Закрыть слот БЕЗ изменения статуса (status уже выставлен вызывающим кодом:
     * SKIPPED с причиной и т.п.). Нужен именно для «Не принимал»: confirm() здесь
     * нельзя — он перезаписал бы SKIPPED на CONFIRMED и исказил историю приёмов.
     */
    suspend fun dismiss(intakeId: String) {
        cancelAlarm(intakeId, ReminderReceiver.ACTION_REMIND)
        cancelAlarm(intakeId, ReminderReceiver.ACTION_MISS_CHECK)
        ReminderNotifier(context).cancel(intakeId)
    }

    suspend fun snooze(intakeId: String, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        intakeDao.byId(intakeId)?.let { intakeDao.update(it.copy(status = IntakeStatus.SNOOZED, actionAt = System.currentTimeMillis())) }
        // Баг-фикс: без отмены ранее поставленного miss-check пользователь, отложивший
        // напоминание прямо перед истечением missedAfterMin (30 мин), уже через минуту
        // получал бы «пропущено» + повторное уведомление — во время активного снуза.
        cancelAlarm(intakeId, ReminderReceiver.ACTION_MISS_CHECK)
        setAlarm(intakeId, at, ReminderReceiver.ACTION_REMIND)
    }

    suspend fun scheduleMissCheck(intakeId: String, atEpoch: Long) = setAlarm(intakeId, atEpoch, ReminderReceiver.ACTION_MISS_CHECK)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    private fun setAlarm(intakeId: String, atEpoch: Long, action: String) {
        val pi = pendingIntent(intakeId, action)
        if (canScheduleExact()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpoch, pi)
        else alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atEpoch - 5 * 60_000L, 15 * 60_000L, pi) // fallback docs/04 §2.4
    }

    private fun cancelAlarm(intakeId: String, action: String) = alarmManager.cancel(pendingIntent(intakeId, action))

    private fun pendingIntent(intakeId: String, action: String): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(ReminderReceiver.EXTRA_INTAKE_ID, intakeId)
        return PendingIntent.getBroadcast(
            context, (intakeId + action).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** epoch UTC для каждого HH:mm на mask-днях в ближайшие daysAhead (локальный пояс устройства). */
    fun upcomingEpochs(schedule: MedicationSchedule, daysAhead: Int): List<Long> {
        val today = LocalDate.now(zone)
        val res = mutableListOf<Long>()
        for (d in 0..daysAhead) {
            val date = today.plusDays(d.toLong())
            val dowIndex = date.dayOfWeek.value - 1 // Пн=0
            if (schedule.daysMask and (1 shl dowIndex) == 0) continue
            if (schedule.endDate != null && date.atStartOfDay(zone).toInstant().toEpochMilli() > schedule.endDate) continue
            schedule.times.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.forEach { time ->
                val ldt = LocalDateTime.of(date, time)
                val epoch = ldt.atZone(zone).toInstant().toEpochMilli()
                if (epoch > System.currentTimeMillis()) res += epoch
            }
        }
        return res.sorted()
    }

    companion object {
        const val SLOTS_WINDOW_MS = 40L * 60 * 1000
        /** Даём запас чуть больше стандартного missedAfterMin=30, чтобы не гонять статус MISSED раньше времени. */
        const val MISSED_STUCK_GRACE_MS = 35L * 60 * 1000
    }
}
