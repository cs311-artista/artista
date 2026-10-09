// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import com.artista.artista.model.artwork.WikiDataRepository
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
  fun onSearchQueryChanged_updatesSearchTextAndRecommendationsWithoutFetchingResults() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    advanceUntilIdle()
    viewModel.onSearchQueryChanged("star")
    advanceUntilIdle()

    assertEquals("star", viewModel.searchQuery.value)
    assertEquals(listOf("The Starry Night"), viewModel.recommendations.value)
    assertTrue(viewModel.artworks.value.isEmpty())
  }

  @Test
  fun onSearchSubmitted_fetchesMatchingArtworksFromWikiData() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    advanceUntilIdle()
    viewModel.onSearchSubmitted("van gogh")
    advanceUntilIdle()

    assertEquals("van gogh", viewModel.searchQuery.value)
    assertEquals(listOf("The Starry Night", "Sunflowers"), viewModel.artworks.value.map { it.name })
  }

  @Test
  fun onSearchSubmitted_handlesArtworksWithoutAnArtist() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    advanceUntilIdle()
    viewModel.onSearchSubmitted("untitled")
    advanceUntilIdle()

    assertEquals(listOf("Untitled study"), viewModel.artworks.value.map { it.name })
    assertEquals(null, viewModel.artworks.value.single().artistName)
  }

  @Test
  fun blankQueries_doNotProduceRecommendations() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    advanceUntilIdle()
    viewModel.onSearchQueryChanged(" ")
    advanceUntilIdle()

    assertEquals(" ", viewModel.searchQuery.value)
    assertTrue(viewModel.recommendations.value.isEmpty())
  }

  private fun fakeArtworkRepository(): ArtworkRepository =
      object : ArtworkRepository {
        override suspend fun getSavedArtworks(): List<Artwork> =
            listOf(
                Artwork(
                    name = "The Starry Night",
                    artistName = "Vincent van Gogh",
                    location = null,
                    conceptionDate = null,
                    dimension = null,
                ),
                Artwork(
                    name = "Girl with a Pearl Earring",
                    artistName = "Johannes Vermeer",
                    location = null,
                    conceptionDate = null,
                    dimension = null,
                ),
                Artwork(
                    name = "Sunflowers",
                    artistName = "Vincent van Gogh",
                    location = null,
                    conceptionDate = null,
                    dimension = null,
                ),
                Artwork(
                    name = "Untitled study",
                    artistName = null,
                    location = null,
                    conceptionDate = null,
                    dimension = null,
                ),
            )
      }

  private fun fakeWikiRepository(): WikiDataRepository =
      object : WikiDataRepository {
        override suspend fun searchArtworks(query: String): List<Artwork> =
            listOf(
                    Artwork(
                        name = "The Starry Night",
                        artistName = "Vincent van Gogh",
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                    ),
                    Artwork(
                        name = "Sunflowers",
                        artistName = "Vincent van Gogh",
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                    ),
                    Artwork(
                        name = "Untitled study",
                        artistName = null,
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                    ),
                )
                .filter {
                  it.name.contains(query, ignoreCase = true) ||
                      it.artistName?.contains(query, ignoreCase = true) == true
                }
      }
}
