// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.lifecycle.ViewModel
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CancellationException
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
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = expectedArtworks
        }
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal())

    advanceUntilIdle()

    assertEquals(expectedArtworks, viewModel.uiState.value.artworks)
  }

  /**
   * Checks that an unexpected provider failure exposes the unknown error and no artworks.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewViewModel_whenLoadingFailsUnexpectedly_exposesUnknownError() = runTest {
    val failing =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = error("Artwork provider failure")
        }
    val viewModel = OverviewViewModel(failing, ArtworkRepositoryLocal())

    advanceUntilIdle()

    assertEquals(R.string.error_unknown, viewModel.uiState.value.errorMessageRes)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
  }

  /**
   * Checks that a network failure while loading exposes the network error and no artworks.
   *
   * @author Timz3rr
   */
  @Test
  fun overviewViewModel_whenLoadingFailsWithNetworkError_exposesNetworkError() = runTest {
    val viewModel =
        OverviewViewModel(repositoryFailingWith(AppException.Network()), ArtworkRepositoryLocal())

    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageRes)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
  }

  /**
   * Checks that a cancelled load does not expose an error.
   *
   * @author Timz3rr
   */
  @Test
  fun overviewViewModel_whenLoadingIsCancelled_exposesNoError() = runTest {
    // Cancellation is how a coroutine stops (e.g. leaving the screen), so it is not a failure.
    val viewModel =
        OverviewViewModel(
            repositoryFailingWith(CancellationException("Load cancelled")),
            ArtworkRepositoryLocal(),
        )

    advanceUntilIdle()

    assertEquals(null, viewModel.uiState.value.errorMessageRes)
  }

  /**
   * Checks that a successful reload clears the error left by an earlier failed load.
   *
   * @author Timz3rr
   */
  @Test
  fun overviewViewModel_whenReloadSucceedsAfterFailure_clearsError() = runTest {
    val expectedArtworks =
        listOf(artwork(name = "The Starry Night", artistName = "Vincent van Gogh"))
    var loadCount = 0
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            loadCount++
            if (loadCount == 1) throw AppException.Network()
            return expectedArtworks
          }
        }
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal())

    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageRes)

    viewModel.refreshUiState()
    advanceUntilIdle()

    assertEquals(null, viewModel.uiState.value.errorMessageRes)
    assertEquals(expectedArtworks, viewModel.uiState.value.artworks)
  }

  /** Verifies toggling an artwork saves and removes it while updating bookmark state. */
  @Test
  fun overviewViewModel_toggleSavedArtwork_persistsArtworkAndUpdatesState() = runTest {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository)

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(artwork.uid in viewModel.uiState.value.savedArtworkIds)
    assertEquals(listOf(artwork), repository.getSavedArtworks())

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(artwork.uid !in viewModel.uiState.value.savedArtworkIds)
    assertTrue(repository.getSavedArtworks().isEmpty())
  }

  /** Verifies repeated taps do not queue conflicting writes for the same artwork. */
  @Test
  fun overviewViewModel_whenToggledAgainWhilePending_keepsFirstToggle() = runTest {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    val viewModel = OverviewViewModel(repository, repository)

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(artwork.uid in viewModel.uiState.value.savedArtworkIds)
    assertEquals(listOf(artwork), repository.getSavedArtworks())
  }

  /** Verifies refreshing saved artworks picks up changes made outside Overview. */
  @Test
  fun overviewViewModel_refreshSavedArtworkIds_includesArtworkSavedElsewhere() = runTest {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    val viewModel = OverviewViewModel(repository, repository)

    advanceUntilIdle()
    repository.saveArtwork(artwork)
    viewModel.refreshSavedArtworkIds()
    advanceUntilIdle()

    assertTrue(artwork.uid in viewModel.uiState.value.savedArtworkIds)
  }

  /** Verifies repository failures while refreshing saved IDs are exposed and can be cleared. */
  @Test
  fun overviewViewModel_whenSavedArtworkRefreshFails_exposesAndClearsError() = runTest {
    val savedRepository =
        object : SavedArtworksRepository by ArtworkRepositoryLocal() {
          override suspend fun getSavedArtworks(): List<Artwork> = throw AppException.Network()
        }
    val viewModel = OverviewViewModel(ArtworkRepositoryLocal(), savedRepository)

    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.saveErrorMessageRes)

    viewModel.clearSaveErrorMessage()

    assertEquals(null, viewModel.uiState.value.saveErrorMessageRes)
  }

  /** Verifies the local repository's invalid-ID error rolls back a failed save. */
  @Test
  fun overviewViewModel_whenSavingArtworkWithInvalidId_rollsBackAndExposesError() = runTest {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh").copy(uid = "")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository)

    advanceUntilIdle()
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(artwork.uid !in viewModel.uiState.value.savedArtworkIds)
    assertEquals(R.string.error_invalid_input, viewModel.uiState.value.saveErrorMessageRes)

    viewModel.clearSaveErrorMessage()

    assertEquals(null, viewModel.uiState.value.saveErrorMessageRes)
  }

  /** Verifies the local repository's not-found error rolls back a failed removal. */
  @Test
  fun overviewViewModel_whenRemovingMissingArtwork_rollsBackAndExposesError() = runTest {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    repository.saveArtwork(artwork)
    val viewModel = OverviewViewModel(repository, repository)

    advanceUntilIdle()
    repository.removeArtworkFromSaved(artwork.uid)
    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertTrue(artwork.uid in viewModel.uiState.value.savedArtworkIds)
    assertEquals(R.string.error_not_found, viewModel.uiState.value.saveErrorMessageRes)
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
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = error("Artwork provider failure")
        }
    val viewModel = OverviewViewModel(failing, ArtworkRepositoryLocal())

    advanceUntilIdle()
    viewModel.clearErrorMessage()

    assertEquals(null, viewModel.uiState.value.errorMessageRes)
  }

  @Test
  fun applyFilter_updatesDisplayedArtworks_andClearRestoresAll() = runTest {
    val first = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val second = artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer")
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = listOf(first, second)
        }
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal())

    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(artists = setOf("Johannes Vermeer")))

    assertEquals(listOf(second), viewModel.uiState.value.artworks)
    assertEquals(
        ArtworkFilter(artists = setOf("Johannes Vermeer")),
        viewModel.uiState.value.activeFilter,
    )

    viewModel.applyFilter(ArtworkFilter())

    assertEquals(listOf(first, second), viewModel.uiState.value.artworks)
  }

  /** Verifies that the factory refuses to create a ViewModel of another class. */
  @Test
  fun provideFactory_forUnknownViewModelClass_throws() {
    val repository = ArtworkRepositoryLocal()
    val factory = OverviewViewModel.provideFactory(repository, repository)

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

/**
 * Builds a repository whose nearby-artworks query always fails.
 *
 * @param failure exception thrown by the query
 * @return the failing repository
 * @author Timz3rr
 */
internal fun repositoryFailingWith(failure: Exception): WikiDataRepository =
    object : WikiDataRepository by ArtworkRepositoryLocal() {
      override suspend fun getArtworksNearLocation(
          searchArea: SearchArea,
          typeValues: List<ArtworkType>,
          limit: Int,
      ): List<Artwork> = throw failure
    }
