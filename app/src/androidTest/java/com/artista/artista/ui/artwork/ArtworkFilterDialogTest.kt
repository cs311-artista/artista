// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the selected-option chips of [ArtworkFilterDialog].
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class ArtworkFilterDialogTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val artistTag = ArtworkFilterTestTags.ARTIST
  private val selectedRowTag = "$artistTag-selected"

  private val manyArtists =
      listOf(
          "Vincent van Gogh",
          "Claude Monet",
          "Pablo Picasso",
          "Frida Kahlo",
          "Rembrandt van Rijn",
          "Johannes Vermeer",
          "Gustav Klimt",
          "Salvador Dali",
      )

  private var primary = Color.Unspecified
  private var onPrimary = Color.Unspecified

  private fun setDialog(
      filter: ArtworkFilter = ArtworkFilter(),
      artists: List<String> = manyArtists,
      darkTheme: Boolean = false,
  ) {
    composeTestRule.setContent {
      ArtistaTheme(darkTheme = darkTheme) {
        primary = MaterialTheme.colorScheme.primary
        onPrimary = MaterialTheme.colorScheme.onPrimary
        ArtworkFilterDialog(
            filter = filter,
            artistOptions = artists,
            museumOptions = emptyList(),
            locationOptions = emptyList(),
            onApply = {},
            onClear = {},
            onDismiss = {},
        )
      }
    }
  }

  private fun selectArtist(name: String) {
    composeTestRule.onNodeWithTag("$artistTag-input").performTextInput(name)
    composeTestRule.onNodeWithTag("$artistTag-option-$name").performClick()
  }

  @Test
  fun noSelection_hidesSelectedRow() {
    setDialog()

    composeTestRule.onAllNodesWithTag(selectedRowTag).assertCountEquals(0)
  }

  @Test
  fun selectingOptions_appendsChipsInSelectionOrder() {
    setDialog()

    selectArtist("Pablo Picasso")
    selectArtist("Claude Monet")
    selectArtist("Vincent van Gogh")

    val row = composeTestRule.onNodeWithTag(selectedRowTag)
    row.assertIsDisplayed()
    row.onChildAt(0).assert(hasTestTag("$artistTag-chip-Pablo Picasso"))
    row.onChildAt(1).assert(hasTestTag("$artistTag-chip-Claude Monet"))
    row.onChildAt(2).assert(hasTestTag("$artistTag-chip-Vincent van Gogh"))
  }

  @Test
  fun manySelections_rowIsScrollableToLastChip() {
    setDialog(filter = ArtworkFilter(artists = manyArtists.toCollection(LinkedHashSet())))

    val row = composeTestRule.onNodeWithTag(selectedRowTag)
    row.assert(hasScrollAction())
    row.performScrollToNode(hasTestTag("$artistTag-chip-${manyArtists.last()}"))
    composeTestRule.onNodeWithTag("$artistTag-chip-${manyArtists.last()}").assertIsDisplayed()
  }

  @Test
  fun clickingChip_removesItAndHidesRowWhenEmpty() {
    setDialog()

    selectArtist("Frida Kahlo")
    composeTestRule.onNodeWithTag("$artistTag-chip-Frida Kahlo").performClick()

    composeTestRule.onAllNodesWithTag("$artistTag-chip-Frida Kahlo").assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(selectedRowTag).assertCountEquals(0)
  }

  @Test
  fun selectedChip_isFilledWithPrimaryInLightTheme() {
    assertChipUsesPrimaryRoles(darkTheme = false)
  }

  @Test
  fun selectedChip_isFilledWithPrimaryInDarkTheme() {
    assertChipUsesPrimaryRoles(darkTheme = true)
  }

  // The chip node also covers its touch-target padding and its glyphs, so a large share of
  // primary pixels shows the fill, and any onPrimary pixel shows the label is drawn on top of it.
  private fun assertChipUsesPrimaryRoles(darkTheme: Boolean) {
    setDialog(filter = ArtworkFilter(artists = setOf("Claude Monet")), darkTheme = darkTheme)

    val pixels =
        composeTestRule.onNodeWithTag("$artistTag-chip-Claude Monet").captureToImage().toPixelMap()
    val colors =
        (0 until pixels.width).flatMap { x ->
          (0 until pixels.height).map { y -> pixels[x, y].toArgb() }
        }
    val primaryShare = colors.count { it == primary.toArgb() }.toFloat() / colors.size

    assertTrue("primary covers only ${primaryShare * 100}% of the chip", primaryShare > 0.3f)
    assertTrue("no onPrimary pixel in the chip", colors.any { it == onPrimary.toArgb() })
  }
}
