// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.compose.ui.text.TextRange
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.WikiDataRepository
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
import org.junit.Assert.assertFalse
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
  fun onSearchSubmitted_exposesLoadingUntilResultsArrive() = runTest {
    val results = CompletableDeferred<List<Artwork>>()
    val wikiRepository =
        object : WikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork =
              throw NoSuchElementException("No artwork matching uid $uid was found")

          override suspend fun searchArtworks(query: String): List<Artwork> = results.await()
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
          override suspend fun getArtwork(uid: ArtworkId): Artwork =
              throw NoSuchElementException("No artwork matching uid $uid was found")

          override suspend fun searchArtworks(query: String): List<Artwork> =
              when (query) {
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
    val repository = SearchTestData.savedArtworksRepository(listOf(SearchTestData.starryNight))
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())
    val artwork = SearchTestData.starryNight

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtworks.none { it.uid == artwork.uid })
    assertTrue(repository.savedArtworks.none { it.uid == artwork.uid })
  }
}
