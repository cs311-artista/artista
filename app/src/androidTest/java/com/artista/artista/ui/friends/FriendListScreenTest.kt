// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friends

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [FriendListScreen].
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class FriendListScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /** Verifies that the screen shows its top bar, message and bottom navigation. */
  @Test
  fun friendListScreen_displaysAllElements() {
    setContent()

    composeTestRule.onNodeWithTag(FriendListTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(FriendListTestTags.BACK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(FriendListTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.friends_title))
    composeTestRule
        .onNodeWithTag(FriendListTestTags.EMPTY_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.friends_empty_message))
  }

  /** Verifies that tapping the back arrow invokes the back callback. */
  @Test
  fun backButton_click_invokesOnBackClick() {
    var backClicked = false
    setContent(onBackClick = { backClicked = true })

    composeTestRule.onNodeWithTag(FriendListTestTags.BACK_BUTTON).performClick()

    composeTestRule.runOnIdle { assertTrue(backClicked) }
  }

  /** Verifies that the Profile tab is the one selected, since friends belong to the profile. */
  @Test
  fun friendListScreen_selectsProfileTab() {
    setContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
  }

  /** Verifies that selecting a tab forwards it to the tab callback. */
  @Test
  fun bottomNavigation_click_invokesOnTabSelected() {
    var selectedTab: Tab? = null
    setContent(onTabSelected = { selectedTab = it })

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).performClick()

    composeTestRule.runOnIdle { assertEquals(Tab.Map, selectedTab) }
  }

  /** Verifies that, on landing, the search bar peeks out by itself and then hides again. */
  @Test
  fun landing_hintRevealsSearchBarThenHidesIt() {
    composeTestRule.mainClock.autoAdvance = false
    setContent()

    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()

    // Halfway through the hold, the content is pulled down and part of the bar is visible.
    composeTestRule.mainClock.advanceTimeBy(
        SEARCH_HINT_START_DELAY_MS + SEARCH_REVEAL_ANIMATION_MS + SEARCH_HINT_HOLD_MS / 2
    )
    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertExists()

    composeTestRule.mainClock.advanceTimeBy(SEARCH_HINT_HOLD_MS + SEARCH_REVEAL_ANIMATION_MS * 2L)
    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()
  }

  /** Verifies that the hint is not replayed when the screen is recreated, e.g. on rotation. */
  @Test
  fun landing_hintIsNotReplayedAfterRestore() {
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent { ArtistaTheme { FriendListScreen(onBackClick = {}) } }
    // Waiting for idle does not run the hint's delays, so the first hint is played to its end
    // explicitly; otherwise it would still be pending and show the bar after the restore.
    composeTestRule.mainClock.advanceTimeBy(FULL_HINT_DURATION_MS)
    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()

    // Restored while the clock still advances by itself: without frames, the screen would not be
    // rebuilt and the test would check nothing.
    restorationTester.emulateSavedInstanceStateRestore()
    // Halfway through where a replayed hint would hold the bar down.
    composeTestRule.mainClock.advanceTimeBy(
        SEARCH_HINT_START_DELAY_MS + SEARCH_REVEAL_ANIMATION_MS + SEARCH_HINT_HOLD_MS / 2
    )

    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()
  }

  /** Verifies that pulling the content down opens the focused search bar with its placeholder. */
  @Test
  fun pullDown_opensSearchBar() {
    setContent()

    pullDown()

    composeTestRule
        .onNodeWithTag(FriendListTestTags.SEARCH_FIELD)
        .assertIsDisplayed()
        .assertIsFocused()
    composeTestRule
        .onNodeWithText(string(R.string.friends_search_placeholder), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Verifies that a pull shorter than half of the bar lets it slide back up. */
  @Test
  fun shortPull_doesNotOpenSearchBar() {
    setContent()

    composeTestRule.onNodeWithTag(FriendListTestTags.CONTENT).performTouchInput {
      val distance = 8.dp.toPx()
      swipeDown(startY = centerY, endY = centerY + distance)
    }

    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()
  }

  /** Verifies that pulling the content back up closes the search bar. */
  @Test
  fun pullUp_closesSearchBar() {
    setContent()
    pullDown()

    composeTestRule.onNodeWithTag(FriendListTestTags.EMPTY_MESSAGE).performTouchInput {
      swipeUp(startY = centerY, endY = top)
    }

    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()
  }

  /** Verifies that the system back closes the search bar instead of leaving the screen. */
  @Test
  fun back_closesSearchBar() {
    var backClicked = false
    setContent(onBackClick = { backClicked = true })
    pullDown()

    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(false, backClicked) }
  }

  /** Verifies that screen reader users can open and close the search without dragging. */
  @Test
  fun accessibilityActions_openAndCloseSearchBar() {
    setContent()

    performCustomAction(string(R.string.friends_search_placeholder))
    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertIsDisplayed()

    performCustomAction(string(R.string.friends_search_close))
    composeTestRule.onNodeWithTag(FriendListTestTags.SEARCH_FIELD).assertDoesNotExist()
  }

  /**
   * Runs the accessibility action with the given label on the message area, on the main thread.
   *
   * Screen readers run actions on the main thread. performCustomAccessibilityActionWithLabel runs
   * them on the test thread instead, where closing the search crashes, since clearing the focus
   * touches the Android views.
   *
   * @param label label of the action to run
   */
  private fun performCustomAction(label: String) {
    val action =
        composeTestRule
            .onNodeWithTag(FriendListTestTags.EMPTY_MESSAGE)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .single { it.label == label }
    composeTestRule.runOnUiThread { action.action() }
  }

  /** Pulls the content down far enough to open the search bar. */
  private fun pullDown() {
    composeTestRule.onNodeWithTag(FriendListTestTags.CONTENT).performTouchInput {
      swipeDown(startY = top + 1f, endY = centerY)
    }
  }

  private fun setContent(onBackClick: () -> Unit = {}, onTabSelected: (Tab) -> Unit = {}) {
    composeTestRule.setContent {
      ArtistaTheme { FriendListScreen(onBackClick = onBackClick, onTabSelected = onTabSelected) }
    }
  }

  private fun string(resId: Int): String = composeTestRule.activity.getString(resId)

  private companion object {
    /** Time for the landing hint to play entirely, with a margin for the last frames. */
    const val FULL_HINT_DURATION_MS =
        SEARCH_HINT_START_DELAY_MS + SEARCH_HINT_HOLD_MS + SEARCH_REVEAL_ANIMATION_MS * 2L + 100L
  }
}
