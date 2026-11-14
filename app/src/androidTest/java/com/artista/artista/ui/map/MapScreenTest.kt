// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import android.location.Location
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.Location as ArtworkLocation
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.resources.C
import com.artista.artista.ui.location.USER_LOCATION_PERMISSIONS
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.theme.ArtistaTheme
import com.google.android.gms.maps.model.LatLng
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the map screen's connected user interface.
 *
 * @author Felix Burchardt
 */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  /** Grants location permissions before the screen starts its location request. */
  @Before
  fun grantLocationPermissions() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    USER_LOCATION_PERMISSIONS.forEach { permission ->
      instrumentation.uiAutomation.grantRuntimePermission(
          instrumentation.targetContext.packageName,
          permission,
      )
    }
  }

  /** Releases shell permissions after each test. */
  @After
  fun releaseShellPermissions() {
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /** Verifies that the real Google Map surface is displayed. */
  @Test
  fun mapScreen_displaysMap() {
    setMapScreenContent()

    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
  }

  /** Verifies that the map screen displays its bottom navigation bar. */
  @Test
  fun mapScreen_displaysBottomNavigationBar() {
    setMapScreenContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /** Verifies that the map preview renders its map and navigation. */
  @Test
  fun mapScreenPreview_displaysMapAndNavigation() {
    composeTestRule.setContent { MapScreenPreview() }

    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /** Verifies that the map get the device's current location properly from the ViewModel. */
  @Test
  fun mapScreen_getsLocationProperlyFromViewModel() {
    val mapViewModel =
        MapViewModel(
            ArtworkRepositoryProvider.mockRepository,
            ArtworkRepositoryProvider.mockRepository,
            WalkingRouteRepositoryRemote(),
            FakeLocationProvider(false),
        )
    composeTestRule.setContent { ArtistaTheme { MapScreen(mapViewModel = mapViewModel) } }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    assertEquals(
        UserLocation(MOCK_LATITUDE, MOCK_LONGITUDE),
        mapViewModel.uiState.userLocation,
    )
  }

  /** Verifies that the marker is not positioned when the mocked device has no location. */
  @Test
  fun mapScreen_doesNotDisplayMarkerWhenDeviceLocationIsUnavailable() {
    val mapViewModel =
        MapViewModel(
            ArtworkRepositoryProvider.mockRepository,
            ArtworkRepositoryProvider.mockRepository,
            WalkingRouteRepositoryRemote(),
            FakeLocationProvider(true),
        )
    composeTestRule.setContent { ArtistaTheme { MapScreen(mapViewModel = mapViewModel) } }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    assertEquals(null, mapViewModel.uiState.userLocation)
  }

  @Test
  fun routeBounds_includesPathUserLocationAndDestination() {
    val path = listOf(Coordinate(46.0, 6.0), Coordinate(46.5, 6.5))
    val user = UserLocation(45.5, 5.5) // beyond the path's south-west corner
    val destination =
        routeArtwork()
            .copy(
                location = ArtworkLocation(longitude = 7.0, latitude = 47.0, null)
            ) // beyond the path's north-east corner

    val bounds = routeBounds(path, user, destination)

    assertEquals(LatLng(45.5, 5.5), bounds?.southwest)
    assertEquals(LatLng(47.0, 7.0), bounds?.northeast)
  }

  @Test
  fun routeBounds_returnsNull_whenNoPointsAvailable() {
    // This is the case where the LaunchedEffect returns early instead of animating the camera.
    assertNull(routeBounds(emptyList(), userLocation = null, destination = routeArtwork()))
  }

  @Test
  fun routeOverlay_success_displaysSummaryAndCloseCallsCallback() {
    var cleared = false
    val artwork = routeArtwork()
    val route = WalkingRoute(listOf(Coordinate(MOCK_LATITUDE, MOCK_LONGITUDE)), 12.minutes, 850)

    setRouteOverlay(RouteUiState.Success(route, artwork), onClearRoute = { cleared = true })

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithText("Artwork destination").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY)
        .assertTextEquals("12 min · 850 m")
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON).performClick()
    assertEquals(true, cleared)
  }

  @Test
  fun routeOverlay_loading_displaysProgressMessage() {
    setRouteOverlay(RouteUiState.Loading)

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText("Finding a walking route...").assertIsDisplayed()
  }

  @Test
  fun routeOverlay_noRoute_displaysInformationalMessage() {
    setRouteOverlay(RouteUiState.NoRoute)

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_NO_ROUTE).assertIsDisplayed()
    composeTestRule.onNodeWithText("No route found.").assertIsDisplayed()
  }

  @Test
  fun routeOverlay_noRoute_closeCallsCallback() {
    var cleared = false

    setRouteOverlay(RouteUiState.NoRoute, onClearRoute = { cleared = true })

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON).performClick()
    assertEquals(true, cleared)
  }

  @Test
  fun routeOverlay_locationUnavailable_displaysMessage() {
    setRouteOverlay(RouteUiState.LocationUnavailable)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_LOCATION_UNAVAILABLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            "We're unable to acquire your location. Please make sure device location is turned on."
        )
        .assertIsDisplayed()
  }

  @Test
  fun routeOverlay_error_withRetry_callsRetryCallback() {
    var retried = false

    setRouteOverlay(
        RouteUiState.Error(RouteErrorKind.NETWORK, R.string.error_network),
        onRetryRoute = { retried = true },
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_RETRY_BUTTON).performClick()
    assertEquals(true, retried)
  }

  @Test
  fun routeOverlay_error_withoutRetry_hidesRetryButton() {
    setRouteOverlay(
        RouteUiState.Error(RouteErrorKind.DAILY_RATE_LIMIT, R.string.error_rate_limited_daily)
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_RETRY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun routeOverlay_error_temporaryRateLimit_showsRetryButton() {
    setRouteOverlay(
        RouteUiState.Error(RouteErrorKind.TEMPORARY_RATE_LIMIT, R.string.error_rate_limited_minute)
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun routeOverlay_idle_displaysNothing() {
    setRouteOverlay(RouteUiState.Idle)

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY_CARD).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_LOADING).assertDoesNotExist()
  }

  /**
   * Verifies that [MapScreen] composes the route polyline and destination marker without crashing.
   * The polyline itself can't be asserted because map content isn't in the semantics tree.
   */
  @Test
  fun mapScreen_withSuccessfulRoute_composesPolylineWithoutCrashing() {
    val route = WalkingRoute(listOf(Coordinate(MOCK_LATITUDE, MOCK_LONGITUDE)), 12.minutes, 850)
    val destination = routeArtwork().copy(location = ArtworkLocation(46.523, 6.636, "Museum"))
    val walkingRepository =
        Proxy.newProxyInstance(
            WalkingRouteRepository::class.java.classLoader,
            arrayOf(WalkingRouteRepository::class.java),
        ) { _, _, _ ->
          route
        } as WalkingRouteRepository
    lateinit var viewModel: MapViewModel
    composeTestRule.runOnUiThread {
      viewModel =
          MapViewModel(
              ArtworkRepositoryProvider.mockRepository,
              ArtworkRepositoryProvider.mockRepository,
              walkingRepository,
              FakeLocationProvider(false),
          )
      viewModel.updateUserLocation(UserLocation(MOCK_LATITUDE, MOCK_LONGITUDE))
      viewModel.loadRouteTo(destination)
    }
    composeTestRule.waitUntil(10_000) { viewModel.routeUiState is RouteUiState.Success }

    composeTestRule.setContent { ArtistaTheme { MapScreen(mapViewModel = viewModel) } }

    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY)
        .assertTextEquals("12 min · 850 m")
  }

  private fun setMapScreenContent() {
    composeTestRule.setContent {
      ArtistaTheme {
        MapScreen(
            mapViewModel =
                viewModel(
                    factory =
                        MapViewModel.provideFactory(
                            ArtworkRepositoryProvider.mockRepository,
                            ArtworkRepositoryProvider.mockRepository,
                            WalkingRouteRepositoryRemote(),
                            FakeLocationProvider(false),
                        )
                )
        )
      }
    }
  }

  private fun setRouteOverlay(
      routeUiState: RouteUiState,
      onClearRoute: () -> Unit = {},
      onRetryRoute: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ArtistaTheme {
        RouteOverlay(
            routeUiState = routeUiState,
            onClearRoute = onClearRoute,
            onRetryRoute = onRetryRoute,
        )
      }
    }
  }

  private fun routeArtwork(): Artwork =
      Artwork(
          uid = "destination",
          name = "Artwork destination",
          artist = Artist("artist", "Artist"),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )

  private companion object {
    const val MOCK_LATITUDE = C.Constants.DEFAULT_MAP_LAT
    const val MOCK_LONGITUDE = C.Constants.DEFAULT_MAP_LONG
  }

  private fun mockDeviceLocation(): Location =
      Location("mock").apply {
        latitude = MOCK_LATITUDE
        longitude = MOCK_LONGITUDE
      }

  /** A fake implementation of [ActivityResultRegistryOwner] for testing purposes. */
  private class FakeActivityResultRegistryOwner(
      private val permissionResult: Map<String, Boolean>
  ) : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry =
        object : ActivityResultRegistry() {
          @Suppress("UNCHECKED_CAST")
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            dispatchResult(requestCode, permissionResult as O)
          }
        }
  }
}
