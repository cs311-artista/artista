package com.artista.artista.ui.overview

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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies navigation actions originating from the Overview screen.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class OverviewScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * Verifies that selecting the Overview search bar opens the Search screen.
   *
   * @author hixeum
   */
  @Test
  fun searchBarClick_navigatesToSearchScreen() {
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaApp(
          navController = navController,
          startDestination = Screen.Overview.route,
      )
    }

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()

    composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.Search.route, navController.currentDestination?.route)
    }
  }
}
