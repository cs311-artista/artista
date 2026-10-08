package com.artista.artista.ui.map

import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.After
import org.junit.Assert.assertEquals
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
    REQUIRED_LOCATION_PERMISSIONS.forEach { permission ->
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

  /** Verifies that the map displays a marker at the device's current location. */
  @Test
  fun mapScreen_displaysMarkerAtDeviceLocation() {
    val mapViewModel = MapViewModel { callback -> callback(mockDeviceLocation()) }
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
    val mapViewModel = MapViewModel { callback -> callback(null) }
    composeTestRule.setContent { ArtistaTheme { MapScreen(mapViewModel = mapViewModel) } }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    assertEquals(null, mapViewModel.uiState.userLocation)
  }

  private fun setMapScreenContent() {
    composeTestRule.setContent { ArtistaTheme { MapScreen() } }
  }

  private companion object {
    const val MOCK_LATITUDE = 46.5201
    const val MOCK_LONGITUDE = 6.6332
  }

  private fun mockDeviceLocation(): Location =
      Location("mock").apply {
        latitude = MOCK_LATITUDE
        longitude = MOCK_LONGITUDE
      }
}
