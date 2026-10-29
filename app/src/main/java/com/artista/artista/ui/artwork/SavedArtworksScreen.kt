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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
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
 * @param viewModel ViewModel providing saved artworks state and actions. Defaults to a ViewModel
 *   backed by the shared [com.artista.artista.model.artwork.ArtworkRepositoryProvider.repository].
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
fun SavedArtworksScreen(
    onBackClick: () -> Unit,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onTabSelected: (Tab) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedArtworksViewModel =
        viewModel(
            factory = SavedArtworksViewModel.provideFactory(ArtworkRepositoryProvider.repository)
        ),
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
      // Top app bar displaying the name of the saved artworks screen.
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Surface(
                  shape = RoundedCornerShape(Dimensions.cornerRadiusPill),
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  modifier = Modifier.testTag(SavedArtworksTestTags.TITLE),
              ) {
                Text(
                    text = stringResource(R.string.saved_artworks_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier.padding(
                            horizontal = Dimensions.savedArtworksTitleHorizontalPadding,
                            vertical = Dimensions.savedArtworksTitleVerticalPadding,
                        ),
                )
              }
            },
            navigationIcon = {
              BackButton(
                  onClick = onBackClick,
                  modifier = Modifier.testTag(SavedArtworksTestTags.BACK_BUTTON),
              )
            },
        )
      },
      // Shared bottom navigation for the application's main sections.
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = null,
            onTabSelected = onTabSelected,
        )
      },
  ) { innerPadding ->
    // Main content area containing the user's saved artworks.
    if (uiState.isLoading) {
      SavedArtworksLoadingState(modifier = Modifier.fillMaxSize().padding(innerPadding))
    } else if (uiState.artworks.isEmpty() && uiState.hasLoadFailed) {
      SavedArtworksLoadErrorState(
          onRetry = onRetry,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    } else if (uiState.artworks.isEmpty()) {
      EmptySavedArtworksState(modifier = Modifier.fillMaxSize().padding(innerPadding))
    } else {
      SavedArtworksList(
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
  const val LIST = "saved_artworks_list"
  const val EMPTY_STATE = "saved_artworks_empty_state"
  const val LOADING_STATE = "saved_artworks_loading_state"
  const val LOAD_ERROR_STATE = "saved_artworks_load_error_state"
  const val RETRY_BUTTON = "saved_artworks_retry_button"
  const val IMAGE = "saved_artwork_image"

  /** Tag of the card showing [artwork]. */
  fun card(artwork: Artwork): String = "${ArtworkCardTestTag.ARTWORK_CARD}_${artwork.uid}"

  /** Tag of the "Unsave" button of [artwork]'s card. */
  fun unsaveButton(artwork: Artwork): String =
      "${ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON}_${artwork.uid}"

  /** Tag of the map button of [artwork]'s card. */
  fun mapButton(artwork: Artwork): String =
      "${ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON}_${artwork.uid}"
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
    ) {
      Text(text = stringResource(R.string.saved_artworks_try_again))
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
 * Displays the saved artworks in a vertically scrolling list.
 *
 * @param artworks artworks to display
 * @param onArtworkDetailsClick called when an artwork card is selected
 * @param onArtworkUnsave called when a saved artwork's save action is selected
 * @param onMapClick called when a saved artwork's map action is selected
 * @param modifier modifier applied to the list
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun SavedArtworksList(
    artworks: List<Artwork>,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onArtworkUnsave: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  // Scrollable collection of artwork cards with spacing for comfortable browsing.
  LazyColumn(
      modifier = modifier.testTag(SavedArtworksTestTags.LIST),
      contentPadding =
          PaddingValues(
              horizontal = Dimensions.savedArtworksListHorizontalPadding,
              vertical = Dimensions.savedArtworksListVerticalPadding,
          ),
      verticalArrangement = Arrangement.spacedBy(Dimensions.savedArtworksItemSpacing),
  ) {
    items(artworks, key = { it.uid }) { artwork ->
      ArtworkCard(
          artwork = artwork,
          isSaved = true,
          onArtworkClick = { onArtworkDetailsClick(artwork) },
          onMapClick = { onMapClick(artwork) },
          onSaveClick = { onArtworkUnsave(artwork) },
          testTagSuffix = artwork.uid,
      )
    }
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
fun SavedArtworksScreenPreview() {
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
        onTabSelected = {},
        onMapClick = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
    )
  }
}
