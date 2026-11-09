// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.ui.artwork.ArtworkCard
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import com.artista.artista.ui.theme.SearchBarPalette

/** Stable semantics tags used by SearchScreen UI tests. */
object SearchScreenTestTags {
  /** Tag of the search text input. */
  const val SEARCH_INPUT = "search_input"

  /** Tag of each artwork recommendation in the dropdown. */
  const val SEARCH_RECOMMENDATION = "search_recommendation"

  /** Tag of the artwork results list. */
  const val ARTWORK_RESULTS = "search_artwork_results"

  /** Tag of the search loading indicator. */
  const val SEARCH_LOADING_INDICATOR = "search_loading_indicator"

  /** Tag of the empty search results state. */
  const val EMPTY_RESULTS = "search_empty_results"

  /** Tag of the hint indicating that artwork types can be scrolled. */
  const val FILTER_SCROLL_HINT = "search_filter_scroll_hint"

  /**
   * Returns the stable tag of an artwork-type filter.
   *
   * @param type artwork type represented by the filter
   * @return test tag for the filter
   * @author IJJA3141
   */
  fun filterType(type: ArtworkType): String = "search_filter_type_${type.name.lowercase()}"
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
    viewModel: SearchScreenViewModel,
    onTabSelected: (Tab) -> Unit,
    onArtworkClick: (Artwork) -> Unit = {},
    onMapClick: (Artwork) -> Unit = {},
    onRecommendationSelected: (Artwork) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  val loadingDescription = stringResource(R.string.search_loading)
  val context = LocalContext.current
  val searchErrorMessage = uiState.searchErrorMessageResId?.let { stringResource(it) }
  val saveErrorMessage = uiState.saveErrorMessageResId?.let { stringResource(it) }
  val textFieldState =
      remember(viewModel) {
        TextFieldState(initialText = uiState.query, initialSelection = uiState.selection)
      }

  LaunchedEffect(searchErrorMessage) {
    searchErrorMessage?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      viewModel.clearSearchError()
    }
  }

  LaunchedEffect(saveErrorMessage) {
    saveErrorMessage?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      viewModel.clearSaveError()
    }
  }

  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        SearchInput(
            textFieldState = textFieldState,
            recommendations = uiState.recommendations,
            selectedTypeValues = uiState.typeValues,
            hasInvalidSearchInput = uiState.hasInvalidSearchInput,
            onQueryChanged = viewModel::onSearchInputChanged,
            onSearchSubmitted = viewModel::onSearchSubmitted,
            onRecommendationSelected = onRecommendationSelected,
            onArtworkTypeToggle = viewModel::toggleArtworkType,
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = null,
            onTabSelected = onTabSelected,
        )
      },
  ) { contentPadding ->
    if (uiState.isLoading) {
      Box(
          modifier = Modifier.fillMaxSize().padding(contentPadding),
          contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
            modifier =
                Modifier.testTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR).semantics {
                  contentDescription = loadingDescription
                }
        )
      }
    } else if (uiState.artworks.isEmpty() && uiState.hasAlreadySearched) {
      EmptySearchResults(modifier = Modifier.fillMaxSize().padding(contentPadding))
    } else {
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
}

