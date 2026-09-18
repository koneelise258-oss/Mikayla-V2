package com.example.mikayala.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentRose,
    onPrimary = TextPrimary,
    secondary = AccentViolet,
    onSecondary = TextPrimary,
    tertiary = AccentGold,
    background = DeepNight,
    onBackground = TextPrimary,
    surface = CardDark,
    onSurface = TextPrimary,
    surfaceVariant = CardElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtleWhite
)

private val LightColorScheme = lightColorScheme(
    primary = AccentRose,
    onPrimary = Color.White,
    secondary = AccentViolet,
    onSecondary = Color.White,
    tertiary = AccentGold,
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFE5E7EB)
)

@Composable
fun MikayalaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            val activity = if (context is Activity) context else {
                var currentContext = context
                while (currentContext is android.content.ContextWrapper) {
                    if (currentContext is Activity) break
                    currentContext = currentContext.baseContext
                }
                currentContext as? Activity
            }
            
            activity?.let { act ->
                val window = act.window
                val systemColor = if (darkTheme) DeepNight else Color(0xFFF9FAFB)
                window.statusBarColor = systemColor.toArgb()
                window.navigationBarColor = systemColor.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
