// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.ui.artwork.ArtworkListCardTestTags
import com.artista.artista.ui.artwork.ArtworkTileTestTags
import com.artista.artista.ui.location.USER_LOCATION_PERMISSIONS
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the V2 layout of the Overview screen: grid and list modes and the empty filter result.
 *
 * @author hixeum
 */
@RunWith(RobolectricTestRunner::class)
class OverviewScreenLayoutTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private val starryNight = artwork("starry", "The Starry Night", "Vincent van Gogh")
  private val pearlEarring = artwork("pearl", "Girl with a Pearl Earring", "Johannes Vermeer")

  @Before
  fun setup() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    USER_LOCATION_PERMISSIONS.forEach { permission ->
      instrumentation.uiAutomation.grantRuntimePermission(
          instrumentation.targetContext.packageName,
          permission,
      )
    }
  }

  /** Checks that nearby artworks are shown as tiles by default. */
  @Test
  fun overviewScreen_byDefault_showsArtworksAsGridTiles() {
    setOverviewContent(nearby = listOf(starryNight, pearlEarring))

    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(2)
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.title(starryNight), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.title(starryNight), useUnmergedTree = true)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(OverviewTestTags.NEARBY_ARTWORKS_TITLE).assertIsDisplayed()
  }

  /** Checks that the display mode button switches to list cards and back to tiles. */
  @Test
  fun overviewScreen_displayModeButton_switchesBetweenGridAndList() {
    val viewModel = setOverviewContent(nearby = listOf(starryNight))

    composeTestRule.onNodeWithTag(OverviewTestTags.DISPLAY_MODE_BUTTON).performClick()

    assertEquals(OverviewDisplayMode.LIST, viewModel.uiState.value.displayMode)
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.title(starryNight), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.title(starryNight), useUnmergedTree = true)
        .assertDoesNotExist()
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(1)

    composeTestRule.onNodeWithTag(OverviewTestTags.DISPLAY_MODE_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.title(starryNight), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Checks that the bookmark of a list card saves the artwork. */
  @Test
  fun overviewScreen_inListMode_bookmarkSavesArtwork() {
    val viewModel = setOverviewContent(nearby = listOf(starryNight))

    composeTestRule.onNodeWithTag(OverviewTestTags.DISPLAY_MODE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ArtworkListCardTestTags.saveButton(starryNight)).performClick()
    composeTestRule.waitForIdle()

    assertTrue(starryNight.uid in viewModel.uiState.value.savedArtworkIds)
  }

  /** Checks that tapping a grid tile opens the tapped artwork. */
  @Test
  fun overviewScreen_inGridMode_tileClickOpensArtwork() {
    var openedArtwork: Artwork? = null
    setOverviewContent(nearby = listOf(starryNight), onArtworkClick = { openedArtwork = it })

    composeTestRule.onNodeWithTag(OverviewTestTags.ARTWORK_ITEM).performClick()

    assertEquals(starryNight, openedArtwork)
  }

  /** Checks that the clear button of the empty filter result restores the artworks. */
  @Test
  fun overviewScreen_emptyFilterResult_clearFiltersButtonRestoresArtworks() {
    val viewModel = setOverviewContent(nearby = listOf(starryNight, pearlEarring))

    composeTestRule.runOnIdle { viewModel.applyFilter(ArtworkFilter(fromYear = 3000)) }

    composeTestRule.onNodeWithTag(OverviewTestTags.EMPTY_FILTER_RESULTS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.ARTWORK_LIST).assertDoesNotExist()

    composeTestRule.onNodeWithTag(OverviewTestTags.CLEAR_FILTERS_BUTTON).performClick()

    composeTestRule.onNodeWithTag(OverviewTestTags.EMPTY_FILTER_RESULTS).assertDoesNotExist()
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(2)
    assertFalse(viewModel.uiState.value.activeFilter.isActive())
  }

  /**
   * Displays the Overview screen with a ViewModel backed by local repositories.
   *
   * @param nearby artworks returned as nearby artworks
   * @param onArtworkClick called when an artwork is opened
   * @return the ViewModel driving the displayed screen
   */
  private fun setOverviewContent(
      nearby: List<Artwork>,
      onArtworkClick: (Artwork) -> Unit = {},
  ): OverviewViewModel {
    val repository = ArtworkRepositoryLocal()
    nearby.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())
    composeTestRule.setContent {
      ArtistaTheme(darkTheme = false) {
        OverviewScreen(viewModel = viewModel, onArtworkClick = onArtworkClick)
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
  }

  private fun artwork(uid: String, name: String, artistName: String): Artwork =
      Artwork(
          uid = uid,
          name = name,
          artist = Artist(uid = "artist-$uid", name = artistName),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )
}
