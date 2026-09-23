package ru.krugzaboty.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "krug_settings")

/** Ключи — docs/03 §3 AppSettings. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    object Keys {
        val THEME = stringPreferencesKey("theme") // SYSTEM|LIGHT|DARK
        val BIG_TEXT = booleanPreferencesKey("big_text")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_min") // минуты от полуночи
        val QUIET_END = intPreferencesKey("quiet_end_min")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val OEM_HELP_SHOWN_FOR = stringPreferencesKey("oem_help_shown_for")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val LAST_PAYWALL_AT = longPreferencesKey("last_paywall_at")
        val NOTIF_DECLINED_AT = longPreferencesKey("notif_declined_at")
    }

    val onboardingDone: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val quietHours: Flow<Triple<Boolean, Int, Int>> = ctx.dataStore.data.map {
        Triple(it[Keys.QUIET_ENABLED] ?: false, it[Keys.QUIET_START] ?: (23 * 60), it[Keys.QUIET_END] ?: (7 * 60))
    }

    suspend fun setOnboardingDone(v: Boolean) = ctx.dataStore.edit { it[Keys.ONBOARDING_DONE] = v }
    suspend fun onboardingDoneOnce(): Boolean = ctx.dataStore.data.first()[Keys.ONBOARDING_DONE] ?: false
    suspend fun setOemHelpShownFor(vendor: String) = ctx.dataStore.edit { it[Keys.OEM_HELP_SHOWN_FOR] = vendor }
    suspend fun setLastPaywallAt(ts: Long) = ctx.dataStore.edit { it[Keys.LAST_PAYWALL_AT] = ts }
    suspend fun lastPaywallAt(): Long = ctx.dataStore.data.first()[Keys.LAST_PAYWALL_AT] ?: 0L
    suspend fun setNotifDeclinedAt(ts: Long) = ctx.dataStore.edit { it[Keys.NOTIF_DECLINED_AT] = ts }
    suspend fun quietActive(minuteOfDay: Int): Boolean {
        val (enabled, start, end) = quietHours.first()
        if (!enabled) return false
        return if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    /**
     * Минут до конца тихих часов (для переноса напоминания к их окончанию);
     * null — тихие часы не активны. Перенос «+30 мин» вместо этого гонял бы
     * будильник по кругу каждые полчаса всю ночь (docs/04 §3).
     */
    suspend fun minutesUntilQuietEnd(minuteOfDay: Int): Int? {
        val (enabled, start, end) = quietHours.first()
        if (!enabled) return null
        val active = if (start <= end) minuteOfDay in start until end
        else minuteOfDay >= start || minuteOfDay < end
        if (!active) return null
        return if (minuteOfDay < end) end - minuteOfDay else (24 * 60 - minuteOfDay) + end
    }
}
