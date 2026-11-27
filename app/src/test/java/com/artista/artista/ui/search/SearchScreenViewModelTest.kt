// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkCandidate
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.FakeLocationProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchScreenViewModelTest {

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // region Recommendations

  @Test
  fun onSearchInputChanged_updatesQuerySelectionAndRecommendations() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()
    viewModel.onSearchInputChanged("star", TextRange(2))
    advanceUntilIdle()

    assertEquals("star", viewModel.uiState.value.query)
    assertEquals(TextRange(2), viewModel.uiState.value.selection)
    assertEquals(
        listOf("The Starry Night"),
        viewModel.uiState.value.savedRecommendations.map { it.label },
    )
    assertTrue(viewModel.uiState.value.resultArtworks.isEmpty())
  }

  @Test
  fun onSearchInputChanged_ignoresSavedArtworksWithoutAName() = runTest {
    val starryNight = SearchTestData.savedArtworks.first { it.uid == "starryId" }
    val viewModel =
        createViewModel(
            savedArtworksRepository =
                SearchTestData.savedArtworksRepository(
                    initialSavedArtworks =
                        listOf(starryNight, starryNight.copy(uid = "noNameId", name = null))
                )
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("star", TextRange(4))
    advanceUntilIdle()

    assertEquals(
        listOf("The Starry Night"),
        viewModel.uiState.value.savedRecommendations.map { it.label },
    )
  }

  @Test
  fun blankQueries_doNotProduceRecommendations() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()
    viewModel.onSearchInputChanged(" ", TextRange(1))
    advanceUntilIdle()

    assertEquals(" ", viewModel.uiState.value.query)
    assertTrue(viewModel.uiState.value.savedRecommendations.isEmpty())
  }

  // endregion

  // region Recommendation loading

  @Test
  fun onSearchInputChanged_exposesRecommendationLoadingUntilSuggestionsArrive() = runTest {
    val candidates = CompletableDeferred<List<ArtworkCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(onArtworkCandidates = { candidates.await() })
        )

    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isRecommendationLoading)

    viewModel.onSearchInputChanged("mo", TextRange(2))
    // Set immediately, so it also covers the debounce pause.
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    advanceUntilIdle()
    // The debounce has elapsed, but the request is still pending.
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    candidates.complete(listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
    assertEquals(
        listOf("Mona Lisa (copy)"),
        viewModel.uiState.value.wikiDataRecommendations.map { it.label },
    )
  }

  @Test
  fun onSearchInputChanged_withTooShortQuery_doesNotLoadRecommendations() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchInputChanged("m", TextRange(1))

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
  }

  @Test
  fun onSearchInputChanged_whenQueryBecomesTooShort_stopsRecommendationLoading() = runTest {
    val candidates = CompletableDeferred<List<ArtworkCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(onArtworkCandidates = { candidates.await() })
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    viewModel.onSearchInputChanged("m", TextRange(1))

    assertFalse(viewModel.uiState.value.isRecommendationLoading)

    // The cancelled request must not bring suggestions back.
    candidates.complete(listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())
  }

  @Test
  fun suggestionFailure_stopsLoadingAndKeepsPreviousSuggestions() = runTest {
    var shouldFail = false
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(
                    onArtworkCandidates = {
                      if (shouldFail) throw AppException.Network("Suggestions failed")
                      listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null))
                    }
                )
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()

    shouldFail = true
    viewModel.onSearchInputChanged("mon", TextRange(3))
    assertTrue(viewModel.uiState.value.isRecommendationLoading)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
    // Suggestions are best effort: no error message, previous suggestions stay.
    assertEquals(null, viewModel.uiState.value.fetchErrorMessageResId)
    assertEquals(
        listOf("Mona Lisa (copy)"),
        viewModel.uiState.value.wikiDataRecommendations.map { it.label },
    )
  }

  @Test
  fun onSearchInputChanged_whenSuperseded_keepsLoadingUntilLatestRequestFinishes() = runTest {
    val moResults = CompletableDeferred<List<ArtworkCandidate>>()
    val monResults = CompletableDeferred<List<ArtworkCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(
                    onArtworkCandidates = { query ->
                      when (query) {
                        "mo" -> moResults.await()
                        "mon" -> monResults.await()
                        else -> emptyList()
                      }
                    }
                )
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    viewModel.onSearchInputChanged("mon", TextRange(3))
    advanceUntilIdle()

    // The stale request finishing must neither apply its result nor clear the flag.
    moResults.complete(listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isRecommendationLoading)
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())

    monResults.complete(listOf(ArtworkCandidate("Q2", "Monet's Garden", null)))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
    assertEquals(
        listOf("Monet's Garden"),
        viewModel.uiState.value.wikiDataRecommendations.map { it.label },
    )
  }

  @Test
  fun onSearchSubmitted_cancelsRecommendationLoading() = runTest {
    val candidates = CompletableDeferred<List<ArtworkCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(onArtworkCandidates = { candidates.await() })
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    viewModel.onSearchSubmitted("mo")

    assertFalse(viewModel.uiState.value.isRecommendationLoading)

    candidates.complete(listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())
  }

  @Test
  fun onSuggestingChange_whenOpened_loadsRecommendationsForCurrentQuery() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isRecommendationLoading)

    viewModel.onSuggestingChange(true)

    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
  }

  @Test
  fun onSuggestingChange_whenClosed_cancelsRecommendationLoading() = runTest {
    val candidates = CompletableDeferred<List<ArtworkCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(onArtworkCandidates = { candidates.await() })
        )

    advanceUntilIdle()
    viewModel.onSuggestingChange(true)
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    viewModel.onSuggestingChange(false)

    assertFalse(viewModel.uiState.value.isRecommendationLoading)

    candidates.complete(listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())
  }

  @Test
  fun onSearchTabSelected_withPaneClosed_cancelsRecommendationLoading() = runTest {
    val candidates = CompletableDeferred<List<ArtworkCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(onArtworkCandidates = { candidates.await() })
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    viewModel.onSearchTabSelected(SearchTab.ARTISTS)

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
  }

  @Test
  fun onSearchTabSelected_withPaneOpen_loadsRecommendationsOfTheNewTab() = runTest {
    val artworkCandidates = CompletableDeferred<List<ArtworkCandidate>>()
    val artistCandidates = CompletableDeferred<List<ArtistCandidate>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                FakeWikiDataRepository(
                    onArtworkCandidates = { artworkCandidates.await() },
                    onArtistCandidates = { artistCandidates.await() },
                )
        )

    advanceUntilIdle()
    viewModel.onSuggestingChange(true)
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()

    viewModel.onSearchTabSelected(SearchTab.ARTISTS)

    // The artwork request was dropped and an artist request started in its place.
    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    artworkCandidates.complete(listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isRecommendationLoading)
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())

    artistCandidates.complete(emptyList())
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
  }

  @Test
  fun toggleArtworkType_onArtworksTab_reloadsRecommendations() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isRecommendationLoading)

    viewModel.toggleArtworkType(ArtworkType.entries.last())

    assertTrue(viewModel.uiState.value.isRecommendationLoading)

    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
  }

  @Test
  fun toggleArtworkType_onArtistsTab_doesNotLoadRecommendations() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()

    viewModel.toggleArtworkType(ArtworkType.entries.last())

    assertFalse(viewModel.uiState.value.isRecommendationLoading)
  }

  // endregion

  // region Suggestions

  @Test
  fun onSearchInputChanged_fetchesWikiDataSuggestionsForSelectedTab() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()

    assertEquals(
        listOf("Mona Lisa (copy)"),
        viewModel.uiState.value.wikiDataRecommendations.map { it.label },
    )
  }

  @Test
  fun onSearchInputChanged_withTooShortQuery_clearsRemoteSuggestions() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    viewModel.onSearchInputChanged("m", TextRange(1))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())
  }

  @Test
  fun onSearchInputChanged_dropsSuggestionsOfTheTabThatIsNotSelected() = runTest {
    val viewModel = createViewModel(wikiDataRepository = wikiRepositoryWithArtworkCandidates())

    advanceUntilIdle()
    viewModel.onSearchInputChanged("mo", TextRange(2))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isNotEmpty())

    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchInputChanged("monet", TextRange(5))

    // The artwork suggestions were fetched for "mo" and must not reappear when switching back.
    assertTrue(viewModel.uiState.value.wikiDataRecommendations.isEmpty())
  }

  // endregion

  // region Loading failures

  @Test
  fun unauthorizedSavedArtworkLoad_doesNotPreventSearching() = runTest {
    val savedArtworksRepository =
        object : SavedArtworksRepository by SearchTestData.savedArtworksRepository() {
          override suspend fun getSavedArtworks(): List<Artwork> {
            throw AppException.Unauthorized("No user is signed in")
          }
        }
    val viewModel = createViewModel(savedArtworksRepository = savedArtworksRepository)

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtworks.isEmpty())
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.resultArtworks)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun savedArtworkLoadFailure_setsSearchErrorMessage() = runTest {
    val savedArtworksRepository =
        object : SavedArtworksRepository by SearchTestData.savedArtworksRepository() {
          override suspend fun getSavedArtworks(): List<Artwork> {
            throw AppException.Network("Could not load saved artworks")
          }
        }
    val viewModel = createViewModel(savedArtworksRepository = savedArtworksRepository)

    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.fetchErrorMessageResId)
  }

  @Test
  fun unavailableLocation_doesNotSetAnErrorMessage() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.nearArtworks.isEmpty())
    assertEquals(null, viewModel.uiState.value.fetchErrorMessageResId)
  }

  // endregion

  // region Submitted searches

  @Test
  fun onSearchSubmitted_whenWikiDataRequestFails_setsSearchErrorMessage() = runTest {
    val viewModel =
        createViewModel(
            wikiDataRepository =
                wikiRepositoryWithArtworkSearch { throw AppException.Network("Search failed") }
        )

    advanceUntilIdle()
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.fetchErrorMessageResId)
    assertFalse(viewModel.uiState.value.hasInvalidSearchInput)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.resultArtworks.isEmpty())
    // A failed search still counts as searched, so the screen can tell "failed" from "never ran".
    assertTrue(viewModel.uiState.value.hasAlreadySearched)

    viewModel.clearSearchError()

    assertEquals(null, viewModel.uiState.value.fetchErrorMessageResId)
  }

  @Test
  fun onSearchSubmitted_withInvalidInputMarksSearchFieldUntilEdited() = runTest {
    val viewModel =
        createViewModel(
            wikiDataRepository =
                wikiRepositoryWithArtworkSearch { throw AppException.InvalidInput("Invalid query") }
        )

    advanceUntilIdle()
    viewModel.onSearchSubmitted("bad query")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.hasInvalidSearchInput)

    viewModel.onSearchInputChanged("corrected query", TextRange(15))

    assertFalse(viewModel.uiState.value.hasInvalidSearchInput)
  }

  @Test
  fun onSearchSubmitted_fetchesMatchingArtworksFromWikiData() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    assertEquals("van gogh", viewModel.uiState.value.query)
    assertEquals(
        listOf("The Starry Night", "Sunflowers"),
        viewModel.uiState.value.resultArtworks.map { it.name },
    )
  }

  @Test
  fun onSearchSubmitted_passesSelectedArtworkTypesToRepository() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = createViewModel(wikiDataRepository = wikiRepository)
    val selectedTypes = listOf(ArtworkType.PAINTING, ArtworkType.MURAL)

    advanceUntilIdle()
    ArtworkType.entries.filterNot { it in selectedTypes }.forEach(viewModel::toggleArtworkType)
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    assertEquals(selectedTypes, wikiRepository.searchedTypeValues.single())
  }

  @Test
  fun toggleArtworkType_doesNotAllowRemovingLastSelectedType() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()
    ArtworkType.entries.drop(1).forEach(viewModel::toggleArtworkType)
    assertEquals(listOf(ArtworkType.PAINTING), viewModel.uiState.value.typeValues)

    viewModel.toggleArtworkType(ArtworkType.PAINTING)

    assertEquals(listOf(ArtworkType.PAINTING), viewModel.uiState.value.typeValues)
  }

  @Test
  fun onSearchSubmitted_exposesLoadingUntilResultsArrive() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val viewModel =
        createViewModel(wikiDataRepository = wikiRepositoryWithArtworkSearch { results.await() })

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    assertTrue(viewModel.uiState.value.isLoading)

    results.complete(listOf(SearchTestData.starryNight))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.resultArtworks)
  }

  @Test
  fun onSearchSubmitted_onlyShowsResultsFromLatestQuery() = runTest {
    val monetResults = CompletableDeferred<List<Artwork>>()
    val vanGoghResults = CompletableDeferred<List<Artwork>>()
    val viewModel =
        createViewModel(
            wikiDataRepository =
                wikiRepositoryWithArtworkSearch { title ->
                  when (title) {
                    "monet" -> monetResults.await()
                    "van Gogh" -> vanGoghResults.await()
                    else -> emptyList()
                  }
                }
        )

    advanceUntilIdle()
    viewModel.onSearchSubmitted("monet")
    advanceUntilIdle()
    viewModel.onSearchSubmitted("van Gogh")
    advanceUntilIdle()

    vanGoghResults.complete(listOf(SearchTestData.starryNight))
    advanceUntilIdle()
    monetResults.complete(listOf(SearchTestData.sunflowers))
    advanceUntilIdle()

    assertEquals("van Gogh", viewModel.uiState.value.query)
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.resultArtworks)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun onSearchInputChanged_whenOnlySelectionChanges_keepsRunningSearch() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val viewModel =
        createViewModel(wikiDataRepository = wikiRepositoryWithArtworkSearch { results.await() })

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    viewModel.onSearchInputChanged("star", TextRange(1))

    assertTrue(viewModel.uiState.value.isLoading)

    results.complete(listOf(SearchTestData.starryNight))
    advanceUntilIdle()

    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.resultArtworks)
  }

  @Test
  fun onSearchInputChanged_whenTextChanges_cancelsRunningSearch() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val viewModel =
        createViewModel(wikiDataRepository = wikiRepositoryWithArtworkSearch { results.await() })

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    viewModel.onSearchInputChanged("moon", TextRange(4))

    assertFalse(viewModel.uiState.value.isLoading)

    results.complete(listOf(SearchTestData.starryNight))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.resultArtworks.isEmpty())
    assertFalse(viewModel.uiState.value.hasAlreadySearched)
  }

  // endregion

  // region Tabs

  @Test
  fun onSearchTabSelected_keepsRunningSearchOfTheOtherTab() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val viewModel =
        createViewModel(wikiDataRepository = wikiRepositoryWithArtworkSearch { results.await() })

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)

    // The artists tab has nothing running, but the artwork search must survive the switch.
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(setOf(SearchTab.ARTWORKS), viewModel.uiState.value.loadingTabs)

    results.complete(listOf(SearchTestData.starryNight))
    advanceUntilIdle()

    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.resultArtworks)
    assertFalse(viewModel.uiState.value.hasAlreadySearched)

    viewModel.onSearchTabSelected(SearchTab.ARTWORKS)

    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.hasAlreadySearched)
  }

  @Test
  fun onSearchTabSelected_showsLoadingOfThatTab() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val viewModel =
        createViewModel(wikiDataRepository = wikiRepositoryWithArtworkSearch { results.await() })

    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchTabSelected(SearchTab.ARTWORKS)
    viewModel.onSearchSubmitted("star")
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    assertFalse(viewModel.uiState.value.isLoading)

    viewModel.onSearchTabSelected(SearchTab.ARTWORKS)

    assertTrue(viewModel.uiState.value.isLoading)
  }

  @Test
  fun onSearchSubmitted_onArtistsTab_onlyMarksThatTabAsSearched() = runTest {
    val viewModel =
        createViewModel(wikiDataRepository = wikiRepositoryWithArtistSearch { emptyList() })

    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    assertEquals(setOf(SearchTab.ARTISTS), viewModel.uiState.value.searchedTabs)
    assertTrue(viewModel.uiState.value.hasAlreadySearched)
    assertTrue(viewModel.uiState.value.resultArtworks.isEmpty())

    viewModel.onSearchTabSelected(SearchTab.ARTWORKS)

    assertFalse(viewModel.uiState.value.hasAlreadySearched)
  }

  @Test
  fun invalidInput_isTrackedPerTab() = runTest {
    val viewModel =
        createViewModel(
            wikiDataRepository =
                wikiRepositoryWithArtistSearch { throw AppException.InvalidInput("Invalid query") }
        )

    advanceUntilIdle()
    viewModel.onSearchTabSelected(SearchTab.ARTISTS)
    viewModel.onSearchSubmitted("bad query")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.hasInvalidSearchInput)

    viewModel.onSearchTabSelected(SearchTab.ARTWORKS)

    assertFalse(viewModel.uiState.value.hasInvalidSearchInput)

    viewModel.onSearchTabSelected(SearchTab.ARTISTS)

    assertTrue(viewModel.uiState.value.hasInvalidSearchInput)
  }

  // endregion

  // region Saved artworks

  @Test
  fun toggleSavedArtwork_whenSaveFails_setsSaveErrorMessage() = runTest {
    val repository =
        object : SavedArtworksRepository by SearchTestData.savedArtworksRepository() {
          override suspend fun saveArtwork(artwork: Artwork) {
            throw AppException.Network("Could not save artwork")
          }
        }
    val viewModel = createViewModel(savedArtworksRepository = repository)
    val artwork = SearchTestData.untitledStudy.copy(uid = "newArtworkId", name = "New Artwork")

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.saveErrorMessageResId)

    viewModel.clearSaveError()

    assertEquals(null, viewModel.uiState.value.saveErrorMessageResId)
  }

  @Test
  fun toggleSavedArtwork_addsArtworkToSavedCollection() = runTest {
    val repository = SearchTestData.savedArtworksRepository()
    val viewModel = createViewModel(savedArtworksRepository = repository)
    val artwork = SearchTestData.untitledStudy.copy(uid = "newArtworkId", name = "New Artwork")

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtworks.any { it.uid == artwork.uid })
    assertTrue(repository.savedArtworks.any { it.uid == artwork.uid && it.name == artwork.name })
  }

  @Test
  fun toggleSavedArtwork_removesArtworkFromSavedCollectionWhenAlreadySaved() = runTest {
    val artwork = SearchTestData.starryNight
    val repository = SearchTestData.savedArtworksRepository(listOf(artwork))
    val viewModel = createViewModel(savedArtworksRepository = repository)

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtworks.none { it.uid == artwork.uid })
    assertTrue(repository.savedArtworks.none { it.uid == artwork.uid })
  }

  // endregion

  @Test
  fun provideFactory_forUnknownViewModelClass_throws() {
    val factory =
        SearchScreenViewModel.provideFactory(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
            FakeLocationProvider(errorType = AppException.PermissionsError()),
        )

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  // region Helpers

  private fun createViewModel(
      savedArtworksRepository: SavedArtworksRepository = SearchTestData.savedArtworksRepository(),
      wikiDataRepository: WikiDataRepository = FakeWikiDataRepository(),
  ) =
      SearchScreenViewModel(
          savedArtworksRepository,
          wikiDataRepository,
          FakeLocationProvider(errorType = AppException.PermissionsError()),
      )

  /** Wiki repository whose artwork search is replaced by [search]. */
  private fun wikiRepositoryWithArtworkSearch(
      search: suspend (title: String) -> List<Artwork>
  ): WikiDataRepository = FakeWikiDataRepository(onArtworksFromQuery = search)

  /** Wiki repository whose artist search is replaced by [search]. */
  private fun wikiRepositoryWithArtistSearch(
      search: suspend (query: String) -> List<Artist>
  ): WikiDataRepository = FakeWikiDataRepository(onArtistsFromQuery = search)

  /** Wiki repository that suggests a single artwork for every query. */
  private fun wikiRepositoryWithArtworkCandidates(): WikiDataRepository =
      FakeWikiDataRepository(
          onArtworkCandidates = { listOf(ArtworkCandidate("Q1", "Mona Lisa (copy)", null)) }
      )

  /**
   * Wiki repository that implements every method the ViewModel can call.
   *
   * Everything the shared [SearchTestData] repository leaves unimplemented (and which would throw
   * `NotImplementedError`, an `Error` that the ViewModel's error handling does not catch) returns
   * an empty value here. Artwork search and single-artwork lookup still fall back to the shared
   * test data, so default results stay the same as before.
   */
  private class FakeWikiDataRepository(
      private val base: WikiDataRepository = SearchTestData.wikiDataRepository(),
      private val onArtworksFromQuery: (suspend (String) -> List<Artwork>)? = null,
      private val onArtistsFromQuery: suspend (String) -> List<Artist> = { emptyList() },
      private val onArtworkCandidates: suspend (String) -> List<ArtworkCandidate> = { emptyList() },
      private val onArtistCandidates: suspend (String) -> List<ArtistCandidate> = { emptyList() },
  ) : WikiDataRepository by base {

    override suspend fun getArtworksNearLocation(
        searchArea: SearchArea,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = emptyList()

    override suspend fun getArtworkCandidates(
        query: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<ArtworkCandidate> = onArtworkCandidates(query)

    override suspend fun getArtistsFromQuery(query: String, limit: Int): List<Artist> =
        onArtistsFromQuery(query)

    override suspend fun getArtist(uid: ArtistId): Artist? = null

    override suspend fun getArtworksFromQuery(
        artworkTitle: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> =
        onArtworksFromQuery?.invoke(artworkTitle)
            ?: base.getArtworksFromQuery(artworkTitle, typeValues, limit)

    override suspend fun getArtistCandidates(query: String, limit: Int): List<ArtistCandidate> =
        onArtistCandidates(query)
  }

  private class OtherViewModel : ViewModel()

  // endregion
}
