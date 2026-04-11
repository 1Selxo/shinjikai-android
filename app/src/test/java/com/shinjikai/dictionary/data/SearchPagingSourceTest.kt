package com.shinjikai.dictionary.data

import androidx.paging.PagingSource
import com.shinjikai.dictionary.ui.ResultMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingSourceTest {
    @Test
    fun `search mode maps paging keys from repository response`() = runTest {
        val source = SearchPagingSource(
            repository = ShinjikaiRepository(
                FakeDictionarySource(
                    searchResponse = SearchWordsResponse(
                        items = listOf(SearchItem(id = 11), SearchItem(id = 12)),
                        page = 1,
                        pageCount = 3,
                        totalCount = 6
                    )
                )
            ),
            spec = SearchRequestSpec(mode = ResultMode.Search, query = "食べる")
        )

        val result = source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 30, placeholdersEnabled = false))

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(11, 12), page.data.map { it.id })
        assertEquals(0, page.prevKey)
        assertEquals(2, page.nextKey)
    }

    @Test
    fun `category mode without category id returns load error`() = runTest {
        val source = SearchPagingSource(
            repository = ShinjikaiRepository(FakeDictionarySource()),
            spec = SearchRequestSpec(mode = ResultMode.Category)
        )

        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false))

        assertTrue(result is PagingSource.LoadResult.Error)
        assertTrue((result as PagingSource.LoadResult.Error).throwable is IllegalStateException)
    }

    @Test
    fun `repository failures bubble up as paging errors`() = runTest {
        val expected = IllegalArgumentException("boom")
        val source = SearchPagingSource(
            repository = ShinjikaiRepository(FakeDictionarySource(searchFailure = expected)),
            spec = SearchRequestSpec(mode = ResultMode.Search, query = "abc")
        )

        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false))

        assertEquals(expected, (result as PagingSource.LoadResult.Error).throwable)
    }

    private class FakeDictionarySource(
        private val searchResponse: SearchWordsResponse = SearchWordsResponse(),
        private val searchFailure: Throwable? = null
    ) : DictionarySource {
        override suspend fun searchWords(term: String, page: Int): Result<SearchWordsResponse> {
            return searchFailure?.let { Result.failure(it) } ?: Result.success(searchResponse)
        }

        override suspend fun loadWordDetails(id: Int): Result<WordDetailsResponse> {
            error("Not needed in this test")
        }

        override suspend fun loadCategories(): Result<LoadCategoriesResponse> {
            error("Not needed in this test")
        }

        override suspend fun loadCategory(id: Int, page: Int): Result<LoadCategoryResponse> {
            error("Not needed in this test")
        }
    }
}
