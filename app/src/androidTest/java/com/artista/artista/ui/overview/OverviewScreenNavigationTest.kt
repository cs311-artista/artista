// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.ArtistaApp
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import com.artista.artista.ui.artwork.ArtworkTestTags
import com.artista.artista.ui.map.MapScreenTestTags
import com.artista.artista.ui.map.REQUIRED_LOCATION_PERMISSIONS
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
          startDestination = OVERVIEW_GRAPH_ROUTE,
      )
    }

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()

    composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.Search.route, navController.currentDestination?.route)
    }
  }

  /**
   * Verifies that an artwork card's map button opens the map destination.
   *
   * @author hixeum
   */
  @Test
  fun artworkMapButtonClick_navigatesToMapScreen() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    try {
      REQUIRED_LOCATION_PERMISSIONS.forEach { permission ->
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            permission,
        )
      }

      lateinit var navController: NavHostController
      composeTestRule.setContent {
        navController = rememberNavController()
        ArtistaApp(
            navController = navController,
            startDestination = OVERVIEW_GRAPH_ROUTE,
        )
      }

      composeTestRule
          .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
          .performScrollToNode(hasTestTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON))
      val mapButtons = composeTestRule.onAllNodesWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
      mapButtons[0].performClick()

      composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
      composeTestRule.runOnIdle {
        assertEquals(Screen.Map.route, navController.currentDestination?.route)
      }
    } finally {
      instrumentation.uiAutomation.dropShellPermissionIdentity()
    }
  }

  /**
   * Verifies that selecting an artwork card opens details for that artwork.
   *
   * @author hixeum
   */
  @Test
  fun artworkCardClick_navigatesToMatchingArtworkScreen() {
    val artwork = mockArtworks.first()
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaApp(
          navController = navController,
          startDestination = OVERVIEW_GRAPH_ROUTE,
      )
    }

    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(OverviewTestTags.ARTWORK_ITEM))
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM)[0].performClick()

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_TITLE)
        .assertTextEquals(checkNotNull(artwork.name))
    composeTestRule.runOnIdle {
      assertEquals(Screen.ArtworkDetails.route, navController.currentDestination?.route)
      assertEquals(
          artwork.uid,
          navController.currentBackStackEntry?.arguments?.getString("artworkId"),
      )
    }
  }

  private companion object {
    const val OVERVIEW_GRAPH_ROUTE = "overview_graph"
  }
}
