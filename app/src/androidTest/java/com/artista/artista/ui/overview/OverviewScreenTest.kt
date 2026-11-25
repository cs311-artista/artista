// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.failingWikiDataRepository
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.map.Location
import com.artista.artista.ui.artwork.ArtworkFilterTestTags
import com.artista.artista.ui.artwork.ArtworkListCardTestTags
import com.artista.artista.ui.artwork.ArtworkTileTestTags
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the user-visible behavior of the Overview screen.
 *
 * @author patrickmcdan, hixeum
 */
@RunWith(AndroidJUnit4::class)
class OverviewScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private companion object {
    const val SAVE_OPERATION_TIMEOUT_MILLIS = 5_000L
  }

  private final val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

  @Before
  fun setup() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
    )
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /** Verifies that the overview preview renders its sample artwork list. */
  @Test
  fun overviewScreenPreview_displaysSampleArtworkList() {
    composeTestRule.setContent { OverviewScreenPreview() }

    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
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

  /** Verifies that the overview map action reports the tapped artwork. */
  @Test
  fun overviewScreen_whenMapButtonIsClicked_callsOnMapClickWithTappedArtwork() {
    val artwork =
        artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
            .copy(location = Location(48.8606, 2.3376, "Louvre"))
    var mappedArtwork: Artwork? = null
    setOverviewContent(listOf(artwork), onMapClick = { mappedArtwork = it })

    composeTestRule.onNodeWithTag(ArtworkTileTestTags.mapButton(artwork)).performClick()

    assertEquals(artwork, mappedArtwork)
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
    val viewModel =
        OverviewViewModel(
            failingWikiDataRepository(),
            ArtworkRepositoryLocal(),
            FakeLocationProvider(),
        )

    setOverviewContent(viewModel, onSavedArtworksClick = { savedArtworksRequested = true })

    composeTestRule.onNodeWithTag(OverviewTestTags.ERROR_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithText("Unable to load nearby artworks.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.SAVED_ARTWORKS_BUTTON).performClick()

    assertTrue(savedArtworksRequested)
  }

  /** Verifies that the clear-filters button of an empty filter result restores the artworks. */
  @Test
  fun overviewScreen_filterWithNoMatches_clearFiltersButtonRestoresArtworks() {
    setOverviewContent(listOf(artwork(name = "The Starry Night", artistName = "Vincent van Gogh")))

    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.FROM_YEAR).performTextInput("3000")
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).performClick()
    composeTestRule.onNodeWithTag(OverviewTestTags.CLEAR_FILTERS_BUTTON).performClick()

    composeTestRule.onNodeWithTag(OverviewTestTags.EMPTY_FILTER_RESULTS).assertDoesNotExist()
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(OverviewTestTags.FILTER_ACTIVE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Verifies that the display mode button switches the artworks to a list and back. */
  @Test
  fun overviewScreen_displayModeButton_switchesBetweenGridAndList() {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    setOverviewContent(listOf(artwork))

    composeTestRule.onNodeWithTag(ArtworkTileTestTags.saveButton(artwork)).assertIsDisplayed()

    composeTestRule.onNodeWithTag(OverviewTestTags.DISPLAY_MODE_BUTTON).performClick()

    // A list card can be taller than the screen, which leaves its footer buttons out of view.
    scrollArtworksTo(ArtworkListCardTestTags.saveButton(artwork))
    composeTestRule.onNodeWithTag(ArtworkListCardTestTags.saveButton(artwork)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkTileTestTags.saveButton(artwork)).assertDoesNotExist()

    // The switch button scrolls with the artworks, so bring it back before tapping it.
    scrollArtworksTo(OverviewTestTags.DISPLAY_MODE_BUTTON)
    composeTestRule.onNodeWithTag(OverviewTestTags.DISPLAY_MODE_BUTTON).performClick()

    scrollArtworksTo(ArtworkTileTestTags.saveButton(artwork))
    composeTestRule.onNodeWithTag(ArtworkTileTestTags.saveButton(artwork)).assertIsDisplayed()
  }

  /**
   * Scrolls the artwork list until the node tagged [tag] is composed and visible.
   *
   * @param tag test tag of the node to bring into view
   */
  private fun scrollArtworksTo(tag: String) {
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(tag))
  }

  /** Verifies the save-error effect clears errors after displaying their message. */
  @Test
  fun overviewScreen_whenSavingArtworkFails_clearsSaveErrorAndRestoresCard() {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh").copy(uid = "")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

    setOverviewContent(viewModel)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.saveButton(artwork))
        .assert(hasContentDescription(saveDescription(artwork)))
        .performClick()
    composeTestRule.waitForIdle()
    assertTrue(viewModel.uiState.value.saveErrorMessageRes == null)
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.saveButton(artwork))
        .assert(hasContentDescription(saveDescription(artwork)))
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
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

    setOverviewContent(viewModel, onSearchClick = { searchRequested = true })

    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()

    assertTrue(searchRequested)
  }

  /** Verifies an artwork saved while Overview is stopped appears saved after returning. */
  @Test
  fun overviewScreen_returningAfterArtworkWasSaved_refreshesBookmarkFromRepository() {
    val artwork = artwork(name = "The Starry Night", artistName = "Vincent van Gogh")
    val repository = ArtworkRepositoryLocal()
    repository.putArtworkInRepositoryUnsavedSection(artwork)
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

    setOverviewContent(viewModel)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.saveButton(artwork))
        .assert(hasContentDescription(saveDescription(artwork)))

    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    runBlocking { repository.saveArtwork(artwork) }
    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.saveButton(artwork))
        .assert(hasContentDescription(unsaveDescription(artwork)))
    composeTestRule.waitUntil(SAVE_OPERATION_TIMEOUT_MILLIS) {
      runBlocking { artwork.uid in repository.getSavedArtworks().map { it.uid } }
    }
  }

  /** Verifies that the filter dialog opens from the Overview action. */
  @Test
  fun overviewScreen_filterButton_opensFilterDialog() {
    setOverviewContent(
        listOf(artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"))
    )

    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()

    composeTestRule.onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-input").performTextInput("ver")
    composeTestRule
        .onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-option-Johannes Vermeer")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.CLEAR_BUTTON).assertIsDisplayed()
  }

  /** Verifies that applying a filter updates the displayed artwork cards. */
  @Test
  fun overviewScreen_applyingFilter_updatesArtworkList() {
    setOverviewContent(
        listOf(
            artwork(name = "The Starry Night", artistName = "Vincent van Gogh"),
            artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"),
        )
    )

    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-input").performTextInput("ver")
    composeTestRule
        .onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-option-Johannes Vermeer")
        .performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).performClick()

    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(1)
    composeTestRule.onNodeWithText("Girl with a Pearl Earring").assertIsDisplayed()
    // assertExists() never passes without useUnmergedTree = true
    composeTestRule
        .onNodeWithTag(OverviewTestTags.FILTER_ACTIVE, useUnmergedTree = true)
        .assertExists()
  }

  /** Verifies that clearing filters restores the complete artwork list. */
  @Test
  fun overviewScreen_clearingFilter_restoresArtworkList() {
    setOverviewContent(
        listOf(
            artwork(name = "The Starry Night", artistName = "Vincent van Gogh"),
            artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"),
        )
    )

    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-input").performTextInput("vin")
    composeTestRule
        .onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-option-Vincent van Gogh")
        .performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).performClick()
    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.CLEAR_BUTTON).performClick()

    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(2)
    // assertDoesNotExist() always passes without useUnmergedTree = true
    composeTestRule
        .onNodeWithTag(OverviewTestTags.FILTER_ACTIVE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Verifies that no-match filtering displays a dedicated empty-result state. */
  @Test
  fun overviewScreen_filterWithNoMatches_showsEmptyResultState() {
    setOverviewContent(listOf(artwork(name = "The Starry Night", artistName = "Vincent van Gogh")))

    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.FROM_YEAR).performTextInput("3000")
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).performClick()

    composeTestRule.onNodeWithTag(OverviewTestTags.EMPTY_FILTER_RESULTS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.ARTWORK_LIST).assertDoesNotExist()
  }

  /** Verifies that an inverted year range shows an error and blocks applying until it is fixed. */
  @Test
  fun filterDialog_invalidYearRange_showsErrorAndDisablesApply() {
    setOverviewContent(
        listOf(artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"))
    )
    composeTestRule.onNodeWithTag(OverviewTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.YEAR_ERROR).assertDoesNotExist()

    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.FROM_YEAR).performTextInput("1900")
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.TO_YEAR).performTextInput("1800")

    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.YEAR_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).assertIsNotEnabled()

    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.TO_YEAR).performTextReplacement("1950")

    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.YEAR_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).assertIsEnabled()
  }

  private fun setOverviewContent(
      artworks: List<Artwork>,
      onMapClick: (Artwork) -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    val repository = ArtworkRepositoryLocal()
    artworks.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }
    val viewModel = OverviewViewModel(repository, repository, FakeLocationProvider())

    setOverviewContent(viewModel, onMapClick = onMapClick, onTabSelected = onTabSelected)
  }

  private fun setOverviewContent(
      viewModel: OverviewViewModel,
      onSavedArtworksClick: () -> Unit = {},
      onSearchClick: () -> Unit = {},
      onMapClick: (Artwork) -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      ArtistaTheme {
        OverviewScreen(
            viewModel = viewModel,
            onSavedArtworksClick = onSavedArtworksClick,
            onSearchClick = onSearchClick,
            onMapClick = onMapClick,
            onTabSelected = onTabSelected,
        )
      }
    }
  }

  private fun saveDescription(artwork: Artwork): String =
      composeTestRule.activity.getString(R.string.artwork_save, artwork.name)

  private fun unsaveDescription(artwork: Artwork): String =
      composeTestRule.activity.getString(R.string.artwork_unsave, artwork.name)

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
