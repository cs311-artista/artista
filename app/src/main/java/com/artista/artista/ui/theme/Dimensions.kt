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
  // Authentication screen.
  val authenticationScreenHorizontalPadding = 24.dp
  val authenticationScreenVerticalPadding = 32.dp
  val authenticationHeroTopSpacing = 48.dp
  val authenticationHeroTitleSpacing = 16.dp
  val authenticationTitleTaglineSpacing = 8.dp
  val authenticationButtonHorizontalPadding = 20.dp
  val authenticationButtonVerticalPadding = 0.dp
  val authenticationButtonIconLabelSpacing = 10.dp
  val authenticationScreenBottomSpacing = 24.dp

  // Artwork cards and artwork details.
  val artworkCardContentPadding = 12.dp
  val artworkCardSectionSpacing = 16.dp
  val artworkCardTextHorizontalPadding = 8.dp
  val artworkCardTextSpacing = 4.dp
  val artworkCardPreviewPadding = 16.dp
  val artworkDetailsContentHorizontalPadding = 20.dp
  val artworkDetailsSectionSpacing = 12.dp
  val artworkDetailsImageVerticalSpacing = 4.dp
  val artworkFieldContentPadding = 10.dp
  val artworkFieldLabelValueSpacing = 4.dp

  // Saved artworks screen.
  val savedArtworksTitleHorizontalPadding = 20.dp
  val savedArtworksTitleVerticalPadding = 8.dp
  val savedArtworksErrorHorizontalPadding = 24.dp
  val savedArtworksRetryButtonSpacing = 16.dp
  val savedArtworksListHorizontalPadding = 16.dp
  val savedArtworksListVerticalPadding = 12.dp
  val savedArtworksItemSpacing = 12.dp

  // Overview screen.
  val overviewListHorizontalPadding = 16.dp
  val overviewListVerticalPadding = 8.dp
  val overviewListItemSpacing = 8.dp
  val overviewSearchBarHorizontalPadding = 16.dp
  val overviewSearchBarVerticalPadding = 8.dp
  val overviewSearchBarContentHorizontalPadding = 16.dp
  val overviewSearchBarIconTextSpacing = 12.dp
  val overviewErrorActionSpacing = 12.dp

  // Search screen.
  val searchInputHorizontalPadding = 16.dp
  val searchInputVerticalPadding = 8.dp
  val searchResultsHorizontalPadding = 16.dp
  val searchResultsVerticalPadding = 8.dp
  val searchResultItemSpacing = 8.dp

  // Artist preferences screen.
  val artistPreferencesScreenHorizontalPadding = 24.dp
  val artistPreferencesScreenVerticalPadding = 16.dp
  val artistPreferencesSectionSpacing = 16.dp
  val artistPreferencesGridHorizontalSpacing = 12.dp
  val artistPreferencesGridVerticalSpacing = 12.dp
  val artistCardContentPadding = 8.dp
  val artistCardSelectionIndicatorPadding = 4.dp
  val artistCardNameTopSpacing = 8.dp

  // Profile screen.
  val profileTopBarHorizontalPadding = 16.dp
  val profileTopBarVerticalPadding = 8.dp
  val profileContentHorizontalPadding = 24.dp
  val profileContentTopPadding = 16.dp
  val profileContentBottomPadding = 45.dp
  val profilePictureFieldsSpacing = 48.dp
  val profileFieldSpacing = 24.dp
  val logoutButtonHorizontalPadding = 20.dp
  val logoutButtonVerticalPadding = 10.dp
  val logoutButtonIconLabelSpacing = 8.dp
  val profileFieldHorizontalPadding = 24.dp

  // Bottom navigation.
  val navigationBarHorizontalPadding = 16.dp
  val navigationBarVerticalPadding = 16.dp

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
}
