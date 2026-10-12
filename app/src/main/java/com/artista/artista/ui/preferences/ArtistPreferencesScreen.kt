package com.artista.artista.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
private val PillShape = RoundedCornerShape(percent = 50)

// Matches the minimum touch target of IconButton, so the texts below the title line up with it.
private val BackButtonSize = 48.dp

/**
 * Displays the popular artists the user can select as favorites to personalize their
 * recommendations.
 *
 * @param artistPreferencesViewModel ViewModel providing the artists and the current selection.
 * @param onBackClick Callback invoked when the user leaves the screen.
 * @param onSaveClick Callback invoked when the user saves their preferences.
 * @param onTabSelected Callback invoked with the destination selected in the bottom navigation.
 * @author Timz3rr
 */
@Composable
fun ArtistPreferencesScreen(
    artistPreferencesViewModel: ArtistPreferencesViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by artistPreferencesViewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(ArtistPreferencesTestTags.SCREEN),
      // Same brand-tinted background as the profile screen, which this screen belongs to.
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Profile, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Header(onBackClick = onBackClick)
      SearchField()
      Text(
          text = stringResource(R.string.artist_preferences_popular_artists),
          modifier = Modifier.testTag(ArtistPreferencesTestTags.POPULAR_ARTISTS_TITLE),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
      )
      // The weight keeps the save button above the bottom bar; the grid scrolls when it overflows.
      LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier =
              Modifier.weight(1f).fillMaxWidth().testTag(ArtistPreferencesTestTags.ARTISTS_GRID),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(uiState.popularArtists, key = { it }) { artist ->
          ArtistCard(
              artist = artist,
              selected = artist in uiState.selectedArtists,
              onToggle = { artistPreferencesViewModel.toggleArtist(artist) },
          )
        }
      }
      Button(
          onClick = onSaveClick,
          modifier =
              Modifier.fillMaxWidth().height(56.dp).testTag(ArtistPreferencesTestTags.SAVE_BUTTON),
          shape = PillShape,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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
 * Back button, title and description at the top of the screen.
 *
 * @param onBackClick Callback invoked when the back button is clicked.
 */
@Composable
private fun Header(onBackClick: () -> Unit) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Same icon as the artwork screen, to keep the back navigation consistent across screens.
      IconButton(
          onClick = onBackClick,
          modifier = Modifier.size(BackButtonSize).testTag(ArtistPreferencesTestTags.BACK_BUTTON),
      ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
        )
      }
      Text(
          text = stringResource(R.string.artist_preferences_title),
          modifier = Modifier.testTag(ArtistPreferencesTestTags.TITLE),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    Text(
        text = stringResource(R.string.artist_preferences_description),
        modifier =
            Modifier.padding(start = BackButtonSize).testTag(ArtistPreferencesTestTags.DESCRIPTION),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
  }
}

/** Search field for artists, disabled until searching is implemented. */
@Composable
private fun SearchField() {
  TextField(
      value = "",
      onValueChange = {},
      modifier = Modifier.fillMaxWidth().testTag(ArtistPreferencesTestTags.SEARCH_BAR),
      enabled = false,
      placeholder = { Text(stringResource(R.string.artist_preferences_search_placeholder)) },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      singleLine = true,
      shape = PillShape,
      // A disabled field is greyed out by default; the mockup shows it like an active one.
      colors =
          TextFieldDefaults.colors(
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              disabledPlaceholderColor = MaterialTheme.colorScheme.primary,
              disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
              disabledIndicatorColor = Color.Transparent,
          ),
  )
}

/**
 * Card of a popular artist, which the user can click to select or deselect the artist.
 *
 * @param artist Name of the artist displayed by the card.
 * @param selected Whether the artist is currently selected.
 * @param onToggle Callback invoked when the card is clicked.
 */
@Composable
private fun ArtistCard(artist: String, selected: Boolean, onToggle: () -> Unit) {
  Surface(
      checked = selected,
      onCheckedChange = { onToggle() },
      modifier =
          Modifier.fillMaxWidth()
              // Lets accessibility services announce the card as a checkbox with its state.
              .semantics { role = Role.Checkbox }
              .testTag(ArtistPreferencesTestTags.getArtistCardTestTag(artist)),
      shape = RoundedCornerShape(20.dp),
      color =
          if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
          else MaterialTheme.colorScheme.secondaryContainer,
      border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
  ) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box {
        ArtistImagePlaceholder()
        SelectionIndicator(
            selected = selected,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        )
      }
      Text(
          text = artist,
          modifier = Modifier.padding(top = 8.dp),
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
              .clip(RoundedCornerShape(12.dp))
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
              .size(24.dp)
              .clip(CircleShape)
              .background(
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceContainerLowest
              ),
      contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onPrimary,
      )
    }
  }
}

/** Previews the artist preferences screen with two selected artists in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun ArtistPreferencesScreenPreview() {
  val viewModel = remember {
    ArtistPreferencesViewModel().apply {
      toggleArtist("Vincent van Gogh")
      toggleArtist("Claude Monet")
    }
  }
  ArtistaTheme { ArtistPreferencesScreen(artistPreferencesViewModel = viewModel) }
}
