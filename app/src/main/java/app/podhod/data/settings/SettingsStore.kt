package app.podhod.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.podhod.domain.AppSettings
import app.podhod.domain.InputRules
import app.podhod.domain.ThemeMode
import app.podhod.domain.WeightUnit
import app.podhod.domain.enumOrDefault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Пользовательские настройки в DataStore. Читаются безопасно: неверное значение заменяется значением по умолчанию. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val unit = stringPreferencesKey("unit")
        val restSeconds = intPreferencesKey("restSeconds")
        val restEnabled = booleanPreferencesKey("restEnabled")
        val autoStartRest = booleanPreferencesKey("autoStartRest")
        val keepScreenOn = booleanPreferencesKey("keepScreenOn")
        val vibrate = booleanPreferencesKey("vibrate")
        val progressionEnabled = booleanPreferencesKey("progressionEnabled")
        val progressionStep = doublePreferencesKey("progressionStep")
        val progressionSessions = intPreferencesKey("progressionSessions")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.theme] = next.theme.name
            prefs[Keys.unit] = next.unit.name
            prefs[Keys.restSeconds] = next.restSeconds.coerceIn(InputRules.MIN_REST_SECONDS, InputRules.MAX_REST_SECONDS)
            prefs[Keys.restEnabled] = next.restEnabled
            prefs[Keys.autoStartRest] = next.autoStartRest
            prefs[Keys.keepScreenOn] = next.keepScreenOn
            prefs[Keys.vibrate] = next.vibrate
            prefs[Keys.progressionEnabled] = next.progressionEnabled
            prefs[Keys.progressionStep] = next.progressionStep.coerceIn(0.1, 100.0)
            prefs[Keys.progressionSessions] = next.progressionSessions.coerceIn(1, 10)
        }
    }

    suspend fun replace(settings: AppSettings) = update { settings }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOrDefault<ThemeMode>(this[Keys.theme], d.theme),
            unit = enumOrDefault<WeightUnit>(this[Keys.unit], d.unit),
            restSeconds = (this[Keys.restSeconds] ?: d.restSeconds)
                .coerceIn(InputRules.MIN_REST_SECONDS, InputRules.MAX_REST_SECONDS),
            restEnabled = this[Keys.restEnabled] ?: d.restEnabled,
            autoStartRest = this[Keys.autoStartRest] ?: d.autoStartRest,
            keepScreenOn = this[Keys.keepScreenOn] ?: d.keepScreenOn,
            vibrate = this[Keys.vibrate] ?: d.vibrate,
            progressionEnabled = this[Keys.progressionEnabled] ?: d.progressionEnabled,
            progressionStep = (this[Keys.progressionStep] ?: d.progressionStep).coerceIn(0.1, 100.0),
            progressionSessions = (this[Keys.progressionSessions] ?: d.progressionSessions).coerceIn(1, 10),
        )
    }
}
