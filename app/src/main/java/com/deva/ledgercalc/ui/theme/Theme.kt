package com.deva.ledgercalc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Accent,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE8DC),
    onSurfaceVariant = Color(0xFF5A5449),
    outline = Rule,
    error = Negative
)

private val DarkScheme = darkColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFF101418),
    secondary = AccentLight,
    onSecondary = Color(0xFF2A1B0C),
    background = PaperDark,
    onBackground = Color(0xFFE6E2DA),
    surface = PaperDark,
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF232830),
    onSurfaceVariant = Color(0xFFB9B3A8),
    outline = RuleDark,
    error = NegativeLight
)

/**
 * Dynamic colour is deliberately off: a user's wallpaper tint would recolour the paper and the
 * rule, and this app's look is a receipt. Consistency is worth more than personalisation here.
 */
@Composable
fun LedgerCalcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content
    )
}
