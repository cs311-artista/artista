package com.artista.artista.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.utils.userNamed
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

  /** Verifies that clicking the username opens the dialog used to edit it. */
  @Test
  fun username_click_opensEditDialog() {
    setProfileContent()

    openUsernameDialog()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertIsDisplayed()
  }

  /** Verifies that the dialog starts from the current username, so a small change is quick. */
  @Test
  fun usernameDialog_prefillsCurrentUsername() {
    val viewModel = UserProfileViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    openUsernameDialog()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
        .assertEditableTextEquals("Alice")
  }

  /** Verifies that the placeholder is not copied into the field when the user has no username. */
  @Test
  fun usernameDialog_startsEmpty_whenUserHasNoUsername() {
    setProfileContent()

    openUsernameDialog()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).assertEditableTextEquals("")
  }

  /** Verifies that saving closes the dialog and displays the entered username. */
  @Test
  fun usernameDialog_save_displaysNewUsername() {
    val viewModel = UserProfileViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    openUsernameDialog()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).performTextReplacement("Bob")
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_SAVE).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals("Bob")
  }

  /** Verifies that cancelling closes the dialog and discards the entered username. */
  @Test
  fun usernameDialog_cancel_keepsPreviousUsername() {
    val viewModel = UserProfileViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    openUsernameDialog()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).performTextReplacement("Bob")
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_CANCEL).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextEquals("Alice")
  }

  /** Verifies that the save button is only enabled while the entered username is valid. */
  @Test
  fun usernameDialog_saveButton_isEnabledOnlyForValidUsername() {
    setProfileContent()
    openUsernameDialog()
    val field = composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
    val saveButton = composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_SAVE)

    field.performTextReplacement("")
    saveButton.assertIsNotEnabled()

    field.performTextReplacement("Tim_42")
    saveButton.assertIsNotEnabled()

    field.performTextReplacement("a".repeat(MAX_USERNAME_LENGTH + 1))
    saveButton.assertIsNotEnabled()

    field.performTextReplacement("Timothée 42")
    saveButton.assertIsEnabled()
  }

  /**
   * Simulates the user clicking the username field, which opens the dialog used to edit the
   * username. Shared by the dialog tests so that each of them starts from an open dialog.
   */
  private fun openUsernameDialog() {
    // The profile content is scrollable: on a small screen the field may be off screen, and a click
    // on a node that is not displayed fails.
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).performScrollTo().performClick()
  }

  /**
   * Asserts the content typed in a text field. It does not act on the screen: it only fails the
   * test when the field holds another text.
   *
   * [assertTextEquals] cannot be used here because the semantics of an OutlinedTextField expose the
   * label in [SemanticsProperties.Text] and the typed content in
   * [SemanticsProperties.EditableText], and [assertTextEquals] compares both.
   *
   * Declared as an extension so that it chains like the built-in assertions:
   * `onNodeWithTag(tag).assertEditableTextEquals("Alice")`.
   *
   * @param expected the text expected in the field.
   */
  private fun SemanticsNodeInteraction.assertEditableTextEquals(expected: String) {
    assert(
        // EditableText is stored as an AnnotatedString, so the expected String is wrapped to be
        // comparable with it.
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected))
    )
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
}
