package com.fmx.manager.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, DARK, BLACK, LIGHT }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF7DD3FC),
    surface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFF1E293B),
    background = Color(0xFF0B1220),
    outline = Color(0xFF334155),
)

private val BlackScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF082F49),
    secondary = Color(0xFF7DD3FC),
    surface = Color.Black,
    surfaceVariant = Color(0xFF111111),
    background = Color.Black,
    outline = Color(0xFF333333),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF0369A1),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    background = Color(0xFFF8FAFC),
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
        mode == ThemeMode.BLACK -> BlackScheme
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
