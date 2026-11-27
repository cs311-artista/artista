// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friends

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

  private fun setContent(onBackClick: () -> Unit = {}, onTabSelected: (Tab) -> Unit = {}) {
    composeTestRule.setContent {
      ArtistaTheme { FriendListScreen(onBackClick = onBackClick, onTabSelected = onTabSelected) }
    }
  }

  private fun string(resId: Int): String = composeTestRule.activity.getString(resId)
}
