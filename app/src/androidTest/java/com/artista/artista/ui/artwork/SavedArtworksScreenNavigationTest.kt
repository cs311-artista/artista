package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.ArtistaApp
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.profile.UserProfileTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies navigation from Saved Artworks back to the screen that opened it.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class SavedArtworksScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * Verifies that the back arrow returns to Profile when Saved Artworks is opened there.
   *
   * @author hixeum
   */
  @Test
  fun backButton_returnsToProfileScreen() {
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

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.runOnUiThread { navController.currentDestination?.route } ==
          Screen.Profile.route
    }
  }

  private companion object {
    const val PROFILE_GRAPH_ROUTE = "profile_graph"
  }
}
