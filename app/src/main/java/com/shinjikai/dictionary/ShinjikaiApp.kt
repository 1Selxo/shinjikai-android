package com.shinjikai.dictionary

import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shinjikai.dictionary.ui.Screen
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.util.Locale
import kotlinx.coroutines.launch

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
                if (viewModel.settingsUiState.showIntroduction) {
                    IntroductionScreen(
                        appName = appName,
                        onFinish = viewModel::dismissIntroduction,
                        onOpenOfflineSetup = {
                            viewModel.dismissIntroduction()
                            viewModel.navigateTo(Screen.Settings)
                        }
                    )
                } else {
                    when (currentScreen) {
                        Screen.Search -> SearchScreenContent(
                            appName = appName,
                            useOfflineMode = settings.useOfflineMode,
                            hasOfflineDictionary = viewModel.settingsUiState.offlineTermCount > 0,
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
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun IntroductionScreen(
    appName: String,
    onFinish: () -> Unit,
    onOpenOfflineSetup: () -> Unit
) {
    val onboardingPages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Default.Search,
                accent = listOf(Color(0xFF2A5EA8), Color(0xFF4EA1D3)),
                eyebrowRes = R.string.intro_page_1_eyebrow,
                titleRes = R.string.intro_page_1_title,
                bodyRes = R.string.intro_page_1_body
            ),
            OnboardingPage(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                accent = listOf(Color(0xFF00796B), Color(0xFF49B6A5)),
                eyebrowRes = R.string.intro_page_2_eyebrow,
                titleRes = R.string.intro_page_2_title,
                bodyRes = R.string.intro_page_2_body
            ),
            OnboardingPage(
                icon = Icons.Default.DownloadForOffline,
                accent = listOf(Color(0xFF7B4DCC), Color(0xFFB786F8)),
                eyebrowRes = R.string.intro_page_3_eyebrow,
                titleRes = R.string.intro_page_3_title,
                bodyRes = R.string.intro_page_3_body
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == onboardingPages.lastIndex

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = appName, style = MaterialTheme.typography.displaySmall)
            Text(
                text = stringResource(R.string.intro_title),
                style = MaterialTheme.typography.headlineMedium
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) { page ->
                IntroPageCard(page = onboardingPages[page])
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                onboardingPages.forEachIndexed { index, _ ->
                    val selected = index == currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (selected) 28.dp else 10.dp, height = 10.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            if (isLastPage) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_start))
                }
                OutlinedButton(
                    onClick = onOpenOfflineSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_offline_setup))
                }
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(currentPage + 1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_next))
                }
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_skip))
                }
            }
        }
    }
}

@Composable
private fun IntroPageCard(
    page: OnboardingPage
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(page.accent))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Text(
                text = stringResource(page.eyebrowRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(page.titleRes),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(page.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val accent: List<Color>,
    val eyebrowRes: Int,
    val titleRes: Int,
    val bodyRes: Int
)

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
                title = { Text(stringResource(R.string.detail_title)) },
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
