package com.shinjikai.dictionary.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.File

private val BULLET_PREFIX_REGEX = Regex("""(?m)^\s*[\uD83D\uDD39\u25AA\u2022\u25CF\u25E6]\s*""")
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
            val japaneseSearchCandidates = if (!isArabic) buildJapaneseSearchCandidates(query) else emptyList()
            val ftsQuery = if (!isArabic) buildGenericFtsQuery(query) else null

            val allRows: List<YomitanTermEntity>
            val pagedRowsDirect: List<YomitanTermEntity>?
            val totalCountDirect: Int?

            if (isArabic) {
                val normalizedQuery = normalizeArabic(query)
                val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

                val ftsCandidates = buildGlossaryOnlyFtsQuery(query)
                    ?.let { glossaryFtsQuery ->
                        yomitanDao.searchGlossaryFts(glossaryFtsQuery, limit = 2500)
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
                        compareBy<YomitanTermEntity> { rankJapaneseQuery(japaneseSearchCandidates, it) }
                            .thenBy { it.id }
                    )
                    .distinctBy { row ->
                        "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                    }
                    .toList()
                pagedRowsDirect = null
                totalCountDirect = null
            } else {
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
                items = pageRows.map(::toSearchItem),
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
                LoadCategoriesResponse(categories = parseCategoryRefs(categoriesJson))
            }
        }
    }

    override suspend fun loadCategory(id: Int, page: Int): Result<LoadCategoryResponse> {
        return runCatching {
            val categories = loadCategories().getOrThrow().categories
            val category = categories.firstOrNull { it.id == id } ?: CategoryRef(id = id, name = "تصنيف محلي")
            val pageIndex = page.coerceAtLeast(0)
            val pageSize = 80
            val offset = pageIndex * pageSize
            val totalCount = yomitanDao.countCategoryTerms(categoryId = id)
            val pageCount = if (totalCount == 0) 0 else ((totalCount + pageSize - 1) / pageSize)
            val pageRows = yomitanDao.loadCategoryTermsPaged(
                categoryId = id,
                limit = pageSize,
                offset = offset
            )

            LoadCategoryResponse(
                category = category,
                members = SearchWordsResponse(
                    items = pageRows.map(::toSearchItem),
                    page = pageIndex,
                    pageCount = pageCount,
                    totalCount = totalCount
                )
            )
        }
    }

    private fun toSearchItem(row: YomitanTermEntity): SearchItem {
        return SearchItem(
            id = row.id,
            kana = row.reading,
            writings = listOf(Writing(text = row.expression)),
            meaningSummary = buildSearchPreview(row.glossary),
            jlpt = 0,
            difficulty = row.difficulty
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
            .replace("ـ", "")
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

        val index = glossary.indexOf(query)
        return if (index >= 0) 100 + index else Int.MAX_VALUE
    }

    private fun rankJapaneseQuery(candidates: List<String>, row: YomitanTermEntity): Int {
        if (candidates.isEmpty()) return Int.MAX_VALUE
        candidates.forEachIndexed { index, candidate ->
            val baseRank = index * 10
            when {
                row.expression == candidate -> return baseRank
                row.reading == candidate -> return baseRank + 1
                row.expression.startsWith(candidate) -> return baseRank + 2
                row.reading.startsWith(candidate) -> return baseRank + 3
            }
        }
        return candidates.size * 10 + 4
    }

    private fun buildGenericFtsQuery(rawQuery: String): String? {
        val tokens = rawQuery.trim()
            .split(Regex("""\s+"""))
            .mapNotNull { token ->
                val variants = JapaneseDeinflector.generateCandidates(token)
                    .mapNotNull(::sanitizeFtsToken)
                    .distinct()
                when {
                    variants.isEmpty() -> null
                    variants.size == 1 -> "${variants.first()}*"
                    else -> variants.joinToString(
                        separator = " OR ",
                        prefix = "(",
                        postfix = ")"
                    ) { "$it*" }
                }
            }
            .distinct()

        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " AND ")
    }

    private fun buildJapaneseSearchCandidates(rawQuery: String): List<String> {
        return rawQuery.trim()
            .split(Regex("""\s+"""))
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { token -> JapaneseDeinflector.generateCandidates(token).asSequence() }
            .distinct()
            .toList()
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

        val cleaned = trimmed
            .replace(Regex("""[\*\^:\(\)\[\]{}!\\|&<>~]"""), "")
            .trim()

        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun parseCategoryRefs(raw: String): List<CategoryRef> {
        val array = gson.fromJson(raw, JsonArray::class.java) ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, CategoryRef::class.java) }.getOrNull()
        }
    }
}
