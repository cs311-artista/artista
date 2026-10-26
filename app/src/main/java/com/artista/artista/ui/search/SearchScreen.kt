// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.ui.artwork.ArtworkCard
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme

/** Stable semantics tags used by SearchScreen UI tests. */
object SearchScreenTestTags {
  /** Tag of the search text input. */
  const val SEARCH_INPUT = "search_input"

  /** Tag of each artwork recommendation in the dropdown. */
  const val SEARCH_RECOMMENDATION = "search_recommendation"

  /** Tag of the artwork results list. */
  const val ARTWORK_RESULTS = "search_artwork_results"
}

/**
 * Displays the artwork search field, its recommendations, and matching artwork results.
 *
 * @param viewModel supplies and updates the screen's search, recommendation, saved-artwork, and
 *   result state
 * @param onTabSelected callback invoked when the user selects a bottom-navigation tab
 * @param onArtworkClick callback invoked when the user selects an artwork in the results list
 * @param onMapClick callback invoked when the user selects the map action for a result
 * @param onRecommendationSelected callback invoked when the user selects a search suggestion
 * @author IJJA3141
 */
@Composable
fun SearchScreen(
    viewModel: SearchScreenViewModel =
        viewModel(
            factory =
                SearchScreenViewModel.provideFactory(
                    savedArtworksRepository = ArtworkRepositoryProvider.repository,
                    wikiDataRepository = ArtworkRepositoryProvider.repository,
                )
        ),
    onTabSelected: (Tab) -> Unit,
    onArtworkClick: (Artwork) -> Unit = {},
    onMapClick: (Artwork) -> Unit = {},
    onRecommendationSelected: (Artwork) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  val textFieldState =
      remember(viewModel) {
        TextFieldState(initialText = uiState.query, initialSelection = uiState.selection)
      }

  Scaffold(
      topBar = {
        SearchInput(
            textFieldState = textFieldState,
            recommendations = uiState.recommendations,
            onQueryChanged = viewModel::onSearchInputChanged,
            onSearchSubmitted = viewModel::onSearchSubmitted,
            onRecommendationSelected = onRecommendationSelected,
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = null,
            onTabSelected = onTabSelected,
        )
      },
  ) { contentPadding ->
    ArtworkResults(
        artworks = uiState.artworks,
        savedArtworkIds = uiState.savedArtworks.map { it.uid }.toSet(),
        onArtworkClick = onArtworkClick,
        onMapClick = onMapClick,
        onSaveClick = viewModel::toggleSavedArtwork,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    )
  }
}

/**
 * Displays the query input and any recommendation strings supplied by the ViewModel.
 *
 * @param textFieldState text and cursor state currently shown in the search field
 * @param recommendations strings to show in the dropdown
 * @param onQueryChanged called when the user edits the query or selection
 * @param onSearchSubmitted called when the user submits the query from the keyboard
 * @param onRecommendationSelected called when the user selects a dropdown recommendation
 * @author IJJA3141
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(
    textFieldState: TextFieldState,
    recommendations: List<Artwork>,
    onQueryChanged: (String, TextRange) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onRecommendationSelected: (Artwork) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  LaunchedEffect(textFieldState) {
    snapshotFlow { textFieldState.text.toString() to textFieldState.selection }
        .collect { (query, selection) -> onQueryChanged(query, selection) }
  }

  SearchBar(
      inputField = {
        SearchBarDefaults.InputField(
            state = textFieldState,
            onSearch = {
              onSearchSubmitted(it)
              expanded = false
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .testTag(SearchScreenTestTags.SEARCH_INPUT),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
        )
      },
      expanded = expanded,
      onExpandedChange = { expanded = it },
      modifier = Modifier.fillMaxWidth(),
  ) {
    LazyColumn {
      items(recommendations) { recommendation ->
        ListItem(
            headlineContent = { Text(recommendation.name) },
            modifier =
                Modifier.fillMaxWidth()
                    .clickable {
                      onRecommendationSelected(recommendation)
                      expanded = false
                    }
                    .testTag(SearchScreenTestTags.SEARCH_RECOMMENDATION),
        )
      }
    }
  }
}

/**
 * Displays the artwork results provided by the ViewModel.
 *
 * @param artworks artwork entries to display
 * @param modifier modifier applied to the result list
 * @author IJJA3141
 */
@Composable
private fun ArtworkResults(
    artworks: List<Artwork>,
    savedArtworkIds: Set<ArtworkId>,
    onArtworkClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    onSaveClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyColumn(
      modifier = modifier.testTag(SearchScreenTestTags.ARTWORK_RESULTS),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(artworks) { artwork ->
      ArtworkCard(
          artwork = artwork,
          isSaved = artwork.uid in savedArtworkIds,
          onArtworkClick = onArtworkClick,
          onMapClick = onMapClick,
          onSaveClick = onSaveClick,
      )
    }
  }
}

/** Previews the complete search screen in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
  ArtistaTheme { SearchScreen(onTabSelected = {}) }
}
