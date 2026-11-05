// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import android.location.Location
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests state updates exposed by [MapViewModel].
 *
 * @author Felix Burchardt
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MapViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * Verifies that a location update exposes the device coordinates.
   *
   * @author Felix Burchardt
   */
  @Test
  fun updateUserLocation_storesLocationAndClearsError() {
    val viewModel = createViewModel()
    viewModel.setLocationError(R.string.map_location_acquisition_error)

    val location = UserLocation(latitude = 46.5201, longitude = 6.6332)
    viewModel.updateUserLocation(location)

    assertEquals(location, viewModel.uiState.userLocation)
    assertNull(viewModel.uiState.errorMsg)
  }

  /**
   * Verifies that location errors are exposed without discarding a known location.
   *
   * @author Felix Burchardt
   */
  @Test
  fun setLocationError_storesMessageAndPreservesLocation() {
    val viewModel = createViewModel()
    val location = UserLocation(latitude = 46.5201, longitude = 6.6332)
    viewModel.updateUserLocation(location)

    viewModel.setLocationError(R.string.map_location_unavailable_error)

    assertEquals(location, viewModel.uiState.userLocation)
    assertEquals(R.string.map_location_unavailable_error, viewModel.uiState.errorMsg)
  }

  /** Verifies that a successful location request stores the acquired coordinates. */
  @Test
  fun acquireLocation_withPermission_storesAcquiredLocation() {
    val viewModel = createViewModel()
    val locationClient = fakeLocationClient(LOCATION)

    viewModel.acquireLocation(locationClient)

    assertEquals(
        UserLocation(LOCATION.latitude, LOCATION.longitude),
        viewModel.uiState.userLocation,
    )
    assertNull(viewModel.uiState.errorMsg)
  }

  /** Verifies that a null location exposes the unavailable-location error. */
  @Test
  fun acquireLocation_withNoLocation_exposesUnavailableError() {
    val viewModel = createViewModel()
    val locationClient = fakeLocationClient(null)

    viewModel.acquireLocation(locationClient)

    assertEquals(R.string.map_location_unavailable_error, viewModel.uiState.errorMsg)
  }

  /** Verifies that a failed location request exposes the acquisition error. */
  @Test
  fun acquireLocation_whenRequestFails_exposesAcquisitionError() {
    val viewModel = createViewModel()
    val locationClient = fakeLocationClientFailure()

    viewModel.acquireLocation(locationClient)

    assertEquals(R.string.map_location_acquisition_error, viewModel.uiState.errorMsg)
  }

  @Test
  fun updateUserLocation_loadsRemoteArtworksAroundTheUser() = runTest {
    val expected = listOf(sampleArtwork("remote"))
    val repository = FakeWikiDataRepository(expected)
    val viewModel = MapViewModel(repository, EmptySavedArtworksRepository())

    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(expected, viewModel.uiState.remoteArtworks)
    assertEquals(SearchArea(46.52, 6.63, 50.0), repository.searchArea)
  }

  @Test
  fun remoteArtworkFailure_exposesAnErrorWithoutClearingExistingArtworks() = runTest {
    val repository = FakeWikiDataRepository(emptyList(), shouldFail = true)
    val viewModel = MapViewModel(repository, EmptySavedArtworksRepository())

    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.errorMsg)
    assertEquals(emptyList<Artwork>(), viewModel.uiState.remoteArtworks)
  }

  @Test
  fun unauthenticatedSavedArtworkRead_keepsMapUsable() = runTest {
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(emptyList()),
            FailingSavedArtworksRepository(),
        )

    advanceUntilIdle()

    assertEquals(emptyList<Artwork>(), viewModel.uiState.savedArtworks)
    assertNull(viewModel.uiState.errorMsg)
  }

  private fun createViewModel(): MapViewModel =
      MapViewModel(
          ArtworkRepositoryProvider.mockRepository,
          ArtworkRepositoryProvider.mockRepository,
      )

  private fun fakeLocationClient(location: Location?): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          FusedLocationProviderClient::class.java.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
      ) { _, method, _ ->
        if (method.name == "getCurrentLocation") Tasks.forResult(location)
        else throw UnsupportedOperationException(method.name)
      } as FusedLocationProviderClient

  private fun fakeLocationClientFailure(): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          FusedLocationProviderClient::class.java.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
      ) { _, method, _ ->
        if (method.name == "getCurrentLocation") {
          Tasks.forException<Location>(LOCATION_EXCEPTION)
        } else {
          throw UnsupportedOperationException(method.name)
        }
      } as FusedLocationProviderClient

  private companion object {
    val LOCATION =
        Location("test").apply {
          latitude = 46.5201
          longitude = 6.6332
        }
    val LOCATION_EXCEPTION = IllegalStateException("location failure")

    fun sampleArtwork(uid: ArtworkId) =
        Artwork(uid, "Artwork $uid", Artist("artist", "Artist"), null, null, null, null, null)
  }

  private open class EmptySavedArtworksRepository : SavedArtworksRepository {
    override suspend fun isArtworkSaved(uid: ArtworkId) = false
    override suspend fun saveArtwork(artwork: Artwork) = Unit
    override suspend fun removeArtworkFromSaved(uid: ArtworkId) = Unit
    override suspend fun getSavedArtworks() = emptyList<Artwork>()
  }

  private class FailingSavedArtworksRepository : EmptySavedArtworksRepository() {
    override suspend fun getSavedArtworks(): List<Artwork> =
        throw com.artista.artista.model.error.AppException.Unauthorized()
  }

  private class FakeWikiDataRepository(
      private val artworks: List<Artwork>,
      private val shouldFail: Boolean = false,
  ) : WikiDataRepository {
    var searchArea: SearchArea? = null

    override suspend fun getArtworksNearLocation(
        searchArea: SearchArea,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> {
      if (shouldFail) throw com.artista.artista.model.error.AppException.Network()
      this.searchArea = searchArea
      return artworks
    }

    @Deprecated("Only required by the repository interface.")
    override suspend fun getPopularArtists(): List<Artist> = emptyList()
    override suspend fun getArtwork(uid: ArtworkId): Artwork = artworks.first { it.uid == uid }
    override suspend fun searchArtworks(
        artworkTitle: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = emptyList()
  }
}
