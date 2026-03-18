package com.shinjikai.dictionary

import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shinjikai.dictionary.ui.Screen
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShinjikaiApp(
    externalSearchTerm: String? = null,
    onExternalSearchTermConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val viewModel: ShinjikaiViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()
    val currentScreen = viewModel.currentScreen
    val screenStack = viewModel.screenStack
    val appName = stringResource(id = R.string.app_name)
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appVersionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "v${info.versionName ?: "?"} ($code)"
        }.getOrDefault("v1.0")
    }

    var textToSpeech by remember(context) { mutableStateOf<TextToSpeech?>(null) }
    var canSpeakJapanese by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var disposed = false
        var localTts: TextToSpeech? = null
        val instance = TextToSpeech(context) { status ->
            if (disposed) return@TextToSpeech
            val ready = localTts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val result = ready.setLanguage(Locale.JAPANESE)
                canSpeakJapanese = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                canSpeakJapanese = false
            }
        }
        localTts = instance
        instance.setSpeechRate(0.92f)
        instance.setPitch(1f)
        textToSpeech = instance
        onDispose {
            disposed = true
            canSpeakJapanese = false
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Settings) viewModel.refreshOfflineTermCount()
    }

    LaunchedEffect(externalSearchTerm) {
        val incoming = externalSearchTerm?.trim().orEmpty()
        if (incoming.isNotBlank()) {
            screenStack.clear()
            screenStack.add(Screen.Search)
            viewModel.currentScreen = Screen.Search
            viewModel.term = incoming
            viewModel.runSearchForTerm(incoming)
            onExternalSearchTermConsumed()
        }
    }

    val darkColors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF8AB4F8),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF0D1B2A),
        secondary = androidx.compose.ui.graphics.Color(0xFF80CBC4),
        background = androidx.compose.ui.graphics.Color(0xFF0E1116),
        surface = androidx.compose.ui.graphics.Color(0xFF171B22),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF222733),
        onBackground = androidx.compose.ui.graphics.Color(0xFFE8EAED),
        onSurface = androidx.compose.ui.graphics.Color(0xFFE8EAED)
    )
    val lightColors = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF2A5EA8),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = androidx.compose.ui.graphics.Color(0xFF00796B),
        background = androidx.compose.ui.graphics.Color(0xFFF4F7FB),
        surface = androidx.compose.ui.graphics.Color.White,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDCE4F0),
        onBackground = androidx.compose.ui.graphics.Color(0xFF10131A),
        onSurface = androidx.compose.ui.graphics.Color(0xFF10131A)
    )
    val colorScheme = when {
        settings.useDynamicColor && supportsDynamicColor && settings.darkMode -> dynamicDarkColorScheme(context).copy(
            background = darkColors.background,
            surface = darkColors.surface,
            surfaceVariant = darkColors.surfaceVariant
        )
        settings.useDynamicColor && supportsDynamicColor -> dynamicLightColorScheme(context).copy(
            background = lightColors.background,
            surface = lightColors.surface,
            surfaceVariant = lightColors.surfaceVariant
        )
        settings.darkMode -> darkColors
        else -> lightColors
    }

    val arabicFontFamily = FontFamily(Font(R.font.noto_sans_arabic))
    val baseTypography = Typography()
    val appTypography = Typography(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = arabicFontFamily),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = arabicFontFamily),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = arabicFontFamily),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = arabicFontFamily),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = arabicFontFamily),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = arabicFontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = arabicFontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = arabicFontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = arabicFontFamily),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = arabicFontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = arabicFontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = arabicFontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = arabicFontFamily),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = arabicFontFamily),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = arabicFontFamily)
    )

    MaterialTheme(colorScheme = colorScheme, typography = appTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BackHandler(enabled = screenStack.size > 1) { viewModel.goBack() }
            Surface(color = MaterialTheme.colorScheme.background) {
                when (currentScreen) {
                    Screen.Search -> SearchScreenContent(
                        appName = appName,
                        useOfflineMode = settings.useOfflineMode,
                        viewModel = viewModel,
                        uiState = viewModel.searchUiState,
                        searchResults = viewModel.searchResults,
                        onNavigateTo = viewModel::navigateTo,
                        onOpenDetails = {
                            focusManager.clearFocus()
                            viewModel.openDetails(it)
                        }
                    )
                    Screen.Bookmarks -> BookmarksScreenContent(
                        viewModel = viewModel,
                        uiState = viewModel.bookmarksUiState,
                        bookmarkFlow = viewModel.bookmarkPagingFlow,
                        onGoBack = viewModel::goBack
                    )
                    Screen.Settings -> SettingsScreenContent(
                        appVersionLabel = appVersionLabel,
                        supportsDynamicColor = supportsDynamicColor,
                        uiState = viewModel.settingsUiState,
                        viewModel = viewModel,
                        onGoBack = viewModel::goBack
                    )
                    Screen.Detail -> DetailScreenContent(
                        useOfflineMode = settings.useOfflineMode,
                        canSpeakJapanese = canSpeakJapanese,
                        textToSpeech = textToSpeech,
                        clipboardManager = clipboardManager,
                        focusManager = focusManager,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DetailScreenContent(
    useOfflineMode: Boolean,
    canSpeakJapanese: Boolean,
    textToSpeech: TextToSpeech?,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    focusManager: androidx.compose.ui.focus.FocusManager,
    viewModel: ShinjikaiViewModel
) {
    val context = LocalContext.current
    val detailState = viewModel.detailUiState
    val item = detailState.selectedItem

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.detail_title))
                        ModeBadge(useOfflineMode = useOfflineMode)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = { item?.let(viewModel::toggleBookmark) }) {
                        Icon(
                            imageVector = if (detailState.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.nav_bookmarks)
                        )
                    }
                }
            )
        }
    ) { padding ->
        DetailScreenBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            useOfflineMode = useOfflineMode,
            detailState = detailState,
            canSpeakJapanese = canSpeakJapanese,
            textToSpeech = textToSpeech,
            context = context,
            clipboardManager = clipboardManager,
            focusManager = focusManager,
            viewModel = viewModel
        )
    }
}
