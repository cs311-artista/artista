// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme

// ---------------------------------------------------------------------------
// Saved artworks screen
// ---------------------------------------------------------------------------

/**
 * Displays the artworks saved by the current user.
 *
 * The screen receives its artwork list and actions from its caller so that loading, persistence,
 * and navigation remain outside the UI layer.
 *
 * @param viewModel ViewModel providing saved-artwork state and actions
 * @param onArtworkDetailsClick called when the user opens an artwork's details
 * @param onTabSelected called when the user selects a bottom-navigation destination
 * @param modifier modifier applied to the screen scaffold
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
fun SavedArtworkScreen(
    modifier: Modifier = Modifier,
    viewModel: SavedArtworkViewModel = viewModel(),
    onArtworkDetailsClick: (Artwork) -> Unit,
    onTabSelected: (Tab) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  SavedArtworkScreenLayout(
      uiState = uiState,
      onArtworkDetailsClick = onArtworkDetailsClick,
      onArtworkUnsave = viewModel::onArtworkUnsave,
      onRetry = viewModel::refreshSavedArtworks,
      onLoadErrorHandled = viewModel::clearLoadError,
      onUnsaveErrorHandled = viewModel::clearUnsaveError,
      onTabSelected = onTabSelected,
      modifier = modifier,
  )
}

/**
 * Renders the saved-artwork UI from an immutable state snapshot.
 *
 * Keeping this layout independent of the ViewModel makes previews deterministic and keeps
 * state-management concerns outside the visual hierarchy.
 *
 * @param uiState state snapshot used to render the screen
 * @param onArtworkDetailsClick called when the user opens an artwork's details
 * @param onArtworkUnsave called when the user removes an artwork from the saved list
 * @param onRetry called when the user requests another loading attempt
 * @param onLoadErrorHandled called after the loading error snackbar is dismissed
 * @param onUnsaveErrorHandled called after the unsave error toast is displayed
 * @param onTabSelected called when the user selects a bottom-navigation destination
 * @param modifier modifier applied to the screen scaffold
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedArtworkScreenLayout(
    uiState: SavedArtworkUIState,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onArtworkUnsave: (Artwork) -> Unit,
    onRetry: () -> Unit,
    onLoadErrorHandled: () -> Unit,
    onUnsaveErrorHandled: () -> Unit,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  LaunchedEffect(uiState.loadErrorMessage) {
    uiState.loadErrorMessage?.let { message ->
      val result =
          snackbarHostState.showSnackbar(
              message = message,
              actionLabel = "Retry",
          )
      if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
        onRetry()
      }
      onLoadErrorHandled()
    }
  }

  LaunchedEffect(uiState.unsaveErrorMessage) {
    uiState.unsaveErrorMessage?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      onUnsaveErrorHandled()
    }
  }

  Scaffold(
      modifier = modifier.testTag(SavedArtworkTestTags.SCREEN),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      // Top app bar displaying the name of the saved-artworks screen.
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Surface(
                  shape = RoundedCornerShape(32.dp),
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  modifier = Modifier.testTag(SavedArtworkTestTags.TITLE),
              ) {
                Text(
                    text = "Saved Artworks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
              }
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
      SavedArtworkLoadingState(modifier = Modifier.fillMaxSize().padding(innerPadding))
    } else if (uiState.artworks.isEmpty()) {
      EmptySavedArtworkState(modifier = Modifier.fillMaxSize().padding(innerPadding))
    } else {
      SavedArtworkList(
          artworks = uiState.artworks,
          onArtworkDetailsClick = onArtworkDetailsClick,
          onArtworkUnsave = onArtworkUnsave,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Test tags
// ---------------------------------------------------------------------------

/**
 * Provides stable semantics test tags for the saved-artwork screen.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
object SavedArtworkTestTags {
  /** Tag identifying the saved-artwork screen. */
  const val SCREEN = "saved_artwork_screen"

  /** Tag identifying the screen title. */
  const val TITLE = "saved_artwork_title"

  /** Tag identifying the saved-artwork list. */
  const val LIST = "saved_artwork_list"

  /** Tag identifying the empty saved-artwork state. */
  const val EMPTY_STATE = "saved_artwork_empty_state"

  /** Tag identifying the saved-artwork loading state. */
  const val LOADING_STATE = "saved_artwork_loading_state"

  /**
   * Returns the tag identifying an artwork card.
   *
   * @param artwork the artwork represented by the card
   * @return a stable tag derived from the artwork title
   */
  fun card(artwork: Artwork): String = "saved_artwork_card_${artwork.name}"

  /**
   * Returns the tag identifying an artwork's details button.
   *
   * @param artwork the artwork represented by the button
   * @return a stable tag derived from the artwork title
   */
  fun detailsButton(artwork: Artwork): String = "saved_artwork_details_${artwork.name}"

  /**
   * Returns the tag identifying an artwork's unsave button.
   *
   * @param artwork the artwork represented by the button
   * @return a stable tag derived from the artwork title
   */
  fun unsaveButton(artwork: Artwork): String = "saved_artwork_unsave_${artwork.name}"
}

