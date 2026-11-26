// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colors of the overview list cards and action buttons.
 *
 * The V2 mockup uses a neutral grey that no Material role matches in both themes, so the grey is
 * picked from the brightness of the active theme's background instead of a color-scheme role.
 *
 * @author hixeum
 */
internal object OverviewPalette {
  private const val DARK_BACKGROUND_LUMINANCE_THRESHOLD = 0.5f

  private val isDarkTheme: Boolean
    @Composable
    get() = MaterialTheme.colorScheme.background.luminance() < DARK_BACKGROUND_LUMINANCE_THRESHOLD

  /** Background of the list cards and of the overview action buttons. */
  val container: Color
    @Composable
    get() =
        if (isDarkTheme) ArtistaColors.OverviewContainerDark else ArtistaColors.OverviewContainer

  /** Text and icons drawn on [container]. */
  val content: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

  /** Background of the avatar circle in a list card header. */
  val avatarContainer: Color
    @Composable
    get() =
        if (isDarkTheme) ArtistaColors.OverviewAvatarContainerDark
        else ArtistaColors.OverviewAvatarContainer
}
