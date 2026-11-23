package com.example.mikayala.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun schemeFor(p: MikayalaPalette) = if (p.isDark) darkColorScheme(
    primary = p.accent, onPrimary = Color.White,
    secondary = p.secondary, onSecondary = p.background,
    tertiary = p.lavender,
    background = p.background, onBackground = p.textPrimary,
    surface = p.surface, onSurface = p.textPrimary,
    surfaceVariant = p.surfaceElevated, onSurfaceVariant = p.textSecondary,
    surfaceContainer = p.surface, surfaceContainerHigh = p.modal,
    outline = p.borderStrong, outlineVariant = p.border,
    error = p.danger
) else lightColorScheme(
    primary = p.accent, onPrimary = Color.White,
    secondary = p.secondary, onSecondary = Color.White,
    tertiary = p.lavender,
    background = p.background, onBackground = p.textPrimary,
    surface = p.surface, onSurface = p.textPrimary,
    surfaceVariant = p.surfaceElevated, onSurfaceVariant = p.textSecondary,
    surfaceContainer = p.surface, surfaceContainerHigh = p.modal,
    outline = p.borderStrong, outlineVariant = p.border,
    error = p.danger
)

@Composable
fun MikayalaTheme(
    darkTheme: Boolean = true,
    accentTheme: String = "violet_emerald",
    fontPreference: String = "default",
    fontSizeScale: String = "medium",
    content: @Composable () -> Unit
) {
    val palette = paletteFor(darkTheme = darkTheme, accentTheme = accentTheme)
    val colorScheme = schemeFor(palette)
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
                val systemColor = palette.background
                @Suppress("DEPRECATION")
                window.statusBarColor = systemColor.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = systemColor.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val scaledDensity = androidx.compose.ui.unit.Density(
        density.density,
        density.fontScale * com.example.mikayala.data.UiPreferencesStore.fontScaleFactor(fontSizeScale)
    )
    val typography = androidx.compose.runtime.remember(fontPreference) { typographyFor(fontFamilyFor(fontPreference)) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMikayalaPalette provides palette,
        androidx.compose.ui.platform.LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