// ---------------------------------------------------------------------------
// Empty saved artwork state
// ---------------------------------------------------------------------------

/**
 * Displays a centered message when the user has not saved any artworks yet.
 *
 * @param modifier modifier applied to the empty-state container
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun EmptySavedArtworkState(modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.testTag(SavedArtworkTestTags.EMPTY_STATE),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "You have no saved artworks yet.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ---------------------------------------------------------------------------
// Saved artwork loading state
// ---------------------------------------------------------------------------
/**
 * Displays a loading message while saved artworks are being retrieved.
 *
 * @param modifier modifier applied to the loading-state container
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun SavedArtworkLoadingState(modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.testTag(SavedArtworkTestTags.LOADING_STATE),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = "Loading saved artworks...")
  }
}

// ---------------------------------------------------------------------------
// Saved artwork list
// ---------------------------------------------------------------------------

/**
 * Displays the saved artworks in a vertically scrolling list.
 *
 * @param artworks artworks to display
 * @param onArtworkDetailsClick called when a details button is pressed
 * @param onArtworkUnsave called when an unsave button is pressed
 * @param modifier modifier applied to the list
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun SavedArtworkList(
    artworks: List<Artwork>,
    onArtworkDetailsClick: (Artwork) -> Unit,
    onArtworkUnsave: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  // Scrollable collection of artwork cards with spacing for comfortable browsing.
  LazyColumn(
      modifier = modifier.testTag(SavedArtworkTestTags.LIST),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(artworks, key = { it.name }) { artwork ->
      SavedArtworkCard(
          artwork = artwork,
          onDetailsClick = { onArtworkDetailsClick(artwork) },
          onUnsaveClick = { onArtworkUnsave(artwork) },
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Saved artwork card
// ---------------------------------------------------------------------------

/**
 * Displays one saved artwork with its image area, metadata, and actions.
 *
 * @param artwork artwork represented by this card
 * @param onDetailsClick called when the user opens the artwork details
 * @param onUnsaveClick called when the user removes the artwork from saved artworks
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
private fun SavedArtworkCard(
    artwork: Artwork,
    onDetailsClick: () -> Unit,
    onUnsaveClick: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(SavedArtworkTestTags.card(artwork)),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    // Artwork visual area and metadata displayed above the card actions.
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      ArtworkImagePlaceholder()
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          text = artwork.artistName ?: "Unknown Artist",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = artwork.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(12.dp))
      // Side-by-side actions for opening details or removing the artwork.
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
            onClick = onDetailsClick,
            modifier = Modifier.weight(1f).testTag(SavedArtworkTestTags.detailsButton(artwork)),
        ) {
          Text("Open details")
        }
        Button(
            onClick = onUnsaveClick,
            modifier = Modifier.weight(1f).testTag(SavedArtworkTestTags.unsaveButton(artwork)),
        ) {
          Text("Unsave")
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Artwork image placeholder
// ---------------------------------------------------------------------------

/**
 * Displays the image area reserved for an artwork.
 *
 * Artwork currently has no image source in the domain model, so this placeholder keeps the card
 * layout ready for image loading without coupling the screen to a future image implementation.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 *
 * TODO: This component serves as a placeholder for the image. Remove it, and implement component
 *   for the image.
 */
