package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    secondary = Accent2Color,
    tertiary = ChipOnTxDark,
    background = BgDark,
    surface = CardDark,
    onPrimary = TxDark,
    onSecondary = CardDark,
    onBackground = TxDark,
    onSurface = TxDark
)

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    secondary = Accent2Color,
    tertiary = ChipOnTxLight,
    background = BgLight,
    surface = CardLight,
    onPrimary = CardLight,
    onSecondary = TxLight,
    onBackground = TxLight,
    onSurface = TxLight
)

@Composable
fun GlassNotesTheme(
    themeSetting: String = "auto", // "auto", "light", "dark"
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val targetColors = (if (darkTheme) DarkGlassColors else LightGlassColors).let { base ->
        base.copy(
            isReduced = reduceTransparency,
            glass = if (reduceTransparency) (if (darkTheme) CardDark else CardLight) else (if (darkTheme) GlassDark else GlassLight)
        )
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalGlassColors provides targetColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object GlassTheme {
    val colors: GlassCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current
}

