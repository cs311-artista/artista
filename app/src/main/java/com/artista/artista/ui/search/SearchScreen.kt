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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkCandidate
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.ui.artwork.ArtworkTile
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.search.SearchScreenTestTags.SEARCH_BACK_BUTTON
import com.artista.artista.ui.theme.Dimensions
import com.artista.artista.ui.theme.SearchBarPalette
import com.artista.artista.ui.theme.SearchLayout

/** Stable semantics tags used by SearchScreen UI tests. */
object SearchScreenTestTags {
  /** Tag of the search text input. */
  const val SEARCH_INPUT = "search_input"

  /** Tag of each artwork recommendation in the suggestions list. */
  const val SEARCH_ARTWORK_RECOMMENDATION = "search_artwork_recommendation"

  const val SEARCH_BACK_BUTTON = "search_back_button"

  /** Tag of each artist recommendation in the suggestions list. */
  const val SEARCH_ARTIST_RECOMMENDATION = "search_artist_recommendation"

  /** Tag of the suggestions list shown while the user is typing. */
  const val SUGGESTION_LIST = "search_suggestion_list"

  /** Tag of the loading row shown at the top of the suggestions list while suggestions load. */
  const val SUGGESTION_LOADING_INDICATOR = "search_suggestion_loading_indicator"

  /** Tag of the "Powered by Wikidata" attribution. */
  const val WIKIDATA_ATTRIBUTION = "search_wikidata_attribution"

  /** Tag of results list. */
  const val SEARCH_RESULT = "search_artwork_results"

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

  /**
   * Returns the stable tag of an Artworks/Artists switch segment.
   *
   * @param tab search tab represented by the segment
   * @return test tag for the segment
   */
  fun tab(tab: SearchTab): String = "search_tab_${tab.name.lowercase()}"
}

/** Kind of entity searched by the screen, selected with the switch under the search field. */
enum class SearchTab {
  ARTWORKS,
  ARTISTS,
}

/** Where an artwork suggestion comes from, so the suggestion bubble can pick its icon. */
enum class SuggestionSource {
  SAVED,
  NEARBY,
  WIKIDATA,
}

/**
 * An artwork suggestion tagged with the [source] it was found in.
 *
 * @property candidate the suggested artwork
 * @property source list the suggestion came from
 */
data class ArtworkSuggestion(val candidate: ArtworkCandidate, val source: SuggestionSource)

private const val ARTWORK_GRID_COLUMN = 2

/**
 * Merges the three suggestion lists into the single list shown under the search field.
 *
 * Order is saved, then nearby, then Wikidata. An identical candidate found in several lists is kept
 * once, with the source that comes first.
 *
 * @param saved suggestions from the user's saved artworks
 * @param nearby suggestions from artworks near the user
 * @param wikiData suggestions returned by Wikidata
 * @return deduplicated suggestions in display order
 */
internal fun buildArtworkSuggestions(
    saved: List<ArtworkCandidate>,
    nearby: List<ArtworkCandidate>,
    wikiData: List<ArtworkCandidate>,
): List<ArtworkSuggestion> =
    (saved.map { ArtworkSuggestion(it, SuggestionSource.SAVED) } +
            nearby.map { ArtworkSuggestion(it, SuggestionSource.NEARBY) } +
            wikiData.map { ArtworkSuggestion(it, SuggestionSource.WIKIDATA) })
        .distinctBy { it.candidate }

/**
 * Displays the search field, the Artworks/Artists switch, suggestions, and search results.
 *
 * @param viewModel supplies and updates the screen's search, recommendation, saved-artwork, and
 *   result state
 * @param onTabSelected callback invoked when the user selects a bottom-navigation tab
 * @param onArtworkClick callback invoked when the user selects an artwork in the results list
 * @param onArtistClick callback invoked when the user selects an artist in the results list
 * @param onMapClick callback invoked when the user selects the map action for a result
 * @param onArtworkRecommendationSelected callback invoked when the user selects an artwork
 *   suggestion
 * @param onArtistRecommendationSelected callback invoked when the user selects an artist suggestion
 * @author IJJA3141
 */
