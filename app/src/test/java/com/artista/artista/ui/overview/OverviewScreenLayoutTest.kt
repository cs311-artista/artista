// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.map.Location
import com.artista.artista.model.map.UserLocation
import com.artista.artista.ui.artwork.ArtworkListCardTestTags
import com.artista.artista.ui.artwork.ArtworkTileTestTags
import com.artista.artista.ui.location.USER_LOCATION_PERMISSIONS
import com.artista.artista.ui.theme.ArtistaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the V2 layout of the Overview screen: grid and list modes, the row of saved artworks close
 * to the user and the empty filter result.
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

  /** Checks that saved artworks close to the user are listed in a row above nearby artworks. */
  @Test
  fun overviewScreen_withCloseSavedArtwork_showsCloseSavedRow() {
    val closeSaved = artwork("close", "Fontaine Jo Siffert", "Jean Tinguely").locatedAt(USER)
    setOverviewContent(nearby = listOf(starryNight), saved = listOf(closeSaved))

    composeTestRule.onNodeWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORK_ITEM)
        .assertCountEquals(1)
    composeTestRule
        .onNode(
            hasTestTag(ArtworkTileTestTags.title(closeSaved)) and
                hasAnyAncestor(hasTestTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS)),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  /**
   * Checks that the screen still starts at the close saved artworks when they arrive after the
   * nearby artworks are displayed.
   */
  @Test
  fun overviewScreen_whenCloseSavedArtworksLoadLast_staysScrolledToTop() {
    val closeSaved = artwork("close", "Fontaine Jo Siffert", "Jean Tinguely").locatedAt(USER)
    val savedGate = CompletableDeferred<Unit>()
    setOverviewContent(
        nearby = manyNearbyArtworks(),
        saved = listOf(closeSaved),
        savedGate = savedGate,
    )
    composeTestRule.onNodeWithTag(OverviewTestTags.NEARBY_ARTWORKS_TITLE).assertIsDisplayed()

    savedGate.complete(Unit)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS).assertIsDisplayed()
  }

  /** Checks that close saved artworks arriving late do not move a user who scrolled down. */
  @Test
  fun overviewScreen_whenCloseSavedArtworksLoadAfterScrolling_keepsScrollPosition() {
    val closeSaved = artwork("close", "Fontaine Jo Siffert", "Jean Tinguely").locatedAt(USER)
    val nearby = manyNearbyArtworks()
    val savedGate = CompletableDeferred<Unit>()
    val viewModel =
        setOverviewContent(nearby = nearby, saved = listOf(closeSaved), savedGate = savedGate)
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasText(checkNotNull(nearby.last().name)))

    savedGate.complete(Unit)
    composeTestRule.waitForIdle()

    // Without it, the assertions below would also pass if the close saved artworks never loaded.
    assertTrue(viewModel.uiState.value.closeSavedArtworks.isNotEmpty())
    composeTestRule.onNodeWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS).assertDoesNotExist()
    composeTestRule.onNodeWithText(checkNotNull(nearby.last().name)).assertIsDisplayed()
  }

  /** Checks that saving a close artwork from the grid shows it in the close row right away. */
  @Test
  fun overviewScreen_savingCloseArtwork_showsItInCloseSavedRow() {
    val closeNearby = artwork("close", "Fontaine Jo Siffert", "Jean Tinguely").locatedAt(USER)
    setOverviewContent(nearby = listOf(closeNearby))
    composeTestRule.onNodeWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS).assertDoesNotExist()

    composeTestRule.onNodeWithTag(ArtworkTileTestTags.saveButton(closeNearby)).performClick()

    composeTestRule.onNodeWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORK_ITEM)
        .assertCountEquals(1)
  }

  /** Checks that saved artworks far from the user do not create the close row. */
  @Test
  fun overviewScreen_withOnlyFarSavedArtwork_hidesCloseSavedRow() {
    val farSaved =
        artwork("far", "Far away", "Somebody")
            .locatedAt(UserLocation(USER.latitude + FAR_LATITUDE_OFFSET, USER.longitude))
    setOverviewContent(nearby = listOf(starryNight), saved = listOf(farSaved))

    composeTestRule.onNodeWithTag(OverviewTestTags.CLOSE_SAVED_ARTWORKS).assertDoesNotExist()
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
   * @param saved artworks already saved by the user
   * @param onArtworkClick called when an artwork is opened
   * @param savedGate when given, saved artworks are only returned once it completes, to load them
   *   after the nearby artworks
   * @return the ViewModel driving the displayed screen
   */
  private fun setOverviewContent(
      nearby: List<Artwork>,
      saved: List<Artwork> = emptyList(),
      onArtworkClick: (Artwork) -> Unit = {},
      savedGate: CompletableDeferred<Unit>? = null,
  ): OverviewViewModel {
    val repository = ArtworkRepositoryLocal()
    nearby.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }
    runBlocking { saved.forEach { repository.saveArtwork(it) } }
    val savedRepository =
        object : SavedArtworksRepository by repository {
          override suspend fun getSavedArtworks(): List<Artwork> {
            savedGate?.await()
            return repository.getSavedArtworks()
          }
        }
    val viewModel =
        OverviewViewModel(
            repository,
            savedRepository,
            FakeLocationProvider(providedLocation = USER),
        )
    composeTestRule.setContent {
      ArtistaTheme(darkTheme = false) {
        OverviewScreen(viewModel = viewModel, onArtworkClick = onArtworkClick)
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
  }

  /** Enough nearby artworks for the grid to scroll. */
  private fun manyNearbyArtworks(): List<Artwork> =
      (1..20).map { artwork("nearby-$it", "Nearby artwork $it", "Artist $it") }

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

  private fun Artwork.locatedAt(location: UserLocation): Artwork =
      copy(location = Location(location.longitude, location.latitude, name))

  private companion object {
    val USER = UserLocation(latitude = 46.5191, longitude = 6.5668)

    private const val METERS_PER_DEGREE_OF_LATITUDE = 111_000.0

    /**
     * Latitude offset placing an artwork at twice the close radius, so it stays far whatever the
     * radius is set to.
     */
    val FAR_LATITUDE_OFFSET =
        2 * OverviewViewModel.CLOSE_SAVED_ARTWORK_RADIUS_METERS / METERS_PER_DEGREE_OF_LATITUDE
  }
}
