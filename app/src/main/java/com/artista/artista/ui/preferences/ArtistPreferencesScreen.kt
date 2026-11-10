// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import com.artista.artista.model.user.UserRepositoryLocal
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaColors
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * Displays the popular artists the user can select as favorites to personalize their
 * recommendations.
 *
 * The callbacks have no default value, so that integrating the screen without wiring one of them
 * fails to compile instead of leaving a button that silently does nothing.
 *
 * Saving keeps the user on the screen: the Save button is enabled only when the selection differs
 * from the saved one, and a toast tells whether the save succeeded.
 *
 * @param onBackClick Callback invoked when the user leaves the screen.
 * @param onTabSelected Callback invoked with the destination selected in the bottom navigation.
 * @param artistPreferencesViewModel ViewModel providing the artists and the current selection.
 * @author Timz3rr, krfpn
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPreferencesScreen(
    onBackClick: () -> Unit,
    onTabSelected: (Tab) -> Unit,
    artistPreferencesViewModel: ArtistPreferencesViewModel,
) {
  val uiState by artistPreferencesViewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val message = uiState.messageResId?.let { stringResource(it) }

  // A toast does not block the screen, so the user can keep editing or retry at once.
  // LaunchedEffect runs only when the message changes, so the toast is shown once per save.
  LaunchedEffect(message) {
    message?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      // Cleared once shown, so a recomposition or a return to the screen does not show it again.
      artistPreferencesViewModel.clearMessage()
    }
  }

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(ArtistPreferencesTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.artist_preferences_title),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(ArtistPreferencesTestTags.TITLE),
              )
            },
            navigationIcon = {
              BackButton(
                  onClick = onBackClick,
                  modifier = Modifier.testTag(ArtistPreferencesTestTags.BACK_BUTTON),
              )
            },
        )
      },
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Profile, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = Dimensions.artistPreferencesScreenHorizontalPadding,
                    vertical = Dimensions.artistPreferencesScreenVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(Dimensions.artistPreferencesSectionSpacing),
    ) {
      Text(
          text = stringResource(R.string.artist_preferences_description),
          modifier = Modifier.testTag(ArtistPreferencesTestTags.DESCRIPTION),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.primary,
      )
      SearchField(
          query = uiState.searchQuery,
          onQueryChange = artistPreferencesViewModel::onSearchQueryChange,
      )
      Text(
          text = stringResource(R.string.artist_preferences_popular_artists),
          modifier = Modifier.testTag(ArtistPreferencesTestTags.POPULAR_ARTISTS_TITLE),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
      )
      // The weight keeps the save button above the bottom bar; the grid scrolls when it overflows.
      // A lazy grid needs a bounded height: making this column scrollable would crash it.
      LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier =
              Modifier.weight(1f).fillMaxWidth().testTag(ArtistPreferencesTestTags.ARTISTS_GRID),
          horizontalArrangement =
              Arrangement.spacedBy(Dimensions.artistPreferencesGridHorizontalSpacing),
          verticalArrangement =
              Arrangement.spacedBy(Dimensions.artistPreferencesGridVerticalSpacing),
      ) {
        items(uiState.displayedArtists, key = { it.uid }) { artist ->
          ArtistCard(
              artist = artist,
              selected = artist.uid in uiState.selectedArtists,
              onToggle = { artistPreferencesViewModel.toggleArtist(artist.uid) },
          )
        }
      }
      Button(
          onClick = artistPreferencesViewModel::saveSelectedArtists,
          // Disabled when there is nothing new to save, and while saving so a double tap does not
          // start a second write.
          enabled = uiState.canSave,
          modifier =
              Modifier.fillMaxWidth()
                  .height(Dimensions.preferencesSaveButtonHeight)
                  .testTag(ArtistPreferencesTestTags.SAVE_BUTTON),
          shape = PillShape,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = MaterialTheme.colorScheme.primary,
              ),
      ) {
        Text(
            text = stringResource(R.string.artist_preferences_save),
            style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}

/**
 * Search field filtering the artists displayed by the grid.
 *
 * @param query Text currently displayed in the field.
 * @param onQueryChange Callback invoked with the new text when the user types.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  TextField(
      value = query,
      onValueChange = onQueryChange,
      modifier = Modifier.fillMaxWidth().testTag(ArtistPreferencesTestTags.SEARCH_BAR),
      placeholder = { Text(stringResource(R.string.artist_preferences_search_placeholder)) },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      singleLine = true,
      shape = PillShape,
      // The mockup shows a pill without an underline, whether the field has focus.
      colors =
          TextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedPlaceholderColor = MaterialTheme.colorScheme.primary,
              unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary,
              focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
              unfocusedLeadingIconColor = MaterialTheme.colorScheme.primary,
              focusedIndicatorColor = ArtistaColors.Transparent,
              unfocusedIndicatorColor = ArtistaColors.Transparent,
          ),
  )
}

/**
 * Card of a popular artist, which the user can click to select or deselect the artist.
 *
 * @param artist Artist displayed by the card.
 * @param selected Whether the artist is currently selected.
 * @param onToggle Callback invoked when the card is clicked.
 */
@Composable
private fun ArtistCard(artist: Artist, selected: Boolean, onToggle: () -> Unit) {
  // A checkable Surface, unlike a clickable one, exposes the on/off state used by assertIsOn.
  Surface(
      checked = selected,
      onCheckedChange = { onToggle() },
      modifier =
          Modifier.fillMaxWidth()
              // Lets accessibility services announce the card as a checkbox with its state.
              .semantics { role = Role.Checkbox }
              .testTag(ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid)),
      shape = RoundedCornerShape(Dimensions.cornerRadiusMedium),
      color =
          if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
          else MaterialTheme.colorScheme.secondaryContainer,
      border =
          if (selected) {
            BorderStroke(Dimensions.standardStrokeWidth, MaterialTheme.colorScheme.primary)
          } else {
            null
          },
  ) {
    Column(
        modifier = Modifier.padding(Dimensions.artistCardContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box {
        // TODO(#104): replace the placeholder with the artist picture once images are available.
        ArtistImagePlaceholder()
        SelectionIndicator(
            selected = selected,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(Dimensions.artistCardSelectionIndicatorPadding),
        )
      }
      Text(
          text = artist.name,
          modifier = Modifier.padding(top = Dimensions.artistCardNameTopSpacing),
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          // Two lines for every name keeps all the cards of a row at the same height.
          minLines = 2,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Static placeholder shown until artist pictures are available. */
@Composable
private fun ArtistImagePlaceholder() {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .aspectRatio(4f / 3f)
              .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall))
              .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = Icons.Outlined.Image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Round indicator in the corner of an artist card, checked when the artist is selected.
 *
 * @param selected Whether the artist is currently selected.
 * @param modifier Modifier applied to the indicator.
 */
@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
  Box(
      modifier =
          modifier
              .size(Dimensions.iconSizeLarge)
              .clip(CircleShape)
              .background(
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surface
              ),
      contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          modifier = Modifier.size(Dimensions.iconSizeSmall),
          tint = MaterialTheme.colorScheme.onPrimary,
      )
    }
  }
}

/** Previews the artist preferences screen with two selected artists in Android Studio. */
@Preview(showBackground = true)
@Composable
internal fun ArtistPreferencesScreenPreview() {
  val viewModel = remember {
    ArtistPreferencesViewModel(
            artworkRepository = ArtworkRepositoryProvider.mockRepository,
            authRepository = PreviewAuthRepository,
            userRepository = UserRepositoryLocal(),
        )
        .apply {
          toggleArtist("mock-artist-002")
          toggleArtist("mock-artist-004")
        }
  }
  ArtistaTheme {
    ArtistPreferencesScreen(
        onBackClick = {},
        onTabSelected = {},
        artistPreferencesViewModel = viewModel,
    )
  }
}

/**
 * Signed-out [AuthRepository] for the preview.
 *
 * [ArtistPreferencesViewModel] needs an [AuthRepository] to know who is signed in, but the preview
 * cannot reach Firebase. This one always answers that nobody is signed in, so saves nothing.
 */
private object PreviewAuthRepository : AuthRepository {
  override val authState: Flow<AuthUser?> = flowOf(null)

  override suspend fun signIn(idToken: String): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("Sign-in is not available in previews"))

  override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}
