package ru.krugzaboty.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.krugzaboty.app.data.local.dao.AppointmentDao
import ru.krugzaboty.app.data.local.dao.CareRecipientDao
import ru.krugzaboty.app.data.local.dao.DiaryDao
import ru.krugzaboty.app.data.local.dao.IntakeDao
import ru.krugzaboty.app.data.local.dao.MedicationDao
import ru.krugzaboty.app.data.local.dao.SubscriptionDao
import ru.krugzaboty.app.data.local.entity.Appointment
import ru.krugzaboty.app.data.local.entity.CareRecipient
import ru.krugzaboty.app.data.local.entity.Converters
import ru.krugzaboty.app.data.local.entity.DiaryEntry
import ru.krugzaboty.app.data.local.entity.DocumentMeta
import ru.krugzaboty.app.data.local.entity.FamilyMember
import ru.krugzaboty.app.data.local.entity.IntakeLog
import ru.krugzaboty.app.data.local.entity.Invitation
import ru.krugzaboty.app.data.local.entity.Medication
import ru.krugzaboty.app.data.local.entity.MedicationSchedule
import ru.krugzaboty.app.data.local.entity.ReminderRule
import ru.krugzaboty.app.data.local.entity.SubscriptionState

@Database(
    entities = [
        CareRecipient::class, Medication::class, MedicationSchedule::class, IntakeLog::class,
        DiaryEntry::class, Appointment::class, DocumentMeta::class, FamilyMember::class,
        Invitation::class, SubscriptionState::class, ReminderRule::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KrugDatabase : RoomDatabase() {
    abstract fun recipientDao(): CareRecipientDao
    abstract fun medicationDao(): MedicationDao
    abstract fun intakeDao(): IntakeDao
    abstract fun diaryDao(): DiaryDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object { const val NAME = "krug.db" }
}
