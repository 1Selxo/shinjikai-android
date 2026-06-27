package com.shinjikai.dictionary.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

private val BROWSE_BULLET_PREFIX_REGEX = Regex("""(?m)^\s*[\uD83D\uDD39\u25AA\u2022\u25CF\u25E6]\s*""")
private val BROWSE_MULTISPACE_REGEX = Regex("""\s{2,}""")

class BrowsePagingSource(
    private val yomitanDao: YomitanDao
) : PagingSource<BrowsePageKey, SearchItem>() {
    override suspend fun load(
        params: LoadParams<BrowsePageKey>
    ): LoadResult<BrowsePageKey, SearchItem> {
        val key = params.key
        val limit = params.loadSize.coerceAtLeast(30)
        return runCatching {
            val rows = yomitanDao.browseTermsAfter(
                lastReading = key?.reading,
                lastExpression = key?.expression,
                lastId = key?.id,
                limit = limit
            )
            LoadResult.Page(
                data = rows.map(YomitanTermListRow::toBrowseSearchItem),
                prevKey = null,
                nextKey = rows.lastOrNull()
                    ?.takeIf { rows.size == limit }
                    ?.let { row ->
                        BrowsePageKey(
                            reading = row.reading,
                            expression = row.expression,
                            id = row.id
                        )
                    }
            )
        }.getOrElse { throwable ->
            LoadResult.Error(throwable)
        }
    }

    override fun getRefreshKey(state: PagingState<BrowsePageKey, SearchItem>): BrowsePageKey? = null
}

data class BrowsePageKey(
    val reading: String,
    val expression: String,
    val id: Int
)

internal fun YomitanTermListRow.toBrowseSearchItem(): SearchItem {
    return SearchItem(
        id = id,
        kana = reading,
        writings = listOf(Writing(text = expression)),
        meaningSummary = glossary
            .replace(BROWSE_BULLET_PREFIX_REGEX, "")
            .replace("\n", " ")
            .replace(BROWSE_MULTISPACE_REGEX, " ")
            .trim(),
        difficulty = difficulty
    )
}
