package com.shinjikai.dictionary

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.data.AppSettings
import com.shinjikai.dictionary.data.AppThemeMode

private val ShinjikaiDarkColors = darkColorScheme(
    primary = Color(0xFFE39A92),
    onPrimary = Color(0xFF33110E),
    primaryContainer = Color(0xFF873C36),
    onPrimaryContainer = Color(0xFFFFE6E1),
    secondary = Color(0xFF9CCFD0),
    onSecondary = Color(0xFF0B282A),
    secondaryContainer = Color(0xFF203F41),
    onSecondaryContainer = Color(0xFFE2F7F7),
    tertiary = Color(0xFFE9C873),
    onTertiary = Color(0xFF2D2303),
    tertiaryContainer = Color(0xFF5A4610),
    onTertiaryContainer = Color(0xFFFFE9A8),
    background = Color(0xFF15110F),
    onBackground = Color(0xFFF7EEEA),
    surface = Color(0xFF211A17),
    onSurface = Color(0xFFF7EEEA),
    surfaceVariant = Color(0xFF2A3738),
    onSurfaceVariant = Color(0xFFD0DEDE),
    outline = Color(0xFFB56860),
    outlineVariant = Color(0xFF70423D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF8F3934),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ShinjikaiLightColors = lightColorScheme(
    primary = Color(0xFF9A3E38),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF3A0806),
    secondary = Color(0xFF3E6668),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEEEE),
    onSecondaryContainer = Color(0xFF001F21),
    tertiary = Color(0xFF765B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE39D),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFFFFBF8),
    onBackground = Color(0xFF241917),
    surface = Color(0xFFFFFCFA),
    onSurface = Color(0xFF241917),
    surfaceVariant = Color(0xFFE9F1F1),
    onSurfaceVariant = Color(0xFF384848),
    outline = Color(0xFFA64A43),
    outlineVariant = Color(0xFFD7A09A),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun ShinjikaiTheme(
    settings: AppSettings,
    supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = settings.resolveDarkTheme()
    val baseScheme = if (darkTheme) ShinjikaiDarkColors else ShinjikaiLightColors
    val colorScheme = remember(settings.useDynamicColor, supportsDynamicColor, darkTheme, context) {
        createColorScheme(
            context = context,
            useDynamicColor = settings.useDynamicColor,
            supportsDynamicColor = supportsDynamicColor,
            darkTheme = darkTheme,
            baseScheme = baseScheme
        )
    }
    val arabicFontFamily = remember { FontFamily(Font(R.font.noto_sans_arabic)) }
    val baseTypography = remember { Typography() }
    val appTypography = remember(arabicFontFamily, baseTypography) {
        Typography(
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
    }

    MaterialTheme(colorScheme = colorScheme, typography = appTypography, content = content)
}

@Composable
fun AppSettings.resolveDarkTheme(): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
}

private fun createColorScheme(
    context: Context,
    useDynamicColor: Boolean,
    supportsDynamicColor: Boolean,
    darkTheme: Boolean,
    baseScheme: androidx.compose.material3.ColorScheme
): androidx.compose.material3.ColorScheme {
    if (!useDynamicColor || !supportsDynamicColor) return baseScheme

    val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return dynamicScheme.copy(
        primary = baseScheme.primary,
        onPrimary = baseScheme.onPrimary,
        primaryContainer = baseScheme.primaryContainer,
        onPrimaryContainer = baseScheme.onPrimaryContainer,
        secondary = baseScheme.secondary,
        secondaryContainer = baseScheme.secondaryContainer,
        background = baseScheme.background,
        surface = baseScheme.surface,
        surfaceVariant = baseScheme.surfaceVariant,
        outline = baseScheme.outline,
        outlineVariant = baseScheme.outlineVariant
    )
}

object ShinjikaiUi {
    val CardShape = RoundedCornerShape(8.dp)
    val CompactShape = RoundedCornerShape(6.dp)
    val PillShape = RoundedCornerShape(999.dp)
    val ScreenPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp)

    @Composable
    fun cardColors(containerColor: Color = MaterialTheme.colorScheme.surface): CardColors {
        return CardDefaults.cardColors(containerColor = containerColor)
    }

    @Composable
    fun cardBorder(alpha: Float = 0.48f): BorderStroke {
        return BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha))
    }

    @Composable
    fun panelColor(alpha: Float = 0.32f): Color {
        return MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)
    }

    @Composable
    fun mutedTextColor(alpha: Float = 0.72f): Color {
        return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    }
}

@Composable
fun ShinjikaiCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    colors: CardColors = ShinjikaiUi.cardColors(),
    border: BorderStroke = ShinjikaiUi.cardBorder(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = ShinjikaiUi.CardShape,
        colors = colors,
        border = border
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun shinjikaiTopAppBarColors(): TopAppBarColors {
    return TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        actionIconContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun ShinjikaiPanel(
    modifier: Modifier = Modifier,
    color: Color = ShinjikaiUi.panelColor(),
    border: BorderStroke = ShinjikaiUi.cardBorder(alpha = 0.28f),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = ShinjikaiUi.CompactShape,
        color = color,
        border = border,
        content = content
    )
}
