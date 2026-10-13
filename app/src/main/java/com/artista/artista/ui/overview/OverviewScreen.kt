// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.overview

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme

/**
 * Provides stable semantics tags for Overview screen UI tests.
 *
 * @author patrickmcdan
 */
object OverviewTestTags {
  const val SCREEN = "overview_screen"
  const val ARTWORK_LIST = "overview_artwork_list"
  const val ARTWORK_ITEM = "overview_artwork_item"
  const val ERROR_MESSAGE = "overview_error_message"
  const val SAVED_ARTWORKS_BUTTON = "overview_saved_artworks_button"
  const val SEARCH_BAR = "overview_search_bar"
}

/**
 * Dimensions used by the Overview screen.
 *
 * Kept local until a project-wide dimensions convention is established.
 *
 * @author patrickmcdan
 */
private object OverviewDimensions {
  val listHorizontalPadding: Dp = 16.dp
  val listVerticalPadding: Dp = 8.dp
  val listItemSpacing: Dp = 8.dp
  val searchBarHorizontalPadding: Dp = 16.dp
  val searchBarVerticalPadding: Dp = 8.dp
  val searchBarHeight: Dp = 56.dp
  val searchBarContentPadding: Dp = 16.dp
  val searchBarTextSpacing: Dp = 12.dp
  val searchBarElevation: Dp = 2.dp
  val errorContentSpacing: Dp = 12.dp
}

/**
 * Displays the artwork overview using the state supplied by its ViewModel.
 *
 * @param onTabSelected callback invoked when a bottom-navigation destination is selected
 * @param onSavedArtworksClick callback invoked when the user chooses saved artworks after an
 *   overview loading error
 * @param viewModel ViewModel supplying the artwork entries
 * @param onSearchClick callback invoked when the search control is selected
 * @author patrickmcdan
 */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
    onSavedArtworksClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
  }

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(OverviewTestTags.SCREEN),
      topBar = { OverviewSearchBar(onClick = onSearchClick) },
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Overview, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    if (errorMessage != null) {
      Box(
          modifier =
              Modifier.fillMaxSize().padding(innerPadding).testTag(OverviewTestTags.ARTWORK_LIST),
          contentAlignment = Alignment.Center,
      ) {
        OverviewErrorContent(onSavedArtworksClick = onSavedArtworksClick)
      }
    } else {
      LazyColumn(
          modifier =
              Modifier.fillMaxSize().padding(innerPadding).testTag(OverviewTestTags.ARTWORK_LIST),
          contentPadding =
              PaddingValues(
                  horizontal = OverviewDimensions.listHorizontalPadding,
                  vertical = OverviewDimensions.listVerticalPadding,
              ),
          verticalArrangement = Arrangement.spacedBy(OverviewDimensions.listItemSpacing),
      ) {
        items(uiState.artworks) { artwork -> ArtworkListItem(artwork) }
      }
    }
  }
}

/**
 * Displays a non-editable search control that delegates search navigation to its caller.
 *
 * @param onClick callback invoked when the search control is selected
 * @param modifier modifier applied to the search control
 * @author patrickmcdan
 */
@Composable
private fun OverviewSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier =
          modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(
                  horizontal = OverviewDimensions.searchBarHorizontalPadding,
                  vertical = OverviewDimensions.searchBarVerticalPadding,
              )
              .height(OverviewDimensions.searchBarHeight)
              .clickable(role = Role.Button, onClick = onClick)
              .semantics(mergeDescendants = true) {}
              .testTag(OverviewTestTags.SEARCH_BAR),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      tonalElevation = OverviewDimensions.searchBarElevation,
  ) {
    Row(
        modifier =
            Modifier.fillMaxSize().padding(horizontal = OverviewDimensions.searchBarContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = stringResource(R.string.overview_search_placeholder),
          modifier = Modifier.padding(start = OverviewDimensions.searchBarTextSpacing),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Displays the loading error and the action available when nearby artworks cannot be loaded.
 *
 * @param onSavedArtworksClick callback invoked when saved artworks are requested
 * @author patrickmcdan
 */
@Composable
private fun OverviewErrorContent(onSavedArtworksClick: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxWidth().testTag(OverviewTestTags.ERROR_MESSAGE),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(OverviewDimensions.errorContentSpacing),
  ) {
    Text(text = stringResource(R.string.overview_load_error))
    Button(
        onClick = onSavedArtworksClick,
        modifier = Modifier.testTag(OverviewTestTags.SAVED_ARTWORKS_BUTTON),
    ) {
      Text(text = stringResource(R.string.overview_saved_artworks_action))
    }
  }
}

/**
 * Displays the temporary list representation of one artwork.
 *
 * Keeping this item separate allows the final artwork-card design to replace this component without
 * changing the Overview screen's list and state structure.
 *
 * @param artwork artwork represented by the item
 * @param modifier modifier applied to the item
 * @author patrickmcdan
 */
@Composable
fun ArtworkListItem(
    artwork: Artwork,
    modifier: Modifier = Modifier,
) {
  ListItem(
      headlineContent = { Text(text = artwork.name, style = MaterialTheme.typography.titleMedium) },
      supportingContent = { Text(text = artwork.artistName ?: "Unknown Artist") },
      modifier = modifier.fillMaxWidth().testTag(OverviewTestTags.ARTWORK_ITEM),
  )
}

/** Previews the overview screen with a sample set of artworks in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun OverviewScreenPreview() {
  val viewModel = remember { OverviewViewModel() }
  ArtistaTheme { OverviewScreen(viewModel = viewModel) }
}
