package com.shinjikai.dictionary.data

import android.content.Context
import java.util.UUID

/**
 * Persistent, anonymous client id used for backend requests.
 *
 * Kept synchronous since it's used from `remember { ... }` callsites.
 */
object ClientIdStore {
    private const val PREFS_NAME = "client_id_store"
    private const val KEY_CLIENT_ID = "client_id"

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_ID, created).apply()
        return created
    }
}

