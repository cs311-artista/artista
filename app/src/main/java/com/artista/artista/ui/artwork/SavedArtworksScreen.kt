// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions

/**
 * Displays the artworks saved by the current user.
 *
 * The artworks and the unsave action come from [viewModel]. Navigation stays with the caller
 * through [onBackClick], [onArtworkDetailsClick], [onMapClick] and [onTabSelected]. Saved artworks
 * are reloaded each time the screen enters the composition.
 *
 * @param onBackClick called when the user taps the back arrow
 * @param onArtworkDetailsClick called when the user opens an artwork's details
 * @param onMapClick called when the user selects the map action for a saved artwork
 * @param onTabSelected called when the user selects a bottom-navigation destination
 * @param modifier modifier applied to the screen scaffold
 * @param viewModel ViewModel providing saved artworks state and actions.
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
fun SavedArtworksScreen(
    onBackClick: () -> Unit,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onTabSelected: (Tab) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedArtworksViewModel,
) {
  val uiState by viewModel.uiState.collectAsState()

  // NavHost only composes the top destination, so this runs on every visit, including when the
  // user comes back from the details screen to a ViewModel that kept its previous list.
  // This prevents stale list to be displayed when user performs save/unsave actions.
  LaunchedEffect(Unit) { viewModel.refreshSavedArtworks() }

  SavedArtworksScreenLayout(
      uiState = uiState,
      onBackClick = onBackClick,
      onArtworkDetailsClick = onArtworkDetailsClick,
      onArtworkUnsave = viewModel::onArtworkUnsave,
      onApplyFilter = viewModel::applyFilter,
      onClearFilter = viewModel::clearFilter,
      onRetry = viewModel::refreshSavedArtworks,
      onLoadErrorHandled = viewModel::clearLoadError,
      onUnsaveErrorHandled = viewModel::clearUnsaveError,
      onMapClick = onMapClick,
      onTabSelected = onTabSelected,
      modifier = modifier,
  )
}

/**
 * Renders the saved artworks UI from an immutable state snapshot.
 *
 * Keeping this layout independent of the ViewModel makes previews deterministic and keeps
 * state-management concerns outside the visual hierarchy.
 *
 * @param uiState state snapshot used to render the screen
 * @param onBackClick called when the user taps the back arrow
 * @param onArtworkDetailsClick called when the user opens an artwork's details
 * @param onArtworkUnsave called when the user removes an artwork from the saved list
 * @param onApplyFilter called when the user applies filter criteria
 * @param onClearFilter called when the user clears active filter criteria
 * @param onRetry called when the user requests another loading attempt
 * @param onLoadErrorHandled called after the loading error toast is displayed
 * @param onUnsaveErrorHandled called after the unsave error toast is displayed
 * @param onTabSelected called when the user selects a bottom-navigation destination
 * @param modifier modifier applied to the screen scaffold
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedArtworksScreenLayout(
    uiState: SavedArtworksUIState,
    onBackClick: () -> Unit,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onArtworkUnsave: (Artwork) -> Unit,
    onApplyFilter: (ArtworkFilter) -> Unit,
    onClearFilter: () -> Unit,
    onRetry: () -> Unit,
    onLoadErrorHandled: () -> Unit,
    onUnsaveErrorHandled: () -> Unit,
    onTabSelected: (Tab) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val loadErrorMessage = uiState.loadErrorMessageResId?.let { stringResource(it) }
  val unsaveErrorMessage = uiState.unsaveErrorMessageResId?.let { stringResource(it) }
  var filterDialogVisible by remember { mutableStateOf(false) }

  LaunchedEffect(loadErrorMessage) {
    loadErrorMessage?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      onLoadErrorHandled()
    }
  }

  LaunchedEffect(unsaveErrorMessage) {
    unsaveErrorMessage?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      onUnsaveErrorHandled()
    }
  }

  Scaffold(
      modifier = modifier.testTag(SavedArtworksTestTags.SCREEN),
      // Top bar blending with the screen background, with round back and filter buttons.
      topBar = {
        TopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.saved_artworks_title),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.testTag(SavedArtworksTestTags.TITLE),
              )
            },
            navigationIcon = {
              TopBarCircle(
                  modifier = Modifier.padding(start = Dimensions.savedArtworksTopBarButtonPadding)
              ) {
                BackButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag(SavedArtworksTestTags.BACK_BUTTON),
                )
              }
            },
            actions = {
              TopBarCircle(
                  modifier = Modifier.padding(end = Dimensions.savedArtworksTopBarButtonPadding)
              ) {
                IconButton(
                    onClick = { filterDialogVisible = true },
                    modifier = Modifier.testTag(SavedArtworksTestTags.FILTER_BUTTON),
                ) {
                  BadgedBox(
                      badge = {
                        if (uiState.activeFilter.isActive()) {
                          Badge(modifier = Modifier.testTag(SavedArtworksTestTags.FILTER_ACTIVE))
                        }
                      }
                  ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.artwork_filter_open),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
        )
      },
      // Saved artworks are reached from the profile, so the Profile tab stays selected.
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.Profile,
            onTabSelected = onTabSelected,
        )
      },
  ) { innerPadding ->
    if (filterDialogVisible) {
      ArtworkFilterDialog(
          filter = uiState.activeFilter,
          onApply = {
            onApplyFilter(it)
            filterDialogVisible = false
          },
          onClear = {
            onClearFilter()
            filterDialogVisible = false
          },
          onDismiss = { filterDialogVisible = false },
          artistOptions = uiState.artistOptions,
          museumOptions = uiState.museumOptions,
          locationOptions = uiState.locationOptions,
      )
    }
    // Main content area containing the user's saved artworks.
    if (uiState.isLoading) {
      SavedArtworksLoadingState(modifier = Modifier.fillMaxSize().padding(innerPadding))
    } else if (uiState.artworks.isEmpty() && uiState.hasLoadFailed) {
      SavedArtworksLoadErrorState(
          onRetry = onRetry,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    } else if (uiState.artworks.isEmpty() && uiState.activeFilter.isActive()) {
      Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = stringResource(R.string.artwork_filter_empty_results),
            modifier = Modifier.testTag(SavedArtworksTestTags.EMPTY_FILTER_RESULTS),
        )
      }
    } else if (uiState.artworks.isEmpty()) {
      EmptySavedArtworksState(modifier = Modifier.fillMaxSize().padding(innerPadding))
    } else {
      SavedArtworksGrid(
          artworks = uiState.artworks,
          onArtworkDetailsClick = onArtworkDetailsClick,
          onArtworkUnsave = onArtworkUnsave,
          onMapClick = onMapClick,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    }
  }
}

/**
 * Provides stable semantics test tags for the saved artworks screen.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
object SavedArtworksTestTags {
  const val SCREEN = "saved_artworks_screen"
  const val TITLE = "saved_artworks_title"
  const val BACK_BUTTON = "saved_artworks_back_button"
  const val FILTER_BUTTON = "saved_artworks_filter_button"
  const val FILTER_ACTIVE = "saved_artworks_filter_active"
  const val EMPTY_FILTER_RESULTS = "saved_artworks_empty_filter_results"
  const val LIST = "saved_artworks_list"
  const val EMPTY_STATE = "saved_artworks_empty_state"
  const val LOADING_STATE = "saved_artworks_loading_state"
  const val LOAD_ERROR_STATE = "saved_artworks_load_error_state"
  const val RETRY_BUTTON = "saved_artworks_retry_button"

  /** Tag of the clickable tile showing [artwork]. */
  fun card(artwork: Artwork): String = "saved_artwork_tile_${artwork.uid}"

  /** Tag of the bookmark button that unsaves [artwork]. */
  fun unsaveButton(artwork: Artwork): String = "saved_artwork_unsave_button_${artwork.uid}"

  /** Tag of the map button of [artwork]'s tile. */
  fun mapButton(artwork: Artwork): String = "saved_artwork_map_button_${artwork.uid}"

  /** Tag of the title text of [artwork]'s tile. */
  fun title(artwork: Artwork): String = "saved_artwork_title_${artwork.uid}"

  /** Tag of the artist text of [artwork]'s tile. */
  fun artist(artwork: Artwork): String = "saved_artwork_artist_${artwork.uid}"

  /** Tag of the image of [artwork]'s tile. */
  fun image(artwork: Artwork): String = "saved_artwork_image_${artwork.uid}"

  /** Tag of the icon shown instead of [artwork]'s image while it is missing or loading. */
  fun imageFallback(artwork: Artwork): String = "saved_artwork_image_fallback_${artwork.uid}"
}

