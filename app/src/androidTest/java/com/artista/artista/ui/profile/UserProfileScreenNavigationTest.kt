package com.artista.artista.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.ArtistaApp
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.SavedArtworksTestTags
import com.artista.artista.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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

    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == Screen.SavedArtworks.route
    }

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.SavedArtworks.route, navController.currentDestination?.route)
    }
  }

  private companion object {
    const val PROFILE_GRAPH_ROUTE = "profile_graph"
  }
}
