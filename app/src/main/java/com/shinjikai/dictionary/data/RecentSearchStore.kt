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
import com.google.gson.JsonObject
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val RECENT_SEARCHES_CACHE_PREFS = "recent_searches_cache"
private const val RECENT_SEARCHES_CACHE_KEY = "recent_terms_json"
private const val MAX_RECENT_SEARCHES = 15

private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_searches")

data class RecentSearchEntry(
    val term: String,
    val searchedAtEpochMs: Long?
)

class RecentSearchStore(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    private val cachePrefs = context.getSharedPreferences(RECENT_SEARCHES_CACHE_PREFS, Context.MODE_PRIVATE)

    private object Keys {
        val RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
    }

    fun readCached(): List<RecentSearchEntry> = decode(cachePrefs.getString(RECENT_SEARCHES_CACHE_KEY, null))

    val recentSearchesFlow: Flow<List<RecentSearchEntry>> = context.recentSearchDataStore
        .data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decode(prefs[Keys.RECENT_SEARCHES_JSON]) }
        .onEach(::writeCache)

    suspend fun save(items: List<RecentSearchEntry>) {
        val normalized = normalize(items)
        val encoded = encode(normalized)
        context.recentSearchDataStore.edit { prefs ->
            prefs[Keys.RECENT_SEARCHES_JSON] = encoded
        }
        writeCache(normalized)
    }

    private fun normalize(items: List<RecentSearchEntry>): List<RecentSearchEntry> {
        val result = ArrayList<RecentSearchEntry>(MAX_RECENT_SEARCHES)
        for (item in items) {
            val normalizedTerm = item.term.trim()
            if (normalizedTerm.isBlank()) continue
            if (result.any { it.term.equals(normalizedTerm, ignoreCase = true) }) continue
            result += item.copy(term = normalizedTerm)
            if (result.size == MAX_RECENT_SEARCHES) break
        }
        return result
    }

    private fun encode(items: List<RecentSearchEntry>): String = gson.toJson(items)

    private fun decode(raw: String?): List<RecentSearchEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = gson.fromJson(raw, JsonArray::class.java) ?: return@runCatching emptyList()
            array.mapIndexedNotNull { index, element ->
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                        val term = runCatching { element.asString }.getOrNull()?.trim().orEmpty()
                        term.takeIf(String::isNotBlank)?.let { normalizedTerm ->
                            RecentSearchEntry(
                                term = normalizedTerm,
                                searchedAtEpochMs = null
                            )
                        }
                    }
                    element.isJsonObject -> decodeEntry(element.asJsonObject)
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
            .distinctBy { it.term.lowercase() }
            .take(MAX_RECENT_SEARCHES)
    }

    private fun decodeEntry(jsonObject: JsonObject): RecentSearchEntry? {
        val term = jsonObject.get("term")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            .orEmpty()
        if (term.isBlank()) return null

        val searchedAtEpochMs = jsonObject.get("searchedAtEpochMs")
            ?.takeIf { it.isJsonPrimitive }
            ?.asLong
            ?.takeIf { it > 0L }
        return RecentSearchEntry(term = term, searchedAtEpochMs = searchedAtEpochMs)
    }

    private fun writeCache(items: List<RecentSearchEntry>) {
        cachePrefs.edit().putString(RECENT_SEARCHES_CACHE_KEY, encode(items)).apply()
    }
}
