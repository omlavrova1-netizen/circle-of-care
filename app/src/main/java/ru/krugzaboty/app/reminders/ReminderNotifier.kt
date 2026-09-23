package ru.krugzaboty.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import ru.krugzaboty.app.R
import ru.krugzaboty.app.data.local.entity.IntakeLog
import ru.krugzaboty.app.data.local.entity.MissedReason
import ru.krugzaboty.app.ui.MainActivity

/**
 * Уведомления приёма (docs/04 §3). Название/доза лекарства — только в уведомлении,
 * в аналитику не попадают (docs/06 §0).
 */
class ReminderNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL, context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_reminders_desc) }
        manager.createNotificationChannel(channel)
    }

    fun showReminder(log: IntakeLog, medName: String, doseText: String?) {
        val title = context.getString(R.string.notify_remind_title, medName, doseText.orEmpty()).trim()
        val actions = listOf(
            action(log.id, ReminderReceiver.ACTION_CONFIRM, R.string.btn_taken),
            skipAction(log.id),
            action(log.id, ReminderReceiver.ACTION_SNOOZE, R.string.btn_snooze_15),
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notify_remind_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .apply { actions.forEach { addAction(it) } }
            .build()
        manager.notify(log.id.notificationId(), n)
    }

    fun showRepeat(log: IntakeLog, medName: String, repeatIndex: Int) {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_repeat_title, medName))
            .setContentText(context.getString(R.string.notify_repeat_text, repeatIndex))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(action(log.id, ReminderReceiver.ACTION_CONFIRM, R.string.btn_taken))
            .addAction(skipAction(log.id))
            .build()
        manager.notify(log.id.notificationId(), n)
    }

    fun showMissed(log: IntakeLog) {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_missed_title))
            .setContentText(context.getString(R.string.notify_missed_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(log.id.notificationId(), n)
    }

    fun cancel(intakeId: String) = manager.cancel(intakeId.notificationId())

    private fun action(intakeId: String, action: String, titleRes: Int) =
        NotificationCompat.Action.Builder(null, context.getString(titleRes), receiverIntent(intakeId, action)).build()

    /** «Не принимал» с чипами причин через RemoteInput (FR-11). */
    private fun skipAction(intakeId: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(ReminderReceiver.EXTRA_REASON).run {
            setChoices(MissedReason.entries.map { it.displayLabel() }.toTypedArray())
            build()
        }
        return NotificationCompat.Action.Builder(null, context.getString(R.string.btn_not_taken), receiverIntent(intakeId, ReminderReceiver.ACTION_SKIP, mutable = true))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /**
     * На Android 12+ PendingIntent с прикреплённым RemoteInput ОБЯЗАН быть FLAG_MUTABLE —
     * иначе система не сможет доставить выбранный вариант причины в Intent (пропуск причины
     * или сбой создания PendingIntent). Остальные действия (без RemoteInput) остаются immutable.
     */
    private fun receiverIntent(intakeId: String, action: String, mutable: Boolean = false): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(ReminderReceiver.EXTRA_INTAKE_ID, intakeId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (intakeId + action).hashCode(), i, flags)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun String.notificationId() = hashCode()

    companion object {
        const val CHANNEL = "medication_reminders"
    }
}

/** Чипы причины «Не принимал»: единое отображение для показа и разбора ответа (FR-11). */
internal fun MissedReason.displayLabel(): String = when (this) {
    MissedReason.FORGOT -> "Забыла"
    MissedReason.DOCTOR_CANCELED -> "Отменил врач"
    MissedReason.RAN_OUT -> "Препарат кончился"
    MissedReason.SIDE_EFFECTS -> "Побочные эффекты"
    MissedReason.OTHER -> "Другое"
}