@Composable
fun SearchScreen(
    viewModel: SearchScreenViewModel,
    onTabSelected: (Tab) -> Unit,
    onArtworkClick: (Artwork) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onMapClick: (Artwork) -> Unit = {},
    onArtworkRecommendationSelected: (ArtworkCandidate) -> Unit = {},
    onArtistRecommendationSelected: (ArtistCandidate) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val searchErrorMessage = uiState.fetchErrorMessageResId?.let { stringResource(it) }
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

  SearchScreenContent(
      uiState = uiState,
      textFieldState = textFieldState,
      onQueryChanged = viewModel::onSearchInputChanged,
      onSearchSubmitted = viewModel::onSearchSubmitted,
      onSuggestingChange = viewModel::onSuggestingChange,
      onSearchTabSelected = viewModel::onSearchTabSelected,
      onArtworkTypeToggle = viewModel::toggleArtworkType,
      onBottomTabSelected = onTabSelected,
      onArtworkClick = onArtworkClick,
      onArtistClick = onArtistClick,
      onMapClick = onMapClick,
      onSaveClick = viewModel::toggleSavedArtwork,
      onRecommendationSelected = onArtworkRecommendationSelected,
      onArtistRecommendationSelected = onArtistRecommendationSelected,
  )
}

/**
 * Stateless layout of the search screen, driven entirely by its parameters so it can be previewed
 * and tested without a ViewModel.
 *
 * While [uiState.isSuggesting] is true the body shows the suggestions for the selected tab.
 * Otherwise, it shows the results of the last submitted search.
 *
 * @param uiState state supplied by the ViewModel
 * @param textFieldState text and cursor state shown in the search field
 * @param onSearchTabSelected called when the user picks a tab
 * @param onSuggestingChange called when the suggestions pane should open or close
 * @param onQueryChanged called when the user edits the query or selection
 * @param onSearchSubmitted called when the user submits the query from the keyboard
 * @param onArtworkTypeToggle called when the user toggles an artwork type
 * @param onBottomTabSelected called when the user picks a bottom-navigation tab
 * @param onArtworkClick called when the user selects an artwork result
 * @param onArtistClick called when the user selects an artist result
 * @param onMapClick called when the user selects the map action of a result
 * @param onSaveClick called when the user saves or unsaves a result
 * @param onRecommendationSelected called when the user picks an artwork suggestion
 * @param onArtistRecommendationSelected called when the user picks an artist suggestion
 */
@Composable
internal fun SearchScreenContent(
    uiState: SearchScreenUiState,
    textFieldState: TextFieldState,
    onSearchTabSelected: (SearchTab) -> Unit = {},
    onSuggestingChange: (Boolean) -> Unit = {},
    onQueryChanged: (String, TextRange) -> Unit = { _, _ -> },
    onSearchSubmitted: (String) -> Unit = {},
    onArtworkTypeToggle: (ArtworkType) -> Unit = {},
    onBottomTabSelected: (Tab) -> Unit = {},
    onArtworkClick: (Artwork) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onMapClick: (Artwork) -> Unit = {},
    onSaveClick: (Artwork) -> Unit = {},
    onRecommendationSelected: (ArtworkCandidate) -> Unit = {},
    onArtistRecommendationSelected: (ArtistCandidate) -> Unit = {},
) {
  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
          SearchInput(
              textFieldState = textFieldState,
              expanded = uiState.isSuggesting,
              selectedTypeValues = uiState.typeValues,
              hasInvalidSearchInput = uiState.hasInvalidSearchInput,
              onExpandedChange = onSuggestingChange,
              onQueryChanged = onQueryChanged,
              onSearchSubmitted = onSearchSubmitted,
              onArtworkTypeToggle = onArtworkTypeToggle,
          )
          SearchTabSwitch(
              textFieldState = textFieldState,
              selectedTab = uiState.selectedTab,
              onTabSelected = onSearchTabSelected,
              modifier =
                  Modifier.padding(horizontal = Dimensions.overviewSearchBarHorizontalPadding),
          )
          HorizontalDivider(modifier = Modifier.padding(top = SearchLayout.TabSwitchBottomSpacing))
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = null,
            onTabSelected = onBottomTabSelected,
        )
      },
  ) { contentPadding ->
    val bodyModifier = Modifier.fillMaxSize().padding(contentPadding)
    val artworkSuggestions =
        remember(
            uiState.savedRecommendations,
            uiState.nearRecommendations,
            uiState.wikiDataRecommendations,
        ) {
          buildArtworkSuggestions(
              saved = uiState.savedRecommendations,
              nearby = uiState.nearRecommendations,
              wikiData = uiState.wikiDataRecommendations,
          )
        }

    when {
      uiState.isSuggesting ->
          SuggestionsPane(
              selectedTab = uiState.selectedTab,
              isLoading = uiState.isRecommendationLoading,
              artworkSuggestions = artworkSuggestions,
              artistSuggestions = uiState.artistRecommendations,
              onArtworkSuggestionClick = { candidate ->
                onRecommendationSelected(candidate)
                onSuggestingChange(false)
              },
              onArtistSuggestionClick = { candidate ->
                onArtistRecommendationSelected(candidate)
                onSuggestingChange(false)
              },
              modifier = bodyModifier,
          )
      uiState.selectedTab == SearchTab.ARTISTS ->
          ArtistSearchBody(
              uiState = uiState,
              onArtistClick = onArtistClick,
              modifier = bodyModifier,
          )
      else ->
          ArtworkSearchBody(
              uiState = uiState,
              onArtworkClick = onArtworkClick,
              onMapClick = onMapClick,
              onSaveClick = onSaveClick,
              modifier = bodyModifier,
          )
    }
  }
}

