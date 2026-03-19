package com.shinjikai.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.BookmarkItem
import com.shinjikai.dictionary.ui.BookmarksUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookmarksScreenContent(
    viewModel: ShinjikaiViewModel,
    uiState: BookmarksUiState,
    bookmarkFlow: Flow<PagingData<BookmarkItem>>,
    onGoBack: () -> Unit
) {
    val bookmarks = bookmarkFlow.collectAsLazyPagingItems()
    val locale = Locale.getDefault()
    val fallbackBookmarkLabel = stringResource(R.string.detail_word_fallback)
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("HH:mm", locale)
    }
    val allIds = uiState.items.map { it.id }.toSet()

    LaunchedEffect(uiState.isEditMode, uiState.items.size) {
        viewModel.pruneBookmarkSelection(if (uiState.isEditMode) allIds else emptySet())
    }

    fun localDateOf(epochMs: Long): LocalDate {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    fun localTimeLabel(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditMode) {
                            stringResource(R.string.bookmarks_selected_count, uiState.selectedIds.size)
                        } else {
                            stringResource(R.string.bookmarks_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        val isAllSelected = allIds.isNotEmpty() && uiState.selectedIds.size == allIds.size
                        IconButton(
                            onClick = {
                                if (isAllSelected) {
                                    viewModel.pruneBookmarkSelection(emptySet())
                                } else {
                                    val missingIds = allIds - uiState.selectedIds
                                    missingIds.forEach(viewModel::toggleBookmarkSelection)
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ClearAll, contentDescription = stringResource(R.string.bookmarks_select_all))
                        }
                        IconButton(
                            onClick = {
                                if (uiState.selectedIds.isNotEmpty()) {
                                    viewModel.requestDeleteBookmarks(uiState.selectedIds)
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.bookmarks_delete))
                        }
                        IconButton(onClick = { viewModel.updateBookmarkEditMode(false) }) {
                            Icon(imageVector = Icons.Default.Done, contentDescription = stringResource(R.string.bookmarks_done))
                        }
                    } else {
                        IconButton(onClick = { viewModel.updateBookmarkEditMode(true) }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.bookmarks_manage))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (bookmarks.loadState.refresh is LoadState.NotLoading && bookmarks.itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.bookmarks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = bookmarks.itemCount,
                    key = { index -> bookmarks[index]?.id ?: "bookmark-$index" }
                ) { index ->
                    val bookmark = bookmarks[index] ?: return@items
                    val previous = if (index > 0) bookmarks.peek(index - 1) else null
                    val thisDate = localDateOf(bookmark.createdAt)
                    val prevDate = previous?.let { localDateOf(it.createdAt) }
                    val showHeader = prevDate == null || prevDate != thisDate
                    val item = bookmark.item
                    val isSelected = uiState.selectedIds.contains(bookmark.id)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (showHeader) {
                            Text(
                                text = thisDate.format(dateFormatter),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (uiState.isEditMode) {
                                        viewModel.toggleBookmarkSelection(bookmark.id)
                                    } else {
                                        viewModel.openBookmarkedDetails(item)
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.kana,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.primaryWriting.ifBlank { item.kana },
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        CommonnessBadge(difficulty = item.difficulty)
                                    }
                                    Text(
                                        text = forceRtlText(
                                            if (uiState.useOfflineMode) {
                                                formatOfflineSearchPreview(item.meaningSummary)
                                            } else {
                                                item.meaningSummary
                                            }
                                        ),
                                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                                        textAlign = TextAlign.Right,
                                        maxLines = if (uiState.useOfflineMode || uiState.activeCategoryId != null) 1 else Int.MAX_VALUE,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.bookmarks_added_at, localTimeLabel(bookmark.createdAt)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                    )
                                }

                                if (uiState.isEditMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleBookmarkSelection(bookmark.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        uiState.pendingDeletionIds?.let { ids ->
            val idsList = ids.toList()
            val label = if (idsList.size == 1) {
                val firstId = idsList.first()
                val target = uiState.items.firstOrNull { it.id == firstId }?.item
                (target?.primaryWriting ?: "").ifBlank {
                    (target?.kana ?: "").ifBlank { fallbackBookmarkLabel.format(firstId) }
                }
            } else {
                stringResource(R.string.bookmarks_delete_count, idsList.size)
            }

            AlertDialog(
                onDismissRequest = { viewModel.pendingBookmarkDeletionIds = null },
                title = { Text(stringResource(R.string.bookmarks_delete_confirmation_title)) },
                text = { Text(label) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteBookmarks(idsList) }) {
                        Text(stringResource(R.string.bookmarks_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.pendingBookmarkDeletionIds = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
