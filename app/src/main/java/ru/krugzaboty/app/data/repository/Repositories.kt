package ru.krugzaboty.app.data.repository

import ru.krugzaboty.app.data.local.dao.IntakeDao
import ru.krugzaboty.app.data.local.dao.MedicationDao
import ru.krugzaboty.app.data.local.entity.Medication
import ru.krugzaboty.app.data.local.entity.MedicationSchedule
import ru.krugzaboty.app.reminders.ReminderEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-04/05/06/07 + завязка на напоминания (docs/04).
 * Ограничение Free (≤5 лекарств) проверяет вызывающий слой через PaywallGate,
 * репозиторий — про данные, не про монетизацию.
 */
@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val reminderEngine: ReminderEngine,
) {
    fun observeActive(recipientId: String) = medicationDao.observeActive(recipientId)
    suspend fun activeCount(recipientId: String) = medicationDao.activeCount(recipientId)
    suspend fun byId(id: String) = medicationDao.byId(id)
    suspend fun scheduleFor(medicationId: String) = medicationDao.scheduleFor(medicationId)

    suspend fun addMedication(medication: Medication, schedule: MedicationSchedule?): String {
        medicationDao.insert(medication)
        if (schedule != null) {
            medicationDao.replaceSchedule(schedule.copy(medicationId = medication.id))
            medicationDao.scheduleFor(medication.id)?.let { reminderEngine.syncSchedule(it) }
        }
        return medication.id
    }

    suspend fun updateSchedule(medicationId: String, daysMask: Int, times: List<String>) {
        val now = System.currentTimeMillis()
        // Баг-фикс: старое расписание нужно снять с будильников/будущих слотов ДО замены,
        // иначе medicationDao.replaceSchedule лишь помечает его active=0 в БД, а его
        // AlarmManager-будильники и SCHEDULED-слоты остаются жить — пользователь получит
        // ДВА напоминания на одно лекарство (старое время + новое) после редактирования.
        medicationDao.scheduleFor(medicationId)?.let { reminderEngine.cancelSchedule(it.id) }
        medicationDao.replaceSchedule(
            MedicationSchedule(
                id = UUID.randomUUID().toString(),
                medicationId = medicationId,
                daysMask = daysMask,
                times = times,
                startDate = now,
                active = true,
            ),
        )
        medicationDao.scheduleFor(medicationId)?.let { reminderEngine.syncSchedule(it) }
    }

    suspend fun deleteMedication(medicationId: String) {
        // Важно: сначала снять будильники/будущие слоты, ПОТОМ деактивировать расписание
        // и мягко удалить лекарство — иначе после перезагрузки ReminderEngine.syncAll()
        // пересоздаст напоминания по уже удалённому лекарству (allActiveSchedules).
        medicationDao.scheduleFor(medicationId)?.let { reminderEngine.cancelSchedule(it.id) }
        medicationDao.deactivateSchedules(medicationId)
        medicationDao.softDelete(medicationId)
    }
}

@Singleton
class IntakeRepository @Inject constructor(
    private val intakeDao: IntakeDao,
    private val medicationDao: MedicationDao,
    private val reminderEngine: ReminderEngine,
) {
    fun observeBetween(from: Long, to: Long) = intakeDao.observeBetween(from, to)

    suspend fun confirm(intakeId: String) = reminderEngine.confirm(intakeId)

    suspend fun confirmOutsideApp(medicationId: String) {
        // TC-28: «приняла вне приложения» — закрываем незавершённые просроченные слоты
        // (SCHEDULED/SHOWN/SNOOZED/MISSED), не трогая будущие. reminderEngine.confirm
        // дополнительно снимает будильники и гасит уведомление.
        intakeDao.pendingOverdueForMedication(medicationId, System.currentTimeMillis())
            .forEach { reminderEngine.confirm(it.id) }
    }

    suspend fun medicationName(intakeId: String): String? =
        intakeDao.byId(intakeId)?.let { medicationDao.byId(it.medicationId)?.name }
}
