package com.shinjikai.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.ui.Screen

@Composable
fun PrimaryBottomBar(
    currentScreen: Screen,
    isSearchFocused: Boolean = false,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val shape = RoundedCornerShape(28.dp)
    val selectedPillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 60.dp, vertical = 8.dp),
        shape = shape,
        color = containerColor,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            BottomBarIcon(
                selected = currentScreen == Screen.Search,
                selectedContainerColor = if (currentScreen == Screen.Search || isSearchFocused) selectedPillColor else Color.Transparent,
                imageTint = if (currentScreen == Screen.Search || isSearchFocused) activeColor else inactiveColor,
                onClick = onSearchClick
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            BottomBarIcon(
                selected = currentScreen == Screen.History,
                selectedContainerColor = if (currentScreen == Screen.History) selectedPillColor else Color.Transparent,
                imageTint = if (currentScreen == Screen.History) activeColor else inactiveColor,
                onClick = onHistoryClick
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            BottomBarIcon(
                selected = currentScreen == Screen.Bookmarks,
                selectedContainerColor = if (currentScreen == Screen.Bookmarks) selectedPillColor else Color.Transparent,
                imageTint = if (currentScreen == Screen.Bookmarks) activeColor else inactiveColor,
                onClick = onBookmarksClick
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            BottomBarIcon(
                selected = currentScreen == Screen.Settings,
                selectedContainerColor = if (currentScreen == Screen.Settings) selectedPillColor else Color.Transparent,
                imageTint = if (currentScreen == Screen.Settings) activeColor else inactiveColor,
                onClick = onSettingsClick
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomBarIcon(
    selected: Boolean,
    selectedContainerColor: Color,
    imageTint: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) selectedContainerColor else Color.Transparent
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 44.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides imageTint) {
                content()
            }
        }
    }
}
