// Co-authored-by: DeepSeek Flash
package com.artista.artista

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import com.artista.artista.ui.artwork.ArtworkTestTags
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.overview.OverviewTestTags
import com.artista.artista.ui.search.SearchScreenTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) tests for [ArtistaNavHost].
 *
 * Covers the wiring the graph itself contributes: which repositories its destinations are built
 * with, and which destination each artwork selection opens. The screens below the graph are tested
 * on their own, so the assertions here stop at the destination that is reached and the argument it
 * received.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class ArtistaNavHostTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var repository: ArtworkRepositoryLocal
  private lateinit var navController: NavHostController

  /**
   * Gives each test its own repository, seeded with the artworks the graph's destinations resolve
   * their arguments against, and with the first of them saved so that the search screen has a
   * recommendation to offer.
   *
   * Every test passes the repositories explicitly, so nothing here reads the application's default
   * repositories and no test needs a Firebase app.
   *
   * @author timo-by
   */
  @Before
  fun setUp() {
    repository =
        ArtworkRepositoryLocal().also { localRepository ->
          mockArtworks.forEach { localRepository.putArtworkInRepositoryUnsavedSection(it) }
          runBlocking { localRepository.saveArtwork(mockArtworks.first()) }
        }
  }

  /**
   * Verifies that selecting a search recommendation opens the details of the recommended artwork.
   *
   * @author timo-by
   */
  @Test
  fun searchRecommendationClickOpensThatArtworksDetails() {
    val artwork = mockArtworks.first()
    showNavHost()

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT)
        .performTextInput(checkNotNull(artwork.name))
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION).performClick()

    assertArtworkDetailsOf(artwork.uid)
  }

  /**
   * Verifies that selecting an artwork among the search results opens the details of that artwork.
   *
   * @author timo-by
   */
  @Test
  fun searchResultClickOpensThatArtworksDetails() {
    val artwork = mockArtworks.first()
    showNavHost()

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()
    val searchInput = composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT)
    searchInput.performTextInput(checkNotNull(artwork.name))
    searchInput.performImeAction()
    // Selected through the card's click semantics rather than by tapping it: once the keyboard
    // submission has collapsed the search bar, Robolectric lays the result list out below the
    // window, where a synthetic touch would land outside the screen and be dropped.
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD)
        .performSemanticsAction(SemanticsActions.OnClick)

    assertArtworkDetailsOf(artwork.uid)
  }

  /**
   * Composes the graph over the seeded repository, ignoring the logout intent: the graph reports it
   * to [ArtistaApp], which is not part of these tests.
   *
   * @author timo-by
   */
  private fun showNavHost() {
    composeTestRule.setContent {
      navController = rememberNavController()
      ArtistaNavHost(
          onLogout = {},
          navController = navController,
          wikiDataRepository = repository,
          savedArtworksRepository = repository,
      )
    }
  }

  /**
   * Checks that the graph is on the details of the artwork identified by [artworkId].
   *
   * @param artworkId identifier the details destination should have received
   * @author timo-by
   */
  private fun assertArtworkDetailsOf(artworkId: String) {
    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Screen.ArtworkDetails.route, navController.currentDestination?.route)
      assertEquals(
          artworkId,
          navController.currentBackStackEntry?.arguments?.getString(ARTWORK_ID_ARGUMENT),
      )
    }
  }

  private companion object {
    /** Name the details destination declares for the identifier of the artwork it displays. */
    const val ARTWORK_ID_ARGUMENT = "artworkId"
  }
}
