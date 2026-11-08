package com.artista.artista.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.ArtistaApp
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.authentication.AuthRepositoryProvider
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.SavedArtworksTestTags
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.overview.OverviewTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Verifies navigation actions originating from the profile screen.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class UserProfileScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * Verifies that tapping the profile logout button navigates to and displays authentication.
   *
   * @author hixeum
   */
  @Test
  fun logout_navigatesToAuthScreen() {
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaApp(
          navController = navController,
          startDestination = PROFILE_GRAPH_ROUTE,
      )
    }

    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).performClick()

    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.Auth.route, navController.currentDestination?.route)
      assertNull(navController.previousBackStackEntry)
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
      ArtistaApp(
          navController = navController,
          startDestination = PROFILE_GRAPH_ROUTE,
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
   * Checks that a username just saved is still displayed after a round trip through Overview.
   *
   * Instrumented rather than on Robolectric, where Compose never becomes idle once the username
   * dialog is open.
   */
  @Test
  fun renamedUsername_isStillDisplayedAfterSwitchingTabs() {
    val realAuthRepository = AuthRepositoryProvider.repository
    // Signed out, as in the app until the Google sign-in is wired: the name only lives in the
    // ViewModel, so it is lost as soon as the ViewModel is.
    AuthRepositoryProvider.repository = FakeAuthRepository()
    try {
      val artworkRepository = ArtworkRepositoryLocal()
      composeTestRule.setContent {
        ArtistaApp(
            startDestination = PROFILE_GRAPH_ROUTE,
            wikiDataRepository = artworkRepository,
            savedArtworksRepository = artworkRepository,
        )
      }

      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).performClick()
      composeTestRule
          .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
          .performTextReplacement(NEW_USERNAME)
      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_SAVE).performClick()
      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals(NEW_USERNAME)

      composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
      composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
      composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()

      composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals(NEW_USERNAME)
    } finally {
      AuthRepositoryProvider.repository = realAuthRepository
    }
  }

  private companion object {
    const val PROFILE_GRAPH_ROUTE = "profile_graph"

    /** Name entered in the edit dialog, different from the placeholder shown by default. */
    const val NEW_USERNAME = "Claude Monet"
  }
}
