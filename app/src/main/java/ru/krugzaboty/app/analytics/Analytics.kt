package ru.krugzaboty.app.analytics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка аналитики с белым списком (docs/06 §0).
 * Философия: SDK (AppMetrica) подключается после docs/00 #18; сейчас — no-op,
 * но белый список работает уже теперь: запрещённое событие/параметр не уйдёт НИКОГДА.
 */
@Singleton
class Analytics @Inject constructor(@ApplicationContext context: Context) {

    object Events {
        const val APP_INSTALLED = "app_installed"
        const val ONBOARDING_STARTED = "onboarding_started"
        const val ONBOARDING_COMPLETED = "onboarding_completed"
        const val CARE_RECIPIENT_CREATED = "care_recipient_created"
        const val MEDICATION_ADDED = "medication_added"
        const val REMINDER_CREATED = "reminder_created"
        const val NOTIF_PERMISSION_REQUESTED = "notification_permission_requested"
        const val NOTIF_PERMISSION_GRANTED = "notification_permission_granted"
        const val NOTIF_PERMISSION_DENIED = "notification_permission_denied"
        const val EXACT_ALARM_DENIED = "exact_alarm_denied"
        const val OEM_INSTRUCTION_SHOWN = "oem_battery_instruction_shown"
        const val REMINDER_FIRED = "reminder_fired"
        const val INTAKE_CONFIRMED = "intake_confirmed"
        const val INTAKE_MISSED = "intake_missed"
        const val INTAKE_SKIPPED = "intake_skipped"
        const val REMINDER_SNOOZED = "reminder_snoozed"
        const val DIARY_ENTRY_CREATED = "diary_entry_created"
        const val APPOINTMENT_ADDED = "appointment_added"
        const val PDF_EXPORT_STARTED = "pdf_export_started"
        const val PDF_EXPORT_COMPLETED = "pdf_export_completed"
        const val FAMILY_INVITE_CREATED = "family_invite_created"
        const val PAYWALL_SHOWN = "paywall_shown"
        const val TRIAL_STARTED = "trial_started"
        const val PURCHASE_STARTED = "purchase_started"
        const val PURCHASE_SUCCESS = "purchase_success"
        const val PURCHASE_FAILED = "purchase_failed"
        const val SUBSCRIPTION_CANCELLED = "subscription_cancelled"
        const val SUBSCRIPTION_RESTORED = "subscription_restored"
        const val PREMIUM_ACTIVE = "premium_active"
        const val PREMIUM_EXPIRED = "premium_expired"
    }

    /**
     * Белые списки параметров по событиям — источник правды "какие события существуют".
     * ВАЖНО: событие без параметров всё равно должно быть перечислено здесь с emptySet(),
     * иначе log() посчитает его "неизвестным" и молча отбросит (баг: так терялись
     * onboarding_started, notification_permission_granted/denied, reminder_snoozed и др.,
     * критичные для воронок из docs/06).
     */
    private val allowed: Map<String, Set<String>> = mapOf(
        Events.APP_INSTALLED to emptySet(),
        Events.ONBOARDING_STARTED to emptySet(),
        Events.ONBOARDING_COMPLETED to setOf("meds_added_bucket"),
        Events.CARE_RECIPIENT_CREATED to setOf("has_photo"),
        Events.MEDICATION_ADDED to setOf("count_after_bucket", "source"),
        Events.REMINDER_CREATED to setOf("times_per_day_bucket"),
        Events.NOTIF_PERMISSION_REQUESTED to emptySet(),
        Events.NOTIF_PERMISSION_GRANTED to emptySet(),
        Events.NOTIF_PERMISSION_DENIED to emptySet(),
        Events.EXACT_ALARM_DENIED to emptySet(),
        Events.OEM_INSTRUCTION_SHOWN to setOf("vendor"),
        Events.REMINDER_FIRED to setOf("delay_bucket_min"),
        Events.INTAKE_CONFIRMED to setOf("source", "delay_bucket_min"),
        Events.INTAKE_MISSED to setOf("repeat_index"),
        Events.INTAKE_SKIPPED to setOf("reason"),
        Events.REMINDER_SNOOZED to setOf("count"),
        Events.DIARY_ENTRY_CREATED to setOf("type", "has_photo"),
        Events.APPOINTMENT_ADDED to emptySet(),
        Events.PDF_EXPORT_STARTED to setOf("kind"),
        Events.PDF_EXPORT_COMPLETED to setOf("kind"),
        Events.FAMILY_INVITE_CREATED to setOf("channel"),
        Events.PAYWALL_SHOWN to setOf("trigger", "is_first"),
        Events.TRIAL_STARTED to setOf("sku"),
        Events.PURCHASE_STARTED to setOf("sku"),
        Events.PURCHASE_SUCCESS to setOf("sku"),
        Events.PURCHASE_FAILED to setOf("reason"),
        Events.SUBSCRIPTION_CANCELLED to setOf("sku"),
        Events.SUBSCRIPTION_RESTORED to emptySet(),
        Events.PREMIUM_ACTIVE to setOf("sku"),
        Events.PREMIUM_EXPIRED to setOf("from"),
    )

    fun log(event: String, params: Map<String, Any> = emptyMap()) {
        val allowedKeys = allowed[event] ?: run {
            // Событие не описано в белом списке вообще — это ошибка разработчика, а не
            // нормальный случай "без параметров" (для него используется emptySet()).
            android.util.Log.w("Analytics", "Unknown event dropped: $event — добавьте в allowed{}")
            return
        }
        val safe = params.filterKeys { it in allowedKeys }
        // TODO(docs/00 #18): AppMetrica.reportEvent(event, safe.mapValues { it.value.toString() })
        android.util.Log.d("Analytics", "$event $safe")
    }

    companion object {
        /** bucket-хелперы: никаких точных значений health-данных */
        fun countBucket(n: Int) = when {
            n <= 0 -> "0"; n <= 2 -> "1-2"; n <= 5 -> "3-5"; n <= 10 -> "6-10"; else -> "11+"
        }
        fun delayBucket(min: Long) = when {
            min <= 5 -> "le5"; min <= 15 -> "6-15"; min <= 60 -> "16-60"; else -> "60+"
        }
    }
}
