// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.artista.artista.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = ArtistaColors.PrimaryDark,
        onPrimary = ArtistaColors.OnPrimaryDark,
        primaryContainer = ArtistaColors.PrimaryContainerDark,
        onPrimaryContainer = ArtistaColors.OnPrimaryContainerDark,
        inversePrimary = ArtistaColors.InversePrimaryDark,
        secondary = ArtistaColors.SecondaryDark,
        onSecondary = ArtistaColors.OnSecondaryDark,
        secondaryContainer = ArtistaColors.SecondaryContainerDark,
        onSecondaryContainer = ArtistaColors.OnSecondaryContainerDark,
        tertiary = ArtistaColors.TertiaryDark,
        onTertiary = ArtistaColors.OnTertiaryDark,
        tertiaryContainer = ArtistaColors.TertiaryContainerDark,
        onTertiaryContainer = ArtistaColors.OnTertiaryContainerDark,
        background = ArtistaColors.BackgroundDark,
        onBackground = ArtistaColors.OnBackgroundDark,
        surface = ArtistaColors.SurfaceDark,
        onSurface = ArtistaColors.OnSurfaceDark,
        surfaceVariant = ArtistaColors.SurfaceVariantDark,
        onSurfaceVariant = ArtistaColors.OnSurfaceVariantDark,
        inverseSurface = ArtistaColors.InverseSurfaceDark,
        inverseOnSurface = ArtistaColors.InverseOnSurfaceDark,
        error = ArtistaColors.ErrorDark,
        onError = ArtistaColors.OnErrorDark,
        errorContainer = ArtistaColors.ErrorContainerDark,
        onErrorContainer = ArtistaColors.OnErrorContainerDark,
        outline = ArtistaColors.OutlineDark,
        outlineVariant = ArtistaColors.OutlineVariantDark,
        scrim = ArtistaColors.ScrimDark,
        surfaceTint = ArtistaColors.SurfaceTintDark,
        surfaceBright = ArtistaColors.SurfaceBrightDark,
        surfaceDim = ArtistaColors.SurfaceDimDark,
        surfaceContainerLowest = ArtistaColors.SurfaceContainerLowestDark,
        surfaceContainerLow = ArtistaColors.SurfaceContainerLowDark,
        surfaceContainer = ArtistaColors.SurfaceContainerDark,
        surfaceContainerHigh = ArtistaColors.SurfaceContainerHighDark,
        surfaceContainerHighest = ArtistaColors.SurfaceContainerHighestDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = ArtistaColors.Primary,
        onPrimary = ArtistaColors.OnPrimary,
        primaryContainer = ArtistaColors.PrimaryContainer,
        onPrimaryContainer = ArtistaColors.OnPrimaryContainer,
        inversePrimary = ArtistaColors.InversePrimary,
        secondary = ArtistaColors.Secondary,
        onSecondary = ArtistaColors.OnSecondary,
        secondaryContainer = ArtistaColors.SecondaryContainer,
        onSecondaryContainer = ArtistaColors.OnSecondaryContainer,
        tertiary = ArtistaColors.Tertiary,
        onTertiary = ArtistaColors.OnTertiary,
        tertiaryContainer = ArtistaColors.TertiaryContainer,
        onTertiaryContainer = ArtistaColors.OnTertiaryContainer,
        background = ArtistaColors.Background,
        onBackground = ArtistaColors.OnBackground,
        surface = ArtistaColors.Surface,
        onSurface = ArtistaColors.OnSurface,
        surfaceVariant = ArtistaColors.SurfaceVariant,
        onSurfaceVariant = ArtistaColors.OnSurfaceVariant,
        inverseSurface = ArtistaColors.InverseSurface,
        inverseOnSurface = ArtistaColors.InverseOnSurface,
        error = ArtistaColors.Error,
        onError = ArtistaColors.OnError,
        errorContainer = ArtistaColors.ErrorContainer,
        onErrorContainer = ArtistaColors.OnErrorContainer,
        outline = ArtistaColors.Outline,
        outlineVariant = ArtistaColors.OutlineVariant,
        scrim = ArtistaColors.Scrim,
        surfaceTint = ArtistaColors.SurfaceTint,
        surfaceBright = ArtistaColors.SurfaceBright,
        surfaceDim = ArtistaColors.SurfaceDim,
        surfaceContainerLowest = ArtistaColors.SurfaceContainerLowest,
        surfaceContainerLow = ArtistaColors.SurfaceContainerLow,
        surfaceContainer = ArtistaColors.SurfaceContainer,
        surfaceContainerHigh = ArtistaColors.SurfaceContainerHigh,
        surfaceContainerHighest = ArtistaColors.SurfaceContainerHighest,
    )

/**
 * Applies the Artista color scheme and typography to the provided content.
 *
 * @param darkTheme whether to use the dark color scheme
 * @param content composable content styled by the Artista theme
 */
@Composable
fun ArtistaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val activity = view.context as ComponentActivity
      activity.enableEdgeToEdge()
      val window = activity.window
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
