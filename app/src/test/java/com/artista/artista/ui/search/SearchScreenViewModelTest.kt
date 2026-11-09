// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.compose.ui.text.TextRange
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.artwork.mockArtworks
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
    viewModel.onSearchInputChanged("thinker", TextRange(2))
    advanceUntilIdle()

    assertEquals("thinker", viewModel.uiState.value.query)
    assertEquals(TextRange(2), viewModel.uiState.value.selection)
    assertEquals(listOf("mock-001"), viewModel.uiState.value.recommendations.map { it.uid })
    assertEquals(
        listOf("The Thinker"),
        viewModel.uiState.value.recommendations.map { it.name },
    )
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
  }

  @Test
  fun onSearchInputChanged_usesDevelopmentMockArtworksForRecommendations() = runTest {
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(initialSavedArtworks = emptyList()),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    viewModel.onSearchInputChanged("blue", TextRange(4))
    advanceUntilIdle()

    assertEquals(listOf("mock-004"), viewModel.uiState.value.recommendations.map { it.uid })
  }

  @Test
  fun developmentMockArtworksAreUsedWithoutLoadingSavedArtworks() = runTest {
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

    assertEquals(mockArtworks, viewModel.uiState.value.savedArtworks)
    assertEquals(listOf(SearchTestData.starryNight), viewModel.uiState.value.artworks)
    assertFalse(viewModel.uiState.value.isLoading)
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

    assertEquals(R.string.error_network, viewModel.uiState.value.searchErrorMessageResId)
    assertFalse(viewModel.uiState.value.hasInvalidSearchInput)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
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

  @Test
  fun onSearchSubmitted_passesSelectedArtworkTypesToRepository() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)
    val selectedTypes = listOf(ArtworkType.PAINTING, ArtworkType.MURAL)

    advanceUntilIdle()
    ArtworkType.entries.filterNot { it in selectedTypes }.forEach(viewModel::toggleArtworkType)
    viewModel.onSearchSubmitted("star")
    advanceUntilIdle()

    assertEquals(selectedTypes, wikiRepository.searchedTypeValues.single())
  }

  @Test
  fun toggleArtworkType_doesNotAllowRemovingLastSelectedType() = runTest {
    val viewModel =
        SearchScreenViewModel(
            SearchTestData.savedArtworksRepository(),
            SearchTestData.wikiDataRepository(),
        )

    advanceUntilIdle()
    ArtworkType.entries.drop(1).forEach(viewModel::toggleArtworkType)
    assertEquals(listOf(ArtworkType.PAINTING), viewModel.uiState.value.typeValues)

    viewModel.toggleArtworkType(ArtworkType.PAINTING)

    assertEquals(listOf(ArtworkType.PAINTING), viewModel.uiState.value.typeValues)
  }

  @Test
  fun onSearchSubmitted_exposesLoadingUntilResultsArrive() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val wikiRepository =
        object : WikiDataRepository {

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
  }

  @Test
  fun toggleSavedArtwork_addsToRepositoryAndKeepsDevelopmentMocksInUiState() = runTest {
    val repository = SearchTestData.savedArtworksRepository()
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())
    val artwork = SearchTestData.untitledStudy.copy(uid = "newArtworkId", name = "New Artwork")

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(mockArtworks, viewModel.uiState.value.savedArtworks)
    assertTrue(repository.savedArtworks.any { it.uid == artwork.uid && it.name == artwork.name })
  }

  @Test
  fun toggleSavedArtwork_removesFromRepositoryAndKeepsDevelopmentMocksInUiState() = runTest {
    val artwork = mockArtworks.first()
    val repository = SearchTestData.savedArtworksRepository(listOf(artwork))
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(mockArtworks, viewModel.uiState.value.savedArtworks)
    assertTrue(repository.savedArtworks.none { it.uid == artwork.uid })
  }
}