@Composable
private fun ArtistSearchBody(
    uiState: SearchScreenUiState,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
  val loadingDescription = stringResource(R.string.search_loading)

  when {
    uiState.isLoading ->
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
              modifier =
                  Modifier.testTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR).semantics {
                    contentDescription = loadingDescription
                  }
          )
        }
    uiState.resultArtist.isEmpty() && !uiState.hasInvalidSearchInput ->
        EmptySearchResults(
            stringResource(
                if (uiState.hasAlreadySearched) R.string.search_empty_artist_results_title
                else R.string.search_empty_query_title
            ),
            stringResource(
                if (uiState.hasAlreadySearched) R.string.search_empty_artist_results_message
                else R.string.search_empty_query_message
            ),
            modifier = modifier,
        )
    else ->
        ArtistResults(
            artists = uiState.resultArtist,
            onArtistClick = onArtistClick,
            modifier = modifier,
        )
  }
}

@Composable
private fun ArtistResults(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyColumn(
      modifier = modifier.testTag(SearchScreenTestTags.SEARCH_RESULT),
      contentPadding =
          PaddingValues(
              horizontal = Dimensions.searchResultsHorizontalPadding,
              vertical = Dimensions.searchResultsVerticalPadding,
          ),
      verticalArrangement = Arrangement.spacedBy(Dimensions.searchResultItemSpacing),
  ) {
    items(artists) { artist ->
      // TODO(use same artist card as in user pref.) need Kaio's PR
      Text(artist.name)
    }
  }
}

/**
 * Displays the loading, empty, or result state of the last artwork search.
 *
 * @param uiState state supplied by the ViewModel
 * @param onArtworkClick called when the user selects an artwork
 * @param onMapClick called when the user selects the map action of an artwork
 * @param onSaveClick called when the user saves or unsaves an artwork
 * @param modifier modifier applied to the body
 */
@Composable
private fun ArtworkSearchBody(
    uiState: SearchScreenUiState,
    onArtworkClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    onSaveClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  val loadingDescription = stringResource(R.string.search_loading)

  when {
    uiState.isLoading ->
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
              modifier =
                  Modifier.testTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR).semantics {
                    contentDescription = loadingDescription
                  }
          )
        }
    uiState.resultArtworks.isEmpty() && !uiState.hasInvalidSearchInput ->
        EmptySearchResults(
            stringResource(
                if (uiState.hasAlreadySearched) R.string.search_empty_artwork_results_title
                else R.string.search_empty_query_title
            ),
            stringResource(
                if (uiState.hasAlreadySearched) R.string.search_empty_artwork_results_message
                else R.string.search_empty_query_message
            ),
            modifier = modifier,
        )
    else ->
        ArtworkResults(
            artworks = uiState.resultArtworks,
            savedArtworkIds = uiState.savedArtworks.map { it.uid }.toSet(),
            onArtworkClick = onArtworkClick,
            onMapClick = onMapClick,
            onSaveClick = onSaveClick,
            modifier = modifier,
        )
  }
}

@Composable
private fun EmptySearchResults(title: String, message: String, modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.testTag(SearchScreenTestTags.EMPTY_RESULTS),
      contentAlignment = Alignment.Center,
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Dimensions.searchEmptyStateHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.searchEmptyStateSpacing),
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text = message,
          modifier = Modifier.widthIn(max = Dimensions.searchEmptyStateMessageMaxWidth),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Displays the query field and the artwork-type filter.
 *
 * @param textFieldState text and cursor state currently shown in the search field
 * @param expanded whether the suggestions pane is open, which swaps the search icon for a back
 *   arrow
 * @param selectedTypeValues artwork types included in submitted searches
 * @param hasInvalidSearchInput whether the last search failed because its input was invalid
 * @param onExpandedChange called when the field asks to open or close the suggestions pane
 * @param onQueryChanged called when the user edits the query or selection
 * @param onSearchSubmitted called when the user submits the query from the keyboard
 * @param onArtworkTypeToggle called when the user toggles an artwork type
 * @author IJJA3141
 */