/**
 * Displays a centered message when the user has not saved any artworks yet.
 *
 * @param modifier modifier applied to the empty-state container
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun EmptySavedArtworksState(modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.testTag(SavedArtworksTestTags.EMPTY_STATE),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = stringResource(R.string.saved_artworks_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Displays the load error with a Retry button, so the user can retry after the toast is gone.
 *
 * @param onRetry called when the user taps Retry
 * @param modifier modifier applied to the error-state container
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun SavedArtworksLoadErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier =
          modifier
              .padding(horizontal = Dimensions.savedArtworksErrorHorizontalPadding)
              .testTag(SavedArtworksTestTags.LOAD_ERROR_STATE),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = stringResource(R.string.saved_artworks_load_error_state),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(Dimensions.savedArtworksRetryButtonSpacing))
    Button(
        onClick = onRetry,
        modifier = Modifier.testTag(SavedArtworksTestTags.RETRY_BUTTON),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
    ) {
      Text(text = stringResource(R.string.try_again))
    }
  }
}

/**
 * Displays a loading message while saved artworks are being retrieved.
 *
 * @param modifier modifier applied to the loading-state container
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun SavedArtworksLoadingState(modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.testTag(SavedArtworksTestTags.LOADING_STATE),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = stringResource(R.string.saved_artworks_loading))
  }
}

/**
 * Displays the saved artworks as tiles in a vertically scrolling two-column grid.
 *
 * @param artworks artworks to display
 * @param onArtworkDetailsClick called when an artwork tile is selected
 * @param onArtworkUnsave called when a saved artwork's bookmark is selected
 * @param onMapClick called when a saved artwork's map action is selected
 * @param modifier modifier applied to the grid
 * @author Kaio R. Freitas Pereira Nascimento (krfpn), Timz3rr
 */
