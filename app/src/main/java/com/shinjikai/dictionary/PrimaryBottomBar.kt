package com.shinjikai.dictionary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
    val activePillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val activeContentColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(64.dp),
        shape = RoundedCornerShape(26.dp),
        color = barColor,
        tonalElevation = 2.dp,
        shadowElevation = 7.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPillItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                selected = currentScreen == Screen.Search,
                selectedContainerColor = activePillColor,
                selectedContentColor = activeContentColor,
                unselectedContentColor = inactiveColor,
                onClick = onSearchClick
            )
            NavPillItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                selected = currentScreen == Screen.History,
                selectedContainerColor = activePillColor,
                selectedContentColor = activeContentColor,
                unselectedContentColor = inactiveColor,
                onClick = onHistoryClick
            )
            NavPillItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                selected = currentScreen == Screen.Bookmarks,
                selectedContainerColor = activePillColor,
                selectedContentColor = activeContentColor,
                unselectedContentColor = inactiveColor,
                onClick = onBookmarksClick
            )
            NavPillItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                selected = currentScreen == Screen.Settings,
                selectedContainerColor = activePillColor,
                selectedContentColor = activeContentColor,
                unselectedContentColor = inactiveColor,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun RowScope.NavPillItem(
    icon: @Composable () -> Unit,
    selected: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    onClick: () -> Unit
) {
    val containerColor = animateColorAsState(
        targetValue = if (selected) selectedContainerColor else Color.Transparent,
        animationSpec = spring(stiffness = 420f),
        label = "navPillContainer"
    )
    val contentColor = animateColorAsState(
        targetValue = if (selected) selectedContentColor else unselectedContentColor,
        animationSpec = spring(stiffness = 420f),
        label = "navPillContent"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .widthIn(min = 48.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor.value
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor.value) {
                icon()
            }
        }
    }
}
