package com.shinjikai.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.ui.ResultMode
import com.shinjikai.dictionary.ui.Screen
import com.shinjikai.dictionary.ui.SearchUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SearchScreenContent(
    appName: String,
    useOfflineMode: Boolean,
    hasOfflineDictionary: Boolean,
    searchFocusNonce: Int,
    viewModel: ShinjikaiViewModel,
    uiState: SearchUiState,
    searchResults: Flow<PagingData<SearchItem>>,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenDetails: (SearchItem) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val lazyResults = searchResults.collectAsLazyPagingItems()
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    var showOfflineDownloadPrompt by remember { mutableStateOf(false) }

    val refreshState = lazyResults.loadState.refresh
    val hasActiveSearch = uiState.resultMode != ResultMode.None
    val isRefreshing = hasActiveSearch && refreshState is LoadState.Loading
    val refreshError = (refreshState as? LoadState.Error)?.error?.message?.takeIf { hasActiveSearch }
    val showLanding = !hasActiveSearch && uiState.term.isBlank() && !isRefreshing && refreshError == null
    val showNoResults = hasActiveSearch && uiState.term.isNotBlank() && lazyResults.itemCount == 0 && !isRefreshing && refreshError == null

    LaunchedEffect(searchFocusNonce) {
        if (searchFocusNonce > 0) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(appName) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.activeCategoryName != null) {
                    CategorySearchBanner(
                        label = uiState.activeCategoryName.orEmpty(),
                        onClear = {
                            viewModel.clearCategorySearch()
                            focusManager.clearFocus()
                        }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.term,
                                onValueChange = { viewModel.term = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                                    .onFocusChanged { state -> isSearchFieldFocused = state.isFocused },
                                singleLine = true,
                                label = { Text(stringResource(R.string.search_hint)) },
                                shape = RoundedCornerShape(20.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        viewModel.runSearch()
                                    }
                                ),
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (uiState.term.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.term = ""
                                                    searchFocusRequester.requestFocus()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.nav_clear)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                viewModel.runSearch()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = stringResource(R.string.nav_search)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        SearchModeTabs(
                            useOfflineMode = useOfflineMode,
                            onModeSelected = { selectedOffline ->
                                if (selectedOffline && !hasOfflineDictionary) {
                                    showOfflineDownloadPrompt = true
                                } else if (selectedOffline != useOfflineMode) {
                                    viewModel.setUseOfflineMode(selectedOffline)
                                }
                            }
                        )
                    }
                }

                if (isRefreshing) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                refreshError?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }

                when {
                    showLanding -> {
                        if (useOfflineMode && uiState.offlinePreviewItems.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = uiState.offlinePreviewItems,
                                    key = { it.id }
                                ) { item ->
                                    OfflinePreviewCard(
                                        item = item,
                                        onClick = { onOpenDetails(item) }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    showNoResults -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_results),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                count = lazyResults.itemCount,
                                key = { index -> lazyResults[index]?.id ?: "result-$index" }
                            ) { index ->
                                val item = lazyResults[index] ?: return@items
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenDetails(item) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
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
                                                if (useOfflineMode) {
                                                    formatOfflineSearchPreview(item.meaningSummary)
                                                } else {
                                                    formatOnlineSearchPreview(item.meaningSummary)
                                                }
                                            ),
                                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                                            textAlign = TextAlign.Right,
                                            maxLines = if (useOfflineMode || uiState.activeCategoryId != null) 1 else Int.MAX_VALUE,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                        )
                                    }
                                }
                            }

                            if (lazyResults.loadState.append is LoadState.Loading) {
                                item(key = "append-loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            (lazyResults.loadState.append as? LoadState.Error)?.let { appendError ->
                                item(key = "append-error") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(onClick = { lazyResults.retry() }) {
                                            Text(appendError.error.message ?: stringResource(R.string.detail_retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PrimaryBottomBar(
                currentScreen = Screen.Search,
                isSearchFocused = isSearchFieldFocused,
                onSearchClick = onSearchClick,
                onHistoryClick = onHistoryClick,
                onBookmarksClick = onBookmarksClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    if (showOfflineDownloadPrompt) {
        AlertDialog(
            onDismissRequest = { showOfflineDownloadPrompt = false },
            title = { Text(stringResource(R.string.offline_download_prompt_title)) },
            text = { Text(stringResource(R.string.offline_download_prompt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOfflineDownloadPrompt = false
                        onSettingsClick()
                    }
                ) {
                    Text(stringResource(R.string.offline_download_prompt_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineDownloadPrompt = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun OfflinePreviewCard(
    item: SearchItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.kana,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.primaryWriting.ifBlank { item.kana },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                CommonnessBadge(difficulty = item.difficulty)
            }
            Text(
                text = forceRtlText(formatOfflineSearchPreview(item.meaningSummary)),
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Right,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SearchModeTabs(
    useOfflineMode: Boolean,
    onModeSelected: (Boolean) -> Unit
) {
    val options = listOf(
        stringResource(R.string.mode_offline) to true,
        stringResource(R.string.mode_online) to false
    )
    val selectedBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    val selectedColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            options.forEach { (label, isOfflineOption) ->
                val selected = useOfflineMode == isOfflineOption
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) selectedBackground else Color.Transparent,
                    onClick = { onModeSelected(isOfflineOption) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) selectedColor else unselectedColor
                        )
                    }
                }
            }
        }
    }
}