@Composable
private fun ArtworkImagePlaceholder() {
  // Reserved visual area for the artwork image until image data is available in the model.
  Card(
      modifier = Modifier.fillMaxWidth().height(180.dp).testTag("saved_artwork_image"),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(
          text = "Artwork image",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Compose preview
// ---------------------------------------------------------------------------

/**
 * Creates a representative artwork for saved-artwork previews.
 *
 * @param name title used for the preview artwork
 * @param artistName artist used for the preview artwork
 * @return artwork containing the supplied preview metadata
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
private fun previewArtwork(name: String, artistName: String): Artwork =
    Artwork(
        name = name,
        artistName = artistName,
        description = null,
        conceptionDate = null,
        museum = null,
        location = null,
        dimension = null,
    )

/**
 * Displays a preview of the saved-artwork screen with one representative artwork.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworkScreenPreview() {
  ArtistaTheme {
    SavedArtworkScreenLayout(
        uiState =
            SavedArtworkUIState(
                artworks = listOf(previewArtwork("The Starry Night", "Vincent van Gogh")),
                isLoading = false,
            ),
        onArtworkDetailsClick = {},
        onArtworkUnsave = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
        onTabSelected = {},
    )
  }
}

/**
 * Displays a preview of the empty saved-artworks state.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworkEmptyPreview() {
  ArtistaTheme {
    SavedArtworkScreenLayout(
        uiState = SavedArtworkUIState(isLoading = false),
        onArtworkDetailsClick = {},
        onArtworkUnsave = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
        onTabSelected = {},
    )
  }
}

/**
 * Displays a preview of the loading saved-artworks state.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworkLoadingPreview() {
  ArtistaTheme {
    SavedArtworkScreenLayout(
        uiState = SavedArtworkUIState(isLoading = true),
        onArtworkDetailsClick = {},
        onArtworkUnsave = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
        onTabSelected = {},
    )
  }
}

/**
 * Displays a preview containing multiple saved artworks.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworkMultipleItemsPreview() {
  val artworks =
      listOf(
          previewArtwork("The Starry Night", "Vincent van Gogh"),
          previewArtwork("Girl with a Pearl Earring", "Johannes Vermeer"),
          previewArtwork("The Persistence of Memory", "Salvador Dalí"),
      )

  ArtistaTheme {
    SavedArtworkScreenLayout(
        uiState = SavedArtworkUIState(artworks = artworks, isLoading = false),
        onArtworkDetailsClick = {},
        onArtworkUnsave = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
        onTabSelected = {},
    )
  }
}

/**
 * Displays a preview of the toast shown when unsaving an artwork fails.
 *
 * Android Toasts are runtime side effects and cannot be rendered reliably by Compose Preview, so
 * this preview uses a Material 3 surface with the same transient-message role.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworkUnsaveErrorPreview() {
  val artwork = previewArtwork("The Starry Night", "Vincent van Gogh")

  ArtistaTheme {
    Box {
      SavedArtworkScreenLayout(
          uiState = SavedArtworkUIState(artworks = listOf(artwork), isLoading = false),
          onArtworkDetailsClick = {},
          onArtworkUnsave = {},
          onRetry = {},
          onLoadErrorHandled = {},
          onUnsaveErrorHandled = {},
          onTabSelected = {},
      )
      Surface(
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.inverseSurface,
      ) {
        Text(
            text = "Unable to unsave artwork.",
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
      }
    }
  }
}

/**
 * Displays a preview of the loading-error snackbar with its retry action.
 *
 * The preview renders the snackbar directly so its message and action remain visible in Android
 * Studio without requiring a running coroutine or repository operation.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworkLoadingErrorPreview() {
  ArtistaTheme {
    Box {
      SavedArtworkScreenLayout(
          uiState = SavedArtworkUIState(isLoading = false),
          onArtworkDetailsClick = {},
          onArtworkUnsave = {},
          onRetry = {},
          onLoadErrorHandled = {},
          onUnsaveErrorHandled = {},
          onTabSelected = {},
      )
      Snackbar(
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
          action = { Text("Retry") },
      ) {
        Text("Unable to load saved artworks.")
      }
    }
  }
}