@Composable
private fun SavedArtworksGrid(
    artworks: List<Artwork>,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onArtworkUnsave: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = modifier.testTag(SavedArtworksTestTags.LIST),
      contentPadding =
          PaddingValues(
              horizontal = Dimensions.savedArtworksListHorizontalPadding,
              vertical = Dimensions.savedArtworksListVerticalPadding,
          ),
      horizontalArrangement = Arrangement.spacedBy(Dimensions.savedArtworksItemSpacing),
      verticalArrangement = Arrangement.spacedBy(Dimensions.savedArtworksItemSpacing),
  ) {
    items(artworks, key = { it.uid }) { artwork ->
      SavedArtworkTile(
          artwork = artwork,
          onClick = onArtworkDetailsClick,
          onUnsaveClick = onArtworkUnsave,
          onMapClick = onMapClick,
      )
    }
  }
}

/**
 * Displays [content] inside a circle of the surface color, so a top bar button stands out from the
 * screen background.
 *
 * @param modifier modifier applied to the circle
 * @param content button shown in the circle
 * @author Timz3rr
 */
@Composable
private fun TopBarCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.size(Dimensions.standardTouchTargetSize),
  ) {
    Box(contentAlignment = Alignment.Center) { content() }
  }
}

/**
 * Displays a preview of the saved artworks screen with several artworks.
 *
 * To preview other states, change `uiState`:
 * - empty: `SavedArtworksUIState(isLoading = false)`
 * - loading: `SavedArtworksUIState(isLoading = true)`
 *
 * Load and unsave errors are toasts, shown at runtime only.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
internal fun SavedArtworksScreenPreview() {
  // Picked by uid to show edge cases: no artist, a non-Latin title and a long punctuated title.
  val artworks = mockArtworks.filter {
    it.uid in setOf("mock-001", "mock-004", "mock-007", "mock-010")
  }

  ArtistaTheme {
    SavedArtworksScreenLayout(
        uiState = SavedArtworksUIState(artworks = artworks, isLoading = false),
        onBackClick = {},
        onArtworkDetailsClick = {},
        onArtworkUnsave = {},
        onApplyFilter = {},
        onClearFilter = {},
        onTabSelected = {},
        onMapClick = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
    )
  }
}
