package com.shinjikai.dictionary.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

private val BULLET_PREFIX_REGEX = Regex("""(?m)^\s*[🔹▪•●◦]\s*""")
private val MULTISPACE_REGEX = Regex("""\s{2,}""")
private val ARABIC_DIACRITICS_REGEX = Regex("""[\u064B-\u065F\u0670\u06D6-\u06ED]""")
private val ARABIC_ALEF_VARIANTS_REGEX = Regex("""[أإآٱ]""")
private val NON_LETTER_NUMBER_SPACE_REGEX = Regex("""[^\p{L}\p{N}\s]""")

class LocalYomitanSource(
    private val yomitanDao: YomitanDao,
    private val gson: Gson = Gson()
) : DictionarySource {
    override suspend fun searchWords(term: String, page: Int): Result<SearchWordsResponse> {
        val query = term.trim()
        if (query.isBlank()) return Result.success(SearchWordsResponse(items = emptyList()))
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = 80
        val offset = pageIndex * pageSize

        return runCatching {
            val isArabic = isArabicQuery(query)
            val ftsQuery = if (!isArabic) buildGenericFtsQuery(query) else null

            val allRows: List<YomitanTermEntity>
            val pagedRowsDirect: List<YomitanTermEntity>?
            val totalCountDirect: Int?

            if (isArabic) {
                val normalizedQuery = normalizeArabic(query)
                val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

                val ftsCandidates = buildGlossaryOnlyFtsQuery(query)
                    ?.let { ftsQuery ->
                        yomitanDao.searchGlossaryFts(ftsQuery, limit = 2500)
                    }
                    .orEmpty()

                val baseRows = if (ftsCandidates.isNotEmpty()) {
                    ftsCandidates
                } else {
                    yomitanDao.searchArabic(
                        term = query,
                        normalizedTerm = normalizedQuery
                    )
                }

                allRows = baseRows
                    .asSequence()
                    .map { row ->
                        val normalizedGlossary = normalizeArabic(row.glossary)
                        val score = scoreArabicMatch(
                            query = normalizedQuery,
                            glossary = normalizedGlossary,
                            tokens = tokens
                        )
                        Triple(row, normalizedGlossary, score)
                    }
                    .filter { (_, glossary, score) ->
                        score < Int.MAX_VALUE && glossary.isNotBlank()
                    }
                    .sortedWith(
                        compareBy<Triple<YomitanTermEntity, String, Int>> { it.third }
                            .thenBy { it.second.length }
                            .thenBy { it.first.id }
                    )
                    .map { it.first }
                    .distinctBy { row ->
                        "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                    }
                    .toList()
                pagedRowsDirect = null
                totalCountDirect = null
            } else if (ftsQuery != null) {
                val desiredCandidates = ((pageIndex + 1) * pageSize * 5).coerceAtLeast(250)
                val ftsRows = yomitanDao.searchFts(
                    matchQuery = ftsQuery,
                    limit = desiredCandidates.coerceAtMost(2500)
                )
                allRows = ftsRows
                    .sortedWith(
                        compareBy<YomitanTermEntity> { rankJapaneseQuery(query, it) }
                            .thenBy { it.id }
                    )
                    .distinctBy { row ->
                        "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                    }
                    .toList()
                pagedRowsDirect = null
                totalCountDirect = null
            } else {
                // Fall back to a paged LIKE query. Use a separate COUNT so paging metadata stays correct.
                pagedRowsDirect = yomitanDao.searchPaged(
                    term = query,
                    prefix = "${query}%",
                    limit = pageSize,
                    offset = offset
                ).distinctBy { row ->
                    "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                }
                totalCountDirect = yomitanDao.countSearchMatches(query)
                allRows = emptyList()
            }

            val totalCount = totalCountDirect ?: allRows.size
            val pageCount = if (totalCount == 0) 0 else ((totalCount + pageSize - 1) / pageSize)
            val pageRows = pagedRowsDirect ?: allRows.drop(offset).take(pageSize)

            SearchWordsResponse(
                items = pageRows.map { row ->
                    SearchItem(
                        id = row.id,
                        kana = row.reading,
                        writings = listOf(Writing(text = row.expression)),
                        meaningSummary = buildSearchPreview(row.glossary),
                        jlpt = 0
                    )
                },
                page = pageIndex,
                pageCount = pageCount,
                totalCount = totalCount
            )
        }
    }

    override suspend fun loadWordDetails(id: Int): Result<WordDetailsResponse> {
        return runCatching {
            val row = yomitanDao.getById(id)
                ?: error("No local entry found for id=$id")
            val imageDirectory = yomitanDao.getMetaValue(OFFLINE_IMAGE_DIR_META_KEY)
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
            row.detailsJson
                ?.takeIf { it.isNotBlank() }
                ?.let { serialized ->
                    return@runCatching gson.fromJson(serialized, WordDetailsResponse::class.java)
                        .withResolvedOfflineImages(imageDirectory)
                }
            WordDetailsResponse(
                word = WordDetailsWord(
                    id = row.id,
                    kana = row.reading,
                    writings = listOf(Writing(text = row.expression)),
                    meanings = listOf(
                        Meaning(
                            arabic = cleanGlossary(row.glossary),
                            note = row.note
                        )
                    ),
                    jlpt = 0
                )
            )
        }
    }

    override suspend fun loadCategories(): Result<LoadCategoriesResponse> {
        return runCatching {
            val categoriesJson = yomitanDao.getMetaValue("categories_json").orEmpty()
            if (categoriesJson.isBlank()) {
                LoadCategoriesResponse()
            } else {
                val type = object : TypeToken<List<CategoryRef>>() {}.type
                LoadCategoriesResponse(categories = gson.fromJson(categoriesJson, type) ?: emptyList())
            }
        }
    }

    override suspend fun loadCategory(id: Int, page: Int): Result<LoadCategoryResponse> {
        return Result.success(
            LoadCategoryResponse(
                category = CategoryRef(id = id, name = "تصنيف محلي"),
                members = SearchWordsResponse()
            )
        )
    }

    private fun buildSearchPreview(glossary: String): String {
        return cleanGlossary(glossary)
            .replace("\n", " ")
            .replace(MULTISPACE_REGEX, " ")
            .trim()
    }

    private fun cleanGlossary(glossary: String): String {
        return glossary
            .replace(BULLET_PREFIX_REGEX, "")
            .trim()
    }

    private fun isArabicQuery(text: String): Boolean {
        return text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }
    }

    private fun normalizeArabic(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(ARABIC_DIACRITICS_REGEX, "")
            .replace("ـ", "") // tatweel
            .replace(ARABIC_ALEF_VARIANTS_REGEX, "ا")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace("ة", "ه")
            .replace(NON_LETTER_NUMBER_SPACE_REGEX, " ")
            .replace(MULTISPACE_REGEX, " ")
            .trim()
    }

    private fun scoreArabicMatch(
        query: String,
        glossary: String,
        tokens: List<String>
    ): Int {
        if (query.isBlank() || glossary.isBlank()) return Int.MAX_VALUE
        val hasAllTokens = tokens.all { glossary.contains(it) }
        if (!hasAllTokens) return Int.MAX_VALUE
        if (glossary == query) return 0
        if (glossary.startsWith("$query ")) return 1
        if (glossary.startsWith(query)) return 2
        val wholeWord = Regex("""(^|\s)${Regex.escape(query)}(\s|$)""")
        if (wholeWord.containsMatchIn(glossary)) return 3

        // Prefer earlier occurrences for partial matches.
        val index = glossary.indexOf(query)
        return if (index >= 0) 100 + index else Int.MAX_VALUE
    }

    private fun rankJapaneseQuery(query: String, row: YomitanTermEntity): Int {
        val q = query.trim()
        if (q.isBlank()) return Int.MAX_VALUE
        return when {
            row.expression == q -> 0
            row.reading == q -> 1
            row.expression.startsWith(q) -> 2
            row.reading.startsWith(q) -> 3
            else -> 4
        }
    }

    private fun buildGenericFtsQuery(rawQuery: String): String? {
        val tokens = rawQuery.trim()
            .split(Regex("""\s+"""))
            .mapNotNull { token -> sanitizeFtsToken(token)?.let { "$it*" } }
            .distinct()

        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " AND ")
    }

    private fun buildGlossaryOnlyFtsQuery(rawQuery: String): String? {
        val tokens = rawQuery.trim()
            .split(Regex("""\s+"""))
            .mapNotNull { token -> sanitizeFtsToken(token)?.let { "glossary:$it*" } }
            .distinct()

        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " AND ")
    }

    private fun sanitizeFtsToken(raw: String): String? {
        val trimmed = raw.trim().trim('"', '\'', '`')
        if (trimmed.isBlank()) return null

        // FTS query syntax is picky; keep it simple by stripping common operators.
        val cleaned = trimmed
            .replace(Regex("""[\*\^:\(\)\[\]{}!\\|&<>~]"""), "")
            .trim()

        return cleaned.takeIf { it.isNotBlank() }
    }
}
