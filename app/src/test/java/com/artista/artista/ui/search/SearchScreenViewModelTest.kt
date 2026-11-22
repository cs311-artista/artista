// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
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

  @Test
  fun onSearchInputChanged_updatesQuerySelectionAndRecommendations() = runTest {
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("star", TextRange(2))
    advanceUntilIdle()

    assertEquals("star", viewModel.uiState.value.query)
    assertEquals(TextRange(2), viewModel.uiState.value.selection)
    assertEquals(listOf("starryId"), viewModel.uiState.value.recommendations.map { it.uid })
    assertEquals(
        listOf("The Starry Night"),
        viewModel.uiState.value.recommendations.map { it.name },
    )
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
  }

  @Test
  fun onSearchInputChanged_ignoresSavedArtworksWithoutAName() = runTest {
    val starryNight = SearchTestData.savedArtworks.first { it.uid == "starryId" }
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(
                initialSavedArtworks =
                    listOf(starryNight, starryNight.copy(uid = "noNameId", name = null))
            ),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("star", TextRange(4))
    advanceUntilIdle()

    assertEquals(listOf("starryId"), viewModel.uiState.value.recommendations.map { it.uid })
  }

  @Test
  fun unauthorizedSavedArtworkLoad_doesNotPreventSearching() = runTest {
    val savedArtworksRepository =
        object : SavedArtworksRepository by SearchTestData.savedArtworksRepository() {
          override suspend fun getSavedArtworks(): List<Artwork> {
            throw AppException.Unauthorized("No user is signed in")
          }
        }
    val viewModel =
        SearchScreenViewModel(savedArtworksRepository, SearchTestData.wikiDataRepository())

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtworks.isEmpty())
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.artworks)
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
    val viewModel =
        SearchScreenViewModel(savedArtworksRepository, SearchTestData.wikiDataRepository())

    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.fetchErrorMessageResId)
  }

  @Test
  fun onSearchSubmitted_whenWikiDataRequestFails_setsSearchErrorMessage() = runTest {
    val wikiRepository =
        object : WikiDataRepository {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtist(uid: ArtistId): Artist? {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtistFromName(query: String): Artist? {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getPopularArtists(): List<Artist> {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtwork(uid: ArtworkId): Artwork =
              throw NoSuchElementException("No artwork matching uid $uid was found")

          override suspend fun searchArtworks(
              artworkTitle: String,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = throw AppException.Network("Search failed")
        }
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.fetchErrorMessageResId)
    assertFalse(viewModel.uiState.value.hasInvalidSearchInput)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())

    viewModel.clearSearchError()

    assertEquals(null, viewModel.uiState.value.fetchErrorMessageResId)
  }

  @Test
  fun onSearchSubmitted_withInvalidInputMarksSearchFieldUntilEdited() = runTest {
    val wikiRepository =
        object : WikiDataRepository by SearchTestData.wikiDataRepository() {
          override suspend fun searchArtworks(
              artworkTitle: String,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            throw AppException.InvalidInput("Invalid search text")
          }
        }
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.onSearchSubmitted("bad query")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.hasInvalidSearchInput)

    viewModel.onSearchInputChanged("corrected query", TextRange(15))

    assertFalse(viewModel.uiState.value.hasInvalidSearchInput)
  }

  @Test
  fun onSearchSubmitted_fetchesMatchingArtworksFromWikiData() = runTest {
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    assertEquals("van gogh", viewModel.uiState.value.query)
    assertEquals(
        listOf("The Starry Night", "Sunflowers"),
        viewModel.uiState.value.artworks.map { it.name },
    )
  }

  /** Checks that the selected types reach the repository, in a stable order. */
  @Test
  fun onSearchSubmitted_passesSelectedTypesToRepositoryInEnumOrder() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(types = setOf(ArtworkType.MURAL, ArtworkType.PAINTING)))
    advanceUntilIdle()
    // Nothing was searched yet, so there is no query to re-run.
    assertTrue(wikiRepository.searchedQueries.isEmpty())

    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    assertEquals(
        listOf(ArtworkType.PAINTING, ArtworkType.MURAL),
        wikiRepository.searchedTypeValues.single(),
    )
  }

  /** Checks that no selected type means no type restriction, since the repository needs one. */
  @Test
  fun onSearchSubmitted_withoutSelectedTypes_searchesAllTypes() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    assertEquals(ArtworkType.entries, wikiRepository.searchedTypeValues.single())
  }

  /**
   * Checks that changing the types re-runs the last submitted query, not the text edited since, and
   * leaves that text untouched.
   */
  @Test
  fun applyFilter_withChangedTypes_reRunsLastSubmittedQuery() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()
    viewModel.onSearchInputChanged("sun", TextRange(3))
    viewModel.applyFilter(ArtworkFilter(types = setOf(ArtworkType.SCULPTURE)))
    advanceUntilIdle()

    assertEquals(listOf("star", "star"), wikiRepository.searchedQueries)
    assertEquals(listOf(ArtworkType.SCULPTURE), wikiRepository.searchedTypeValues.last())
    assertEquals("sun", viewModel.uiState.value.query)
  }

  /** Checks that removing every type re-runs the query without type restriction. */
  @Test
  fun applyFilter_clearingTypes_reRunsQueryWithAllTypes() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(types = setOf(ArtworkType.PAINTING)))
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter())
    advanceUntilIdle()

    assertEquals(2, wikiRepository.searchedQueries.size)
    assertEquals(ArtworkType.entries, wikiRepository.searchedTypeValues.last())
  }

  /**
   * Checks that criteria other than types filter the current results at once, without a new
   * request, while the options still list every result.
   */
  @Test
  fun applyFilter_withSameTypes_filtersCurrentResultsWithoutNewRequest() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    // "e" matches every sample artwork, so the results hold several artists.
    viewModel.onSearchSubmitted("e")
    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(artists = setOf("Vincent van Gogh")))
    advanceUntilIdle()

    assertEquals(1, wikiRepository.searchedQueries.size)
    assertEquals(
        listOf("The Starry Night", "Sunflowers"),
        viewModel.uiState.value.artworks.map { it.name },
    )
    assertEquals(
        listOf("Johannes Vermeer", "Vincent van Gogh"),
        viewModel.uiState.value.artistOptions,
    )
  }

  /** Checks that criteria other than types also apply to the results of a later search. */
  @Test
  fun onSearchSubmitted_appliesActiveCriteriaToNewResults() = runTest {
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(artists = setOf("Johannes Vermeer")))
    viewModel.onSearchSubmitted("e")
    advanceUntilIdle()

    assertEquals(
        listOf("Girl with a Pearl Earring"),
        viewModel.uiState.value.artworks.map { it.name },
    )
  }

  /**
   * Checks that results are not filtered by type again on the device: Wikidata already restricted
   * them, and an artwork whose type was not parsed must not disappear.
   */
  @Test
  fun applyFilter_doesNotFilterResultsByTypeLocally() = runTest {
    // The fake ignores the requested types and its artworks carry none.
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(types = setOf(ArtworkType.PAINTING)))
    advanceUntilIdle()

    assertEquals(
        listOf("The Starry Night", "Sunflowers"),
        viewModel.uiState.value.artworks.map { it.name },
    )
  }

  @Test
  fun onSearchSubmitted_exposesLoadingUntilResultsArrive() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val wikiRepository =
        object : WikiDataRepository {

          override suspend fun getArtistFromName(query: String): Artist? {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtist(uid: ArtistId): Artist? {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getPopularArtists(): List<Artist> {
            throw NotImplementedError("Not yet implemented")
          }

          override suspend fun getArtwork(uid: ArtworkId): Artwork =
              throw NoSuchElementException("No artwork matching uid $uid was found")

          override suspend fun searchArtworks(
              artworkTitle: String,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = results.await()
        }
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    advanceUntilIdle()
    viewModel.onSearchSubmitted("star")
    assertTrue(viewModel.uiState.value.isLoading)

    results.complete(listOf(SearchTestData.starryNight))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.artworks)
  }

  @Test
  fun onSearchSubmitted_onlyShowsResultsFromLatestQuery() = runTest {
    val monetResults = CompletableDeferred<List<Artwork>>()
    val vanGoghResults = CompletableDeferred<List<Artwork>>()
    val wikiRepository =
        object : WikiDataRepository {
          override suspend fun getArtistFromName(query: String): Artist? {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtist(uid: ArtistId): Artist? {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            throw NotImplementedError("Should not be used in this test")
          }

          override suspend fun getPopularArtists(): List<Artist> {
            throw NotImplementedError("Not yet implemented")
          }

          override suspend fun getArtwork(uid: ArtworkId): Artwork =
              throw NoSuchElementException("No artwork matching uid $uid was found")

          override suspend fun searchArtworks(
              artworkTitle: String,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> =
              when (artworkTitle) {
                "monet" -> monetResults.await()
                "van Gogh" -> vanGoghResults.await()
                else -> emptyList()
              }
        }
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

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
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.artworks)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun blankQueries_doNotProduceRecommendations() = runTest {
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged(" ", TextRange(1))
    advanceUntilIdle()

    assertEquals(" ", viewModel.uiState.value.query)
    assertTrue(viewModel.uiState.value.recommendations.isEmpty())
  }

  @Test
  fun toggleSavedArtwork_whenSaveFails_setsSaveErrorMessage() = runTest {
    val repository =
        object : SavedArtworksRepository by SearchTestData.savedArtworksRepository() {
          override suspend fun saveArtwork(artwork: Artwork) {
            throw AppException.Network("Could not save artwork")
          }
        }
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())
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
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())
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
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtworks.none { it.uid == artwork.uid })
    assertTrue(repository.savedArtworks.none { it.uid == artwork.uid })
  }

  @Test
  fun provideFactory_forUnknownViewModelClass_throws() {
    val factory =
        SearchScreenViewModel.provideFactory(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  private class OtherViewModel : ViewModel()
}
