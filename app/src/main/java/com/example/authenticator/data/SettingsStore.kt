package com.example.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

data class AppSettings(
    val ready: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val appLock: Boolean = false,
    val secureScreen: Boolean = true,
    val readError: Boolean = false
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    val settings = store.data.map { prefs ->
        AppSettings(
            ready = true,
            theme = runCatching { ThemeMode.valueOf(prefs[THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            appLock = prefs[APP_LOCK] ?: false,
            secureScreen = prefs[SECURE_SCREEN] ?: true
        )
    }.catch {
        // A damaged preference file must not silently disable the app lock.
        emit(AppSettings(ready = true, appLock = true, readError = true))
    }

    suspend fun setTheme(value: ThemeMode) { store.edit { it[THEME] = value.name } }
    suspend fun setAppLock(value: Boolean) { store.edit { it[APP_LOCK] = value } }
    suspend fun setSecureScreen(value: Boolean) { store.edit { it[SECURE_SCREEN] = value } }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
    }
}
