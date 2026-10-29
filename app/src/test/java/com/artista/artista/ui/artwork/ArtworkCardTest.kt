// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.map.Location
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [ArtworkCard].
 *
 * @author IJJA3141
 */
@RunWith(RobolectricTestRunner::class)
class ArtworkCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun artworkCard_displaysArtworkMetadataAndActions() {
    var selectedArtwork: Artwork? = null
    var mappedArtwork: Artwork? = null
    var saveRequest: Artwork? = null

    val artwork =
        Artwork(
            uid = "monaLisa",
            name = "Mona Lisa",
            artist = Artist("test-da-vinci", "Leonardo da Vinci"),
            description = "A portrait of Lisa Gherardini",
            conceptionDate = null,
            museum = "Louvre",
            location = Location(latitude = 48.8606, longitude = 2.3376, name = "Louvre"),
            dimension = null,
        )

    composeTestRule.setContent {
      MaterialTheme {
        ArtworkCard(
            artwork = artwork,
            isSaved = false,
            onArtworkClick = { selectedArtwork = it },
            onMapClick = { mappedArtwork = it },
            onSaveClick = { requestedArtwork -> saveRequest = requestedArtwork },
            modifier = Modifier,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_THUMBNAIL, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Picture/thumbnail Placeholder")
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Mona Lisa")
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Leonardo da Vinci")

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Map")
        .assertIsEnabled()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Save")
        .assertIsEnabled()

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).performClick()
    assertEquals(artwork, selectedArtwork)

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON).performClick()
    assertEquals(artwork, mappedArtwork)

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON).performClick()
    assertEquals(artwork, saveRequest)
  }

  @Test
  fun artworkCard_withoutLocation_disablesMapActionAndShowsSavedState() {
    var saveRequest: Artwork? = null
    val artwork =
        Artwork(
            uid = "nightWatch",
            name = "The Night Watch",
            artist = Artist("test-rembrandt", "Rembrandt"),
            description = "An iconic Dutch Golden Age painting",
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        )

    composeTestRule.setContent {
      MaterialTheme {
        ArtworkCard(
            artwork = artwork,
            isSaved = true,
            onArtworkClick = {},
            onMapClick = {},
            onSaveClick = { requestedArtwork -> saveRequest = requestedArtwork },
            modifier = Modifier,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
        .assertIsDisplayed()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Saved")
        .performClick()
    assertEquals(artwork, saveRequest)
  }

  @Test
  fun artworkCard_withoutArtist_displaysUnknownArtistFallback() {
    val artwork =
        Artwork(
            uid = "unknownArtist",
            name = "Untitled Study",
            artist = null,
            description = null,
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        )

    composeTestRule.setContent {
      MaterialTheme {
        ArtworkCard(
            artwork = artwork,
            isSaved = false,
            onArtworkClick = {},
            onMapClick = {},
            onSaveClick = { _ -> },
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Unknown Artist")
  }
}
