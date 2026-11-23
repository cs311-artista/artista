// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * UI tests for [ArtistaTopBar], identifying every element by its test tag.
 *
 * @author hixeum
 */
// Native graphics measure text for real, which the ellipsis check needs: the legacy mode does not.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ArtistaTopBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Displays an [ArtistaTopBar] in a container of [BAR_WIDTH], with tagged side buttons when
   * requested.
   *
   * @param title text shown in the middle of the bar, or null for none
   * @param withLeftButton whether a back button is shown on the left
   * @param withRightButton whether a search button is shown on the right
   * @param onLeftClick callback invoked when the left button is tapped
   * @param onRightClick callback invoked when the right button is tapped
   */
  private fun setTopBar(
      title: String? = TITLE,
      withLeftButton: Boolean = false,
      withRightButton: Boolean = false,
      onLeftClick: () -> Unit = {},
      onRightClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ArtistaTheme {
        Box(modifier = Modifier.width(BAR_WIDTH)) {
          ArtistaTopBar(
              title = title,
              leftButton =
                  if (withLeftButton) {
                    { BackButton(onClick = onLeftClick, modifier = Modifier.testTag(LEFT_BUTTON)) }
                  } else null,
              rightButton =
                  if (withRightButton) {
                    {
                      IconButton(
                          onClick = onRightClick,
                          modifier = Modifier.testTag(RIGHT_BUTTON),
                      ) {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                      }
                    }
                  } else null,
          )
        }
      }
    }
  }

  private fun boundsOf(tag: String): DpRect =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

  private fun DpRect.centerX(): Dp = (left + right) / 2

  /** Asserts the title's horizontal center matches the bar's, within one pixel-sized tolerance. */
  private fun assertTitleIsCentered() {
    val bar = boundsOf(ArtistaTopBarTestTags.BAR)
    val title = boundsOf(ArtistaTopBarTestTags.TITLE)
    assertTrue(
        "Title center ${title.centerX()} differs from bar center ${bar.centerX()}",
        abs((title.centerX() - bar.centerX()).value) <= CENTER_TOLERANCE_DP,
    )
  }

  @Test
  fun preview_displaysTheBar() {
    composeTestRule.setContent { ArtistaTopBarPreview() }

    composeTestRule.onNodeWithTag(ArtistaTopBarTestTags.BAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistaTopBarTestTags.TITLE).assertTextEquals("Saved Artworks")
  }

  @Test
  fun title_isDisplayedWithItsText() {
    setTopBar()

    composeTestRule
        .onNodeWithTag(ArtistaTopBarTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(TITLE)
  }

  @Test
  fun customTitleTestTag_replacesTheDefaultTag() {
    composeTestRule.setContent {
      ArtistaTheme { ArtistaTopBar(title = TITLE, titleTestTag = "screen_title") }
    }

    composeTestRule.onNodeWithTag("screen_title").assertTextEquals(TITLE)
    composeTestRule.onNodeWithTag(ArtistaTopBarTestTags.TITLE).assertDoesNotExist()
  }

  @Test
  fun withoutButtons_titleIsCenteredAndNoSlotIsShown() {
    setTopBar()

    assertTitleIsCentered()
    composeTestRule.onNodeWithTag(ArtistaTopBarTestTags.LEFT_SLOT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistaTopBarTestTags.RIGHT_SLOT).assertDoesNotExist()
  }

  @Test
  fun withOnlyLeftButton_titleIsCentered() {
    setTopBar(withLeftButton = true)

    assertTitleIsCentered()
    composeTestRule.onNodeWithTag(LEFT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun withOnlyRightButton_titleIsCentered() {
    setTopBar(withRightButton = true)

    assertTitleIsCentered()
    composeTestRule.onNodeWithTag(RIGHT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun withBothButtons_titleIsCentered() {
    setTopBar(withLeftButton = true, withRightButton = true)

    assertTitleIsCentered()
  }

  @Test
  fun withoutTitle_buttonsSitAtTheEdges() {
    setTopBar(title = null, withLeftButton = true, withRightButton = true)

    composeTestRule.onNodeWithTag(ArtistaTopBarTestTags.TITLE).assertDoesNotExist()
    val bar = boundsOf(ArtistaTopBarTestTags.BAR)
    val left = boundsOf(ArtistaTopBarTestTags.LEFT_SLOT)
    val right = boundsOf(ArtistaTopBarTestTags.RIGHT_SLOT)
    assertTrue("Left button is not at the left edge", left.centerX() < bar.centerX())
    assertTrue("Right button is not at the right edge", right.centerX() > bar.centerX())
    // Only the bar's own horizontal padding separates the buttons from the edges.
    assertTrue(left.left - bar.left <= EDGE_TOLERANCE)
    assertTrue(bar.right - right.right <= EDGE_TOLERANCE)
  }

  @Test
  fun longTitle_isCutWithEllipsisAndDoesNotOverlapButtons() {
    setTopBar(title = LONG_TITLE, withLeftButton = true, withRightButton = true)

    val left = boundsOf(ArtistaTopBarTestTags.LEFT_SLOT)
    val right = boundsOf(ArtistaTopBarTestTags.RIGHT_SLOT)
    val title = boundsOf(ArtistaTopBarTestTags.TITLE)
    assertTrue("Title overlaps the left button", title.left >= left.right)
    assertTrue("Title overlaps the right button", title.right <= right.left)

    val layoutResults = mutableListOf<TextLayoutResult>()
    composeTestRule
        .onNodeWithTag(ArtistaTopBarTestTags.TITLE)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(layoutResults)
    val layout = layoutResults.single()
    assertEquals(1, layout.lineCount)
    assertTrue("Long title is not ellipsized", layout.isLineEllipsized(0))
  }

  @Test
  fun shortTitle_isNotEllipsized() {
    setTopBar(withLeftButton = true, withRightButton = true)

    val layoutResults = mutableListOf<TextLayoutResult>()
    composeTestRule
        .onNodeWithTag(ArtistaTopBarTestTags.TITLE)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(layoutResults)
    assertFalse(layoutResults.single().isLineEllipsized(0))
  }

  @Test
  fun buttons_invokeTheirCallbacks() {
    var leftClicks = 0
    var rightClicks = 0
    setTopBar(
        withLeftButton = true,
        withRightButton = true,
        onLeftClick = { leftClicks++ },
        onRightClick = { rightClicks++ },
    )

    composeTestRule.onNodeWithTag(LEFT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(RIGHT_BUTTON).performClick()

    assertEquals(1, leftClicks)
    assertEquals(1, rightClicks)
  }

  private companion object {
    const val TITLE = "Saved Artworks"
    const val LONG_TITLE =
        "The Garden of Earthly Delights, a triptych painted between 1490 and 1510 in Brabant"
    const val LEFT_BUTTON = "test_left_button"
    const val RIGHT_BUTTON = "test_right_button"
    const val CENTER_TOLERANCE_DP = 1f
    val BAR_WIDTH = 360.dp
    val EDGE_TOLERANCE = Dimensions.topBarHorizontalPadding
  }
}
