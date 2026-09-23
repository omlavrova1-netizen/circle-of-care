package ru.krugzaboty.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.krugzaboty.app.data.local.entity.Appointment
import ru.krugzaboty.app.data.local.entity.CareRecipient
import ru.krugzaboty.app.data.local.entity.DiaryEntry
import ru.krugzaboty.app.data.local.entity.IntakeLog
import ru.krugzaboty.app.data.local.entity.IntakeStatus
import ru.krugzaboty.app.data.local.entity.Medication
import ru.krugzaboty.app.data.local.entity.MedicationSchedule
import ru.krugzaboty.app.data.local.entity.SubscriptionState

@Dao
interface CareRecipientDao {
    @Upsert suspend fun upsert(recipient: CareRecipient)
    @Query("SELECT * FROM care_recipients WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    fun observeActive(): Flow<CareRecipient?>
    @Query("SELECT COUNT(*) FROM care_recipients WHERE isActive = 1") suspend fun activeCount(): Int
    @Query("SELECT * FROM care_recipients WHERE isActive = 1") suspend fun activeAll(): List<CareRecipient>
}

/** Карточка списка лекарств: препарат + активное расписание. */
data class MedicationWithSchedule(val medication: Medication, val schedule: MedicationSchedule?)

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(medication: Medication)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSchedule(schedule: MedicationSchedule)
    @Update suspend fun update(medication: Medication)
    @Query("UPDATE medications SET deletedAt = :now WHERE id = :id") suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
    @Query("SELECT * FROM medications WHERE id = :id") suspend fun byId(id: String): Medication?
    @Query("SELECT * FROM medications WHERE recipientId = :rid AND deletedAt IS NULL ORDER BY createdAt")
    fun observeActive(rid: String): Flow<List<Medication>>
    @Query("SELECT COUNT(*) FROM medications WHERE recipientId = :rid AND deletedAt IS NULL") suspend fun activeCount(rid: String): Int
    @Query("SELECT * FROM medications WHERE deletedAt IS NULL") suspend fun allActive(): List<Medication>
    @Query("SELECT * FROM medication_schedules WHERE medicationId = :mid AND active = 1 LIMIT 1")
    suspend fun scheduleFor(mid: String): MedicationSchedule?
    @Query("SELECT * FROM medication_schedules WHERE medicationId = :mid") suspend fun schedulesFor(mid: String): List<MedicationSchedule>
    /** JOIN защищает от «оживших» напоминаний удалённого лекарства, если где-то забыли деактивировать расписание. */
    @Query(
        "SELECT s.* FROM medication_schedules s " +
            "INNER JOIN medications m ON m.id = s.medicationId " +
            "WHERE s.active = 1 AND m.deletedAt IS NULL",
    )
    suspend fun allActiveSchedules(): List<MedicationSchedule>
    @Query("UPDATE medication_schedules SET active = 0 WHERE medicationId = :mid") suspend fun deactivateSchedules(mid: String)
    @Transaction
    suspend fun replaceSchedule(schedule: MedicationSchedule) {
        deactivateSchedules(schedule.medicationId)
        insertSchedule(schedule)
    }
}

@Dao
interface IntakeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(logs: List<IntakeLog>)
    @Query("SELECT * FROM intake_logs WHERE scheduleId = :sid AND plannedAt >= :from") suspend fun future(sid: String, from: Long): List<IntakeLog>
    @Query("DELETE FROM intake_logs WHERE scheduleId = :sid AND status = 'SCHEDULED' AND plannedAt >= :from") suspend fun deleteFuture(sid: String, from: Long)
    @Query("SELECT * FROM intake_logs WHERE id = :id") suspend fun byId(id: String): IntakeLog?
    @Update suspend fun update(log: IntakeLog)
    @Query("SELECT * FROM intake_logs WHERE plannedAt >= :dayStart AND plannedAt < :dayEnd ORDER BY plannedAt")
    fun observeBetween(dayStart: Long, dayEnd: Long): Flow<List<IntakeLog>>
    @Query("SELECT * FROM intake_logs WHERE status IN (:statuses) AND plannedAt < :now")
    suspend fun withStatusesOlderThan(statuses: List<IntakeStatus>, now: Long): List<IntakeLog>
    @Query("SELECT * FROM intake_logs WHERE status = 'SCHEDULED' AND plannedAt < :now") suspend fun overdue(now: Long): List<IntakeLog>
    /**
     * Слоты, "застрявшие" в SHOWN/SNOOZED — их miss-check будильник не сработал
     * (телефон был выключен/выгружен), поэтому эскалация до MISSED не произошла сама.
     * missedAfterMs — тот же интервал, что и обычный miss-check (docs/04 §3–4, §8).
     */
    @Query(
        "SELECT * FROM intake_logs WHERE status IN ('SHOWN','SNOOZED') AND plannedAt < :now",
    )
    suspend fun stuckShownOrSnoozed(now: Long): List<IntakeLog>
    /** Незавершённые (не CONFIRMED/SKIPPED/CANCELLED) просроченные слоты конкретного лекарства — для «принял вне приложения» (TC-28). */
    @Query(
        "SELECT * FROM intake_logs WHERE medicationId = :medicationId AND plannedAt < :now " +
            "AND status IN ('SCHEDULED','SHOWN','SNOOZED','MISSED')",
    )
    suspend fun pendingOverdueForMedication(medicationId: String, now: Long): List<IntakeLog>
}

@Dao
interface DiaryDao {
    @Insert suspend fun insert(entry: DiaryEntry)
    @Query("SELECT * FROM diary_entries WHERE recipientId = :rid ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(rid: String): Flow<List<DiaryEntry>>
}

@Dao
interface AppointmentDao {
    @Upsert suspend fun upsert(appointment: Appointment)
    @Query("SELECT * FROM appointments WHERE recipientId = :rid AND startsAt >= :from ORDER BY startsAt")
    fun observeUpcoming(rid: String, from: Long): Flow<List<Appointment>>
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription_state WHERE id = 1") suspend fun get(): SubscriptionState?
    @Upsert suspend fun upsert(state: SubscriptionState)
}
