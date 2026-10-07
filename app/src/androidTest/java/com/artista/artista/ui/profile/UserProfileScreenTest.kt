package com.artista.artista.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Verifies the user-visible behavior of the user profile screen.
 *
 * @author Timz3rr
 */
@RunWith(AndroidJUnit4::class)
class UserProfileScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /** Verifies that every element of the profile screen is displayed. */
  @Test
  fun userProfileScreen_displaysAllElements() {
    setProfileContent()

    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /** Verifies that a placeholder is displayed when the user has no username. */
  @Test
  fun userProfileScreen_showsPlaceholder_whenUserHasNoUsername() {
    val viewModel = UserProfileViewModel().apply { setUser(userNamed(null)) }
    setProfileContent(viewModel = viewModel)

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals("USERNAME")
  }

  /** Verifies that the username of the user is displayed. */
  @Test
  fun userProfileScreen_showsUsername_whenUserHasUsername() {
    val viewModel = UserProfileViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals("Alice")
  }

  /** Verifies that the displayed username follows the state of the ViewModel. */
  @Test
  fun userProfileScreen_updatesUsername_whenUserChanges() {
    val viewModel = UserProfileViewModel()
    setProfileContent(viewModel = viewModel)

    composeTestRule.runOnIdle { viewModel.setUser(userNamed("Bob")) }

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals("Bob")
  }

  /** Verifies that the preferences and saved artwork buttons display their labels. */
  @Test
  fun userProfileScreen_displaysButtonLabels() {
    setProfileContent()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .assertTextContains("PREFERENCES (ARTIST)")
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .assertTextContains("SAVED ARTWORK")
  }

  /** Verifies that the profile tab is the one selected in the bottom navigation. */
  @Test
  fun userProfileScreen_selectsProfileTab() {
    setProfileContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
  }

  /** Verifies that clicking the preferences button invokes the preferences callback. */
  @Test
  fun preferencesButton_click_invokesOnPreferencesClick() {
    var preferencesOpened = false
    setProfileContent(onPreferencesClick = { preferencesOpened = true })

    composeTestRule.onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON).performClick()

    assertTrue(preferencesOpened)
  }

  /** Verifies that clicking the saved artwork button invokes the saved artwork callback. */
  @Test
  fun savedArtworkButton_click_invokesOnSavedArtworkClick() {
    var savedArtworkOpened = false
    setProfileContent(onSavedArtworkClick = { savedArtworkOpened = true })

    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()

    assertTrue(savedArtworkOpened)
  }

  /** Verifies that selecting a bottom navigation tab reports the selected destination. */
  @Test
  fun bottomNavigationTab_click_forwardsSelectedTab() {
    var selectedTab: Tab? = null
    setProfileContent(onTabSelected = { selectedTab = it })

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).performClick()

    assertEquals(Tab.Map, selectedTab)
  }

  private fun setProfileContent(
      viewModel: UserProfileViewModel = UserProfileViewModel(),
      onPreferencesClick: () -> Unit = {},
      onSavedArtworkClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MaterialTheme {
        UserProfileScreen(
            userProfileViewModel = viewModel,
            onPreferencesClick = onPreferencesClick,
            onSavedArtworkClick = onSavedArtworkClick,
            onTabSelected = onTabSelected,
        )
      }
    }
  }

  /**
   * Builds a test user that differs only by its name, since the profile only displays the name.
   *
   * @param userName the name of the user, or null for a user without a name.
   * @return a user with a fixed uid and default preferences.
   */
  private fun userNamed(userName: String?): User =
      User(uid = "uid-1", userName = userName, preference = UserPreference())
}
