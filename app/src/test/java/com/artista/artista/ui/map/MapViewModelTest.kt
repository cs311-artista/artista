// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import android.content.ContextWrapper
import android.location.Location
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.Location as ArtworkLocation
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Tests state updates exposed by [MapViewModel].
 *
 * @author Felix Burchardt, patrickmcdan
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
    viewModel.setLocationError(AppException.UserLocationError())

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

    viewModel.setLocationError(AppException.UserLocationError())

    assertEquals(location, viewModel.uiState.userLocation)
    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  /** Verifies that a successful location request stores the acquired coordinates. */
  @Test
  fun acquireLocation_withPermission_storesAcquiredLocation() {
    val viewModel = createViewModel(locationProvider = fakeLocationProvider(LOCATION))

    viewModel.acquireLocation(ContextWrapper(null))

    assertEquals(
        UserLocation(LOCATION.latitude, LOCATION.longitude),
        viewModel.uiState.userLocation,
    )
    assertNull(viewModel.uiState.errorMsg)
  }

  /** Verifies that a null location exposes the unavailable-location error. */
  @Test
  fun acquireLocation_withNoLocation_exposesUnavailableError() {
    val viewModel = createViewModel(locationProvider = fakeLocationProvider(null))

    viewModel.acquireLocation(ContextWrapper(null))

    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  /** Verifies that a failed location request exposes the acquisition error. */
  @Test
  fun acquireLocation_whenRequestFails_exposesAcquisitionError() {
    val viewModel = createViewModel(locationProvider = fakeLocationProviderFailure())

    viewModel.acquireLocation(ContextWrapper(null))

    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  @Test
  fun updateUserLocation_loadsRemoteArtworksAroundTheUser() = runTest {
    val expected = listOf(sampleArtwork("remote"))
    val repository = FakeWikiDataRepository(expected)
    val viewModel =
        MapViewModel(repository, EmptySavedArtworksRepository(), FakeWalkingRouteRepository())

    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(expected, viewModel.uiState.remoteArtworks)
    assertEquals(SearchArea(46.52, 6.63, 50.0), repository.searchArea)
  }

  @Test
  fun remoteArtworkFailure_exposesAnErrorWithoutClearingExistingArtworks() = runTest {
    val repository = FakeWikiDataRepository(emptyList(), shouldFail = true)
    val viewModel =
        MapViewModel(repository, EmptySavedArtworksRepository(), FakeWalkingRouteRepository())

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
            FakeWalkingRouteRepository(),
        )

    advanceUntilIdle()

    assertEquals(emptyList<Artwork>(), viewModel.uiState.savedArtworks)
    assertNull(viewModel.uiState.errorMsg)
  }

  private fun createViewModel(locationProvider: LocationProvider? = null): MapViewModel =
      MapViewModel(
          ArtworkRepositoryProvider.mockRepository,
          ArtworkRepositoryProvider.mockRepository,
          FakeWalkingRouteRepository(),
          locationProvider,
      )

  @Test
  fun loadRouteTo_success_exposesRouteAndArtwork() = runTest {
    val artwork = artworkWithLocation("destination")
    val route = WalkingRoute(listOf(Coordinate(46.52, 6.63)), 12.minutes, 850)
    val repository = FakeWalkingRouteRepository(route = route)
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    assertEquals(RouteUiState.Success(route, artwork), viewModel.routeUiState)
    assertEquals(Coordinate(46.52, 6.63), repository.origin)
    assertEquals(Coordinate(46.52, 6.64), repository.destination)
  }

  @Test
  fun loadRouteTo_withoutLocation_exposesLocationUnavailable() = runTest {
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(emptyList()),
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(),
        )

    viewModel.loadRouteTo(sampleArtwork("destination"))
    advanceUntilIdle()

    assertEquals(RouteUiState.LocationUnavailable, viewModel.routeUiState)
  }

  @Test
  fun loadRouteTo_routeServiceNotFound_exposesNoRoute() = runTest {
    val artwork = artworkWithLocation("destination")
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(exception = AppException.NotFound()),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    assertEquals(RouteUiState.NoRoute, viewModel.routeUiState)
  }

  @Test
  fun loadRouteTo_artworkWithoutLocation_exposesNoRoute() = runTest {
    val artwork = sampleArtwork("destination")
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    assertEquals(RouteUiState.NoRoute, viewModel.routeUiState)
  }

  @Test
  fun loadRouteTo_mapsRepositoryExceptionsToRouteErrors() = runTest {
    val artwork = artworkWithLocation("destination")
    val exceptions =
        listOf(
            AppException.Network() to RouteErrorKind.NETWORK,
            AppException.RateLimited(true) to RouteErrorKind.TEMPORARY_RATE_LIMIT,
            AppException.RateLimited(false) to RouteErrorKind.DAILY_RATE_LIMIT,
            AppException.InvalidData() to RouteErrorKind.GENERIC,
            AppException.Unauthorized() to RouteErrorKind.GENERIC,
            AppException.Unknown() to RouteErrorKind.GENERIC,
        )

    exceptions.forEach { (exception, expectedKind) ->
      val viewModel =
          MapViewModel(
              FakeWikiDataRepository(listOf(artwork)),
              EmptySavedArtworksRepository(),
              FakeWalkingRouteRepository(exception = exception),
          )
      viewModel.updateUserLocation(UserLocation(46.52, 6.63))

      viewModel.loadRouteTo(artwork)
      advanceUntilIdle()

      assertEquals(RouteUiState.Error(expectedKind), viewModel.routeUiState)
    }
  }

  @Test
  fun clearRoute_whileLoading_returnsToIdle() = runTest {
    val artwork = artworkWithLocation("destination")
    val repository = FakeWalkingRouteRepository(blockCalls = true)
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    runCurrent()
    viewModel.clearRoute()
    advanceUntilIdle()

    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun secondRouteRequest_cancelsFirstRequest() = runTest {
    val first = artworkWithLocation("first", longitude = 6.64, latitude = 46.52)
    val second = artworkWithLocation("second", longitude = 6.65, latitude = 46.53)
    val route = WalkingRoute(emptyList(), 5.minutes, 100)
    val repository = FakeWalkingRouteRepository(blockCalls = true, route = route)
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(first, second)),
            EmptySavedArtworksRepository(),
            repository,
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(first)
    runCurrent()
    viewModel.loadRouteTo(second)
    runCurrent()
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)
    repository.release(0)
    runCurrent()
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)
    repository.release(1)
    advanceUntilIdle()

    assertEquals(RouteUiState.Success(route, second), viewModel.routeUiState)
  }

  private fun fakeLocationProvider(location: Location?): LocationProvider =
      LocationProvider { callback ->
        callback(location)
      }

  private fun fakeLocationProviderFailure(): LocationProvider = LocationProvider { callback ->
    callback(null)
  }

  private companion object {
    val LOCATION =
        Location("test").apply {
          latitude = 46.5201
          longitude = 6.6332
        }

    fun sampleArtwork(uid: ArtworkId) =
        Artwork(uid, "Artwork $uid", Artist("artist", "Artist"), null, null, null, null, null)

    fun artworkWithLocation(
        uid: ArtworkId,
        longitude: Double = 6.64,
        latitude: Double = 46.52,
    ) = sampleArtwork(uid).copy(location = ArtworkLocation(longitude, latitude, null))
  }

  private open class EmptySavedArtworksRepository : SavedArtworksRepository {
    override suspend fun isArtworkSaved(uid: ArtworkId) = false

    override suspend fun saveArtwork(artwork: Artwork) = Unit

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) = Unit

    override suspend fun getSavedArtworks() = emptyList<Artwork>()
  }

  private class FailingSavedArtworksRepository : EmptySavedArtworksRepository() {
    override suspend fun getSavedArtworks(): List<Artwork> = throw AppException.Unauthorized()
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
      if (shouldFail) throw AppException.Network()
      this.searchArea = searchArea
      return artworks
    }

    @Deprecated("Only required by the repository interface.")
    override suspend fun getPopularArtists(): List<Artist> = emptyList()

    override suspend fun getArtwork(uid: ArtworkId): Artwork {
      return artworks.first { it.uid == uid }
    }

    override suspend fun searchArtworks(
        artworkTitle: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = emptyList()
  }

  /**
   * Controls route completion per invocation so tests can observe cancellation and ordering.
   *
   * Each blocked call waits on its own gate; recorded coordinates and configured failures let tests
   * verify the exact request sent by the ViewModel.
   */
  private class FakeWalkingRouteRepository(
      private val route: WalkingRoute? = null,
      private val exception: AppException? = null,
      private val blockCalls: Boolean = false,
  ) : WalkingRouteRepository {
    private val releaseSignals = mutableListOf<CompletableDeferred<Unit>>()
    var origin: Coordinate? = null
    var destination: Coordinate? = null

    override suspend fun getWalkingRoute(
        origin: Coordinate,
        destination: Coordinate,
    ): WalkingRoute {
      this.origin = origin
      this.destination = destination
      if (blockCalls) {
        val releaseSignal = CompletableDeferred<Unit>()
        releaseSignals += releaseSignal
        releaseSignal.await()
      }
      exception?.let { throw it }
      return route ?: WalkingRoute(emptyList(), 1.minutes, 1)
    }

    fun release(index: Int) {
      releaseSignals[index].complete(Unit)
    }
  }
}
