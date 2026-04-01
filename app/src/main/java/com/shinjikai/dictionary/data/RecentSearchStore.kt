package com.shinjikai.dictionary.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val RECENT_SEARCHES_CACHE_PREFS = "recent_searches_cache"
private const val RECENT_SEARCHES_CACHE_KEY = "recent_terms_json"
private const val MAX_RECENT_SEARCHES = 15

private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_searches")

class RecentSearchStore(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    private val cachePrefs = context.getSharedPreferences(RECENT_SEARCHES_CACHE_PREFS, Context.MODE_PRIVATE)

    private object Keys {
        val RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
    }

    fun readCached(): List<String> = decode(cachePrefs.getString(RECENT_SEARCHES_CACHE_KEY, null))

    val recentSearchesFlow: Flow<List<String>> = context.recentSearchDataStore
        .data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decode(prefs[Keys.RECENT_SEARCHES_JSON]) }
        .onEach(::writeCache)

    suspend fun save(items: List<String>) {
        val normalized = normalize(items)
        val encoded = encode(normalized)
        context.recentSearchDataStore.edit { prefs ->
            prefs[Keys.RECENT_SEARCHES_JSON] = encoded
        }
        writeCache(normalized)
    }

    private fun normalize(items: List<String>): List<String> {
        val result = ArrayList<String>(MAX_RECENT_SEARCHES)
        for (item in items) {
            val normalized = item.trim()
            if (normalized.isBlank()) continue
            if (result.any { it.equals(normalized, ignoreCase = true) }) continue
            result += normalized
            if (result.size == MAX_RECENT_SEARCHES) break
        }
        return result
    }

    private fun encode(items: List<String>): String = gson.toJson(items)

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = gson.fromJson(raw, JsonArray::class.java) ?: return@runCatching emptyList()
            array.mapNotNull { element ->
                runCatching { element.asString }.getOrNull()
            }
        }.getOrDefault(emptyList())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
            .take(MAX_RECENT_SEARCHES)
    }

    private fun writeCache(items: List<String>) {
        cachePrefs.edit().putString(RECENT_SEARCHES_CACHE_KEY, encode(items)).apply()
    }
}
