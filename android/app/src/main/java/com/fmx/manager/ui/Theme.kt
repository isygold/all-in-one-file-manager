package com.fmx.manager.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, DARK, BLACK, LIGHT }

/**
 * Accent colours for file-type glyphs, taken from the Astryx colour ramp.
 * Kept separate from the Material scheme so a dynamic/Material You palette
 * never turns the file icons into an unrecognisable rainbow.
 */
data class FmxPalette(
    val folder: Color,
    val image: Color,
    val video: Color,
    val audio: Color,
    val archive: Color,
    val document: Color,
    val pdf: Color,
    val apk: Color,
)

val LocalFmxPalette = staticCompositionLocalOf {
    FmxPalette(
        folder = Color(0xFFFBC02D),
        image = Color(0xFF009688),
        video = Color(0xFFC2185B),
        audio = Color(0xFFE9690B),
        archive = Color(0xFF5B08D8),
        document = Color(0xFF0064E0),
        pdf = Color(0xFFD31130),
        apk = Color(0xFF0D8626),
    )
}

// ---------------------------------------------------------------- Astryx schemes
// Values mirror @astryxdesign/core color tokens (light-dark pairs).

private val AstryxLight = lightColorScheme(
    primary = Color(0xFF0064E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF042F97),
    inversePrimary = Color(0xFF3E9EFB),
    secondary = Color(0xFF4E606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDF3),
    onSecondaryContainer = Color(0xFF0A1317),
    tertiary = Color(0xFFE9690B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6D2),
    onTertiaryContainer = Color(0xFF6B2203),
    background = Color(0xFFF1F4F7),
    onBackground = Color(0xFF0A1317),
    surface = Color.White,
    onSurface = Color(0xFF0A1317),
    surfaceVariant = Color(0xFFF1F4F7),
    onSurfaceVariant = Color(0xFF4E606F),
    surfaceTint = Color(0xFF0064E0),
    inverseSurface = Color(0xFF0A1317),
    inverseOnSurface = Color(0xFFF1F4F7),
    error = Color(0xFFE3193B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E5),
    onErrorContainer = Color(0xFF7B0210),
    outline = Color(0xFFCCD3DB),
    outlineVariant = Color(0xFFE1E7EE),
    scrim = Color(0x66011228),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE2E8EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF4F6F9),
    surfaceContainerHigh = Color(0xFFEDF1F6),
    surfaceContainerHighest = Color(0xFFE7ECF2),
)

private val AstryxDark = darkColorScheme(
    primary = Color(0xFF0171E3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF113B66),
    onPrimaryContainer = Color(0xFFAFD7FF),
    inversePrimary = Color(0xFF0064E0),
    secondary = Color(0xFFAAAFB5),
    onSecondary = Color(0xFF111112),
    secondaryContainer = Color(0xFF2A2B2F),
    onSecondaryContainer = Color(0xFFDFE2E5),
    tertiary = Color(0xFFFB8C00),
    onTertiary = Color(0xFF111112),
    tertiaryContainer = Color(0xFF4A2A05),
    onTertiaryContainer = Color(0xFFFDB876),
    background = Color(0xFF111112),
    onBackground = Color(0xFFDFE2E5),
    surface = Color(0xFF1F1F22),
    onSurface = Color(0xFFDFE2E5),
    surfaceVariant = Color(0xFF28292C),
    onSurfaceVariant = Color(0xFFAAAFB5),
    surfaceTint = Color(0xFF3E9EFB),
    inverseSurface = Color(0xFFDFE2E5),
    inverseOnSurface = Color(0xFF1F1F22),
    error = Color(0xFFF5394F),
    onError = Color(0xFF0A1317),
    errorContainer = Color(0xFF3B1218),
    onErrorContainer = Color(0xFFFFB2B8),
    outline = Color(0xFF494D53),
    outlineVariant = Color(0xFF2F3034),
    scrim = Color(0x99111112),
    surfaceBright = Color(0xFF2A2B2F),
    surfaceDim = Color(0xFF111112),
    surfaceContainerLowest = Color(0xFF0B0B0C),
    surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Color(0xFF1F1F22),
    surfaceContainerHigh = Color(0xFF28292C),
    surfaceContainerHighest = Color(0xFF303136),
)

/** True-black variant, still using Astryx dark text/border ramps. */
private val AstryxBlack = AstryxDark.copy(
    background = Color.Black,
    surface = Color(0xFF0B0B0C),
    surfaceVariant = Color(0xFF141416),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF09090A),
    surfaceContainer = Color(0xFF0E0E10),
    surfaceContainerHigh = Color(0xFF141416),
    surfaceContainerHighest = Color(0xFF1A1A1D),
    outlineVariant = Color(0xFF242528),
)

private val AstryxTypography = Typography().copy(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 52.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 35.sp,
        lineHeight = 44.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 29.sp,
        lineHeight = 36.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

private val AstryxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FmxTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sysDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> sysDark
        ThemeMode.DARK, ThemeMode.BLACK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> {
            val c = LocalContext.current
            if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
        }
        mode == ThemeMode.BLACK -> AstryxBlack
        dark -> AstryxDark
        else -> AstryxLight
    }
    val palette = if (dark) {
        LocalFmxPalette.current.copy(
            folder = Color(0xFFFFEE58),
            image = Color(0xFF26A69A),
            video = Color(0xFFEC407A),
            audio = Color(0xFFFB8C00),
            archive = Color(0xFFB3B0FE),
            document = Color(0xFF2694FE),
            pdf = Color(0xFFE3193B),
            apk = Color(0xFF26A756),
        )
    } else {
        LocalFmxPalette.current
    }
    CompositionLocalProvider(LocalFmxPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AstryxTypography,
            shapes = AstryxShapes,
            content = content,
        )
    }
}
