package com.shinjikai.dictionary.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class RawShinjikaiImporter(
    private val database: AppDatabase,
    private val gson: Gson = Gson()
) {
    private val yomitanDao = database.yomitanDao()

    suspend fun importFromJsonLines(
        jsonlFile: File,
        sourceLabel: String = "raw-shinjikai-jsonl"
    ): Result<Int> {
        return runCatching {
            withContext(Dispatchers.IO) {
                require(jsonlFile.exists()) { "Offline source file was not found." }
                require(jsonlFile.isFile) { "Offline source path is not a file." }

                var importedCount = 0
                val categoriesById = linkedMapOf<Int, CategoryRef>()
                val buffer = ArrayList<YomitanTermEntity>(250)

                database.withTransaction {
                    yomitanDao.clearTermsFts()
                    yomitanDao.clearTerms()

                    jsonlFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { rawLine ->
                            coroutineContext.ensureActive()
                            val line = rawLine.trim()
                            if (line.isEmpty()) return@forEach

                            val record = gson.fromJson(line, RawShinjikaiRecord::class.java) ?: return@forEach
                            val word = record.word ?: return@forEach
                            val id = word.id
                            if (id <= 0) return@forEach

                            record.categories.forEach { category ->
                                if (category.id > 0 && category.name.isNotBlank()) {
                                    categoriesById.putIfAbsent(category.id, category)
                                }
                            }

                            val normalizedWord = word.copy(
                                meanings = word.meanings.map { meaning ->
                                    meaning.copy(
                                        pictures = meaning.pictures.mapNotNull { picture ->
                                            normalizePictureElement(picture)
                                        }
                                    )
                                }
                            )

                            val details = WordDetailsResponse(
                                word = normalizedWord,
                                similarWords = record.similarWords.map {
                                    WordRef(id = it.id, kana = it.kana, writings = it.writings)
                                },
                                sentenceSearch = record.sentenceSearch
                            )

                            buffer.add(
                                YomitanTermEntity(
                                    id = id,
                                    expression = normalizedWord.writings.firstOrNull()?.text?.trim()
                                        .orEmpty()
                                        .ifBlank { normalizedWord.kana.trim() },
                                    reading = normalizedWord.kana.trim()
                                        .ifBlank { normalizedWord.writings.firstOrNull()?.text?.trim().orEmpty() },
                                    glossary = buildMeaningSummary(normalizedWord.meanings).ifBlank { "-" },
                                    note = normalizedWord.meanings.firstOrNull()?.note.orEmpty().trim(),
                                    source = sourceLabel,
                                    detailsJson = gson.toJson(details)
                                )
                            )
                            importedCount += 1

                            if (buffer.size >= 250) {
                                flush(buffer)
                            }
                        }
                    }

                    if (buffer.isNotEmpty()) {
                        flush(buffer)
                    }

                    yomitanDao.upsertMeta(
                        YomitanMetaEntity(
                            key = "categories_json",
                            value = gson.toJson(categoriesById.values.toList())
                        )
                    )
                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_source", value = sourceLabel))
                    yomitanDao.upsertMeta(YomitanMetaEntity(key = "last_import_count", value = importedCount.toString()))
                    yomitanDao.upsertMeta(
                        YomitanMetaEntity(
                            key = "last_import_epoch_ms",
                            value = System.currentTimeMillis().toString()
                        )
                    )
                }

                importedCount
            }
        }
    }

    private suspend fun flush(buffer: MutableList<YomitanTermEntity>) {
        yomitanDao.upsertAll(buffer)
        yomitanDao.upsertAllFts(buffer.map { it.toFts() })
        buffer.clear()
    }

    private fun YomitanTermEntity.toFts(): YomitanTermFtsEntity {
        return YomitanTermFtsEntity(
            rowId = id,
            expression = expression,
            reading = reading,
            glossary = glossary
        )
    }

    private fun buildMeaningSummary(meanings: List<Meaning>): String {
        return meanings
            .asSequence()
            .map { it.arabic.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(separator = " / ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizePictureElement(picture: JsonElement): JsonElement? {
        if (picture.isJsonNull) return null
        if (picture.isJsonPrimitive) {
            val value = picture.asString.trim()
            if (value.isBlank()) return null
            return JsonPrimitive(value.replace('\\', '/'))
        }
        if (!picture.isJsonObject) return picture

        val obj = picture.asJsonObject
        val imageRef = sequenceOf("Filename", "FileName", "filename", "Url", "url", "Src", "src", "Path", "path")
            .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return picture

        return JsonPrimitive(imageRef.replace('\\', '/'))
    }
}

private data class RawShinjikaiRecord(
    @SerializedName("Word") val word: WordDetailsWord? = null,
    @SerializedName("Categories") val categories: List<CategoryRef> = emptyList(),
    @SerializedName("SimilarWords") val similarWords: List<SearchItem> = emptyList(),
    @SerializedName("SentenceSearch") val sentenceSearch: List<SentenceExample> = emptyList()
)
