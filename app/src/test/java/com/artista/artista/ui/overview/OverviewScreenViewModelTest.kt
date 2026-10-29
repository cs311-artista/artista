// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.overview

import androidx.lifecycle.ViewModel
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.map.UserLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the state exposed by [OverviewViewModel].
 *
 * @author patrickmcdan
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewScreenViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * Verifies that the ViewModel loads artworks from its replaceable data source.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewViewModel_loadsArtworksFromDataSource() = runTest {
    val expectedArtworks =
        listOf(
            artwork(name = "The Starry Night", artistName = "Vincent van Gogh"),
            artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"),
        )
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(location: UserLocation?): List<Artwork> =
              expectedArtworks
        }
    val viewModel = OverviewViewModel(repository)

    advanceUntilIdle()

    assertEquals(expectedArtworks, viewModel.uiState.value.artworks)
  }

  /**
   * Verifies that a provider failure exposes a load error and no stale artwork list.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewViewModel_whenLoadingFails_exposesLoadError() = runTest {
    val failing =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(location: UserLocation?): List<Artwork> =
              error("Artwork provider failure")
        }
    val viewModel = OverviewViewModel(failing)

    advanceUntilIdle()

    assertEquals(R.string.overview_load_error, viewModel.uiState.value.errorMessageRes)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
  }

  /**
   * Verifies that clearing the error removes it from the UI state.
   *
   * @author patrickmcdan
   */
  @Test
  fun clearErrorMessage_removesTheCurrentError() = runTest {
    val failing =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(location: UserLocation?): List<Artwork> =
              error("Artwork provider failure")
        }
    val viewModel = OverviewViewModel(failing)

    advanceUntilIdle()
    viewModel.clearErrorMessage()

    assertEquals(null, viewModel.uiState.value.errorMessageRes)
  }

  /** Verifies that the factory refuses to create a ViewModel of another class. */
  @Test
  fun provideFactory_forUnknownViewModelClass_throws() {
    val factory = OverviewViewModel.provideFactory(ArtworkRepositoryLocal())

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  /** ViewModel unrelated to the overview screen, used to trigger the factory's error. */
  private class OtherViewModel : ViewModel()

  private fun artwork(name: String, artistName: String): Artwork =
      Artwork(
          uid = "$name-$artistName",
          name = name,
          artist = Artist(uid = "test-$artistName", name = artistName),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )
}
