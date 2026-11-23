package app.braindump.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

data class Settings(
    /** Minutes between check-ins. */
    val intervalMinutes: Int = 60,
    /** Waking hours, as minutes after midnight. */
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 22 * 60,
    /** Evening review time, minutes after midnight. */
    val reviewMinute: Int = 22 * 60,
    /** Wake-up goal, minutes after midnight. */
    val wakeGoalMinute: Int = 6 * 60,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Ask for fingerprint, face or the phone's PIN when opening the app. */
    val appLock: Boolean = false,
    /** Size of your writing: 0.85 (small) to 1.3 (large). */
    val textScale: Float = 1f,
) {
    companion object {
        val intervalChoices = listOf(30, 60, 90, 120, 180, 240, 360, 480)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val interval = intPreferencesKey("interval_minutes_v2")
        val start = intPreferencesKey("start_minute")
        val end = intPreferencesKey("end_minute")
        val review = intPreferencesKey("review_minute")
        val wakeGoal = intPreferencesKey("wake_goal_minute")
        val theme = stringPreferencesKey("theme")
        val appLock = androidx.datastore.preferences.core.booleanPreferencesKey("app_lock")
        val textScale = androidx.datastore.preferences.core.floatPreferencesKey("text_scale")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            intervalMinutes = p[Keys.interval] ?: d.intervalMinutes,
            startMinute = p[Keys.start] ?: d.startMinute,
            endMinute = p[Keys.end] ?: d.endMinute,
            reviewMinute = p[Keys.review] ?: d.reviewMinute,
            wakeGoalMinute = p[Keys.wakeGoal] ?: d.wakeGoalMinute,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            appLock = p[Keys.appLock] ?: d.appLock,
            textScale = p[Keys.textScale] ?: d.textScale,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(s: Settings) {
        context.dataStore.edit { p ->
            p[Keys.interval] = s.intervalMinutes
            p[Keys.start] = s.startMinute
            p[Keys.end] = s.endMinute
            p[Keys.review] = s.reviewMinute
            p[Keys.wakeGoal] = s.wakeGoalMinute
            p[Keys.theme] = s.theme.name
            p[Keys.appLock] = s.appLock
            p[Keys.textScale] = s.textScale
        }
    }
}
