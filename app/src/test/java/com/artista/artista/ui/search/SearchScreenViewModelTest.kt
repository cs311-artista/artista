// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.map.UserLocation
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

  @Test
  fun toggleSavedArtwork_addsArtworkToSavedCollection() = runTest {
    val repository = fakeArtworkRepository()
    val viewModel = SearchScreenViewModel(repository, fakeWikiRepository())
    val artwork =
        Artwork(
            uid = "newArtworkId",
            name = "New Artwork",
            artistName = "Unknown Artist",
            location = null,
            conceptionDate = null,
            dimension = null,
            description = null,
            museum = null,
        )

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(viewModel.savedArtworks.value.any { it.uid == artwork.uid })
    assertTrue(repository.savedArtworks.any { it.uid == artwork.uid && it.name == artwork.name })
  }

  @Test
  fun toggleSavedArtwork_removesArtworkFromSavedCollectionWhenAlreadySaved() = runTest {
    val repository = fakeArtworkRepository()
    val viewModel = SearchScreenViewModel(repository, fakeWikiRepository())
    val artwork =
        Artwork(
            uid = "starryId",
            name = "The Starry Night",
            artistName = "Vincent van Gogh",
            location = null,
            conceptionDate = null,
            dimension = null,
            description = null,
            museum = null,
        )

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(viewModel.savedArtworks.value.none { it.uid == artwork.uid })
    assertTrue(repository.savedArtworks.none { it.uid == artwork.uid })
  }

  private fun fakeArtworkRepository(): FakeArtworkRepository =
      FakeArtworkRepository(
          initialSavedArtworks =
              listOf(
                  Artwork(
                      uid = "starryId",
                      name = "The Starry Night",
                      artistName = "Vincent van Gogh",
                      location = null,
                      conceptionDate = null,
                      dimension = null,
                      description = null,
                      museum = null,
                  ),
                  Artwork(
                      uid = "girlId",
                      name = "Girl with a Pearl Earring",
                      artistName = "Johannes Vermeer",
                      location = null,
                      conceptionDate = null,
                      dimension = null,
                      description = null,
                      museum = null,
                  ),
                  Artwork(
                      uid = "sunflowersId",
                      name = "Sunflowers",
                      artistName = "Vincent van Gogh",
                      location = null,
                      conceptionDate = null,
                      dimension = null,
                      description = null,
                      museum = null,
                  ),
                  Artwork(
                      uid = "untitledId",
                      name = "Untitled study",
                      artistName = null,
                      location = null,
                      conceptionDate = null,
                      dimension = null,
                      description = null,
                      museum = null,
                  ),
              )
      )

  private class FakeArtworkRepository(
      initialSavedArtworks: List<Artwork> = emptyList(),
  ) : SavedArtworksRepository {
    val savedArtworks = initialSavedArtworks.toMutableList()
    var shouldThrowError = false

    override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
      if (shouldThrowError) throw RuntimeException("Simulated error")
      return savedArtworks.any { it.uid == uid }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.uid == artwork.uid }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.uid == uid }
    }

    override suspend fun getSavedArtworks(): List<Artwork> = savedArtworks.toList()
  }

  private fun fakeWikiRepository(): WikiDataRepository =
      object : WikiDataRepository {
        override suspend fun getArtworksNearLocation(location: UserLocation?): List<Artwork> {
          throw NotImplementedError()
        }

        override suspend fun getPopularArtists(): List<String> {
          throw NotImplementedError()
        }

        override suspend fun getArtwork(uid: ArtworkId): Artwork {
          throw NotImplementedError()
        }

        override suspend fun searchArtworks(query: String): List<Artwork> =
            listOf(
                    Artwork(
                        uid = "starryId",
                        name = "The Starry Night",
                        artistName = "Vincent van Gogh",
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                        description = null,
                        museum = null,
                    ),
                    Artwork(
                        uid = "sunflowersId",
                        name = "Sunflowers",
                        artistName = "Vincent van Gogh",
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                        description = null,
                        museum = null,
                    ),
                    Artwork(
                        uid = "untitledId",
                        name = "Untitled study",
                        artistName = null,
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                        description = null,
                        museum = null,
                    ),
                )
                .filter {
                  it.name.contains(query, ignoreCase = true) ||
                      it.artistName?.contains(query, ignoreCase = true) == true
                }
      }
}
