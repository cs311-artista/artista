// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.map.Location
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtworkCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun artworkCard_displaysArtworkMetadataAndActions() {
    var artworkClickCount = 0
    var mapClickCount = 0
    var saveClickCount = 0

    val artwork =
        Artwork(
            uid = "monaLisa",
            name = "Mona Lisa",
            artistName = "Leonardo da Vinci",
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
            onArtworkClick = { artworkClickCount++ },
            onMapClick = { mapClickCount++ },
            onSaveClick = { _, _ -> saveClickCount++ },
            modifier = Modifier,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithText("Mona Lisa").assertIsDisplayed()
    composeTestRule.onNodeWithText("Leonardo da Vinci").assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
        .assertIsDisplayed()
        .assert(hasText("Map"))
        .assertIsEnabled()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON)
        .assertIsDisplayed()
        .assert(hasText("Save"))
        .assertIsEnabled()

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).performClick()
    assertEquals(1, artworkClickCount)

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON).performClick()
    assertEquals(1, mapClickCount)

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON).performClick()
    assertEquals(1, saveClickCount)
  }

  @Test
  fun artworkCard_withoutLocation_disablesMapActionAndShowsSavedState() {
    val artwork =
        Artwork(
            uid = "nightWatch",
            name = "The Night Watch",
            artistName = "Rembrandt",
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
            onSaveClick = { _, _ -> },
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
        .assert(hasText("Saved"))
  }
}
