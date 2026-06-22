package com.shinjikai.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.data.RecentSearchEntry
import com.shinjikai.dictionary.ui.SearchUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HistoryScreenContent(
    uiState: SearchUiState,
    viewModel: ShinjikaiViewModel,
    onOpenHistoryTerm: (String) -> Unit,
) {
    var pendingDeleteTerm by remember { mutableStateOf<String?>(null) }
    var pendingClearAllHistory by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                colors = shinjikaiTopAppBarColors()
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.recentSearches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "history-header") {
                        Surface(
                            shape = ShinjikaiUi.CardShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            border = ShinjikaiUi.cardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_recent_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.search_recent_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                                    )
                                }
                                IconButton(onClick = { pendingClearAllHistory = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = stringResource(R.string.search_clear_history)
                                    )
                                }
                            }
                        }
                    }

                    items(uiState.recentSearches, key = { it.term.lowercase(Locale.ROOT) }) { historyEntry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShinjikaiUi.CardShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = ShinjikaiUi.cardBorder(),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenHistoryTerm(historyEntry.term)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ShinjikaiUi.panelColor(alpha = 0.42f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = historyEntry.term,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    formatHistoryDate(historyEntry)?.let { formattedDate ->
                                        Text(
                                            text = formattedDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { pendingDeleteTerm = historyEntry.term }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.search_remove_history),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    pendingDeleteTerm?.let { historyTerm ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTerm = null },
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_message, historyTerm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeRecentSearch(historyTerm)
                        pendingDeleteTerm = null
                    }
                ) {
                    Text(stringResource(R.string.search_remove_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTerm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (pendingClearAllHistory) {
        AlertDialog(
            onDismissRequest = { pendingClearAllHistory = false },
            title = { Text(stringResource(R.string.history_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_all_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecentSearches()
                        pendingClearAllHistory = false
                    }
                ) {
                    Text(stringResource(R.string.search_clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearAllHistory = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun formatHistoryDate(historyEntry: RecentSearchEntry): String? {
    val searchedAtEpochMs = historyEntry.searchedAtEpochMs ?: return null
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(searchedAtEpochMs))
}
