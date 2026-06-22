package com.shinjikai.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BrowseScreenContent(
    viewModel: ShinjikaiViewModel,
    browseFlow: Flow<PagingData<SearchItem>>,
    totalEntries: Int,
    onOpenDetails: (SearchItem) -> Unit
) {
    val entries = browseFlow.collectAsLazyPagingItems()
    val refreshState = entries.loadState.refresh

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_title)) },
                actions = {
                    FilledTonalIconButton(
                        onClick = { viewModel.openRandomDictionaryEntry(onOpenDetails) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.browse_random)
                        )
                    }
                },
                colors = shinjikaiTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "browse-header") {
                ShinjikaiCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ShinjikaiUi.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f)
                    ),
                    border = ShinjikaiUi.cardBorder(alpha = 0.28f)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.browse_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.browse_count, totalEntries),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ShinjikaiUi.mutedTextColor(),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (refreshState is LoadState.Loading) {
                item(key = "browse-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (refreshState is LoadState.NotLoading && entries.itemCount == 0) {
                item(key = "browse-empty") {
                    Text(
                        text = stringResource(R.string.browse_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ShinjikaiUi.mutedTextColor(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            (refreshState as? LoadState.Error)?.let { error ->
                item(key = "browse-refresh-error") {
                    TextButton(
                        onClick = { entries.retry() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(error.error.message ?: stringResource(R.string.detail_retry))
                    }
                }
            }

            items(
                count = entries.itemCount,
                key = { index -> entries[index]?.id ?: "browse-$index" }
            ) { index ->
                val item = entries[index] ?: return@items
                DictionaryEntryCard(
                    item = item,
                    onClick = { onOpenDetails(item) },
                    previewMaxLines = 2
                )
            }

            if (entries.loadState.append is LoadState.Loading) {
                item(key = "browse-append-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            (entries.loadState.append as? LoadState.Error)?.let { appendError ->
                item(key = "browse-append-error") {
                    TextButton(
                        onClick = { entries.retry() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(appendError.error.message ?: stringResource(R.string.detail_retry))
                    }
                }
            }
        }
    }
}
