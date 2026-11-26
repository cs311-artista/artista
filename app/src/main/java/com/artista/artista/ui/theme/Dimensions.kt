// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared spacing, shape and component dimensions for app screens.
 *
 * @author hixeum
 */
internal object Dimensions {
  // Shared shapes and control dimensions.
  val cornerRadiusSmall = 12.dp
  val cornerRadiusMedium = 20.dp
  val cornerRadiusLarge = 30.dp
  val cornerRadiusPill = 32.dp

  /** Shared 48 dp size for the authentication button and preferences back-button touch target. */
  val standardTouchTargetSize = 48.dp
  val iconSizeSmall = 16.dp
  val iconSizeMedium = 22.dp
  val iconSizeLarge = 24.dp

  /** Shared stroke width for selected card borders and progress indicators. */
  val standardStrokeWidth = 2.dp

  private val standardControlHeight = 56.dp

  // Authentication screen.
  val authenticationScreenHorizontalPadding = 24.dp
  val authenticationScreenVerticalPadding = 32.dp
  val authenticationHeroTopSpacing = 48.dp
  val authenticationHeroTitleSpacing = 16.dp
  val authenticationTitleTaglineSpacing = 8.dp
  val authenticationButtonHorizontalPadding = 20.dp
  val authenticationButtonVerticalPadding = 0.dp
  val authenticationButtonCornerRadiusPercent = 50
  val authenticationButtonIconLabelSpacing = 10.dp
  val authenticationErrorMessageTopSpacing = 12.dp
  val authenticationScreenBottomSpacing = 24.dp

  // Artwork cards and artwork details.
  val artworkCardContentPadding = 12.dp
  val artworkCardSectionSpacing = 16.dp
  val artworkCardTextHorizontalPadding = 8.dp
  val artworkCardTextSpacing = 4.dp
  val artworkCardPreviewPadding = 16.dp
  val artworkDetailsContentHorizontalPadding = 20.dp
  val artworkDetailsContentVerticalPadding = 20.dp
  val artworkDetailsSectionSpacing = 12.dp
  val artworkDetailsHeaderBottomPadding = 8.dp
  val artworkDetailsMapHeaderTopPadding = 8.dp
  val artworkFieldContentPadding = 14.dp
  val artworkFieldLabelValueSpacing = 8.dp
  val artworkMapTileHeight = 284.dp
  val artworkMapRecenterButtonPadding = 12.dp
  val artworkMapRecenterButtonSize = 44.dp

  /** Height of artwork images in artwork cards. */
  val artworkCardImageHeight = 160.dp

  /** Size of the icon shown while an artwork image is missing or loading, on cards and details. */
  val artworkImageFallbackIconSize = 48.dp

  /** Height of the artwork image on the artwork details screen. */
  val artworkDetailsImageHeight = 220.dp

  // Saved artworks screen.
  val savedArtworksErrorHorizontalPadding = 24.dp
  val savedArtworksRetryButtonSpacing = 16.dp
  val savedArtworksListHorizontalPadding = 16.dp
  val savedArtworksListVerticalPadding = 12.dp
  val savedArtworksItemSpacing = 12.dp

  /** Width over height of a saved artwork tile, slightly taller than wide like most paintings. */
  const val SAVED_ARTWORK_TILE_ASPECT_RATIO = 5f / 6f
  val savedArtworkTileCornerRadius = 4.dp
  val savedArtworkTileBandHorizontalPadding = 8.dp
  val savedArtworkTileBandVerticalPadding = 4.dp
  val savedArtworkTileButtonPadding = 6.dp
  val savedArtworkTileButtonSize = 36.dp
  val savedArtworkTileIconSize = 20.dp

  // Overview screen.
  val overviewListHorizontalPadding = 16.dp
  val overviewListVerticalPadding = 8.dp
  val overviewListItemSpacing = 8.dp
  val overviewSearchBarHorizontalPadding = 16.dp
  val overviewSearchBarVerticalPadding = 8.dp
  val overviewSearchBarContentHorizontalPadding = 16.dp
  val overviewSearchBarIconTextSpacing = 12.dp
  val overviewErrorActionSpacing = 12.dp
  val overviewFilterFieldSpacing = 8.dp
  val artworkFilterContentVerticalPadding = 4.dp
  val artworkFilterSuggestionsMaxHeight = 160.dp
  val overviewSearchBarHeight = standardControlHeight
  val overviewSearchBarElevation = 2.dp

