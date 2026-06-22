package com.shinjikai.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.ui.ResultMode
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
    onRetryBundledImport: () -> Unit,
    onOpenDetails: (SearchItem) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val lazyResults = searchResults.collectAsLazyPagingItems()
    val resultsListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFieldReady by remember { mutableStateOf(false) }

    val refreshState = lazyResults.loadState.refresh
    val hasActiveSearch = uiState.resultMode != ResultMode.None
    val isRefreshing = hasActiveSearch && refreshState is LoadState.Loading
    val refreshError = (refreshState as? LoadState.Error)?.error?.message?.takeIf { hasActiveSearch }
    val showLanding = !hasActiveSearch && uiState.term.isBlank() && !isRefreshing && refreshError == null
    val showNoResults =
        hasActiveSearch && uiState.term.isNotBlank() && lazyResults.itemCount == 0 && !isRefreshing && refreshError == null
    val offlineDictionaryUnavailable = useOfflineMode && !hasOfflineDictionary
    val landingSuggestions = remember {
        listOf(
            "猫",
            "学校",
            "食べる",
            "電車",
            "友達",
            "水",
            "先生",
            "日本語",
            "かわいい",
            "勉強",
            "本",
            "旅行",
            "海"
        ).shuffled().take(7)
    }

    LaunchedEffect(searchFocusNonce, isSearchFieldReady) {
        if (searchFocusNonce > 0 && isSearchFieldReady) {
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(uiState.term, uiState.resultMode, uiState.activeCategoryId, useOfflineMode) {
        resultsListState.scrollToItem(0)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.activeCategoryName == null) {
                    SearchTopDock(
                        term = uiState.term,
                        activeCategoryName = null,
                        searchFocusNonce = searchFocusNonce,
                        onTermChange = { viewModel.term = it },
                        onRunSearch = {
                            focusManager.clearFocus()
                            viewModel.runSearch()
                        },
                        onClearTerm = { viewModel.runSearchForTerm("") },
                        onClearCategory = { viewModel.clearCategorySearch() },
                        focusRequester = searchFocusRequester,
                        onFieldReady = { isSearchFieldReady = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CategorySearchBanner(
                        label = uiState.activeCategoryName,
                        onClear = { viewModel.clearCategorySearch() }
                    )
                }

                if (isRefreshing) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                refreshError?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }

                if (offlineDictionaryUnavailable) {
                    OfflineDictionaryStateCard(
                        uiState = uiState,
                        onRetry = onRetryBundledImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f))
                } else {
                    AnimatedContent(
                    targetState = useOfflineMode,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val forward = targetState != initialState
                        (
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 240),
                                initialOffsetX = { fullWidth -> if (forward) fullWidth / 10 else -fullWidth / 10 }
                            ) + fadeIn(animationSpec = tween(durationMillis = 220))
                        ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 200),
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth / 12 else fullWidth / 12 }
                            ) + fadeOut(animationSpec = tween(durationMillis = 160))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "searchModeContent"
                ) {
                    when {
                        showLanding -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!useOfflineMode) {
                                    LandingSuggestions(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        appName = appName,
                                        suggestions = landingSuggestions,
                                        onSuggestionClick = { suggestion ->
                                            viewModel.term = suggestion
                                            focusManager.clearFocus()
                                            viewModel.runSearchForTerm(suggestion)
                                        }
                                    )
                                }

                                if (useOfflineMode && uiState.offlinePreviewItems.isNotEmpty()) {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                } else if (useOfflineMode) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
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
                        }

                        showNoResults -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.search_no_results),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                if (!useOfflineMode && uiState.activeCategoryId == null) {
                                    LandingSuggestions(
                                        modifier = Modifier.fillMaxWidth(),
                                        appName = appName,
                                        suggestions = landingSuggestions,
                                        title = stringResource(R.string.search_try_other_words),
                                        onSuggestionClick = { suggestion ->
                                            viewModel.term = suggestion
                                            focusManager.clearFocus()
                                            viewModel.runSearchForTerm(suggestion)
                                        }
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = resultsListState,
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    count = lazyResults.itemCount,
                                    key = { index -> lazyResults[index]?.id ?: "result-$index" }
                                ) { index ->
                                    val item = lazyResults[index] ?: return@items
                                    DictionaryEntryCard(
                                        item = item,
                                        onClick = { onOpenDetails(item) },
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        previewMaxLines = if (uiState.activeCategoryId != null) 1 else 2
                                    )
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
                }
            }

        }
    }
}

@Composable
private fun OfflineDictionaryStateCard(
    uiState: SearchUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (uiState.isImportingOfflineData) {
        stringResource(R.string.offline_dictionary_indexing_title)
    } else {
        stringResource(R.string.offline_dictionary_missing_title)
    }
    val message = uiState.offlineImportStatus
        ?: uiState.offlineImportPhase
        ?: if (uiState.isImportingOfflineData) {
            stringResource(R.string.offline_dictionary_indexing_message)
        } else {
            stringResource(R.string.offline_dictionary_missing_message)
        }

    ShinjikaiCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (uiState.offlineImportError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            textAlign = TextAlign.Right
        )
        if (uiState.isImportingOfflineData) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            )
        } else {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(stringResource(R.string.offline_dictionary_retry))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun SearchTopDock(
    term: String,
    activeCategoryName: String?,
    searchFocusNonce: Int,
    onTermChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onClearTerm: () -> Unit,
    onClearCategory: () -> Unit,
    focusRequester: FocusRequester,
    onFieldReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(searchFocusNonce) {
        if (searchFocusNonce > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

        Column(
            modifier = modifier.padding(vertical = if (isImeVisible) 2.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        activeCategoryName?.let {
            CategorySearchBanner(
                label = it,
                onClear = onClearCategory
            )
        }

        Surface(
            shape = ShinjikaiUi.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ShinjikaiUi.cardBorder(),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                OutlinedTextField(
                    value = term,
                    onValueChange = onTermChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { onFieldReady() },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onRunSearch() }),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.nav_search)
                        )
                    },
                    trailingIcon = {
                        if (term.isNotEmpty()) {
                            IconButton(onClick = onClearTerm) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.nav_clear)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LandingSuggestions(
    modifier: Modifier = Modifier,
    appName: String,
    suggestions: List<String>,
    title: String = stringResource(R.string.search_try_japanese_words),
    onSuggestionClick: (String) -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Surface(
                        onClick = { onSuggestionClick(suggestion) },
                        shape = ShinjikaiUi.PillShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflinePreviewCard(
    item: SearchItem,
    onClick: () -> Unit
) {
    DictionaryEntryCard(
        item = item,
        onClick = onClick,
        previewMaxLines = 2
    )
}
