// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.lifecycle.ViewModel
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.failingWikiDataRepository
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.map.Location
import com.artista.artista.model.map.UserLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the state exposed by [OverviewViewModel].
 *
 * @author patrickmcdan, hixeum
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
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal(), FakeLocationProvider())

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
    val viewModel =
        OverviewViewModel(
            failingWikiDataRepository(IllegalStateException("Artwork provider failure")),
            ArtworkRepositoryLocal(),
            FakeLocationProvider(),
        )

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
        OverviewViewModel(
            failingWikiDataRepository(AppException.Network()),
            ArtworkRepositoryLocal(),
            FakeLocationProvider(),
        )

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
            failingWikiDataRepository(CancellationException("Load cancelled")),
            ArtworkRepositoryLocal(),
            FakeLocationProvider(),
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
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal(), FakeLocationProvider())

    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageRes)

    viewModel.refreshUiState()
    advanceUntilIdle()

    assertEquals(null, viewModel.uiState.value.errorMessageRes)
    assertEquals(expectedArtworks, viewModel.uiState.value.artworks)
  }

  /**
   * Checks that refreshes requested while a load is running do not start a second load, so a
   * failing parallel query cannot replace the loaded artworks with an error.
   *
   * @author Timz3rr
   */
  @Test
  fun overviewViewModel_whenRefreshedWhileLoading_loadsOnlyOnce() = runTest {
    val expectedArtworks =
        listOf(artwork(name = "The Starry Night", artistName = "Vincent van Gogh"))
    val loadGate = CompletableDeferred<Unit>()
    var loadCount = 0
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            loadCount++
            if (loadCount > 1) throw AppException.Network()
            loadGate.await()
            return expectedArtworks
          }
        }
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal(), FakeLocationProvider())

    advanceUntilIdle()
    viewModel.refreshUiState()
    viewModel.refreshUiState()
    loadGate.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, loadCount)
    assertEquals(null, viewModel.uiState.value.errorMessageRes)
    assertEquals(expectedArtworks, viewModel.uiState.value.artworks)
  }

  /**
   * Checks that a refresh requested after the previous load finished starts a new load.
   *
   * @author Timz3rr
   */
  @Test
  fun overviewViewModel_whenRefreshedAfterLoadFinished_loadsAgain() = runTest {
    var loadCount = 0
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> {
            loadCount++
            return emptyList()
          }
        }
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal(), FakeLocationProvider())

    advanceUntilIdle()
    viewModel.refreshUiState()
    advanceUntilIdle()

    assertEquals(2, loadCount)
  }

  /** Verifies toggling an artwork saves and removes it while updating bookmark state. */
  @Test
  fun overviewViewModel_toggleSavedArtwork_persistsArtworkAndUpdatesState() = runTest {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

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
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

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
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

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
    val viewModel =
        OverviewViewModel(ArtworkRepositoryLocal(), savedRepository, FakeLocationProvider())

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
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

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
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

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
    val viewModel =
        OverviewViewModel(
            failingWikiDataRepository(),
            ArtworkRepositoryLocal(),
            FakeLocationProvider(),
        )

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
    val viewModel = OverviewViewModel(repository, ArtworkRepositoryLocal(), FakeLocationProvider())

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

  /** Verifies that clearing the filter restores every artwork and deactivates the filter. */
  @Test
  fun clearFilter_restoresAllArtworksAndDeactivatesFilter() = runTest {
    val first = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val second = artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer")
    val viewModel =
        OverviewViewModel(
            nearbyRepository(listOf(first, second)),
            ArtworkRepositoryLocal(),
            FakeLocationProvider(),
        )

    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(fromYear = 3000))
    assertTrue(viewModel.uiState.value.artworks.isEmpty())

    viewModel.clearFilter()

    assertEquals(listOf(first, second), viewModel.uiState.value.artworks)
    assertFalse(viewModel.uiState.value.activeFilter.isActive())
  }

  /** Verifies that artworks start as a grid and that the toggle alternates grid and list. */
  @Test
  fun toggleDisplayMode_alternatesBetweenGridAndList() = runTest {
    val repository = ArtworkRepositoryLocal()
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

    assertEquals(OverviewDisplayMode.GRID, viewModel.uiState.value.displayMode)

    viewModel.toggleDisplayMode()
    assertEquals(OverviewDisplayMode.LIST, viewModel.uiState.value.displayMode)

    viewModel.toggleDisplayMode()
    assertEquals(OverviewDisplayMode.GRID, viewModel.uiState.value.displayMode)
  }

  /** Verifies that the loaded user location is exposed in the state. */
  @Test
  fun overviewViewModel_afterLoading_exposesUserLocation() = runTest {
    val repository = ArtworkRepositoryLocal()
    val viewModel =
        OverviewViewModel(repository, repository, FakeLocationProvider(providedLocation = USER))

    advanceUntilIdle()

    assertEquals(USER, viewModel.uiState.value.userLocation)
  }

  /** Verifies that only saved artworks within the close radius are highlighted, closest first. */
  @Test
  fun overviewViewModel_closeSavedArtworks_keepsOnlyCloseOnesSortedByDistance() = runTest {
    val near = artworkAt("Near", latitudeOffset = 0.001)
    val nearest = artworkAt("Nearest", latitudeOffset = 0.0001)
    val far = artworkAt("Far", latitudeOffset = FAR_LATITUDE_OFFSET)
    val unlocated = artwork(name = "Unlocated", artistName = "Nobody")
    val savedRepository = ArtworkRepositoryLocal()
    listOf(near, nearest, far, unlocated).forEach { savedRepository.saveArtwork(it) }
    val viewModel =
        OverviewViewModel(
            ArtworkRepositoryLocal(),
            savedRepository,
            FakeLocationProvider(providedLocation = USER),
        )

    advanceUntilIdle()

    assertEquals(listOf(nearest, near), viewModel.uiState.value.closeSavedArtworks)
  }

  /** Verifies that no saved artwork is highlighted while the user location is unknown. */
  @Test
  fun overviewViewModel_whenLocationFails_highlightsNoSavedArtwork() = runTest {
    val savedRepository = ArtworkRepositoryLocal()
    savedRepository.saveArtwork(artworkAt("Near", latitudeOffset = 0.001))
    val viewModel =
        OverviewViewModel(
            ArtworkRepositoryLocal(),
            savedRepository,
            FakeLocationProvider(errorType = AppException.Network()),
        )

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.closeSavedArtworks.isEmpty())
  }

  /** Verifies that the active filter also applies to the highlighted saved artworks. */
  @Test
  fun overviewViewModel_closeSavedArtworks_followActiveFilter() = runTest {
    val vanGogh = artworkAt("Starry", latitudeOffset = 0.001, artistName = "Vincent van Gogh")
    val vermeer = artworkAt("Pearl", latitudeOffset = 0.001, artistName = "Johannes Vermeer")
    val savedRepository = ArtworkRepositoryLocal()
    savedRepository.saveArtwork(vanGogh)
    savedRepository.saveArtwork(vermeer)
    val viewModel =
        OverviewViewModel(
            ArtworkRepositoryLocal(),
            savedRepository,
            FakeLocationProvider(providedLocation = USER),
        )

    advanceUntilIdle()
    viewModel.applyFilter(ArtworkFilter(artists = setOf("Johannes Vermeer")))

    assertEquals(listOf(vermeer), viewModel.uiState.value.closeSavedArtworks)

    viewModel.clearFilter()

    assertEquals(2, viewModel.uiState.value.closeSavedArtworks.size)
  }

  /** Verifies that an artwork saved elsewhere appears among the close ones after a refresh. */
  @Test
  fun overviewViewModel_refreshSavedArtworkIds_updatesCloseSavedArtworks() = runTest {
    val close = artworkAt("Near", latitudeOffset = 0.001)
    val repository = ArtworkRepositoryLocal()
    val viewModel =
        OverviewViewModel(repository, repository, FakeLocationProvider(providedLocation = USER))

    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.closeSavedArtworks.isEmpty())

    repository.saveArtwork(close)
    viewModel.refreshSavedArtworkIds()
    advanceUntilIdle()

    assertEquals(listOf(close), viewModel.uiState.value.closeSavedArtworks)
  }

  /** Verifies that saving a close artwork from Overview highlights it right away. */
  @Test
  fun toggleSavedArtwork_whenSavingCloseArtwork_addsItToCloseSavedArtworks() = runTest {
    val close = artworkAt("Near", latitudeOffset = 0.001)
    val repository = ArtworkRepositoryLocal()
    val viewModel =
        OverviewViewModel(repository, repository, FakeLocationProvider(providedLocation = USER))
    advanceUntilIdle()

    viewModel.toggleSavedArtwork(close)

    // Checked before the repository write completes: the row follows the bookmark optimistically.
    assertEquals(listOf(close), viewModel.uiState.value.closeSavedArtworks)
    advanceUntilIdle()
    assertEquals(listOf(close), viewModel.uiState.value.closeSavedArtworks)
  }

  /** Verifies that saving a far artwork from Overview does not highlight it. */
  @Test
  fun toggleSavedArtwork_whenSavingFarArtwork_keepsCloseSavedArtworksEmpty() = runTest {
    val far = artworkAt("Far", latitudeOffset = FAR_LATITUDE_OFFSET)
    val repository = ArtworkRepositoryLocal()
    val viewModel =
        OverviewViewModel(repository, repository, FakeLocationProvider(providedLocation = USER))
    advanceUntilIdle()

    viewModel.toggleSavedArtwork(far)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.closeSavedArtworks.isEmpty())
  }

  /** Verifies that unsaving a close artwork removes it from the highlighted ones right away. */
  @Test
  fun toggleSavedArtwork_whenUnsavingCloseArtwork_removesItFromCloseSavedArtworks() = runTest {
    val close = artworkAt("Near", latitudeOffset = 0.001)
    val repository = ArtworkRepositoryLocal()
    repository.saveArtwork(close)
    val viewModel =
        OverviewViewModel(repository, repository, FakeLocationProvider(providedLocation = USER))
    advanceUntilIdle()
    assertEquals(listOf(close), viewModel.uiState.value.closeSavedArtworks)

    viewModel.toggleSavedArtwork(close)

    assertTrue(viewModel.uiState.value.closeSavedArtworks.isEmpty())
  }

  /** Verifies that a failed save of a close artwork removes it again from the highlighted ones. */
  @Test
  fun toggleSavedArtwork_whenSavingCloseArtworkFails_rollsBackCloseSavedArtworks() = runTest {
    val close = artworkAt("Near", latitudeOffset = 0.001)
    val repository = ArtworkRepositoryLocal()
    val failingSaves =
        object : SavedArtworksRepository by repository {
          override suspend fun saveArtwork(artwork: Artwork) = throw AppException.Network()
        }
    val viewModel =
        OverviewViewModel(repository, failingSaves, FakeLocationProvider(providedLocation = USER))
    advanceUntilIdle()

    viewModel.toggleSavedArtwork(close)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.closeSavedArtworks.isEmpty())
    assertEquals(R.string.error_network, viewModel.uiState.value.saveErrorMessageRes)
  }

  /** Verifies that the factory refuses to create a ViewModel of another class. */
  @Test
  fun provideFactory_forUnknownViewModelClass_throws() {
    val repository = ArtworkRepositoryLocal()
    val savedRepository = ArtworkRepositoryLocal()
    val factory =
        OverviewViewModel.provideFactory(repository, savedRepository, FakeLocationProvider())

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  /** ViewModel unrelated to the overview screen, used to trigger the factory's error. */
  private class OtherViewModel : ViewModel()

  private fun nearbyRepository(artworks: List<Artwork>): WikiDataRepository =
      object : WikiDataRepository by ArtworkRepositoryLocal() {
        override suspend fun getArtworksNearLocation(
            searchArea: SearchArea,
            typeValues: List<ArtworkType>,
            limit: Int,
        ): List<Artwork> = artworks
      }

  /** Builds an artwork located north of [USER]; 0.001 degree of latitude is about 111 meters. */
  private fun artworkAt(
      name: String,
      latitudeOffset: Double,
      artistName: String = "Artist",
  ): Artwork =
      artwork(name = name, artistName = artistName)
          .copy(
              location =
                  Location(
                      longitude = USER.longitude,
                      latitude = USER.latitude + latitudeOffset,
                      name = name,
                  )
          )

  private companion object {
    val USER = UserLocation(latitude = 46.5191, longitude = 6.5668)

    private const val METERS_PER_DEGREE_OF_LATITUDE = 111_000.0

    /**
     * Latitude offset placing an artwork at twice the close radius, so it stays far whatever the
     * radius is set to.
     */
    val FAR_LATITUDE_OFFSET =
        2 * OverviewViewModel.CLOSE_SAVED_ARTWORK_RADIUS_METERS / METERS_PER_DEGREE_OF_LATITUDE
  }

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
