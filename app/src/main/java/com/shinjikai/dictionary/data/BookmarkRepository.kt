package com.shinjikai.dictionary.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {
    fun pagedFlow(pageSize: Int = 30): Flow<PagingData<SearchItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                prefetchDistance = pageSize / 2,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { bookmarkDao.pagingSource() }
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toSearchItem() }
        }
    }

    fun observeBookmarkedIds(): Flow<Set<Int>> {
        return bookmarkDao.observeIds().map { it.toSet() }
    }

    suspend fun getAll(): List<BookmarkItem> {
        return bookmarkDao.getAll().map { entity -> entity.toBookmarkItem() }
    }

    suspend fun upsert(item: SearchItem) {
        bookmarkDao.upsert(
            BookmarkEntity(
                id = item.id,
                primaryWriting = item.primaryWriting,
                kana = item.kana,
                meaningSummary = item.meaningSummary
            )
        )
    }

    suspend fun deleteById(id: Int) {
        bookmarkDao.deleteById(id)
    }

    suspend fun deleteByIds(ids: List<Int>) {
        if (ids.isEmpty()) return
        bookmarkDao.deleteByIds(ids)
    }

    private fun BookmarkEntity.toSearchItem(): SearchItem {
        return SearchItem(
            id = id,
            kana = kana,
            writings = listOf(Writing(primaryWriting)),
            meaningSummary = meaningSummary
        )
    }

    private fun BookmarkEntity.toBookmarkItem(): BookmarkItem {
        return BookmarkItem(
            item = toSearchItem(),
            createdAt = createdAt
        )
    }
}