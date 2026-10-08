package com.artista.artista.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.After
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

  private fun setMapScreenContent() {
    composeTestRule.setContent { ArtistaTheme { MapScreen() } }
  }
}
