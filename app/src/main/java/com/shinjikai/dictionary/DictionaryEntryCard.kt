package com.shinjikai.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.data.SearchItem

@Composable
fun DictionaryEntryCard(
    item: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewMaxLines: Int = 2,
    footer: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ShinjikaiUi.CardShape,
        colors = ShinjikaiUi.cardColors(),
        border = ShinjikaiUi.cardBorder(alpha = 0.58f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 13.dp, end = 12.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
            ) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = ShinjikaiUi.PillShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                    content = {}
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommonnessBadge(difficulty = item.difficulty)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.kana,
                            style = MaterialTheme.typography.titleSmall,
                            color = ShinjikaiUi.mutedTextColor(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.primaryWriting.ifBlank { item.kana },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val preview = formatOfflineSearchPreview(item.meaningSummary)
                if (preview.isNotBlank()) {
                    ShinjikaiPanel(
                        modifier = Modifier.fillMaxWidth(),
                        color = ShinjikaiUi.panelColor(alpha = 0.22f),
                        border = ShinjikaiUi.cardBorder(alpha = 0.22f)
                    ) {
                        Text(
                            text = forceRtlText(preview),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
                            textAlign = TextAlign.Right,
                            maxLines = previewMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                footer?.invoke()
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
            )
        }
    }
}
