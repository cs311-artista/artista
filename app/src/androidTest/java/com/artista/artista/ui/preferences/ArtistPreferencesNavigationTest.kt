package com.artista.artista.ui.preferences

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.ArtistaApp
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.profile.UserProfileTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies navigation to and from the artist preferences screen.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class ArtistPreferencesNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * Verifies that the preferences back button returns to the profile screen.
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

    composeTestRule.onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.Profile.route, navController.currentDestination?.route)
    }
  }

  private companion object {
    const val PROFILE_GRAPH_ROUTE = "profile_graph"
  }
}
