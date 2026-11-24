// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash
package com.artista.artista.ui.profile

import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.ArtistaNavHost
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.authentication.AuthRepositoryProvider
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.map.FakeAddressRepository
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.SavedArtworksTestTags
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.overview.OverviewTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies navigation actions originating from the profile screen.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class UserProfileScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // Owned by the test rather than read from the application: the graph now takes its repositories
  // as arguments, so nothing here touches the providers the application configures.
  private val artworkRepository = ArtworkRepositoryLocal()

  private final val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

  @Before
  fun setup() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
    )
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /**
   * Verifies that tapping the profile logout button reports the logout intent to the caller.
   *
   * The graph has no sign-in destination to navigate to: signing out replaces the whole graph from
   * the session gate of the application root, so the button reports the intent and the graph stays
   * where it is. What the gate then displays is covered by the local session-gate test and by the
   * end-to-end journey test.
   *
   * @author hixeum
   */
  @Test
  fun logout_invokesTheLogoutCallback() {
    var logoutCount = 0
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaNavHost(
          onLogout = { logoutCount++ },
          wikiDataRepository = artworkRepository,
          savedArtworksRepository = artworkRepository,
          walkingRouteRepository = WalkingRouteRepositoryRemote(),
          addressRepository = FakeAddressRepository(),
          navController = navController,
          startDestination = C.Constants.PROFILE_GRAPH_ROUTE,
      )
    }

    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON)
        .performScrollTo()
        .performClick()

    composeTestRule.runOnIdle {
      assertEquals(1, logoutCount)
      assertEquals(Screen.Profile.route, navController.currentDestination?.route)
    }
  }

  /**
   * Verifies that tapping saved artworks displays its destination and updates the current route.
   *
   * @author hixeum
   */
  @Test
  fun savedArtworksButton_navigatesToSavedArtworksScreen() {
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaNavHost(
          onLogout = {},
          wikiDataRepository = artworkRepository,
          savedArtworksRepository = artworkRepository,
          walkingRouteRepository = WalkingRouteRepositoryRemote(),
          addressRepository = FakeAddressRepository(),
          navController = navController,
          startDestination = C.Constants.PROFILE_GRAPH_ROUTE,
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.runOnUiThread { navController.currentDestination?.route } ==
          Screen.SavedArtworks.route
    }

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.SavedArtworks.route, navController.currentDestination?.route)
    }
  }

  /**
   * Checks that a username just saved is still the one prefilled in the edit dialog after a round
   * trip through Overview.
   *
   * Instrumented rather than on Robolectric, where Compose never becomes idle once the username
   * dialog is open.
   */
  @Test
  fun renamedUsername_isKeptAfterSwitchingTabs() {
    // Signed out, so the profile ViewModel has no stored name to read back: the name only lives in
    // the ViewModel, and is lost as soon as the ViewModel is.
    AuthRepositoryProvider.repository = FakeAuthRepository()
    try {
      val artworkRepository = ArtworkRepositoryLocal()
      composeTestRule.setContent {
        // The graph rather than the application root: the root gates on a signed-out session, so it
        // would show the sign-in screen instead of the profile this test drives.
        ArtistaNavHost(
            onLogout = {},
            startDestination = C.Constants.PROFILE_GRAPH_ROUTE,
            wikiDataRepository = artworkRepository,
            savedArtworksRepository = artworkRepository,
            walkingRouteRepository = WalkingRouteRepositoryRemote(),
            addressRepository = FakeAddressRepository(),
        )
      }

      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).performScrollTo().performClick()
      composeTestRule
          .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
          .performTextReplacement(NEW_USERNAME)
      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_SAVE).performClick()

      composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
      composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
      composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()

      // The profile screen no longer shows the username: the edit dialog prefills it instead.
      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).performScrollTo().performClick()
      composeTestRule
          .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
          .assertTextContains(NEW_USERNAME)
    } finally {
      AuthRepositoryProvider.resetToApplicationRepository()
    }
  }

  private companion object {
    /** Name entered in the edit dialog, different from any name the user starts with. */
    const val NEW_USERNAME = "Claude Monet"
  }
}
