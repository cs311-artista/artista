// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.artista.artista.R
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the shared [FriendCard].
 *
 * @author hixeum
 */
@RunWith(RobolectricTestRunner::class)
class FriendCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Checks that the card shows the user's name. */
  @Test
  fun friendCard_displaysName() {
    setCard(name = "Claude Monet")

    composeTestRule.onNodeWithText("Claude Monet").assertIsDisplayed()
  }

  /** Checks that a user without a name gets the generic label instead of an empty card. */
  @Test
  fun friendCard_nullName_displaysUnnamedLabel() {
    setCard(name = null)

    composeTestRule.onNodeWithText(string(R.string.friend_card_unnamed)).assertIsDisplayed()
  }

  /** Checks that a blank name counts as no name. */
  @Test
  fun friendCard_blankName_displaysUnnamedLabel() {
    setCard(name = "   ")

    composeTestRule.onNodeWithText(string(R.string.friend_card_unnamed)).assertIsDisplayed()
  }

  /** Checks that the placeholder is shown when the user has no profile picture. */
  @Test
  fun friendCard_noPhoto_displaysPlaceholder() {
    setCard(photoUrl = null)

    composeTestRule
        .onNodeWithTag(FriendCardTestTags.PICTURE_PLACEHOLDER, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(FriendCardTestTags.PICTURE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that no action slot is drawn when the caller gives no action. */
  @Test
  fun friendCard_withoutAction_hasNoActionSlot() {
    setCard(action = null)

    composeTestRule.onNodeWithTag(FriendCardTestTags.ACTION).assertDoesNotExist()
  }

  /** Checks that the given action is displayed and receives clicks. */
  @Test
  fun friendCard_action_isDisplayedAndClickable() {
    var clicks = 0
    setCard(
        action = {
          IconButton(onClick = { clicks++ }, modifier = Modifier.testTag(ACTION_BUTTON_TAG)) {
            Text("+")
          }
        }
    )

    composeTestRule.onNodeWithTag(ACTION_BUTTON_TAG).assertIsDisplayed().performClick()

    composeTestRule.runOnIdle { assertEquals(1, clicks) }
  }

  /**
   * Checks that the action slot is centered on the card's border, halfway along the rounded top-end
   * corner, as in the mock.
   */
  @Test
  fun friendCard_action_isCenteredOnRoundedCornerBorder() {
    setCard(action = { Text("+") })

    val card = composeTestRule.onNodeWithTag(FriendCardTestTags.CARD).getUnclippedBoundsInRoot()
    val action = composeTestRule.onNodeWithTag(FriendCardTestTags.ACTION).getUnclippedBoundsInRoot()

    // Middle of the border line along the corner arc, measured from the card's bounding corner.
    val radius = Dimensions.cornerRadiusMedium.value
    val borderMiddleRadius = radius - Dimensions.standardStrokeWidth.value / 2
    val inset = radius - borderMiddleRadius / sqrt(2f)
    assertEquals(card.right.value - inset, ((action.left + action.right) / 2).value, TOLERANCE_DP)
    assertEquals(card.top.value + inset, ((action.top + action.bottom) / 2).value, TOLERANCE_DP)
  }

  /** Checks that the action slot has the size given by the design. */
  @Test
  fun friendCard_action_hasDesignSize() {
    setCard(action = { Text("+") })

    val action = composeTestRule.onNodeWithTag(FriendCardTestTags.ACTION).getUnclippedBoundsInRoot()
    assertEquals(Dimensions.friendCardActionSize, action.width)
    assertEquals(Dimensions.friendCardActionSize, action.height)
  }

  /** Checks that the card keeps the same size whether or not it carries an action. */
  @Test
  fun friendCard_sizeDoesNotDependOnAction() {
    composeTestRule.setContent {
      ArtistaTheme {
        Column {
          FriendCard(
              name = "With action",
              photoUrl = null,
              modifier = Modifier.testTag(WITH_ACTION_TAG),
              action = { Text("+") },
          )
          FriendCard(
              name = "Without action",
              photoUrl = null,
              modifier = Modifier.testTag(WITHOUT_ACTION_TAG),
          )
        }
      }
    }

    val withAction = composeTestRule.onNodeWithTag(WITH_ACTION_TAG).getUnclippedBoundsInRoot()
    val withoutAction = composeTestRule.onNodeWithTag(WITHOUT_ACTION_TAG).getUnclippedBoundsInRoot()
    assertEquals(withAction.width, withoutAction.width)
    assertEquals(withAction.height, withoutAction.height)
  }

  /**
   * Displays a single card inside the app theme.
   *
   * @param name the user's name
   * @param photoUrl link of the user's picture
   * @param action content of the corner slot
   */
  private fun setCard(
      name: String? = "Claude Monet",
      photoUrl: String? = null,
      action: (@Composable () -> Unit)? = null,
  ) {
    composeTestRule.setContent {
      ArtistaTheme { FriendCard(name = name, photoUrl = photoUrl, action = action) }
    }
  }

  private fun string(resId: Int): String =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

  private companion object {
    const val ACTION_BUTTON_TAG = "test_action_button"
    const val WITH_ACTION_TAG = "test_card_with_action"
    const val WITHOUT_ACTION_TAG = "test_card_without_action"
    /** Allowed rounding difference, in dp, between the slot's center and the card's corner. */
    val TOLERANCE_DP = 0.5.dp.value
  }
}