  // Search screen.
  val searchRecommendationVerticalPadding = 8.dp
  val searchRecommendationHorizontalPadding = 8.dp
  val searchRecommendationIconContainerSize = 40.dp
  val searchRecommendationsMaxHeight = 320.dp
  val searchTypeFilterMaxHeight = 400.dp
  val searchTypeFilterHorizontalPadding = 16.dp
  val searchTypeFilterTitleVerticalPadding = 8.dp
  val searchResultsHorizontalPadding = 16.dp
  val searchResultsVerticalPadding = 8.dp
  val searchResultItemSpacing = 8.dp
  val searchEmptyStateHorizontalPadding = 32.dp
  val searchEmptyStateSpacing = 12.dp
  val searchEmptyStateIconContainerSize = 64.dp
  val searchEmptyStateMessageMaxWidth = 280.dp

  // Artist preferences screen.
  val artistPreferencesScreenHorizontalPadding = 24.dp
  val artistPreferencesScreenVerticalPadding = 16.dp
  val artistPreferencesSectionSpacing = 16.dp
  val artistPreferencesGridHorizontalSpacing = 12.dp
  val artistPreferencesGridVerticalSpacing = 12.dp
  val artistPreferencesRetryButtonSpacing = 16.dp
  val artistPreferencesRetryIconSpacing = 8.dp
  val artistPreferencesErrorHorizontalPadding = 24.dp
  val artistCardContentPadding = 8.dp
  val artistCardSelectionIndicatorPadding = 4.dp
  val artistCardNameTopSpacing = 8.dp
  val preferencesSaveButtonHeight = standardControlHeight

  // Profile screen.
  val profileContentHorizontalPadding = 20.dp
  val profileContentTopPadding = 24.dp
  val profileContentBottomPadding = 24.dp
  val profileTitleSubtitleSpacing = 4.dp
  val profileHeaderBannerSpacing = 24.dp
  /** Width-to-height ratio of the profile picture banner. */
  const val PROFILE_BANNER_ASPECT_RATIO = 2.1f
  val profileBannerSectionSpacing = 20.dp
  val profileSectionLabelCardsSpacing = 12.dp
  val profileOptionCardSpacing = 10.dp
  val profileOptionCardHorizontalPadding = 14.dp
  val profileOptionCardVerticalPadding = 14.dp
  val profileOptionIconTextSpacing = 14.dp
  val profileOptionTitleSubtitleSpacing = 2.dp
  val profileOptionIconBoxSize = 40.dp
  val profileEditPhotoPadding = 10.dp
  val profileEditPhotoHorizontalPadding = 14.dp
  val profileEditPhotoVerticalPadding = 6.dp
  val profileLogoutTopSpacing = 64.dp
  val logoutButtonHorizontalPadding = 20.dp
  val logoutButtonVerticalPadding = 10.dp
  val logoutButtonIconLabelSpacing = 8.dp
  val profileSheetPadding = 20.dp
  val profileSheetSubtitleOptionsSpacing = 20.dp
  val profileSheetCancelTopSpacing = 16.dp
  val profileSheetCancelVerticalPadding = 14.dp
  val profileInitializationWarningPadding = 16.dp

  // Bottom navigation.
  val navigationBarHorizontalPadding = 16.dp
  val navigationBarVerticalPadding = 16.dp

  // Map screen.
  val bottomMapToolBarPadding = 110.dp
  val topMapToolBarPadding = 24.dp

  // Map route overlays.
  val mapRouteOverlayHorizontalPadding = 16.dp
  val mapRouteOverlayTopPadding = 60.dp
  val mapRouteOverlayContentPadding = 16.dp
  val mapRouteOverlaySpacing = 8.dp
  const val MAP_ROUTE_CAMERA_PADDING = 180
  const val POLY_LINE_WIDTH = 10f
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
