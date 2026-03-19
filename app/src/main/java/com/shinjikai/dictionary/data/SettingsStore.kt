package com.shinjikai.dictionary.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsStore(
    private val context: Context
) {
    private val cachePrefs = context.getSharedPreferences("app_settings_cache", Context.MODE_PRIVATE)

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val USE_OFFLINE_MODE = booleanPreferencesKey("use_offline_mode")
    }

    /**
     * Synchronous snapshot used to avoid a visible theme flash on cold start.
     * This cache is kept in sync by the setter methods below.
     */
    fun readCached(): AppSettings {
        return AppSettings(
            darkMode = cachePrefs.getBoolean("dark_mode", false),
            useDynamicColor = cachePrefs.getBoolean("use_dynamic_color", true),
            useOfflineMode = cachePrefs.getBoolean("use_offline_mode", false)
        )
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore
        .data
        .catch { e ->
            // If the datastore is temporarily unreadable, fall back to defaults.
            if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e
        }
        .map { prefs ->
            AppSettings(
                darkMode = prefs[Keys.DARK_MODE] ?: false,
                useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
                useOfflineMode = prefs[Keys.USE_OFFLINE_MODE] ?: false
            )
        }
        .onEach { settings ->
            // Keep the synchronous startup cache in sync even if settings were set before this cache existed.
            cachePrefs.edit()
                .putBoolean("dark_mode", settings.darkMode)
                .putBoolean("use_dynamic_color", settings.useDynamicColor)
                .putBoolean("use_offline_mode", settings.useOfflineMode)
                .apply()
        }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.DARK_MODE] = enabled }
        cachePrefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.USE_DYNAMIC_COLOR] = enabled }
        cachePrefs.edit().putBoolean("use_dynamic_color", enabled).apply()
    }

    suspend fun setUseOfflineMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.USE_OFFLINE_MODE] = enabled }
        cachePrefs.edit().putBoolean("use_offline_mode", enabled).apply()
    }
}
