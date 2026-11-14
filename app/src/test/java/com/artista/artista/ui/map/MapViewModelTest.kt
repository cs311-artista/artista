// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

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
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.Location as ArtworkLocation
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.resources.C
import com.google.android.gms.maps.model.LatLng
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    val viewModel = createViewModel(FakeLocationProvider())
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
    val viewModel = createViewModel(FakeLocationProvider())
    val location = UserLocation(latitude = 46.5201, longitude = 6.6332)
    viewModel.updateUserLocation(location)

    viewModel.setLocationError(AppException.UserLocationError())

    assertEquals(location, viewModel.uiState.userLocation)
    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  /** Verifies that a successful location request stores the acquired coordinates. */
  @Test
  fun acquireLocation_withPermission_storesAcquiredLocation() = runTest {
    val viewModel = createViewModel(FakeLocationProvider())

    viewModel.acquireLocation()
    advanceUntilIdle()

    assertEquals(
        UserLocation(LOCATION.latitude, LOCATION.longitude),
        viewModel.uiState.userLocation,
    )
    assertNull(viewModel.uiState.errorMsg)
  }

  /** Verifies that a null location exposes the unavailable-location error. */
  @Test
  fun acquireLocation_withNoLocation_exposesUnavailableError() = runTest {
    val viewModel =
        createViewModel(FakeLocationProvider(errorType = AppException.UserLocationError()))

    viewModel.acquireLocation()
    advanceUntilIdle()

    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  /** Verifies that a failed location request exposes the acquisition error. */
  @Test
  fun acquireLocation_whenRequestFails_exposesAcquisitionError() = runTest {
    val viewModel =
        createViewModel(FakeLocationProvider(errorType = AppException.UserLocationError()))

    viewModel.acquireLocation()
    advanceUntilIdle()

    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  @Test
  fun updateUserLocation_loadsRemoteArtworksAroundTheUser() = runTest {
    val expected = listOf(sampleArtwork("remote"))
    val repository = FakeWikiDataRepository(expected)
    val viewModel =
        MapViewModel(
            repository,
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(),
            FakeLocationProvider(),
        )

    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(expected, viewModel.uiState.remoteArtworks)
    assertEquals(SearchArea(46.52, 6.63, 50.0), repository.searchArea)
  }

  @Test
  fun remoteArtworkFailure_exposesAnErrorWithoutClearingExistingArtworks() = runTest {
    val repository = FakeWikiDataRepository(emptyList(), shouldFail = true)
    val viewModel =
        MapViewModel(
            repository,
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(),
            FakeLocationProvider(),
        )

    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.errorMsg)
    assertEquals(emptyList<Artwork>(), viewModel.uiState.remoteArtworks)
  }

  @Test
  fun unauthenticatedSavedArtworkRead_keepsMapUsable() = runTest {
    val viewModel = createViewModel(FakeLocationProvider())

    advanceUntilIdle()

    assertEquals(emptyList<Artwork>(), viewModel.uiState.savedArtworks)
    assertNull(viewModel.uiState.errorMsg)
  }

  @Test
  fun setLocationPermissionError_correctlyExposes_LocationPermissionError() = runTest {
    val viewModel = createViewModel(FakeLocationProvider())
    assertNull(viewModel.uiState.errorMsg)

    viewModel.setLocationPermissionError(AppException.UserLocationError())

    assertEquals(R.string.error_user_location, viewModel.uiState.errorMsg)
  }

  private fun createViewModel(fakeLocationProvider: FakeLocationProvider): MapViewModel =
      MapViewModel(
          ArtworkRepositoryProvider.mockRepository,
          ArtworkRepositoryProvider.mockRepository,
          FakeWalkingRouteRepository(),
          fakeLocationProvider,
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
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    assertEquals(RouteUiState.Success(route, artwork), viewModel.routeUiState)
    assertEquals(Coordinate(46.52, 6.63), repository.origin)
    assertEquals(Coordinate(46.52, 6.64), repository.destination)
  }

  @Test
  fun loadRouteTo_withoutLocation_waitsForLocationThenLoadsRoute() = runTest {
    val artwork = artworkWithLocation("destination")
    val route = WalkingRoute(emptyList(), 12.minutes, 850)
    val repository = FakeWalkingRouteRepository(route = route)
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(emptyList()),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )

    viewModel.loadRouteTo(artwork)
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)

    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(RouteUiState.Success(route, artwork), viewModel.routeUiState)
  }

  @Test
  fun loadRouteTo_withoutLocation_thenLocationFails_exposesLocationUnavailable() = runTest {
    val failures =
        listOf<Pair<String, (MapViewModel) -> Unit>>(
            "no location returned" to { vm -> vm.acquireLocation() },
            "permission denied" to
                { vm ->
                  vm.setLocationPermissionError(AppException.UserLocationError())
                },
        )

    failures.forEach { (name, fail) ->
      val viewModel =
          createViewModel(FakeLocationProvider(errorType = AppException.UserLocationError()))

      viewModel.loadRouteTo(artworkWithLocation("destination"))
      fail(viewModel)

      advanceUntilIdle()
      assertEquals(name, RouteUiState.LocationUnavailable, viewModel.routeUiState)
    }
  }

  @Test
  fun loadRouteTo_routeServiceNotFound_exposesNoRoute() = runTest {
    val artwork = artworkWithLocation("destination")
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(exception = AppException.NotFound()),
            FakeLocationProvider(),
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
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    assertEquals(RouteUiState.NoRoute, viewModel.routeUiState)
  }

  @Test
  fun loadRouteTo_mapsRepositoryExceptionsToRouteErrors() = runTest {
    val artwork = artworkWithLocation("destination")
    val cases =
        listOf(
            Triple(AppException.Network(), RouteErrorKind.NETWORK, R.string.error_network),
            Triple(
                AppException.RateLimited(true),
                RouteErrorKind.TEMPORARY_RATE_LIMIT,
                R.string.error_rate_limited_minute,
            ),
            Triple(
                AppException.RateLimited(false),
                RouteErrorKind.DAILY_RATE_LIMIT,
                R.string.error_rate_limited_daily,
            ),
            Triple(AppException.InvalidData(), RouteErrorKind.GENERIC, R.string.error_invalid_data),
            Triple(
                AppException.Unauthorized(),
                RouteErrorKind.GENERIC,
                R.string.error_unauthorized,
            ),
            Triple(AppException.Unknown(), RouteErrorKind.GENERIC, R.string.error_unknown),
        )

    cases.forEach { (exception, expectedKind, expectedMessage) ->
      val viewModel =
          MapViewModel(
              FakeWikiDataRepository(listOf(artwork)),
              EmptySavedArtworksRepository(),
              FakeWalkingRouteRepository(exception = exception),
              FakeLocationProvider(),
          )
      viewModel.updateUserLocation(UserLocation(46.52, 6.63))

      viewModel.loadRouteTo(artwork)
      advanceUntilIdle()

      assertEquals(RouteUiState.Error(expectedKind, expectedMessage), viewModel.routeUiState)
    }
  }

  @Test
  fun loadRouteTo_afterAcquisitionFailure_exposesLocationUnavailableImmediately() = runTest {
    val artwork = artworkWithLocation("destination")
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )
    viewModel.setLocationError(AppException.UserLocationError())

    viewModel.loadRouteTo(artwork)

    assertEquals(RouteUiState.LocationUnavailable, viewModel.routeUiState)

    // A location arriving later must not start a route nobody is waiting for.
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(RouteUiState.LocationUnavailable, viewModel.routeUiState)
    assertEquals(0, repository.callCount)
  }

  @Test
  fun loadRouteTo_sameArtworkWhileRouteShown_doesNotRequestAgain() = runTest {
    val artwork = artworkWithLocation("destination")
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    assertEquals(1, repository.callCount)
    assertEquals(RouteUiState.Success(repository.route, artwork), viewModel.routeUiState)
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
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    runCurrent()
    viewModel.clearRoute()
    advanceUntilIdle()

    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun retryRoute_reloadsTheLastArtwork() = runTest {
    val artwork = artworkWithLocation("destination")
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()
    viewModel.retryRoute()
    advanceUntilIdle()

    assertEquals(2, repository.callCount)
    assertEquals(RouteUiState.Success(repository.route, artwork), viewModel.routeUiState)
  }

  @Test
  fun retryRoute_withoutPreviousArtwork_doesNothing() = runTest {
    val viewModel = createViewModel(FakeLocationProvider())

    viewModel.retryRoute()

    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun clearRoute_clearsTheArtworkUsedByRetry() = runTest {
    val artwork = artworkWithLocation("destination")
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    viewModel.clearRoute()
    viewModel.retryRoute()
    advanceUntilIdle()

    assertEquals(1, repository.callCount)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun secondRouteRequest_replacesPendingFirstRequest() = runTest {
    val first = artworkWithLocation("first")
    val second = artworkWithLocation("second")
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(first, second)),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )

    viewModel.loadRouteTo(first)
    viewModel.loadRouteTo(second)
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    advanceUntilIdle()

    assertEquals(RouteUiState.Success(repository.route, second), viewModel.routeUiState)
  }

  @Test
  fun routeBounds_containsPathUserAndDestination() {
    val destination = artworkWithLocation("destination", longitude = 6.65, latitude = 46.54)

    val bounds =
        routeBounds(
            path = listOf(Coordinate(46.53, 6.64)),
            userLocation = UserLocation(46.52, 6.63),
            destination = destination,
        )

    assertTrue(bounds!!.contains(LatLng(46.52, 6.63)))
    assertTrue(bounds.contains(LatLng(46.53, 6.64)))
    assertTrue(bounds.contains(LatLng(46.54, 6.65)))
  }

  @Test
  fun routeBounds_withoutAnyPoints_returnsNull() {
    val destination = sampleArtwork("destination")

    assertNull(routeBounds(emptyList(), null, destination))
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
            FakeLocationProvider(),
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

  private companion object {
    val LOCATION = UserLocation(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG)

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
      val route: WalkingRoute = WalkingRoute(emptyList(), 1.minutes, 1),
      private val exception: AppException? = null,
      private val blockCalls: Boolean = false,
  ) : WalkingRouteRepository {
    private val releaseSignals = mutableListOf<CompletableDeferred<Unit>>()
    var callCount = 0
    var origin: Coordinate? = null
    var destination: Coordinate? = null

    override suspend fun getWalkingRoute(
        origin: Coordinate,
        destination: Coordinate,
    ): WalkingRoute {
      callCount++
      this.origin = origin
      this.destination = destination
      if (blockCalls) {
        val releaseSignal = CompletableDeferred<Unit>()
        releaseSignals += releaseSignal
        releaseSignal.await()
      }
      exception?.let { throw it }
      return route
    }

    fun release(index: Int) {
      releaseSignals[index].complete(Unit)
    }
  }
}
