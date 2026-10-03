package com.eleitorix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.eleitorix.data.preferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = DarkElectionBluePrimary,
    onPrimary = DarkElectionBlueOnPrimary,
    primaryContainer = DarkElectionBlueContainer,
    onPrimaryContainer = DarkElectionBlueOnContainer,
    secondary = DarkElectionGold,
    onSecondary = DarkElectionGoldOnContainer,
    secondaryContainer = DarkElectionGoldContainer,
    onSecondaryContainer = DarkElectionGoldOnContainer,
    tertiary = DarkElectionGreen,
    onTertiary = DarkElectionBlueOnPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = ElectionBluePrimary,
    onPrimary = ElectionBlueOnPrimary,
    primaryContainer = ElectionBlueContainer,
    onPrimaryContainer = ElectionBlueOnContainer,
    secondary = ElectionGold,
    onSecondary = ElectionBlueOnPrimary,
    secondaryContainer = ElectionGoldContainer,
    onSecondaryContainer = ElectionGoldOnContainer,
    tertiary = ElectionGreen,
    onTertiary = ElectionBlueOnPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

@Composable
fun LeitorBuTheme(
    themeMode: ThemeMode = ThemeMode.SISTEMA,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SISTEMA -> systemDark
        ThemeMode.CLARO -> false
        ThemeMode.ESCURO -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Compatibilidade
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    LeitorBuTheme(
        themeMode = if (darkTheme) ThemeMode.ESCURO else ThemeMode.CLARO,
        content = content
    )
}
