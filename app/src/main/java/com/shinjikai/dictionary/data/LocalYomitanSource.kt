package com.shinjikai.dictionary.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.File

private val BULLET_PREFIX_REGEX = Regex("""(?m)^\s*[\uD83D\uDD39\u25AA\u2022\u25CF\u25E6]\s*""")
private val MULTISPACE_REGEX = Regex("""\s{2,}""")
private val ARABIC_DIACRITICS_REGEX = Regex("""[\u064B-\u065F\u0670\u06D6-\u06ED]""")
private val ARABIC_ALEF_VARIANTS_REGEX = Regex("""[أإآٱ]""")
private val NON_LETTER_NUMBER_SPACE_REGEX = Regex("""[^\p{L}\p{N}\s]""")
private val SENTENCE_SPLIT_REGEX = Regex("""[\s\u3000,،;；、。.!?！？()\[\]{}<>\"“”'‘’/\\]+""")
private val JAPANESE_PARTICLES = listOf("から", "まで", "より", "だけ", "ほど", "など", "って", "では", "には", "は", "が", "を", "に", "で", "と", "へ", "も", "や", "か", "の")

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
            val normalizedQueries = if (!isArabic) buildNormalizedJapaneseQueries(query) else emptyList()
            val japaneseSearchSegments = if (!isArabic) {
                normalizedQueries.flatMap(::extractSearchSegments).distinct()
            } else {
                emptyList()
            }
            val japaneseSearchCandidates = if (!isArabic) {
                normalizedQueries.flatMap(::buildJapaneseSearchCandidates).distinct()
            } else {
                emptyList()
            }
            val ftsQueries = if (!isArabic) {
                normalizedQueries.flatMap(::buildGenericFtsQueries).distinct()
            } else {
                emptyList()
            }

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
            } else if (ftsQueries.isNotEmpty()) {
                val desiredCandidates = ((pageIndex + 1) * pageSize * 5).coerceAtLeast(250)
                val perQueryLimit = (desiredCandidates / ftsQueries.size).coerceAtLeast(pageSize).coerceAtMost(1000)
                val ftsRows = ftsQueries
                    .flatMap { ftsQuery ->
                        yomitanDao.searchFts(
                            matchQuery = ftsQuery,
                            limit = perQueryLimit
                        )
                    }
                allRows = ftsRows
                    .sortedWith(
                        compareByDescending<YomitanTermEntity> { scoreJapaneseSegmentMatches(japaneseSearchSegments, it) }
                            .thenBy { rankJapaneseQuery(japaneseSearchCandidates, it) }
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

    private fun buildGenericFtsQueries(rawQuery: String): List<String> {
        return extractSearchSegments(rawQuery)
            .mapNotNull(::buildSegmentFtsQuery)
            .distinct()
    }

    private fun buildNormalizedJapaneseQueries(rawQuery: String): List<String> {
        return buildList {
            add(rawQuery)
            RomajiConverter.toHiraganaIfRomaji(rawQuery)
                ?.takeIf { it != rawQuery }
                ?.let(::add)
        }.distinct()
    }

    private fun buildJapaneseSearchCandidates(rawQuery: String): List<String> {
        return extractSearchSegments(rawQuery)
            .asSequence()
            .flatMap { token -> expandJapaneseTokenVariants(token).asSequence() }
            .distinct()
            .toList()
    }

    private fun buildSegmentFtsQuery(token: String): String? {
        val variants = expandJapaneseTokenVariants(token)
            .mapNotNull(::sanitizeFtsToken)
            .distinct()

        return when {
            variants.isEmpty() -> null
            variants.size == 1 -> "${variants.first()}*"
            else -> variants.joinToString(
                separator = " OR ",
                prefix = "(",
                postfix = ")"
            ) { "$it*" }
        }
    }

    private fun extractSearchSegments(rawQuery: String): List<String> {
        return rawQuery.trim()
            .split(SENTENCE_SPLIT_REGEX)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { token -> splitJapaneseSentenceToken(token).asSequence() }
            .distinct()
            .toList()
    }

    private fun splitJapaneseSentenceToken(token: String): List<String> {
        if (token.isBlank()) return emptyList()
        if (!token.any(::isJapaneseCharacter)) return listOf(token)

        val parts = mutableListOf<String>()
        var index = 0
        var segmentStart = 0

        while (index < token.length) {
            val particle = JAPANESE_PARTICLES.firstOrNull { candidate ->
                token.startsWith(candidate, index) &&
                    index > segmentStart &&
                    index + candidate.length < token.length &&
                    isJapaneseCharacter(token[index - 1]) &&
                    isJapaneseCharacter(token[index + candidate.length])
            }

            if (particle != null) {
                token.substring(segmentStart, index)
                    .takeIf { it.isNotBlank() }
                    ?.let(parts::add)
                index += particle.length
                segmentStart = index
            } else {
                index += 1
            }
        }

        token.substring(segmentStart)
            .takeIf { it.isNotBlank() }
            ?.let(parts::add)

        return if (parts.isEmpty()) listOf(token) else parts
    }

    private fun expandJapaneseTokenVariants(token: String): List<String> {
        if (token.isBlank()) return emptyList()

        val variants = linkedSetOf<String>()
        variants.addAll(JapaneseDeinflector.generateCandidates(token))

        val trailingKanaLength = token.reversed().takeWhile(::isKana).length
        if (trailingKanaLength in 1 until token.length) {
            val stem = token.dropLast(trailingKanaLength)
            val trailingKana = token.takeLast(trailingKanaLength)
            val trailingVariants = JapaneseDeinflector.generateCandidates(trailingKana)
            if (shouldSearchBareStem(token, stem, trailingKanaLength, trailingVariants)) {
                variants.add(stem)
            }
            variants.addAll(trailingVariants)
            trailingVariants.forEach { variants.add(stem + it) }
        }

        return variants.toList()
    }

    private fun scoreJapaneseSegmentMatches(segments: List<String>, row: YomitanTermEntity): Int {
        if (segments.isEmpty()) return 0
        return segments.sumOf { segment ->
            expandJapaneseTokenVariants(segment).maxOfOrNull { variant ->
                when {
                    row.expression == variant -> 120
                    row.reading == variant -> 110
                    row.expression.startsWith(variant) -> 80
                    row.reading.startsWith(variant) -> 70
                    row.expression.contains(variant) -> 40
                    row.reading.contains(variant) -> 30
                    else -> 0
                }
            } ?: 0
        }
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

    private fun isJapaneseCharacter(ch: Char): Boolean {
        return ch in '\u3040'..'\u30FF' || ch in '\u3400'..'\u9FFF' || ch == '々'
    }

    private fun isKana(ch: Char): Boolean {
        return ch in '\u3040'..'\u30FF'
    }

    private fun shouldSearchBareStem(
        token: String,
        stem: String,
        trailingKanaLength: Int,
        trailingVariants: List<String>
    ): Boolean {
        if (stem.isBlank()) return false
        if (trailingKanaLength < 2) return false
        if (!stem.any { !isKana(it) }) return false
        val trailingKana = token.takeLast(trailingKanaLength)
        return trailingVariants.any { it != trailingKana }
    }

    private fun parseCategoryRefs(raw: String): List<CategoryRef> {
        val array = gson.fromJson(raw, JsonArray::class.java) ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, CategoryRef::class.java) }.getOrNull()
        }
    }
}
