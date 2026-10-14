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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.ui.artwork.ArtworkCard
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab

/**
 * Displays the artwork search field, its recommendations, and matching artwork results.
 *
 * @param onTabSelected called when a bottom-navigation destination is selected
 * @param viewModel provides the query, recommendations, and artwork results
 * @param onArtworkClick called when a search result is selected
 * @param onMapClick called when the map action is selected for a search result
 * @author IJJA3141
 */
@Composable
fun SearchScreen(
    onTabSelected: (Tab) -> Unit,
    viewModel: SearchScreenViewModel = viewModel(),
    onArtworkClick: (Artwork) -> Unit = {},
    onMapClick: (Artwork) -> Unit = {},
) {
  val query by viewModel.searchQuery.collectAsState()
  val recommendations by viewModel.recommendations.collectAsState()
  val artworks by viewModel.artworks.collectAsState()
  val savedArtworkIds = viewModel.savedArtworks.collectAsState().value.map { it.uid }.toSet()

  SearchScreenContent(
      query = query,
      recommendations = recommendations,
      artworks = artworks,
      savedArtworkIds = savedArtworkIds,
      onTabSelected = onTabSelected,
      onArtworkClick = onArtworkClick,
      onMapClick = onMapClick,
      onQueryChanged = viewModel::onSearchQueryChanged,
      onSearchSubmitted = viewModel::onSearchSubmitted,
      onRecommendationSelected = viewModel::onRecommendationSelected,
      onSaveClick = viewModel::toggleSavedArtwork,
  )
}

@Composable
private fun SearchScreenContent(
    query: String,
    recommendations: List<String>,
    artworks: List<Artwork>,
    savedArtworkIds: Set<ArtworkId>,
    onTabSelected: (Tab) -> Unit,
    onArtworkClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onRecommendationSelected: (String) -> Unit,
    onSaveClick: (Artwork) -> Unit,
) {
  Scaffold(
      topBar = {
        SearchInput(
            query = query,
            recommendations = recommendations,
            onQueryChanged = onQueryChanged,
            onSearchSubmitted = onSearchSubmitted,
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
        artworks = artworks,
        savedArtworkIds = savedArtworkIds,
        onArtworkClick = onArtworkClick,
        onMapClick = onMapClick,
        onSaveClick = onSaveClick,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    )
  }
}

/**
 * Displays the query input and any recommendation strings supplied by the ViewModel.
 *
 * @param query text currently entered in the search field
 * @param recommendations strings to show in the dropdown
 * @param onQueryChanged called when the user edits the query
 * @param onSearchSubmitted called when the user submits the query from the keyboard
 * @param onRecommendationSelected called when the user selects a dropdown recommendation
 * @author IJJA3141
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(
    query: String,
    recommendations: List<String>,
    onQueryChanged: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onRecommendationSelected: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  SearchBar(
      inputField = {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = {
              onQueryChanged(it)
              expanded = true
            },
            onSearch = {
              onSearchSubmitted(it)
              expanded = false
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_input"),
            placeholder = { Text("Search artworks or artists") },
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
            headlineContent = { Text(recommendation) },
            modifier =
                Modifier.fillMaxWidth()
                    .clickable {
                      onRecommendationSelected(recommendation)
                      expanded = false
                    }
                    .testTag("search_recommendation"),
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
      modifier = modifier.testTag("search_artwork_results"),
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

// /** Previews the search screen with sample artwork results. */
// @Preview(showBackground = true)
// @Composable
// private fun SearchScreenPreview() {
//   val artworks =
//       listOf(
//           Artwork(
//               uid = "monaLisa",
//               name = "Mona Lisa",
//               artistName = "Leonardo da Vinci",
//               description = null,
//               conceptionDate = null,
//               museum = "Louvre Museum",
//               location = Location(longitude = 2.3376, latitude = 48.8606, name = "Louvre"),
//               dimension = null,
//           ),
//           Artwork(
//               uid = "starryNight",
//               name = "The Starry Night",
//               artistName = "Vincent van Gogh",
//               description = null,
//               conceptionDate = null,
//               museum = "Museum of Modern Art",
//               location = Location(longitude = -73.9776, latitude = 40.7614, name = "MoMA"),
//               dimension = null,
//           ),
//       )
//
//   ArtistaTheme {
//     SearchScreenContent(
//         query = "",
//         recommendations = emptyList(),
//         artworks = artworks,
//         savedArtworkIds = setOf("monaLisa"),
//         onTabSelected = {},
//         onArtworkClick = {},
//         onMapClick = {},
//         onQueryChanged = {},
//         onSearchSubmitted = {},
//         onRecommendationSelected = {},
//         onSaveClick = {},
//     )
//   }
// }
