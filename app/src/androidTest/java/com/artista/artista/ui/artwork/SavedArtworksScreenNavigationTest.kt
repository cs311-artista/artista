package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.ArtistaNavHost
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.profile.UserProfileTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash
/**
 * Verifies navigation from Saved Artworks back to the screen that opened it.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class SavedArtworksScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private companion object {
    const val NAVIGATION_TIMEOUT_MILLIS = 5_000L
    const val PROFILE_GRAPH_ROUTE = "profile_graph"
  }

  private lateinit var repository: ArtworkRepositoryLocal

  @Before
  fun setUp() {
    repository =
        ArtworkRepositoryLocal().also { localRepository ->
          mockArtworks.forEach(localRepository::putArtworkInRepositoryUnsavedSection)
          runBlocking { localRepository.saveArtwork(mockArtworks.first()) }
        }
  }

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
      ArtistaNavHost(
          onLogout = {},
          navController = navController,
          startDestination = PROFILE_GRAPH_ROUTE,
          wikiDataRepository = repository,
          savedArtworksRepository = repository,
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { navController.currentDestination?.route } ==
          Screen.SavedArtworks.route
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { navController.currentDestination?.route } ==
          Screen.Profile.route
    }
  }

  /** Verifies selecting a saved artwork opens details for that same artwork. */
  @Test
  fun savedArtworkCard_opensMatchingArtworkDetails() {
    val artwork = mockArtworks.first()
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaNavHost(
          onLogout = {},
          navController = navController,
          startDestination = PROFILE_GRAPH_ROUTE,
          wikiDataRepository = repository,
          savedArtworksRepository = repository,
      )
    }

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { navController.currentDestination?.route } ==
          Screen.SavedArtworks.route
    }
    val artworkCard = SavedArtworksTestTags.card(artwork)
    composeTestRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithTag(artworkCard).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(artworkCard).performClick()

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_TITLE)
        .assertTextEquals(checkNotNull(artwork.name))
    composeTestRule.runOnIdle {
      assertTrue(navController.currentDestination?.route == Screen.ArtworkDetails.route)
    }
  }
}
