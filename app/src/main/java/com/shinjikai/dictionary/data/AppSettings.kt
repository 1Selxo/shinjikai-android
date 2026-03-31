package com.shinjikai.dictionary.data

/**
 * Lightweight UI settings persisted by [SettingsStore].
 */
data class AppSettings(
    val darkMode: Boolean = false,
    val useDynamicColor: Boolean = true,
    val useOfflineMode: Boolean = false,
    val hasSeenIntroduction: Boolean = false,
    val lastUpdateCheckEpochMs: Long = 0L
)
