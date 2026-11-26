// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkCandidate
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
import com.artista.artista.model.walking.OFF_ROUTE_UPDATES_REQUIRED
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.resources.C
import com.google.android.gms.maps.model.LatLng
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
  fun selectArtwork_whenNoArtworkSelected_thenStoresSelectionWithoutRoute() = runTest {
    val artwork = artworkWithLocation("selected")
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )

    viewModel.selectArtwork(artwork)

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertEquals(0, repository.callCount)
  }

  @Test
  fun selectArtwork_whenSameArtworkIsRouteDestination_thenKeepsSelectionAndRoute() = runTest {
    val artwork = artworkWithLocation("selected")
    val viewModel = createViewModel(FakeLocationProvider())
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    viewModel.selectArtwork(artwork)

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertTrue(viewModel.routeUiState is RouteUiState.Success)
  }

  @Test
  fun selectArtwork_whenSameArtworkSelectedAgainWhileIdle_thenClearsSelection() = runTest {
    val artwork = artworkWithLocation("selected")
    val viewModel = createViewModel(FakeLocationProvider())
    viewModel.selectArtwork(artwork)

    viewModel.selectArtwork(artwork)

    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun selectArtwork_whenRouteLoadingForSameArtwork_thenKeepsRequest() = runTest {
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
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)

    viewModel.selectArtwork(artwork)
    repository.release(0)
    advanceUntilIdle()

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Success(repository.route, artwork), viewModel.routeUiState)
    assertEquals(1, repository.callCount)
  }

  @Test
  fun selectArtwork_whenAnotherArtworkSelected_thenReplacesSelectionAndClearsRoute() = runTest {
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
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    viewModel.loadRouteTo(first)
    advanceUntilIdle()

    viewModel.selectArtwork(second)

    assertEquals(second, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertNull(viewModel.routeProgressUiState)
    assertEquals(1, repository.callCount)
  }

  @Test
  fun selectArtwork_whenRouteLoadingForAnotherArtwork_thenCancelsRequest() = runTest {
    val first = artworkWithLocation("first")
    val second = artworkWithLocation("second")
    val repository = FakeWalkingRouteRepository(blockCalls = true)
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

    viewModel.selectArtwork(second)
    advanceUntilIdle()

    assertEquals(second, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertEquals(1, repository.callCount)

    // Release the parked first request. It must have been cancelled, otherwise it would land
    // after the selection changed and flip the card back to the first artwork.
    repository.release(0)
    advanceUntilIdle()

    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertEquals(second, viewModel.uiState.selectedArtwork)
  }

  @Test
  fun selectArtwork_whenRouteErrorShown_thenClearsErrorAndSelects() = runTest {
    val first = artworkWithLocation("first")
    val second = artworkWithLocation("second")
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(first, second)),
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(exception = AppException.Network()),
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    viewModel.loadRouteTo(first)
    advanceUntilIdle()
    assertTrue(viewModel.routeUiState is RouteUiState.Error)

    viewModel.selectArtwork(second)

    assertEquals(second, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun loadRouteTo_whenCalled_thenSelectsArtwork() = runTest {
    val artwork = artworkWithLocation("destination")
    val viewModel = createViewModel(FakeLocationProvider())
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))

    viewModel.loadRouteTo(artwork)

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
  }

  @Test
  fun navigateToSelectedArtwork_whenSelectionExists_thenLoadsRoute() = runTest {
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
    viewModel.selectArtwork(artwork)

    viewModel.navigateToSelectedArtwork()
    advanceUntilIdle()

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Success(repository.route, artwork), viewModel.routeUiState)
    assertEquals(1, repository.callCount)
  }

  @Test
  fun navigateToSelectedArtwork_whenNoSelection_thenDoesNothing() = runTest {
    val repository = FakeWalkingRouteRepository()
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(emptyList()),
            EmptySavedArtworksRepository(),
            repository,
            FakeLocationProvider(),
        )

    viewModel.navigateToSelectedArtwork()
    advanceUntilIdle()

    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertEquals(0, repository.callCount)
  }

  @Test
  fun clearRoute_whenArtworkSelected_thenClearsSelection() = runTest {
    val artwork = artworkWithLocation("selected")
    val viewModel = createViewModel(FakeLocationProvider())
    viewModel.selectArtwork(artwork)

    viewModel.clearRoute()

    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun dismissSelectedArtwork_whenIdle_thenClearsOnlyTheSelection() = runTest {
    val artwork = artworkWithLocation("selected")
    val viewModel = createViewModel(FakeLocationProvider())
    viewModel.selectArtwork(artwork)

    viewModel.dismissSelectedArtwork()

    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun dismissSelectedArtwork_whenRouteActive_thenKeepsSelectionAndRoute() = runTest {
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

    viewModel.dismissSelectedArtwork()

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Success(repository.route, artwork), viewModel.routeUiState)
  }

  @Test
  fun dismissSelectedArtwork_whenRouteLoading_thenKeepsSelectionAndRequest() = runTest {
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
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)

    viewModel.dismissSelectedArtwork()
    repository.release(0)
    advanceUntilIdle()

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Success(repository.route, artwork), viewModel.routeUiState)
    assertEquals(1, repository.callCount)
  }

  @Test
  fun dismissSelectedArtwork_whenRouteErrorShown_thenClearsSelectionAndError() = runTest {
    val artwork = artworkWithLocation("destination")
    val viewModel =
        MapViewModel(
            FakeWikiDataRepository(listOf(artwork)),
            EmptySavedArtworksRepository(),
            FakeWalkingRouteRepository(exception = AppException.Network()),
            FakeLocationProvider(),
        )
    viewModel.updateUserLocation(UserLocation(46.52, 6.63))
    viewModel.loadRouteTo(artwork)
    advanceUntilIdle()

    viewModel.dismissSelectedArtwork()

    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun dismissSelectedArtwork_whenLocationUnavailable_thenClearsSelectionAndState() = runTest {
    val artwork = artworkWithLocation("destination")
    val viewModel = createViewModel(FakeLocationProvider())
    viewModel.loadRouteTo(artwork)
    viewModel.setLocationError(AppException.UserLocationError())
    assertEquals(RouteUiState.LocationUnavailable, viewModel.routeUiState)

    viewModel.dismissSelectedArtwork()

    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  @Test
  fun clearRoute_whenRouteActive_thenStopsUpdatesAndClearsProgressAndSelection() = runTest {
    val provider = FakeLocationProvider()
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.updateUserLocation(UserLocation(46.0, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    viewModel.startLocationUpdates()
    advanceUntilIdle()
    assertEquals(primaryRouteDestination, viewModel.uiState.selectedArtwork)
    assertNotNull(viewModel.routeProgressUiState)

    viewModel.clearRoute()
    advanceUntilIdle()

    assertNull(viewModel.uiState.selectedArtwork)
    assertNull(viewModel.routeProgressUiState)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertEquals(0, provider.activeCollectors)
  }

  @Test
  fun retryRoute_afterError_keepsSelectedArtwork() = runTest {
    val artwork = artworkWithLocation("destination")
    val repository = FakeWalkingRouteRepository(exception = AppException.Network())
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

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertTrue(viewModel.routeUiState is RouteUiState.Error)

    viewModel.retryRoute()
    advanceUntilIdle()

    assertEquals(artwork, viewModel.uiState.selectedArtwork)
    assertEquals(2, repository.callCount)
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
  fun loadRouteTo_duringRecalculation_discardsTheLateRecalculationResponse() = runTest {
    val provider = FakeLocationProvider()
    val repository = primaryRouteRepository()
    val viewModel = createRouteViewModel(provider, repository)
    startOffRoute(viewModel, provider)
    repository.blockCalls = true
    viewModel.recalculateRoute()
    runCurrent()

    viewModel.loadRouteTo(alternateRouteDestination)
    runCurrent()
    assertEquals(RouteRecalculationUiState(), viewModel.recalculationUiStateFlow.value)

    repository.release(1)
    runCurrent()
    repository.release(0)
    runCurrent()

    assertEquals(
        RouteUiState.Success(repository.route, alternateRouteDestination),
        viewModel.routeUiState,
    )
  }

  @Test
  fun clearRoute_whenRouteLoading_thenCancelsRequestAndClearsSelection() = runTest {
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
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)
    assertEquals(artwork, viewModel.uiState.selectedArtwork)

    viewModel.clearRoute()
    repository.release(0)
    advanceUntilIdle()

    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertNull(viewModel.uiState.selectedArtwork)
    assertEquals(1, repository.callCount)
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

  @Test
  fun routeProgress_remainingShrinksAndDurationScales() = runTest {
    val viewModel = createRouteViewModel(walkingRouteRepository = primaryRouteRepository())
    viewModel.updateUserLocation(UserLocation(46.0, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    advanceUntilIdle()
    val atStart = viewModel.routeProgressUiState!!

    viewModel.updateUserLocation(UserLocation(46.0015, 6.0)) // halfway
    val halfway = viewModel.routeProgressUiState!!

    assertTrue(halfway.remainingDistanceMeters < atStart.remainingDistanceMeters)
    assertEquals(atStart.remainingDistanceMeters / 2, halfway.remainingDistanceMeters, 1.0)
    assertEquals(300.0, halfway.remainingDurationSeconds, 2.0) // half of 10 minutes
    assertEquals(46.0015, halfway.path.first().latitude, 1e-6)
  }

  @Test
  fun routeProgress_doesNotMoveBackwards() = runTest {
    val viewModel = createRouteViewModel(walkingRouteRepository = primaryRouteRepository())
    viewModel.updateUserLocation(UserLocation(46.0025, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    advanceUntilIdle()
    val ahead = viewModel.routeProgressUiState!!

    viewModel.updateUserLocation(UserLocation(46.0005, 6.0))
    assertEquals(
        ahead.remainingDistanceMeters,
        viewModel.routeProgressUiState!!.remainingDistanceMeters,
        1.0,
    )
  }

  @Test
  fun routeProgress_makesNoExtraRoutingCalls() = runTest {
    val repository = primaryRouteRepository()
    val viewModel = createRouteViewModel(walkingRouteRepository = repository)
    viewModel.updateUserLocation(UserLocation(46.0, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    advanceUntilIdle()

    viewModel.updateUserLocation(UserLocation(46.001, 6.0))
    viewModel.updateUserLocation(UserLocation(46.002, 6.0))
    assertEquals(1, repository.callCount)
  }

  @Test
  fun routeProgress_resetsOnClearRoute() = runTest {
    val viewModel = createRouteViewModel(walkingRouteRepository = primaryRouteRepository())
    viewModel.updateUserLocation(UserLocation(46.0, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    advanceUntilIdle()
    assertNotNull(viewModel.routeProgressUiState)

    viewModel.clearRoute()
    assertNull(viewModel.routeProgressUiState)
  }

  @Test
  fun locationUpdates_areForwardedToUserLocation() = runTest {
    val provider = FakeLocationProvider()
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.startLocationUpdates()
    advanceUntilIdle()

    provider.updates.tryEmit(UserLocation(46.0015, 6.0))
    advanceUntilIdle()
    assertEquals(UserLocation(46.0015, 6.0), viewModel.uiState.userLocation)
  }

  @Test
  fun startLocationUpdates_twiceCreatesOneCollector() = runTest {
    val provider = FakeLocationProvider()
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.startLocationUpdates()
    viewModel.startLocationUpdates()
    advanceUntilIdle()
    assertEquals(1, provider.activeCollectors)
  }

  @Test
  fun stopLocationUpdates_cancelsCollectionAndIgnoresLaterLocations() = runTest {
    val provider = FakeLocationProvider()
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.startLocationUpdates()
    advanceUntilIdle()

    viewModel.stopLocationUpdates()
    advanceUntilIdle()
    assertEquals(0, provider.activeCollectors)

    provider.updates.tryEmit(UserLocation(46.0015, 6.0))
    advanceUntilIdle()
    assertNull(viewModel.uiState.userLocation)
  }

  @Test
  fun clearRoute_stopsLocationUpdates() = runTest {
    val provider = FakeLocationProvider()
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.updateUserLocation(UserLocation(46.0, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    viewModel.startLocationUpdates()
    advanceUntilIdle()
    assertEquals(1, provider.activeCollectors)

    viewModel.clearRoute()
    advanceUntilIdle()
    assertEquals(0, provider.activeCollectors)
  }

  @Test
  fun locationUpdates_trimTheActiveRoute() = runTest {
    val provider = FakeLocationProvider()
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.updateUserLocation(UserLocation(46.0, 6.0))
    viewModel.loadRouteTo(primaryRouteDestination)
    viewModel.startLocationUpdates()
    advanceUntilIdle()
    val atStart = viewModel.routeProgressUiState!!

    provider.updates.tryEmit(UserLocation(46.0015, 6.0))
    advanceUntilIdle()
    assertEquals(
        atStart.remainingDistanceMeters / 2,
        viewModel.routeProgressUiState!!.remainingDistanceMeters,
        1.0,
    )
  }

  @Test
  fun locationUpdates_failureSetsErrorInsteadOfCrashing() = runTest {
    val provider = FakeLocationProvider(errorType = AppException.UserLocationError())
    val viewModel =
        createRouteViewModel(provider, walkingRouteRepository = primaryRouteRepository())
    viewModel.startLocationUpdates()
    advanceUntilIdle()
    assertNotNull(viewModel.uiState.errorMsg)
  }

  @Test
  fun offRouteDetection_afterConsecutiveOffRouteUpdates_offersRecalculationUntilBackOnRoute() =
      runTest {
        val provider = FakeLocationProvider()
        val repository = primaryRouteRepository()
        val viewModel = createRouteViewModel(provider, repository)
        startPrimaryRoute(viewModel, provider)
        val calls = repository.callCount

        emit(provider, offRouteLocationBeyondThreshold, OFF_ROUTE_UPDATES_REQUIRED - 1)
        assertFalse(viewModel.recalculationUiStateFlow.value.isAvailable)
        viewModel.recalculateRoute() // not available yet: ignored
        runCurrent()
        assertEquals(calls, repository.callCount)

        emit(provider, offRouteLocationBeyondThreshold)
        assertTrue(viewModel.recalculationUiStateFlow.value.isAvailable)

        emit(provider, backOnRouteLocation) // back on the route: hidden, and the count starts over
        assertFalse(viewModel.recalculationUiStateFlow.value.isAvailable)
        emit(provider, offRouteLocationBeyondThreshold, OFF_ROUTE_UPDATES_REQUIRED - 1)
        assertFalse(viewModel.recalculationUiStateFlow.value.isAvailable)
      }

  @Test
  fun recalculateRoute_whenRequested_keepsOldRouteWhileLoadingThenReplacesIt() = runTest {
    val provider = FakeLocationProvider()
    val repository = primaryRouteRepository()
    val viewModel = createRouteViewModel(provider, repository)
    startOffRoute(viewModel, provider)
    val oldRoute = repository.route
    repository.route = recalculatedRoute
    repository.blockCalls = true

    viewModel.recalculateRoute()
    runCurrent()

    assertTrue(viewModel.recalculationUiStateFlow.value.isLoading)
    assertEquals(RouteUiState.Success(oldRoute, primaryRouteDestination), viewModel.routeUiState)
    assertEquals(
        Coordinate(
            offRouteLocationBeyondThreshold.latitude,
            offRouteLocationBeyondThreshold.longitude,
        ),
        repository.origin,
    )

    repository.release(0)
    runCurrent()

    assertEquals(
        RouteUiState.Success(recalculatedRoute, primaryRouteDestination),
        viewModel.routeUiState,
    )
    assertEquals(
        RouteRecalculationUiState(isCoolingDown = true),
        viewModel.recalculationUiStateFlow.value,
    )
  }

  @Test
  fun recalculateRoute_whenRequestFails_keepsOldRouteAndIgnoresRequestsUntilCooldownEnds() =
      runTest {
        val provider = FakeLocationProvider()
        val repository = primaryRouteRepository()
        val viewModel = createRouteViewModel(provider, repository)
        startOffRoute(viewModel, provider)
        val calls = repository.callCount
        repository.exception =
            AppException.NotFound() // not an Error state: must fall back to GENERIC

        viewModel.recalculateRoute()
        runCurrent()
        assertEquals(
            RouteUiState.Success(repository.route, primaryRouteDestination),
            viewModel.routeUiState,
        )
        assertEquals(RouteErrorKind.GENERIC, viewModel.recalculationUiStateFlow.value.error?.kind)

        viewModel.recalculateRoute() // still cooling down: ignored
        runCurrent()
        assertEquals(calls + 1, repository.callCount)

        advanceTimeBy(RECALCULATE_COOLDOWN_MS.milliseconds)
        runCurrent()
        viewModel.recalculateRoute()
        runCurrent()
        assertEquals(calls + 2, repository.callCount)
      }

  @Test
  fun clearRoute_duringRecalculation_cancelsRequestAndResetsState() = runTest {
    val provider = FakeLocationProvider()
    val repository = primaryRouteRepository()
    val viewModel = createRouteViewModel(provider, repository)
    startOffRoute(viewModel, provider)
    repository.blockCalls = true
    viewModel.recalculateRoute()
    runCurrent()

    viewModel.clearRoute()
    repository.release(0) // a late response must not bring the route back
    runCurrent()

    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
    assertEquals(RouteRecalculationUiState(), viewModel.recalculationUiStateFlow.value)
  }

  @Test
  fun arrival_withinThresholdOfDestination_marksArrivedAndStopsTracking() = runTest {
    val provider = FakeLocationProvider()
    val viewModel = createRouteViewModel(provider, primaryRouteRepository())
    startPrimaryRoute(viewModel, provider)

    emit(provider, locationOutsideArrivalThreshold)
    assertFalse(viewModel.hasArrived.value)
    assertEquals(1, provider.activeCollectors)

    emit(provider, locationWithinArrivalThreshold)
    assertTrue(viewModel.hasArrived.value)
    assertEquals(0, provider.activeCollectors)
  }

  @Test
  fun arrival_whenRouteLoadsWhileAlreadyAtDestination_marksArrived() = runTest {
    val provider = FakeLocationProvider()
    val viewModel = createRouteViewModel(provider, primaryRouteRepository())
    viewModel.startLocationUpdates()
    runCurrent()
    viewModel.loadRouteTo(primaryRouteDestination)

    emit(provider, locationWithinArrivalThreshold)

    assertTrue(viewModel.hasArrived.value)
    assertEquals(0, provider.activeCollectors)
  }

  @Test
  fun startLocationUpdates_afterArrival_doesNotRestartTracking() = runTest {
    val provider = FakeLocationProvider()
    val viewModel = createRouteViewModel(provider, primaryRouteRepository())
    startPrimaryRoute(viewModel, provider)
    emit(provider, locationWithinArrivalThreshold)

    viewModel.startLocationUpdates()
    runCurrent()

    assertEquals(0, provider.activeCollectors)
    assertTrue(viewModel.hasArrived.value)
  }

  @Test
  fun arrival_duringRecalculation_cancelsTheRequestAndHidesTheButton() = runTest {
    val provider = FakeLocationProvider()
    val repository = primaryRouteRepository()
    val viewModel = createRouteViewModel(provider, repository)
    startOffRoute(viewModel, provider)
    val oldRoute = repository.route
    repository.route = recalculatedRoute
    repository.blockCalls = true
    viewModel.recalculateRoute()
    runCurrent()

    emit(provider, locationWithinArrivalThreshold)
    repository.release(0) // the late response must not replace the route
    runCurrent()

    assertEquals(RouteRecalculationUiState(), viewModel.recalculationUiStateFlow.value)
    assertEquals(RouteUiState.Success(oldRoute, primaryRouteDestination), viewModel.routeUiState)
  }

  @Test
  fun arrival_whenReachedWhileOffRoute_doesNotOfferRecalculation() = runTest {
    val provider = FakeLocationProvider()
    // The route ends 100 m short of the artwork, so the artwork can be reached far from the route.
    val repository =
        FakeWalkingRouteRepository(route = WalkingRoute(primaryRoutePath.take(3), 10.minutes, 222))
    val viewModel = createRouteViewModel(provider, repository)
    startPrimaryRoute(viewModel, provider)
    emit(provider, offRouteLocationBeyondThreshold, OFF_ROUTE_UPDATES_REQUIRED - 1)

    emit(
        provider,
        offRouteLocationWithinArrivalThreshold,
    )

    assertTrue(viewModel.hasArrived.value)
    assertFalse(viewModel.recalculationUiStateFlow.value.isAvailable)
  }

  @Test
  fun loadRouteTo_afterArrival_resetsArrivalAndAllowsTrackingAgain() = runTest {
    val provider = FakeLocationProvider()
    val viewModel = createRouteViewModel(provider, primaryRouteRepository())
    startPrimaryRoute(viewModel, provider)
    emit(provider, locationWithinArrivalThreshold)

    viewModel.loadRouteTo(alternateRouteDestination)
    runCurrent()
    assertFalse(viewModel.hasArrived.value)

    viewModel.startLocationUpdates()
    runCurrent()
    assertEquals(1, provider.activeCollectors)
  }

  @Test
  fun loadRouteTo_sameArtworkAfterArrival_waitsForAFreshLocationThenStartsANewRoute() = runTest {
    val provider = FakeLocationProvider()
    val repository = primaryRouteRepository()
    val viewModel = createRouteViewModel(provider, repository)
    startPrimaryRoute(viewModel, provider)
    emit(provider, locationWithinArrivalThreshold)
    val calls = repository.callCount

    viewModel.loadRouteTo(primaryRouteDestination)
    runCurrent()
    // The stored location is where the user arrived: no request until a fresh fix comes in.
    assertEquals(RouteUiState.Loading, viewModel.routeUiState)
    assertEquals(calls, repository.callCount)

    viewModel.startLocationUpdates() // what the lifecycle effect does once arrival is reset
    runCurrent()
    emit(provider, onRouteLocation) // the user has walked away
    assertFalse(viewModel.hasArrived.value)
    assertEquals(calls + 1, repository.callCount)
    assertEquals(
        RouteUiState.Success(repository.route, primaryRouteDestination),
        viewModel.routeUiState,
    )
  }

  @Test
  fun loadRouteTo_sameArtworkAfterArrival_arrivesAgainWhenStillAtDestination() = runTest {
    val provider = FakeLocationProvider()
    val viewModel = createRouteViewModel(provider, primaryRouteRepository())
    startPrimaryRoute(viewModel, provider)
    emit(provider, locationWithinArrivalThreshold)

    viewModel.loadRouteTo(primaryRouteDestination)
    viewModel.startLocationUpdates()
    runCurrent()
    emit(provider, locationWithinArrivalThreshold) // the user is still there

    assertTrue(viewModel.hasArrived.value)
  }

  @Test
  fun clearRoute_afterArrival_resetsArrival() = runTest {
    val provider = FakeLocationProvider()
    val viewModel = createRouteViewModel(provider, primaryRouteRepository())
    startPrimaryRoute(viewModel, provider)
    emit(provider, locationWithinArrivalThreshold)

    viewModel.clearRoute()

    assertFalse(viewModel.hasArrived.value)
    assertEquals(RouteUiState.Idle, viewModel.routeUiState)
  }

  // Primary route fixture: a northbound route ending at the primary destination.
  private val primaryRoutePath =
      listOf(
          Coordinate(46.000, 6.0),
          Coordinate(46.001, 6.0),
          Coordinate(46.002, 6.0),
          Coordinate(46.003, 6.0),
      )
  private val primaryRouteDestination =
      artworkWithLocation("north", latitude = 46.003, longitude = 6.0)
  private val alternateRouteDestination =
      artworkWithLocation("south", latitude = 45.999, longitude = 6.0)
  private val recalculatedRoute =
      WalkingRoute(listOf(Coordinate(46.001, 6.001), Coordinate(46.003, 6.0)), 5.minutes, 250)

  // Location checkpoints used to simulate arrival and route deviation.
  private val locationWithinArrivalThreshold =
      UserLocation(46.0028, 6.0) // ~22 m from the artwork; marks arrival
  private val locationOutsideArrivalThreshold =
      UserLocation(46.0027, 6.0) // ~33 m from the artwork; does not mark arrival
  private val offRouteLocationWithinArrivalThreshold =
      UserLocation(46.0029, 6.0003) // ~26 m from artwork, ~100 m from shortened route
  private val onRouteLocation = UserLocation(46.001, 6.0)
  private val backOnRouteLocation =
      UserLocation(46.001, 6.0004) // ~31 m from route; resets off-route tracking
  private val offRouteLocationBeyondThreshold =
      UserLocation(46.001, 6.001) // ~77 m from route; counts as off-route

  private suspend fun TestScope.emit(
      provider: FakeLocationProvider,
      location: UserLocation,
      times: Int = 1,
  ) =
      repeat(times) {
        provider.updates.emit(location)
        runCurrent()
      }

  private suspend fun TestScope.startPrimaryRoute(
      viewModel: MapViewModel,
      provider: FakeLocationProvider,
  ) {
    viewModel.startLocationUpdates()
    runCurrent()
    viewModel.loadRouteTo(primaryRouteDestination) // waits for the first fix
    emit(provider, onRouteLocation)
  }

  private suspend fun TestScope.startOffRoute(
      viewModel: MapViewModel,
      provider: FakeLocationProvider,
  ) {
    startPrimaryRoute(viewModel, provider)
    emit(provider, offRouteLocationBeyondThreshold, OFF_ROUTE_UPDATES_REQUIRED)
  }

  private fun primaryRouteRepository() =
      FakeWalkingRouteRepository(route = WalkingRoute(primaryRoutePath, 10.minutes, 333))

  private fun createRouteViewModel(
      provider: FakeLocationProvider = FakeLocationProvider(),
      walkingRouteRepository: WalkingRouteRepository = FakeWalkingRouteRepository(),
  ) =
      MapViewModel(
          wikiDataRepository = FakeWikiDataRepository(listOf()),
          savedArtworksRepository = EmptySavedArtworksRepository(),
          walkingRouteRepository = walkingRouteRepository,
          locationProvider = provider,
      )

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

    override suspend fun getSavedArtwork(uid: ArtworkId): Artwork? = null

    override suspend fun getCachedSavedArtwork(uid: ArtworkId): Artwork? = null

    override suspend fun getCachedSavedArtworks(): List<Artwork> = emptyList()
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

    override suspend fun getArtworkCandidates(
        query: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<ArtworkCandidate> {
      throw NotImplementedError("Should not be used")
    }

    override suspend fun getArtistCandidates(
        query: String,
        limit: Int,
    ): List<ArtistCandidate> {
      throw NotImplementedError("Should not be used")
    }

    override suspend fun getArtistsFromQuery(query: String, limit: Int): List<Artist> {
      throw NotImplementedError("Shouldn't be used in this test.")
    }

    override suspend fun getArtist(uid: ArtistId): Artist? {
      throw NotImplementedError("Shouldn't be used in this test.")
    }

    override suspend fun getArtwork(uid: ArtworkId): Artwork {
      return artworks.first { it.uid == uid }
    }

    override suspend fun getArtworksFromQuery(
        query: String,
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
      var route: WalkingRoute = WalkingRoute(emptyList(), 1.minutes, 1),
      var exception: AppException? = null,
      var blockCalls: Boolean = false,
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
