package com.shinjikai.dictionary.data

class LocalYomitanSource(
    private val yomitanDao: YomitanDao
) : DictionarySource {
    override suspend fun searchWords(term: String, page: Int): Result<SearchWordsResponse> {
        val query = term.trim()
        if (query.isBlank()) return Result.success(SearchWordsResponse(items = emptyList()))
        if (page > 0) {
            return Result.success(
                SearchWordsResponse(
                    items = emptyList(),
                    page = page,
                    pageCount = 1,
                    totalCount = 0
                )
            )
        }

        return runCatching {
            val rows = if (isArabicQuery(query)) {
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

                baseRows
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
                    .take(80)
                    .toList()
            } else {
                val ftsQuery = buildGenericFtsQuery(query)
                val ftsRows = ftsQuery?.let { matchQuery ->
                    yomitanDao.searchFts(matchQuery, limit = 250)
                }.orEmpty()

                val baseRows = if (ftsRows.isNotEmpty()) {
                    ftsRows.sortedWith(compareBy<YomitanTermEntity> { rankJapaneseQuery(query, it) }
                        .thenBy { it.id })
                } else {
                    yomitanDao.search(
                        term = query,
                        prefix = "${query}%"
                    )
                }

                baseRows.distinctBy { row ->
                    "${row.expression}\u0000${row.reading}\u0000${row.glossary}"
                }.take(80)
            }

            SearchWordsResponse(
                items = rows.map { row ->
                    SearchItem(
                        id = row.id,
                        kana = row.reading,
                        writings = listOf(Writing(text = row.expression)),
                        meaningSummary = buildSearchPreview(row.glossary),
                        jlpt = 0
                    )
                },
                page = 0,
                pageCount = if (rows.isEmpty()) 0 else 1,
                totalCount = rows.size
            )
        }
    }

    override suspend fun loadWordDetails(id: Int): Result<WordDetailsResponse> {
        return runCatching {
            val row = yomitanDao.getById(id)
                ?: error("No local entry found for id=$id")
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
        return Result.success(LoadCategoriesResponse())
    }

    override suspend fun loadCategory(id: Int, page: Int): Result<LoadCategoryResponse> {
        return Result.success(
            LoadCategoryResponse(
                category = CategoryRef(id = id, name = "Local Category"),
                members = SearchWordsResponse()
            )
        )
    }

    private fun buildSearchPreview(glossary: String): String {
        return cleanGlossary(glossary)
            .replace("\n", " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    private fun cleanGlossary(glossary: String): String {
        return glossary
            .replace(Regex("""(?m)^\s*[ðŸ”¹â–ªâ€¢â—â—¦]\s*"""), "")
            .trim()
    }

    private fun isArabicQuery(text: String): Boolean {
        return text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }
    }

    private fun normalizeArabic(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(Regex("""[\u064B-\u065F\u0670\u06D6-\u06ED]"""), "")
            .replace("Ù€", "")
            .replace(Regex("""[Ø£Ø¥Ø¢Ù±]"""), "Ø§")
            .replace("Ù‰", "ÙŠ")
            .replace("Ø¤", "Ùˆ")
            .replace("Ø¦", "ÙŠ")
            .replace("Ø©", "Ù‡")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
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
