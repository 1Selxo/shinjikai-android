package com.shinjikai.dictionary.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsStore(
    private val context: Context
) {
    private val cachePrefs = context.getSharedPreferences("app_settings_cache", Context.MODE_PRIVATE)
    private val introSeenFlagFile = File(context.noBackupFilesDir, "has_seen_introduction.flag")
    private val isUpdatedInstall by lazy(LazyThreadSafetyMode.NONE) { hasAppBeenUpdatedSinceInstall() }

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val USE_OFFLINE_MODE = booleanPreferencesKey("use_offline_mode")
        val HAS_SEEN_INTRODUCTION = booleanPreferencesKey("has_seen_introduction")
        val SELECTED_ANKI_DECK_NAME = stringPreferencesKey("selected_anki_deck_name")
    }

    /**
     * Synchronous snapshot used to avoid a visible theme flash on cold start.
     * This cache is kept in sync by the setter methods below.
     */
    fun readCached(): AppSettings {
        return AppSettings(
            darkMode = cachePrefs.getBoolean("dark_mode", false),
            useDynamicColor = cachePrefs.getBoolean("use_dynamic_color", true),
            useOfflineMode = cachePrefs.getBoolean("use_offline_mode", false),
            hasSeenIntroduction = resolveHasSeenIntroduction(
                legacySeen = cachePrefs.getBoolean("has_seen_introduction", false)
            ),
            selectedAnkiDeckName = cachePrefs.getString("selected_anki_deck_name", "Shinjikai") ?: "Shinjikai"
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
                useOfflineMode = prefs[Keys.USE_OFFLINE_MODE] ?: false,
                hasSeenIntroduction = resolveHasSeenIntroduction(
                    legacySeen = prefs[Keys.HAS_SEEN_INTRODUCTION] ?: false
                ),
                selectedAnkiDeckName = prefs[Keys.SELECTED_ANKI_DECK_NAME] ?: "Shinjikai"
            )
        }
        .onEach { settings ->
            // Keep the synchronous startup cache in sync even if settings were set before this cache existed.
            cachePrefs.edit()
                .putBoolean("dark_mode", settings.darkMode)
                .putBoolean("use_dynamic_color", settings.useDynamicColor)
                .putBoolean("use_offline_mode", settings.useOfflineMode)
                .putBoolean("has_seen_introduction", settings.hasSeenIntroduction)
                .putString("selected_anki_deck_name", settings.selectedAnkiDeckName)
                .apply()
            syncIntroductionMarker(settings.hasSeenIntroduction)
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

    suspend fun setHasSeenIntroduction(seen: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.HAS_SEEN_INTRODUCTION] = seen }
        cachePrefs.edit().putBoolean("has_seen_introduction", seen).apply()
        syncIntroductionMarker(seen)
    }

    suspend fun setSelectedAnkiDeckName(name: String) {
        val normalized = name.trim().ifBlank { "Shinjikai" }
        context.settingsDataStore.edit { prefs -> prefs[Keys.SELECTED_ANKI_DECK_NAME] = normalized }
        cachePrefs.edit().putString("selected_anki_deck_name", normalized).apply()
    }

    private fun resolveHasSeenIntroduction(legacySeen: Boolean): Boolean {
        if (introSeenFlagFile.exists()) return true
        if (isUpdatedInstall && legacySeen) {
            syncIntroductionMarker(true)
            return true
        }
        return false
    }

    private fun syncIntroductionMarker(seen: Boolean) {
        if (seen) {
            introSeenFlagFile.parentFile?.mkdirs()
            if (!introSeenFlagFile.exists()) {
                introSeenFlagFile.writeText("1")
            }
        } else {
            if (introSeenFlagFile.exists()) {
                introSeenFlagFile.delete()
            }
        }
    }

    private fun hasAppBeenUpdatedSinceInstall(): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.lastUpdateTime > packageInfo.firstInstallTime
    }
}
