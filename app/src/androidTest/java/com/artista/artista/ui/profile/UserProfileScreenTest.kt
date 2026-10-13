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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
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
    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    // The content scrolls on small screens, such as the CI emulator, so it may start off screen.
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
  }

  /** Verifies that a placeholder is displayed when the user has no username. */
  @Test
  fun userProfileScreen_showsPlaceholder_whenUserHasNoUsername() {
    val viewModel = UserProfileViewModel().apply { setUser(userNamed(null)) }
    setProfileContent(viewModel = viewModel)

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME)
        .assertTextEquals(composeTestRule.activity.getString(R.string.profile_username_placeholder))
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
        .assertTextContains(composeTestRule.activity.getString(R.string.profile_preferences))
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .assertTextContains(composeTestRule.activity.getString(R.string.profile_saved_artwork))
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

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .performScrollTo()
        .performClick()

    assertTrue(preferencesOpened)
  }

  /** Verifies that clicking the saved artwork button invokes the saved artwork callback. */
  @Test
  fun savedArtworkButton_click_invokesOnSavedArtworkClick() {
    var savedArtworkOpened = false
    setProfileContent(onSavedArtworkClick = { savedArtworkOpened = true })

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .performClick()

    assertTrue(savedArtworkOpened)
  }

  /**
   * Verifies that clicking the logout button invokes the logout callback.
   *
   * The test passes its own callback and only checks that it is called: signing out and leaving the
   * screen are handled by the caller, so this test stays valid once the callback is wired to the
   * authentication and the navigation.
   */
  @Test
  fun logoutButton_click_invokesOnLogoutClick() {
    // Only the callback below can flip this flag, so it proves the click reached the callback.
    var logoutClicked = false
    setProfileContent(onLogoutClick = { logoutClicked = true })

    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).performClick()

    // Fails if the button is wired to another callback, such as onPreferencesClick.
    assertTrue(logoutClicked)
  }

  /** Verifies that the logout button displays its label. */
  @Test
  fun logoutButton_displaysLabel() {
    setProfileContent()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON)
        .assertTextEquals(composeTestRule.activity.getString(R.string.profile_logout))
  }

  private fun setProfileContent(
      viewModel: UserProfileViewModel = UserProfileViewModel(),
      onLogoutClick: () -> Unit = {},
      onPreferencesClick: () -> Unit = {},
      onSavedArtworkClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MaterialTheme {
        UserProfileScreen(
            onLogoutClick = onLogoutClick,
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
