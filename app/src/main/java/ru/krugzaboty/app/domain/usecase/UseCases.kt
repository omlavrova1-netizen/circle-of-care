package ru.krugzaboty.app.domain.usecase

import ru.krugzaboty.app.data.local.dao.CareRecipientDao
import ru.krugzaboty.app.data.local.entity.CareRecipient
import ru.krugzaboty.app.data.local.entity.MedForm
import ru.krugzaboty.app.data.local.entity.Medication
import ru.krugzaboty.app.data.local.entity.MedicationSchedule
import ru.krugzaboty.app.data.repository.MedicationRepository
import ru.krugzaboty.app.subscription.SubscriptionManager
import java.util.UUID
import javax.inject.Inject

/** FR-01. existingId != null — обновление существующего (повторный запуск онбординга не плодит дублей подопечных). */
class CreateRecipientUseCase @Inject constructor(private val dao: CareRecipientDao) {
    suspend operator fun invoke(existingId: String?, name: String, birthYear: Int?, allergiesText: String?): String {
        val id = existingId ?: UUID.randomUUID().toString()
        dao.upsert(CareRecipient(id = id, name = name.trim(), birthYear = birthYear, allergiesText = allergiesText?.trim()?.ifBlank { null }))
        return id
    }
}

/** FR-01: текущий активный подопечный — возобновление онбординга после force-quit без дублей. */
class GetActiveRecipientUseCase @Inject constructor(private val dao: CareRecipientDao) {
    suspend operator fun invoke(): CareRecipient? = dao.activeAll().firstOrNull()
}

sealed interface AddMedicationResult {
    data object Added : AddMedicationResult
    data object FreeLimitReached : AddMedicationResult // → paywall med_limit (FR-06)
}

/**
 * FR-04/05/06: лимит Free (5) учитывает подписку; расписание → ReminderEngine.
 * daysMask: бит 0 = Пн … бит 6 = Вс; times: "HH:mm".
 */
class AddMedicationUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val subscriptionManager: SubscriptionManager,
) {
    suspend operator fun invoke(
        recipientId: String,
        name: String,
        doseText: String?,
        form: MedForm,
        daysMask: Int,
        times: List<String>,
    ): AddMedicationResult {
        // Единый предикат подписки: включает UNKNOWN_OFFLINE в 72-часовом офлайн-грейсе
        // (docs/05 §3). Ручной перебор статусов его терял → пользователь с офлайн-подпиской
        // получал ложный paywall med_limit.
        val premium = subscriptionManager.isPremiumUsable()
        if (!premium && repository.activeCount(recipientId) >= FREE_MED_LIMIT) {
            return AddMedicationResult.FreeLimitReached
        }
        val medication = Medication(id = UUID.randomUUID().toString(), recipientId = recipientId, name = name.trim(), doseText = doseText?.trim()?.ifBlank { null }, form = form)
        val schedule = if (times.isNotEmpty() && daysMask != 0) {
            MedicationSchedule(id = UUID.randomUUID().toString(), medicationId = medication.id, daysMask = daysMask, times = times.take(6), startDate = System.currentTimeMillis())
        } else null
        repository.addMedication(medication, schedule)
        return AddMedicationResult.Added
    }

    companion object { const val FREE_MED_LIMIT = 5 }
}
