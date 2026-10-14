package com.artista.artista.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.MainActivity
import com.artista.artista.resources.C
import com.artista.artista.ui.map.MapScreenTestTags
import com.artista.artista.ui.overview.OverviewTestTags
import com.artista.artista.ui.profile.UserProfileTestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies the root navigation flow exposed by the application.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  /**
   * Grants location permissions before a navigation test opens the map destination.
   *
   * @author hixeum
   */
  @Before
  fun grantLocationPermissions() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        .forEach { permission ->
          instrumentation.uiAutomation.grantRuntimePermission(
              instrumentation.targetContext.packageName,
              permission,
          )
        }
  }

  /**
   * Releases shell permissions after each navigation test.
   *
   * @author hixeum
   */
  @After
  fun releaseShellPermissions() {
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /**
   * Verifies that authentication is the initial destination and sign-in opens the overview.
   *
   * @author hixeum
   */
  @Test
  fun authIsInitialDestination_andSignInOpensOverview() {
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()

    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
  }

  /**
   * Verifies that selecting Map from Overview selects Map and displays the map screen.
   *
   * @author hixeum
   */
  @Test
  fun overviewToMap_selectsMapAndDisplaysMapScreen() {
    openScreen(Tab.Overview)

    assertTabTransition(Tab.Overview, Tab.Map)
  }

  /**
   * Verifies that selecting Overview from Map selects Overview and displays the overview screen.
   *
   * @author hixeum
   */
  @Test
  fun mapToOverview_selectsOverviewAndDisplaysOverviewScreen() {
    openScreen(Tab.Map)

    assertTabTransition(Tab.Map, Tab.Overview)
  }

  /**
   * Verifies that selecting Profile from Overview selects Profile and displays the profile screen.
   *
   * @author hixeum
   */
  @Test
  fun overviewToProfile_selectsProfileAndDisplaysProfileScreen() {
    openScreen(Tab.Overview)

    assertTabTransition(Tab.Overview, Tab.Profile)
  }

  /**
   * Verifies that selecting Overview from Profile selects Overview and displays the overview
   * screen.
   *
   * @author hixeum
   */
  @Test
  fun profileToOverview_selectsOverviewAndDisplaysOverviewScreen() {
    openScreen(Tab.Profile)

    assertTabTransition(Tab.Profile, Tab.Overview)
  }

  /**
   * Verifies that selecting Profile from Map selects Profile and displays the profile screen.
   *
   * @author hixeum
   */
  @Test
  fun mapToProfile_selectsProfileAndDisplaysProfileScreen() {
    openScreen(Tab.Map)

    assertTabTransition(Tab.Map, Tab.Profile)
  }

  /**
   * Verifies that selecting Map from Profile selects Map and displays the map screen.
   *
   * @author hixeum
   */
  @Test
  fun profileToMap_selectsMapAndDisplaysMapScreen() {
    openScreen(Tab.Profile)

    assertTabTransition(Tab.Profile, Tab.Map)
  }

  /**
   * Opens the requested top-level screen from the authentication destination.
   *
   * @param tab Top-level destination to display.
   * @author hixeum
   */
  private fun openScreen(tab: Tab) {
    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()

    if (tab != Tab.Overview) {
      composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(tab)).performClick()
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(tab)).assertIsSelected()
    composeTestRule.onNodeWithTag(screenTestTag(tab)).assertIsDisplayed()
  }

  /**
   * Selects a destination and verifies the tab selection and displayed screen.
   *
   * @param previousTab Tab that should lose selection.
   * @param selectedTab Tab that should become selected and display its screen.
   * @author hixeum
   */
  private fun assertTabTransition(previousTab: Tab, selectedTab: Tab) {
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(selectedTab)).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(selectedTab)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(previousTab))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(screenTestTag(selectedTab)).assertIsDisplayed()
  }

  /**
   * Returns the semantics tag identifying a tab's root screen.
   *
   * @param tab Tab whose screen tag is requested.
   * @return The test tag attached to the tab's screen root.
   * @author hixeum
   */
  private fun screenTestTag(tab: Tab): String =
      when (tab) {
        Tab.Overview -> OverviewTestTags.SCREEN
        Tab.Map -> MapScreenTestTags.MAP
        Tab.Profile -> UserProfileTestTags.SCREEN
      }
}