@Composable
private fun SearchInput(
    textFieldState: TextFieldState,
    expanded: Boolean,
    selectedTypeValues: List<ArtworkType>,
    hasInvalidSearchInput: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChanged: (String, TextRange) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onArtworkTypeToggle: (ArtworkType) -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  var isClearingFocus by remember { mutableStateOf(false) }
  val clearFocus = {
    isClearingFocus = true
    focusManager.clearFocus()
    isClearingFocus = false
  }

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
        horizontalArrangement = Arrangement.spacedBy(Dimensions.overviewSearchBarFilterSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      SearchTextField(
          textFieldState = textFieldState,
          hasInvalidSearchInput = hasInvalidSearchInput,
          expanded = expanded,
          onSearch = { query ->
            onSearchSubmitted(query)
            onExpandedChange(false)
            clearFocus()
          },
          onExpandedChange = {
            if (!(it && isClearingFocus)) onExpandedChange(it)
            if (!it) clearFocus()
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
  // Same rounded shape and card fill as the Overview search bar.
  val containerColor =
      if (hasInvalidSearchInput) SearchBarPalette.invalidContainer
      else CardDefaults.cardColors().containerColor
  SearchBarDefaults.InputField(
      state = textFieldState,
      onSearch = onSearch,
      expanded = false,
      onExpandedChange = onExpandedChange,
      shape = MaterialTheme.shapes.extraLarge,
      colors =
          SearchBarDefaults.inputFieldColors(
              focusedContainerColor = containerColor,
              unfocusedContainerColor = containerColor,
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
          IconButton(
              onClick = { onExpandedChange(false) },
              modifier = Modifier.testTag(SEARCH_BACK_BUTTON),
          ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.arrow_back_description),
            )
          }
        } else {
          Icon(
              Icons.Outlined.Search,
              contentDescription = stringResource(R.string.search_icon_description),
          )
        }
      },
      trailingIcon = {
        if (expanded && textFieldState.text.isNotEmpty()) {
          IconButton(onClick = { textFieldState.clearText() }) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.clear_icon_description),
            )
          }
        }
      },
  )
}

/**
 * Two-segment switch between searching artworks and artists.
 *
 * @param selectedTab segment currently selected
 * @param onTabSelected called when the user taps a segment
 * @param modifier modifier applied to the switch
 */
