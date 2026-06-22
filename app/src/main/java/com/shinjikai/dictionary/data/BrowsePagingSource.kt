package com.shinjikai.dictionary.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

private val BROWSE_BULLET_PREFIX_REGEX = Regex("""(?m)^\s*[\uD83D\uDD39\u25AA\u2022\u25CF\u25E6]\s*""")
private val BROWSE_MULTISPACE_REGEX = Regex("""\s{2,}""")

class BrowsePagingSource(
    private val yomitanDao: YomitanDao
) : PagingSource<Int, SearchItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceAtLeast(30)
        return runCatching {
            val rows = yomitanDao.browseTermsPaged(limit = limit, offset = offset)
            LoadResult.Page(
                data = rows.map(YomitanTermEntity::toBrowseSearchItem),
                prevKey = offset.takeIf { it > 0 }?.let { (it - limit).coerceAtLeast(0) },
                nextKey = (offset + rows.size).takeIf { rows.size == limit }
            )
        }.getOrElse { throwable ->
            LoadResult.Error(throwable)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SearchItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)
    }
}

internal fun YomitanTermEntity.toBrowseSearchItem(): SearchItem {
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
