package ru.krugzaboty.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Перечисления — имена стабильны, не переименовывать (миграции БД). */
enum class MedForm { TABLET, CAPSULE, LIQUID, INJECTION, DROPS, OTHER }

enum class IntakeStatus { SCHEDULED, SHOWN, SNOOZED, CONFIRMED, MISSED, SKIPPED, CANCELLED }

enum class MissedReason { FORGOT, DOCTOR_CANCELED, RAN_OUT, SIDE_EFFECTS, OTHER }

enum class DiaryType { MOOD, SYMPTOM, VISIT, OTHER }

enum class DocCategory { ANALYSIS, DISCHARGE, CONTRACT, OTHER }

enum class MemberRole { ORGANIZER, HELPER, VIEWER }

/** docs/05 §5 */
enum class SubStatus { FREE, TRIAL_ACTIVE, ACTIVE_MONTH, ACTIVE_YEAR, LIFETIME, GRACE, EXPIRED, UNKNOWN_OFFLINE }

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun medFormToString(v: MedForm?) = v?.name
    @TypeConverter fun stringToMedForm(s: String?) = s?.let { runCatching { MedForm.valueOf(it) }.getOrNull() }
    @TypeConverter fun intakeStatusToString(v: IntakeStatus?) = v?.name
    @TypeConverter fun stringToIntakeStatus(s: String?) = s?.let { runCatching { IntakeStatus.valueOf(it) }.getOrNull() }
    @TypeConverter fun missedReasonToString(v: MissedReason?) = v?.name
    @TypeConverter fun stringToMissedReason(s: String?) = s?.let { runCatching { MissedReason.valueOf(it) }.getOrNull() }
    @TypeConverter fun diaryTypeToString(v: DiaryType?) = v?.name
    @TypeConverter fun stringToDiaryType(s: String?) = s?.let { runCatching { DiaryType.valueOf(it) }.getOrNull() }
    @TypeConverter fun docCategoryToString(v: DocCategory?) = v?.name
    @TypeConverter fun stringToDocCategory(s: String?) = s?.let { runCatching { DocCategory.valueOf(it) }.getOrNull() }
    @TypeConverter fun memberRoleToString(v: MemberRole?) = v?.name
    @TypeConverter fun stringToMemberRole(s: String?) = s?.let { runCatching { MemberRole.valueOf(it) }.getOrNull() }
    @TypeConverter fun subStatusToString(v: SubStatus?) = v?.name
    @TypeConverter fun stringToSubStatus(s: String?) = s?.let { runCatching { SubStatus.valueOf(it) }.getOrNull() }
    @TypeConverter fun stringListToString(v: List<String>?) = v?.let { json.encodeToString(it) }
    @TypeConverter fun stringToStringList(s: String?) = s?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
    @TypeConverter fun intListToString(v: List<Int>?) = v?.let { json.encodeToString(it) }
    @TypeConverter fun stringToIntList(s: String?) = s?.let { runCatching { json.decodeFromString<List<Int>>(it) }.getOrNull() }
}

@Entity(tableName = "care_recipients")
data class CareRecipient(
    @PrimaryKey val id: String,
    val name: String,
    val birthYear: Int? = null,
    val photoUri: String? = null,
    val allergiesText: String? = null,
    /** List<Doctor{name, clinic, phone}> как JSON (docs/03). */
    val doctorsJson: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
)

@Entity(
    tableName = "medications",
    foreignKeys = [ForeignKey(entity = CareRecipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipientId")],
)
data class Medication(
    @PrimaryKey val id: String,
    val recipientId: String,
    val name: String,
    val form: MedForm = MedForm.TABLET,
    val doseText: String? = null,
    val note: String? = null,
    val colorTag: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "medication_schedules",
    foreignKeys = [ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicationId")],
)
data class MedicationSchedule(
    @PrimaryKey val id: String,
    val medicationId: String,
    /** бит 0 = Пн … бит 6 = Вс */
    val daysMask: Int,
    /** List<"HH:mm">, 1–6 значений (docs/04 §1) */
    val times: List<String>,
    val startDate: Long,
    val endDate: Long? = null,
    val active: Boolean = true,
)

@Entity(
    tableName = "intake_logs",
    foreignKeys = [
        ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MedicationSchedule::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medicationId", "plannedAt"), Index("status", "plannedAt"), Index(value = ["scheduleId", "plannedAt"], unique = true)],
)
data class IntakeLog(
    @PrimaryKey val id: String,
    val medicationId: String,
    val scheduleId: String,
    /** epoch millis UTC (docs/04 §6) */
    val plannedAt: Long,
    val zoneId: String,
    val status: IntakeStatus = IntakeStatus.SCHEDULED,
    val actionAt: Long? = null,
    val missedReason: MissedReason? = null,
    val repeatIndex: Int = 0,
    val note: String? = null,
)

@Entity(
    tableName = "diary_entries",
    foreignKeys = [ForeignKey(entity = CareRecipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipientId", "createdAt")],
)
data class DiaryEntry(
    @PrimaryKey val id: String,
    val recipientId: String,
    val type: DiaryType = DiaryType.MOOD,
    /** никогда не попадает в аналитику (docs/06 §0) */
    val text: String,
    val photoPaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(entity = CareRecipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("startsAt")],
)
data class Appointment(
    @PrimaryKey val id: String,
    val recipientId: String,
    val title: String,
    val doctor: String? = null,
    val clinic: String? = null,
    val address: String? = null,
    val startsAt: Long,
    val durationMin: Int? = null,
    val notes: String? = null,
    val reminderOffsetsMin: List<Int> = listOf(1440),
    val notifiedOffsetsJson: String? = null,
)

@Entity(
    tableName = "document_meta",
    foreignKeys = [ForeignKey(entity = CareRecipient::class, parentColumns = ["id"], childColumns = ["recipientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipientId")],
)
data class DocumentMeta(
    @PrimaryKey val id: String,
    val recipientId: String,
    val title: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: DocCategory = DocCategory.OTHER,
    val createdAt: Long = System.currentTimeMillis(),
)

/** v1.5-схема: в v1 не пишется (docs/03). */
@Entity(tableName = "family_members", indices = [Index("recipientId")])
data class FamilyMember(
    @PrimaryKey val id: String,
    val recipientId: String,
    val name: String,
    val role: MemberRole = MemberRole.HELPER,
    val invitationCode: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** v1.5-схема. */
@Entity(tableName = "invitations", indices = [Index(value = ["code"], unique = true)])
data class Invitation(
    @PrimaryKey val id: String,
    val recipientId: String,
    val code: String,
    val role: MemberRole,
    val createdAt: Long,
    val expiresAt: Long,
    val accepted: Boolean = false,
)

@Entity(tableName = "subscription_state")
data class SubscriptionState(
    /** singleton */
    @PrimaryKey val id: Int = 1,
    val status: SubStatus = SubStatus.FREE,
    val sku: String? = null,
    val expiresAt: Long? = null,
    val purchaseTokenHash: String? = null,
    val lastVerifiedAt: Long = 0L,
    /** VERIFIED | CACHED (docs/05 §3) */
    val source: String = "CACHED",
)

@Entity(tableName = "reminder_rules")
data class ReminderRule(
    @PrimaryKey val id: String,
    /** null = глобальное правило (кастомизация — Premium) */
    val medicationId: String? = null,
    val repeatCount: Int = 3,
    val repeatIntervalMin: Int = 15,
    val snoozeMin: Int = 15,
    val missedAfterMin: Int = 30,
    val quietGuard: Boolean = true,
)
