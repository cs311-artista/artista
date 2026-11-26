// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkCandidate
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.map.Location
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // region Input and suggestions

  @Test
  fun searchBar_requestsFocusWhenScreenIsOpened() = runTest {
    val viewModel = createViewModel()

    showSearchScreen(viewModel)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).assertIsFocused()
  }

  @Test
  fun searchFilter_opensTypePopupAndUpdatesSelection() = runTest {
    val viewModel = createViewModel()

    showSearchScreen(viewModel)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithContentDescription("Filter artworks").performClick()
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.filterType(ArtworkType.PAINTING))
        .assertIsDisplayed()
        .performClick()

    composeTestRule.runOnIdle {
      assertTrue(ArtworkType.PAINTING !in viewModel.uiState.value.typeValues)
      assertTrue(viewModel.uiState.value.typeValues.isNotEmpty())
    }
  }

  @Test
  fun searchBar_restoresCursorPositionWhenScreenIsRecreated() = runTest {
    val viewModel = createViewModel()
    val showSearchScreen = mutableStateOf(true)

    composeTestRule.setContent {
      MaterialTheme {
        if (showSearchScreen.value) {
          SearchScreen(onTabSelected = {}, viewModel = viewModel)
        } else {
          Text("Artwork details")
        }
      }
    }

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT)
        .performTextInputSelection(TextRange(2))
    composeTestRule.waitForIdle()
    assertEquals("star", viewModel.uiState.value.query)
    assertEquals(TextRange(2), viewModel.uiState.value.selection)

    composeTestRule.runOnIdle { showSearchScreen.value = false }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { showSearchScreen.value = true }
    composeTestRule.waitForIdle()

    val fieldSemantics =
        composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).fetchSemanticsNode().config
    assertEquals(TextRange(2), fieldSemantics[SemanticsProperties.TextSelectionRange])
  }

  @Test
  fun searchBar_displaysMatchingRecommendationsFromSavedArtworks() = runTest {
    val viewModel = createViewModel()

    showSearchScreen(viewModel)

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    val collapsedSearchBounds =
        composeTestRule
            .onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT)
            .fetchSemanticsNode()
            .boundsInRoot
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    advanceUntilIdle()
    val expandedSearchBounds =
        composeTestRule
            .onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT)
            .fetchSemanticsNode()
            .boundsInRoot

    assertEquals(collapsedSearchBounds.top, expandedSearchBounds.top, 0f)
    assertEquals(collapsedSearchBounds.height, expandedSearchBounds.height, 0f)

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assert(hasText("The Starry Night"))
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertIsDisplayed()

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextClearance()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("pearl")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assert(hasText("Girl with a Pearl Earring"))
        .assertIsDisplayed()

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextClearance()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("unmatched")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertCountEquals(0)
  }

  @Test
  fun searchBar_hidesDropdownAndSubmitsOnlyWhenQueryIsSubmitted() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = createViewModel(wikiDataRepository = wikiRepository)

    showSearchScreen(viewModel)

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertIsDisplayed()
    assertTrue(wikiRepository.searchedQueries.isEmpty())

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performImeAction()
    advanceUntilIdle()
    closeSuggestions(viewModel)

    assertEquals(listOf("star"), wikiRepository.searchedQueries)
    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_ARTWORK_RECOMMENDATION)
        .assertCountEquals(0)
  }

  // endregion

  // region Artwork results

  @Test
  fun searchBar_showsLoadingIndicatorUntilSearchResultsArrive() = runTest {
    val resultsGate = CompletableDeferred<Unit>()
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryGatedBy(resultsGate))

    showSearchScreen(viewModel)

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performImeAction()
    advanceUntilIdle()
    closeSuggestions(viewModel)

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR).assertIsDisplayed()
    resultsGate.complete(Unit)
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR)
        .assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("The Starry Night"))
  }

  @Test
  fun searchResults_showsHelpfulEmptyStateWhenNoArtworksMatch() = runTest {
    val viewModel = createViewModel()
    viewModel.onSearchSubmitted("no matching artwork")
    advanceUntilIdle()

    showSearchScreen(viewModel)
    closeSuggestions(viewModel)

    composeTestRule.onNodeWithTag(SearchScreenTestTags.EMPTY_RESULTS).assertIsDisplayed()
    composeTestRule.onNodeWithText("No artworks found").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Try another search or change the artwork type filters.")
        .assertIsDisplayed()
  }

  @Test
  fun searchResults_displayFallbackArtistWhenArtistIsMissing() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()
    viewModel.onSearchSubmitted("untitled")
    advanceUntilIdle()

    showSearchScreen(viewModel)
    closeSuggestions(viewModel)

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("Untitled study"))
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST, useUnmergedTree = true)
        .assert(hasText("Unknown Artist"))
  }

  @Test
  fun toggleSavedArtwork_updatesRepositoryAndUiState() = runTest {
    val artwork = SearchTestData.girlWithPearlEarring
    val repository = SearchTestData.savedArtworksRepository(listOf(artwork))
    val viewModel = createViewModel(savedArtworksRepository = repository)
    advanceUntilIdle()
    assertEquals(listOf(artwork), viewModel.uiState.value.savedArtworks)

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(emptyList<Artwork>(), repository.savedArtworks)
    assertEquals(emptyList<Artwork>(), viewModel.uiState.value.savedArtworks)

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(listOf(artwork), repository.savedArtworks)
    assertEquals(listOf(artwork), viewModel.uiState.value.savedArtworks)
  }

  @Test
  fun searchResults_whenMapButtonIsClicked_callsOnMapClickWithTappedArtwork() = runTest {
    val artwork = SearchTestData.starryNight.copy(location = Location(48.8606, 2.3376, "Louvre"))
    val viewModel =
        createViewModel(
            savedArtworksRepository = SearchTestData.savedArtworksRepository(emptyList()),
            wikiDataRepository = FakeWikiDataRepository(listOf(artwork)),
        )
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()
    var mappedArtwork: Artwork? = null

    composeTestRule.setContent {
      MaterialTheme {
        SearchScreen(
            onMapClick = { mappedArtwork = it },
            viewModel = viewModel,
            onTabSelected = {},
            onArtworkClick = {},
        )
      }
    }
    closeSuggestions(viewModel)

    // Semantic click: performClick() does not reach the button in this Robolectric window.
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
        .performSemanticsAction(SemanticsActions.OnClick)

    assertEquals(artwork, mappedArtwork)
  }

  // endregion

  // region Tabs

  @Test
  fun artistsTab_showsArtistResultsAfterSearch() = runTest {
    val artist = checkNotNull(SearchTestData.starryNight.artist)
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtists(listOf(artist)))
    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    showSearchScreen(viewModel)
    closeSuggestions(viewModel)

    composeTestRule.onNodeWithText(artist.name).assertIsDisplayed()
  }

  @Test
  fun artistsTab_showsEmptyStateWhenNoArtistMatches() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtists(emptyList()))
    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchSubmitted("no matching artist")
    advanceUntilIdle()

    showSearchScreen(viewModel)
    closeSuggestions(viewModel)

    composeTestRule.onNodeWithTag(SearchScreenTestTags.EMPTY_RESULTS).assertIsDisplayed()
  }

  @Test
  fun tabSwitch_keepsArtworkResultsWhenComingBack() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    showSearchScreen(viewModel)
    closeSuggestions(viewModel)
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("The Starry Night"))

    selectTab(SearchTab.ARTISTS)

    composeTestRule
        .onAllNodesWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assertCountEquals(0)

    selectTab(SearchTab.ARTWORKS)

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("The Starry Night"))
  }

  @Test
  fun tabSwitch_keepsArtworkSearchRunningAndShowsItsSpinnerOnlyOnThatTab() = runTest {
    val resultsGate = CompletableDeferred<Unit>()
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryGatedBy(resultsGate))
    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    showSearchScreen(viewModel)
    closeSuggestions(viewModel)
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR).assertIsDisplayed()

    selectTab(SearchTab.ARTISTS)

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR)
        .assertCountEquals(0)

    selectTab(SearchTab.ARTWORKS)

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_LOADING_INDICATOR).assertIsDisplayed()

    resultsGate.complete(Unit)
    advanceUntilIdle()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("The Starry Night"))
  }

  // endregion

  // region Helpers

  private fun createViewModel(
      savedArtworksRepository: SavedArtworksRepository = SearchTestData.savedArtworksRepository(),
      wikiDataRepository: WikiDataRepository = SearchTestData.wikiDataRepository(),
  ) =
      SearchScreenViewModel(
          savedArtworksRepository,
          WithoutRemoteSuggestions(wikiDataRepository),
          FakeLocationProvider(),
      )

  private fun showSearchScreen(viewModel: SearchScreenViewModel) {
    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }
  }

  /**
   * Makes the body show results rather than suggestions.
   *
   * The screen opens its suggestions pane as soon as the field gains focus, and under Robolectric
   * clearing focus (which the screen does after a submit) hands focus straight back to the field,
   * reopening the pane. Closing the pane through the ViewModel does not touch focus, so it stays
   * closed.
   */
  private fun closeSuggestions(viewModel: SearchScreenViewModel) {
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { viewModel.onSuggestingChange(false) }
    composeTestRule.waitForIdle()
  }

  /**
   * Selects a tab through its click semantics, like the other buttons in this Robolectric window.
   */
  private fun selectTab(tab: SearchTab) {
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.tab(tab))
        .performSemanticsAction(SemanticsActions.OnClick)
    composeTestRule.waitForIdle()
  }

  /** Test data repository whose artwork search only answers once [gate] is completed. */
  private fun wikiRepositoryGatedBy(gate: CompletableDeferred<Unit>): WikiDataRepository {
    val base = SearchTestData.wikiDataRepository()
    return object : WikiDataRepository by base {
      override suspend fun getArtworksFromQuery(
          artworkTitle: String,
          typeValues: List<ArtworkType>,
          limit: Int,
      ): List<Artwork> {
        gate.await()
        return base.getArtworksFromQuery(artworkTitle, typeValues, limit)
      }
    }
  }

  /** Test data repository whose artist search always returns [artists]. */
  private fun wikiRepositoryWithArtists(artists: List<Artist>): WikiDataRepository {
    val base = SearchTestData.wikiDataRepository()
    return object : WikiDataRepository by base {
      override suspend fun getArtistsFromQuery(query: String, limit: Int): List<Artist> = artists
    }
  }

  // endregion

  /**
   * Wraps [base] so that the calls the ViewModel makes on its own (nearby artworks on start-up and
   * suggestions whenever the field is focused or edited) return nothing instead of reaching methods
   * that the test repositories leave as `TODO()`.
   *
   * Those calls run in the ViewModel's scope, where a `NotImplementedError` is not caught and fails
   * whichever test happens to be running. Search and lookup calls still go to [base], so recorded
   * queries and results are unchanged.
   */
  private class WithoutRemoteSuggestions(base: WikiDataRepository) : WikiDataRepository by base {
    override suspend fun getArtworksNearLocation(
        searchArea: SearchArea,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = emptyList()

    override suspend fun getArtworkCandidates(
        query: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<ArtworkCandidate> = emptyList()

    override suspend fun getArtistCandidates(query: String, limit: Int): List<ArtistCandidate> =
        emptyList()
  }
}
