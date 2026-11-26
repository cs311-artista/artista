// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.artista.artista.model.artist.Artist
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the shared [ArtistCard] and its [ArtistCardDefaults].
 *
 * @author krfpn
 */
@RunWith(RobolectricTestRunner::class)
class ArtistCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val artist = Artist(uid = "test-monet", name = "Claude Monet")

  /**
   * Displays a card for [artist] inside the app theme.
   *
   * @param selected whether the card is selected
   * @param onToggle called when the card is clicked
   */
  private fun setCard(selected: Boolean, onToggle: () -> Unit = {}) {
    composeTestRule.setContent {
      ArtistaTheme {
        ArtistCard(
            artist = artist,
            selected = selected,
            onToggle = onToggle,
            cardTestTag = CARD_TAG,
            imageTestTag = IMAGE_TAG,
            fallbackTestTag = FALLBACK_TAG,
        )
      }
    }
  }

  /** Checks that the card shows the artist's name and is tagged with the given test tag. */
  @Test
  fun artistCard_displaysNameWithGivenTag() {
    setCard(selected = true)

    composeTestRule.onNodeWithTag(CARD_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithText(artist.name).assertIsDisplayed()
  }

  /** Checks that a selected card is exposed as a checked checkbox. */
  @Test
  fun artistCard_selected_isOn() {
    setCard(selected = true)

    composeTestRule.onNodeWithTag(CARD_TAG).assertIsOn()
  }

  /** Checks that an unselected card is exposed as an unchecked checkbox. */
  @Test
  fun artistCard_unselected_isOff() {
    setCard(selected = false)

    composeTestRule.onNodeWithTag(CARD_TAG).assertIsOff()
  }

  /** Checks that clicking the card calls the toggle callback once. */
  @Test
  fun artistCard_click_callsOnToggle() {
    var toggles = 0
    setCard(selected = true, onToggle = { toggles++ })

    composeTestRule.onNodeWithTag(CARD_TAG).performClick()

    assertEquals(1, toggles)
  }

  /** Checks that the default colors are the V2 colors taken from the theme. */
  @Test
  fun artistCardDefaults_colors_followTheme() {
    lateinit var colors: ArtistCardColors
    lateinit var scheme: ColorScheme
    composeTestRule.setContent {
      ArtistaTheme {
        colors = ArtistCardDefaults.colors()
        scheme = MaterialTheme.colorScheme
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(scheme.surface, colors.selectedContainerColor)
    assertEquals(scheme.surfaceVariant, colors.unselectedContainerColor)
    assertEquals(scheme.onSurface, colors.contentColor)
    assertEquals(scheme.surfaceVariant, colors.imagePlaceholderColor)
    assertEquals(scheme.primary, colors.selectedIndicatorColor)
    assertEquals(scheme.surface, colors.unselectedIndicatorColor)
    assertEquals(scheme.onPrimary, colors.checkmarkColor)
  }

  /** Checks that overriding one color keeps the defaults of the others. */
  @Test
  fun artistCardDefaults_colors_overrideKeepsOtherDefaults() {
    lateinit var colors: ArtistCardColors
    lateinit var defaults: ArtistCardColors
    composeTestRule.setContent {
      ArtistaTheme {
        colors = ArtistCardDefaults.colors(selectedContainerColor = Color.Red)
        defaults = ArtistCardDefaults.colors()
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(defaults.copy(selectedContainerColor = Color.Red), colors)
  }

  private companion object {
    const val CARD_TAG = "artist_card"
    const val IMAGE_TAG = "artist_card_image"
    const val FALLBACK_TAG = "artist_card_fallback"
  }
}