@Composable
private fun SearchTabSwitch(
    textFieldState: TextFieldState,
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.fillMaxWidth().height(SearchLayout.TabSwitchHeight),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.background,
  ) {
    Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
      SearchTab.entries.forEach { tab ->
        val selected = tab == selectedTab
        Surface(
            modifier =
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab) {
                      onTabSelected(tab)
                      if (!selected) textFieldState.clearText()
                    }
                    .testTag(SearchScreenTestTags.tab(tab)),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(tab.toDisplayNameRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

@StringRes
private fun SearchTab.toDisplayNameRes(): Int =
    when (this) {
      SearchTab.ARTWORKS -> R.string.search_tab_artworks
      SearchTab.ARTISTS -> R.string.search_tab_artists
    }

@Composable
private fun ArtworkTypeFilter(
    selectedTypeValues: List<ArtworkType>,
    onArtworkTypeToggle: (ArtworkType) -> Unit,
) {
  var isFilterMenuExpanded by remember { mutableStateOf(false) }

  Box {
    // Matches the Overview filter button.
    FilledIconButton(
        onClick = { isFilterMenuExpanded = true },
        modifier = Modifier.size(Dimensions.overviewFilterButtonSize),
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = CardDefaults.cardColors().containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
      Icon(
          imageVector = Icons.Outlined.FilterList,
          contentDescription = stringResource(R.string.artwork_filter_open),
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

/**
 * Suggestions for the selected tab, shown in place of the results while the user is typing.
 *
 * The "Powered by Wikidata" attribution sits at the bottom of the Artworks tab, as in the design.
 *
 * @param selectedTab tab whose suggestions are shown
 * @param artworkSuggestions artwork suggestions in display order
 * @param artistSuggestions artist suggestions in display order
 * @param onArtworkSuggestionClick called when the user picks an artwork suggestion
 * @param onArtistSuggestionClick called when the user picks an artist suggestion
 * @param modifier modifier applied to the pane
 */
@Composable
private fun SuggestionsPane(
    selectedTab: SearchTab,
    isLoading: Boolean,
    artworkSuggestions: List<ArtworkSuggestion>,
    artistSuggestions: List<ArtistCandidate>,
    onArtworkSuggestionClick: (ArtworkCandidate) -> Unit,
    onArtistSuggestionClick: (ArtistCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth().testTag(SearchScreenTestTags.SUGGESTION_LIST),
        contentPadding =
            PaddingValues(
                horizontal = Dimensions.searchResultsHorizontalPadding,
                vertical = Dimensions.searchResultsVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(Dimensions.searchResultItemSpacing),
    ) {
      if (isLoading) {
        item(key = "loading") { LoadingSuggestionRow() }
      }
      when (selectedTab) {
        SearchTab.ARTWORKS ->
            items(artworkSuggestions) { suggestion ->
              ArtworkSuggestionRow(
                  suggestion = suggestion,
                  onClick = { onArtworkSuggestionClick(suggestion.candidate) },
              )
            }
        SearchTab.ARTISTS ->
            items(artistSuggestions) { candidate ->
              ArtistSuggestionRow(
                  suggestion = candidate,
                  onClick = { onArtistSuggestionClick(candidate) },
              )
            }
      }
    }

    Text(
        text = stringResource(R.string.search_powered_by_wikidata),
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = SearchLayout.AttributionVerticalPadding)
                .testTag(SearchScreenTestTags.WIKIDATA_ATTRIBUTION),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun LoadingSuggestionRow(modifier: Modifier = Modifier) {
  Surface(
      modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
  ) {
    ListItem(
        headlineContent = {
          Text(
              stringResource(R.string.search_query_loading),
              style = MaterialTheme.typography.bodyLarge,
          )
        },
        leadingContent = {
          // Same footprint as the 24dp suggestion icons so the rows line up.
          CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
          )
        },
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Dimensions.searchRecommendationHorizontalPadding)
                .testTag(SearchScreenTestTags.SUGGESTION_LOADING_INDICATOR),
    )
  }
}

@Composable
private fun ArtworkSuggestionRow(
    suggestion: ArtworkSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
  ) {
    ListItem(
        headlineContent = {
          Column {
            Text(
                suggestion.candidate.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (suggestion.candidate.description != null)
                Text(
                    suggestion.candidate.description,
                    style = MaterialTheme.typography.bodySmall,
                )
          }
        },
        leadingContent = {
          Icon(
              imageVector =
                  when (suggestion.source) {
                    SuggestionSource.SAVED -> Icons.Filled.Bookmark
                    SuggestionSource.NEARBY -> Icons.Filled.NearMe
                    SuggestionSource.WIKIDATA -> Icons.Filled.Lightbulb
                  },
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
          )
        },
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = Dimensions.searchRecommendationHorizontalPadding)
                .testTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION),
    )
  }
}

@Composable
private fun ArtistSuggestionRow(
    suggestion: ArtistCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
  ) {
    ListItem(
        headlineContent = {
          Column {
            Text(
                suggestion.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (suggestion.description != null)
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                )
          }
        },
        leadingContent = {
          Icon(
              imageVector = Icons.Filled.Person,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
          )
        },
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = Dimensions.searchRecommendationHorizontalPadding)
                .testTag(SearchScreenTestTags.SEARCH_ARTIST_RECOMMENDATION),
    )
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
  LazyVerticalGrid(
      columns = GridCells.Fixed(ARTWORK_GRID_COLUMN),
      modifier = modifier.testTag(SearchScreenTestTags.SEARCH_RESULT),
      contentPadding =
          PaddingValues(
              horizontal = Dimensions.searchResultsHorizontalPadding,
              vertical = Dimensions.searchResultsVerticalPadding,
          ),
      horizontalArrangement = Arrangement.spacedBy(Dimensions.searchResultItemSpacing),
      verticalArrangement = Arrangement.spacedBy(Dimensions.searchResultItemSpacing),
  ) {
    items(artworks) { artwork ->
      ArtworkTile(
          artwork = artwork,
          isSaved = artwork.uid in savedArtworkIds,
          onClick = onArtworkClick,
          onMapClick = onMapClick,
          onSaveClick = onSaveClick,
      )
    }
  }
}
