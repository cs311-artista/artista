// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.map.FakeAddressRepository
import com.artista.artista.model.map.Location
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the layout of [ArtworkScreen]: top bar, title under the image and map section.
 *
 * @author Timz3rr
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtworkScreenLayoutTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleArtwork =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )

  /** Checks that the top bar shows the fixed screen title instead of the artwork name. */
  @Test
  fun artworkScreen_topBar_displaysFixedTitle() {
    setScreenContent(sampleArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_TOP_BAR_TITLE, useUnmergedTree = true)
        .assertTextEquals("Artwork")
  }

  /** Checks that the header under the image shows the artwork name. */
  @Test
  fun artworkScreen_header_displaysArtworkName() {
    setScreenContent(sampleArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_TITLE, useUnmergedTree = true)
        .assertTextEquals("Mona Lisa")
  }

  /** Checks that the map shows a recenter button that has no click action yet. */
  @Test
  fun artworkScreen_withLocation_displaysNonClickableRecenterButton() {
    setScreenContent(sampleArtwork.copy(location = Location(48.86, 2.34, "Paris")))

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_MAP_RECENTER_BUTTON)
        .assertExists()
        .assertHasNoClickAction()
  }

  /** Checks that no recenter button exists when the artwork has no location, hence no map. */
  @Test
  fun artworkScreen_withoutLocation_hasNoRecenterButton() {
    setScreenContent(sampleArtwork)

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_MAP_RECENTER_BUTTON).assertDoesNotExist()
  }

  /** Checks that a "Location" section title is shown above the map when the artwork has one. */
  @Test
  fun artworkScreen_withLocation_displaysLocationHeader() {
    setScreenContent(sampleArtwork.copy(location = Location(48.86, 2.34, "Paris")))

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_LOCATION_HEADER, useUnmergedTree = true)
        .assertTextEquals("Location")
  }

  /**
   * Checks that no location section title exists when the artwork has no location, hence no map.
   */
  @Test
  fun artworkScreen_withoutLocation_hasNoLocationHeader() {
    setScreenContent(sampleArtwork)

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_LOCATION_HEADER).assertDoesNotExist()
  }

  private fun setScreenContent(artwork: Artwork) {
    val wikiDataRepository =
        ArtworkRepositoryLocal().apply { putArtworkInRepositoryUnsavedSection(artwork) }
    val viewModel =
        ArtworkScreenViewModel(
            artwork.uid,
            wikiDataRepository,
            ArtworkRepositoryLocal(),
            FakeAddressRepository(),
        )
    composeTestRule.setContent {
      MaterialTheme { ArtworkScreen(artworkScreenViewModel = viewModel, onBackClick = {}) }
    }
    // Before the artwork is loaded, the loading indicator replaces the whole content.
    composeTestRule.waitUntilDoesNotExist(
        hasTestTag(ArtworkTestTags.ARTWORK_LOADING_INDICATOR),
        IMAGE_TIMEOUT_MS,
    )
  }
}