@Composable
private fun EmptySearchResults(modifier: Modifier = Modifier) {
  Box(modifier = modifier.testTag(SearchScreenTestTags.EMPTY_RESULTS), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.searchEmptyStateHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.searchEmptyStateSpacing),
    ) {
      Text(
          text = stringResource(R.string.search_empty_results_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text = stringResource(R.string.search_empty_results_message),
          modifier = Modifier.widthIn(max = Dimensions.searchEmptyStateMessageMaxWidth),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Displays the query input and any recommendation artwork supplied by the ViewModel.
 *
 * @param textFieldState text and cursor state currently shown in the search field
 * @param recommendations artwork entries to show in the dropdown
 * @param selectedTypeValues artwork types included in submitted searches
 * @param hasInvalidSearchInput whether the last search failed because its input was invalid
 * @param onQueryChanged called when the user edits the query or selection
 * @param onSearchSubmitted called when the user submits the query from the keyboard
 * @param onRecommendationSelected called when the user selects a dropdown recommendation
 * @param onArtworkTypeToggle called when the user toggles an artwork type
 * @author IJJA3141
 */
@Composable
private fun SearchInput(
    textFieldState: TextFieldState,
    recommendations: List<Artwork>,
    selectedTypeValues: List<ArtworkType>,
    hasInvalidSearchInput: Boolean,
    onQueryChanged: (String, TextRange) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onRecommendationSelected: (Artwork) -> Unit,
    onArtworkTypeToggle: (ArtworkType) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  LaunchedEffect(textFieldState) {
    snapshotFlow { textFieldState.text.toString() to textFieldState.selection }
        .collect { (query, selection) -> onQueryChanged(query, selection) }
  }

  Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = Dimensions.overviewSearchBarHorizontalPadding,
                    vertical = Dimensions.overviewSearchBarVerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      SearchTextField(
          textFieldState = textFieldState,
          hasInvalidSearchInput = hasInvalidSearchInput,
          expanded = expanded,
          onSearch = { query ->
            onSearchSubmitted(query)
            expanded = false
            focusManager.clearFocus()
          },
          onExpandedChange = {
            expanded = it
            if (!expanded) focusManager.clearFocus()
          },
          modifier =
              Modifier.weight(1f)
                  .height(Dimensions.overviewSearchBarHeight)
                  .focusRequester(focusRequester)
                  .testTag(SearchScreenTestTags.SEARCH_INPUT),
      )
      ArtworkTypeFilter(
          selectedTypeValues = selectedTypeValues,
          onArtworkTypeToggle = onArtworkTypeToggle,
      )
    }

    if (expanded && recommendations.isNotEmpty()) {
      Dropdown(
          recommendations,
          onRecommendationSelected = { recommendation ->
            onRecommendationSelected(recommendation)
            expanded = false
            focusManager.clearFocus()
          },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTextField(
    textFieldState: TextFieldState,
    hasInvalidSearchInput: Boolean,
    expanded: Boolean,
    onSearch: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
  SearchBarDefaults.InputField(
      state = textFieldState,
      onSearch = onSearch,
      expanded = false,
      onExpandedChange = onExpandedChange,
      colors =
          SearchBarDefaults.inputFieldColors(
              focusedContainerColor =
                  if (hasInvalidSearchInput) {
                    SearchBarPalette.invalidContainer
                  } else {
                    SearchBarPalette.container
                  },
              unfocusedContainerColor =
                  if (hasInvalidSearchInput) {
                    SearchBarPalette.invalidContainer
                  } else {
                    SearchBarPalette.container
                  },
              cursorColor =
                  if (hasInvalidSearchInput) SearchBarPalette.error else SearchBarPalette.cursor,
              focusedLeadingIconColor =
                  if (hasInvalidSearchInput) SearchBarPalette.error else SearchBarPalette.content,
              unfocusedLeadingIconColor =
                  if (hasInvalidSearchInput) SearchBarPalette.error else SearchBarPalette.content,
          ),
      modifier = modifier,
      placeholder = { Text(stringResource(R.string.search_placeholder)) },
      leadingIcon = {
        if (expanded) {
          IconButton(onClick = { onExpandedChange(false) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        } else {
          Icon(Icons.Outlined.Search, contentDescription = "Search")
        }
      },
  )
}

@Composable
private fun ArtworkTypeFilter(
    selectedTypeValues: List<ArtworkType>,
    onArtworkTypeToggle: (ArtworkType) -> Unit,
) {
  var isFilterMenuExpanded by remember { mutableStateOf(false) }

  Box {
    IconButton(onClick = { isFilterMenuExpanded = true }) {
      Icon(
          imageVector = Icons.Outlined.FilterList,
          contentDescription = stringResource(R.string.overview_filter_open),
          tint = MaterialTheme.colorScheme.onSurface,
      )
    }
    DropdownMenu(
        expanded = isFilterMenuExpanded,
        onDismissRequest = { isFilterMenuExpanded = false },
        modifier = Modifier.heightIn(max = Dimensions.searchTypeFilterMaxHeight),
    ) {
      Text(
          text = stringResource(R.string.search_filter_title),
          style = MaterialTheme.typography.titleSmall,
          modifier =
              Modifier.padding(
                  horizontal = Dimensions.searchTypeFilterHorizontalPadding,
                  vertical = Dimensions.searchTypeFilterTitleVerticalPadding,
              ),
      )
      Text(
          text = stringResource(R.string.search_filter_scroll_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier =
              Modifier.padding(
                      horizontal = Dimensions.searchTypeFilterHorizontalPadding,
                      vertical = Dimensions.searchTypeFilterTitleVerticalPadding,
                  )
                  .testTag(SearchScreenTestTags.FILTER_SCROLL_HINT),
      )
      ArtworkType.entries.forEach { type ->
        DropdownMenuItem(
            text = { Text(stringResource(type.toDisplayNameRes())) },
            leadingIcon = {
              Checkbox(
                  checked = type in selectedTypeValues,
                  onCheckedChange = null,
              )
            },
            onClick = { onArtworkTypeToggle(type) },
            modifier = Modifier.testTag(SearchScreenTestTags.filterType(type)),
        )
      }
    }
  }
}

@Composable
private fun Dropdown(
    recommendations: List<Artwork>,
    onRecommendationSelected: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) =
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.overviewSearchBarHorizontalPadding)
                .heightIn(max = Dimensions.searchRecommendationsMaxHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = SearchBarPalette.container,
        tonalElevation = Dimensions.overviewSearchBarElevation,
    ) {
      LazyColumn(
          contentPadding = PaddingValues(vertical = Dimensions.searchRecommendationVerticalPadding)
      ) {
        items(recommendations) { recommendation ->
          ListItem(
              headlineContent = {
                Text(
                    recommendation.name ?: stringResource(R.string.artwork_unknown_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
              },
              leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
              },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              modifier =
                  Modifier.fillMaxWidth()
                      .clickable { onRecommendationSelected(recommendation) }
                      .padding(horizontal = Dimensions.searchRecommendationHorizontalPadding)
                      .testTag(SearchScreenTestTags.SEARCH_RECOMMENDATION),
          )
        }
      }
    }

@StringRes
private fun ArtworkType.toDisplayNameRes(): Int =
    when (this) {
      ArtworkType.PAINTING -> R.string.search_filter_type_painting
      ArtworkType.DRAWING -> R.string.search_filter_type_drawing
      ArtworkType.SCULPTURE -> R.string.search_filter_type_sculpture
      ArtworkType.PRINT -> R.string.search_filter_type_print
      ArtworkType.PHOTOGRAPH -> R.string.search_filter_type_photograph
      ArtworkType.MURAL -> R.string.search_filter_type_mural
      ArtworkType.OIL_PAINTING -> R.string.search_filter_type_oil_painting
      ArtworkType.WATERCOLOR_PAINTING -> R.string.search_filter_type_watercolor_painting
      ArtworkType.MOSAIC -> R.string.search_filter_type_mosaic
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
      contentPadding =
          PaddingValues(
              horizontal = Dimensions.searchResultsHorizontalPadding,
              vertical = Dimensions.searchResultsVerticalPadding,
          ),
      verticalArrangement = Arrangement.spacedBy(Dimensions.searchResultItemSpacing),
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
internal fun SearchScreenPreview() {
  ArtistaTheme {
    SearchScreen(
        viewModel =
            viewModel(
                factory =
                    SearchScreenViewModel.provideFactory(
                        ArtworkRepositoryProvider.mockRepository,
                        ArtworkRepositoryProvider.mockRepository,
                    )
            ),
        onTabSelected = {},
    )
  }
}
