package com.shinjikai.dictionary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.ui.Screen

@Composable
fun PrimaryBottomBar(
    currentScreen: Screen,
    onSearchClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
            )
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                BottomBarItem(
                    selected = currentScreen == Screen.Search,
                    onClick = onSearchClick,
                    icon = { modifier ->
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.nav_search),
                            modifier = modifier
                        )
                    },
                    label = stringResource(R.string.nav_search),
                    colors = itemColors
                )
                BottomBarItem(
                    selected = currentScreen == Screen.Browse,
                    onClick = onBrowseClick,
                    icon = { modifier ->
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.nav_browse),
                            modifier = modifier
                        )
                    },
                    label = stringResource(R.string.nav_browse),
                    colors = itemColors
                )
                BottomBarItem(
                    selected = currentScreen == Screen.History,
                    onClick = onHistoryClick,
                    icon = { modifier ->
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.history_title),
                            modifier = modifier
                        )
                    },
                    label = stringResource(R.string.history_title),
                    colors = itemColors
                )
                BottomBarItem(
                    selected = currentScreen == Screen.Bookmarks,
                    onClick = onBookmarksClick,
                    icon = { modifier ->
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = stringResource(R.string.nav_bookmarks),
                            modifier = modifier
                        )
                    },
                    label = stringResource(R.string.nav_bookmarks),
                    colors = itemColors
                )
                BottomBarItem(
                    selected = currentScreen == Screen.Settings,
                    onClick = onSettingsClick,
                    icon = { modifier ->
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                            modifier = modifier
                        )
                    },
                    label = stringResource(R.string.nav_settings),
                    colors = itemColors
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Modifier) -> Unit,
    label: String,
    colors: NavigationBarItemColors
) {
    val iconScale = animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(stiffness = 450f, dampingRatio = 0.88f),
        label = "bottomBarIconScale"
    )

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { icon(Modifier.scale(iconScale.value)) },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        alwaysShowLabel = true,
        colors = colors
    )
}
