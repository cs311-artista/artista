// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared spacing, shape and component dimensions for app screens.
 *
 * @author hixeum
 */
internal object Dimensions {
  val spacingNone = 0.dp
  val spacing4 = 4.dp
  val spacing8 = 8.dp
  val spacing10 = 10.dp
  val spacing12 = 12.dp
  val spacing16 = 16.dp
  val spacing20 = 20.dp
  val spacing24 = 24.dp
  val spacing32 = 32.dp
  val spacing45 = 45.dp
  val spacing48 = 48.dp

  val cornerRadiusSmall = 12.dp
  val cornerRadiusMedium = 20.dp
  val cornerRadiusLarge = 30.dp
  val cornerRadiusPill = 32.dp

  /** Height of artwork images in artwork cards. */
  val artworkCardImageHeight = 160.dp

  /** Height of the artwork image on the artwork details screen. */
  val artworkDetailsImageHeight = 220.dp

  private val standardControlHeight = 56.dp
  val overviewSearchBarHeight = standardControlHeight
  val preferencesSaveButtonHeight = standardControlHeight

  private val standardTouchTargetSize = 48.dp
  val authenticationButtonHeight = standardTouchTargetSize
  val preferencesBackButtonSize = standardTouchTargetSize

  /** Height of each profile field. */
  val profileFieldHeight = 70.dp

  val iconSizeSmall = 16.dp
  val iconSizeMedium = 22.dp
  val iconSizeLarge = 24.dp

  private val shallowElevation = 2.dp
  val artworkCardElevation = shallowElevation
  val overviewSearchBarElevation = shallowElevation

  val selectedCardBorderWidth = 2.dp
  val progressIndicatorStrokeWidth = selectedCardBorderWidth
}

/**
 * Shared text dimensions used by the app's typography styles.
 *
 * @author hixeum
 */
internal object TypographyDimensions {
  val bodyLargeFontSize = 16.sp
  val bodyLargeLineHeight = 24.sp
  val bodyLargeLetterSpacing = 0.5.sp

  // other default text styles to override (specified in Type.kt)
  val titleLargeFontSize = 22.sp
  val titleLargeLineHeight = 28.sp
  val titleLargeLetterSpacing = 0.sp

  val labelSmallFontSize = 11.sp
  val labelSmallLineHeight = 16.sp
  val labelSmallLetterSpacing = 0.5.sp
}
