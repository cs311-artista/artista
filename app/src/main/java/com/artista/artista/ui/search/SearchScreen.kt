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
import androidx.compose.material3.MaterialTheme
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
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab

/**
 * Displays the artwork search field, its recommendations, and matching artwork results.
 *
 * @param onTabSelected called when a bottom-navigation destination is selected
 * @param viewModel provides the query, recommendations, and artwork results
 * @author IJJA3141
 */
@Composable
fun SearchScreen(
    onTabSelected: (Tab) -> Unit,
    viewModel: SearchScreenViewModel = viewModel(),
) {
  val query by viewModel.searchQuery.collectAsState()
  val recommendations by viewModel.recommendations.collectAsState()
  val artworks by viewModel.artworks.collectAsState()

  Scaffold(
      topBar = {
        SearchInput(
            query = query,
            recommendations = recommendations,
            onQueryChanged = viewModel::onSearchQueryChanged,
            onSearchSubmitted = viewModel::onSearchSubmitted,
            onRecommendationSelected = viewModel::onRecommendationSelected,
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
private fun ArtworkResults(artworks: List<Artwork>, modifier: Modifier = Modifier) {
  LazyColumn(
      modifier = modifier.testTag("search_artwork_results"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(artworks) { artwork -> ArtworkResult(artwork) }
  }
}

/**
 * Displays an artwork's title and artist in the results list.
 *
 * @param artwork artwork to display
 * @author IJJA3141
 */
@Composable
private fun ArtworkResult(artwork: Artwork) {
  ListItem(
      headlineContent = { Text(artwork.name, style = MaterialTheme.typography.titleMedium) },
      supportingContent = { Text(artwork.artistName) },
  )
}
