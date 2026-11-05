// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the user-visible behavior of the Overview screen.
 *
 * @author patrickmcdan
 */
@RunWith(AndroidJUnit4::class)
class OverviewScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private companion object {
    const val SAVE_OPERATION_TIMEOUT_MILLIS = 5_000L
  }

  /** Verifies that the overview preview renders its sample artwork list. */
  @Test
  fun overviewScreenPreview_displaysSampleArtworkList() {
    composeTestRule.setContent { OverviewScreenPreview() }

    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.ARTWORK_LIST).assertIsDisplayed()
  }

  /**
   * Verifies that the screen and its artwork list are displayed.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewScreen_displaysScreenAndArtworkList() {
    val artworks =
        listOf(
            artwork(name = "The Starry Night", artistName = "Vincent van Gogh"),
            artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"),
        )
    setOverviewContent(artworks)

    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.ARTWORK_LIST).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(2)
  }

  /**
   * Verifies that each artwork item displays its title and artist.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewScreen_displaysArtworkInformation() {
    val artworks = listOf(artwork(name = "The Starry Night", artistName = "Vincent van Gogh"))
    setOverviewContent(artworks)

    composeTestRule.onNodeWithText("The Starry Night").assertIsDisplayed()
    composeTestRule.onNodeWithText("Vincent van Gogh").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_ITEM)
        .assertTextContains("The Starry Night")
        .assertTextContains("Vincent van Gogh")
  }

  /**
   * Verifies that the Overview tab is selected in the bottom navigation.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewScreen_selectsOverviewTab() {
    setOverviewContent(emptyList())

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile))
        .assertIsNotSelected()
  }

  /** Verifies that a load error shows a persistent saved-artworks action. */
  @Test
  fun overviewScreen_whenLoadingFails_showsSavedArtworksAction() {
    var savedArtworksRequested = false
    val failing =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getArtworksNearLocation(
              searchArea: SearchArea,
              typeValues: List<ArtworkType>,
              limit: Int,
          ): List<Artwork> = error("Artwork provider failure")
        }
    val viewModel = OverviewViewModel(failing, ArtworkRepositoryLocal())

    composeTestRule.setContent {
      ArtistaTheme {
        OverviewScreen(
            viewModel = viewModel,
            onSavedArtworksClick = { savedArtworksRequested = true },
        )
      }
    }

    composeTestRule.onNodeWithTag(OverviewTestTags.ERROR_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithText("Unable to load nearby artworks.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.SAVED_ARTWORKS_BUTTON).performClick()

    assertTrue(savedArtworksRequested)
  }

  /** Verifies the save-error effect clears errors after displaying their message. */
  @Test
  fun overviewScreen_whenSavingArtworkFails_clearsSaveErrorAndRestoresCard() {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh").copy(uid = "")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository)

    composeTestRule.setContent {
      ArtistaTheme {
        OverviewScreen(
            viewModel = viewModel,
            onTabSelected = {},
        )
      }
    }

    val saveButtonTag = ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON
    composeTestRule.onNodeWithTag(saveButtonTag).assertTextContains("Save").performClick()
    composeTestRule.waitForIdle()
    assertTrue(viewModel.uiState.value.saveErrorMessageRes == null)
    composeTestRule.onNodeWithTag(saveButtonTag).assertTextContains("Save")
  }

  /**
   * Verifies that the Overview screen displays a search-shaped navigation control.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewScreen_displaysSearchBar() {
    setOverviewContent(emptyList())

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(OverviewTestTags.SEARCH_BAR)
        .assertTextContains("Search artworks or artists")
  }

  /**
   * Verifies that selecting the Overview search bar requests navigation to SearchScreen.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewScreen_searchBarClick_invokesSearchCallback() {
    var searchRequested = false
    val repository = ArtworkRepositoryLocal()
    val viewModel = OverviewViewModel(repository, repository)

    composeTestRule.setContent {
      ArtistaTheme {
        OverviewScreen(
            viewModel = viewModel,
            onSearchClick = { searchRequested = true },
            onTabSelected = {},
        )
      }
    }

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()

    assertTrue(searchRequested)
  }

  /** Verifies an artwork saved while Overview is stopped appears saved after returning. */
  @Test
  fun overviewScreen_returningAfterArtworkWasSaved_refreshesBookmarkFromRepository() {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository)

    composeTestRule.setContent {
      ArtistaTheme {
        OverviewScreen(
            viewModel = viewModel,
            onTabSelected = {},
        )
      }
    }

    val saveButtonTag = ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON
    composeTestRule.onNodeWithTag(saveButtonTag).assertTextContains("Save")

    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    runBlocking { repository.saveArtwork(artwork) }
    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeTestRule.onNodeWithTag(saveButtonTag).assertTextContains("Saved")
    composeTestRule.waitUntil(SAVE_OPERATION_TIMEOUT_MILLIS) {
      runBlocking { artwork.uid in repository.getSavedArtworks().map { it.uid } }
    }
  }

  private fun setOverviewContent(artworks: List<Artwork>) {
    val repository = ArtworkRepositoryLocal()
    artworks.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }
    val viewModel = OverviewViewModel(repository, repository)

    composeTestRule.setContent {
      ArtistaTheme {
        OverviewScreen(
            viewModel = viewModel,
            onTabSelected = {},
        )
      }
    }
  }

  private fun artwork(name: String, artistName: String): Artwork =
      Artwork(
          uid = "$name-$artistName",
          name = name,
          artist = Artist(uid = "uid-$artistName", name = artistName),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )
}
