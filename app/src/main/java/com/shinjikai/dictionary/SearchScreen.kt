package com.shinjikai.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
    onSettingsClick: () -> Unit,
    onOpenDetails: (SearchItem) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val lazyResults = searchResults.collectAsLazyPagingItems()
    val resultsListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFieldReady by remember { mutableStateOf(false) }
    var showOfflineDownloadPrompt by remember { mutableStateOf(false) }

    val refreshState = lazyResults.loadState.refresh
    val hasActiveSearch = uiState.resultMode != ResultMode.None
    val isRefreshing = hasActiveSearch && refreshState is LoadState.Loading
    val refreshError = (refreshState as? LoadState.Error)?.error?.message?.takeIf { hasActiveSearch }
    val showLanding = !hasActiveSearch && uiState.term.isBlank() && !isRefreshing && refreshError == null
    val showNoResults =
        hasActiveSearch && uiState.term.isNotBlank() && lazyResults.itemCount == 0 && !isRefreshing && refreshError == null
    val contentSwipeModifier = Modifier.pointerInput(useOfflineMode, hasOfflineDictionary) {
        val swipeThresholdPx = 24.dp.toPx()
        var accumulatedDrag = 0f
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                accumulatedDrag += dragAmount
                change.consumePositionChange()
            },
            onDragEnd = {
                if (kotlin.math.abs(accumulatedDrag) >= swipeThresholdPx) {
                    val targetOfflineMode = !useOfflineMode
                    if (targetOfflineMode && !hasOfflineDictionary) {
                        showOfflineDownloadPrompt = true
                    } else {
                        viewModel.setUseOfflineMode(targetOfflineMode)
                    }
                }
                accumulatedDrag = 0f
            },
            onDragCancel = {
                accumulatedDrag = 0f
            }
        )
    }
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
                        useOfflineMode = useOfflineMode,
                        activeCategoryName = null,
                        searchFocusNonce = searchFocusNonce,
                        onTermChange = { viewModel.term = it },
                        onRunSearch = {
                            focusManager.clearFocus()
                            viewModel.runSearch()
                        },
                        onClearTerm = { viewModel.runSearchForTerm("") },
                        onClearCategory = { viewModel.clearCategorySearch() },
                        onModeSelected = { selectedOffline ->
                            if (selectedOffline && !hasOfflineDictionary) {
                                showOfflineDownloadPrompt = true
                            } else if (selectedOffline != useOfflineMode) {
                                viewModel.setUseOfflineMode(selectedOffline)
                            }
                        },
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
                                modifier = contentSwipeModifier.fillMaxSize(),
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
                                modifier = contentSwipeModifier.fillMaxSize(),
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
                                modifier = contentSwipeModifier.fillMaxSize(),
                                state = resultsListState,
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
            }

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun SearchTopDock(
    term: String,
    useOfflineMode: Boolean,
    activeCategoryName: String?,
    searchFocusNonce: Int,
    onTermChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onClearTerm: () -> Unit,
    onClearCategory: () -> Unit,
    onModeSelected: (Boolean) -> Unit,
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
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
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
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
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

                if (activeCategoryName == null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(min = 210.dp, max = 250.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            SearchModeTabs(
                                useOfflineMode = useOfflineMode,
                                onModeSelected = onModeSelected
                            )
                        }
                    }
                }
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
                        shape = RoundedCornerShape(999.dp),
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
    val selectedBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val selectedColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (label, isOfflineOption) ->
                val selected = useOfflineMode == isOfflineOption
                val containerColor = animateColorAsState(
                    targetValue = if (selected) selectedBackground else Color.Transparent,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 520f
                    ),
                    label = "searchModeContainer"
                )
                val textScale = animateFloatAsState(
                    targetValue = if (selected) 1f else 0.96f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 620f
                    ),
                    label = "searchModeTextScale"
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = containerColor.value,
                    tonalElevation = if (selected) 0.5.dp else 0.dp,
                    shadowElevation = 0.dp,
                    onClick = { onModeSelected(isOfflineOption) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) selectedColor else unselectedColor,
                            modifier = Modifier.scale(textScale.value)
                        )
                    }
                }
            }
        }
    }
}
