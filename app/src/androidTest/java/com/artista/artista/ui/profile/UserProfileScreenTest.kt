// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ColorImage
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.test.FakeImageLoaderEngine
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserRepositoryLocal
import com.artista.artista.model.user.UsernameCacheInMemory
import com.artista.artista.ui.artwork.failingImageEngine
import com.artista.artista.ui.artwork.installFakeImageLoader
import com.artista.artista.ui.artwork.waitUntilFallbackDisappears
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.utils.TEST_OTHER_PICTURE_URL
import com.artista.artista.utils.TEST_PICTURE_URL
import com.artista.artista.utils.userNamed
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
/**
 * Verifies the user-visible behavior of the user profile screen.
 *
 * @author Timz3rr
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
class UserProfileScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private companion object {
    /** Time the tests let a fake download take before failing, in milliseconds. */
    const val PICTURE_TIMEOUT_MILLIS = 5_000L
  }

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /** Verifies that the profile preview renders the profile and its Name card. */
  @Test
  fun userProfileScreenPreview_displaysSampleProfile() {
    composeTestRule.setContent { UserProfileScreenPreview() }

    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertIsDisplayed()
  }

  /** Verifies that the username-dialog preview renders its dialog. */
  @Test
  fun editUsernameDialogPreview_displaysDialog() {
    composeTestRule.setContent { EditUsernameDialogPreview() }

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertIsDisplayed()
  }

  /** Verifies that every element of the profile screen is displayed. */
  @Test
  fun userProfileScreen_displaysAllElements() {
    setProfileContent()

    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
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
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
  }

  /** Verifies that the username prefilled in the edit dialog follows the state of the ViewModel. */
  @Test
  fun usernameDialog_prefillsUsername_whenUserChanges() {
    val viewModel = signedOutViewModel()
    setProfileContent(viewModel = viewModel)

    composeTestRule.runOnIdle { viewModel.setUser(userNamed("Bob")) }
    openUsernameDialog()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
        .assertEditableTextEquals("Bob")
  }

  /** Verifies that the preferences and saved artwork cards display their titles. */
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

  /** Verifies that the header shows the title and subtitle of the screen. */
  @Test
  fun userProfileScreen_displaysHeader() {
    setProfileContent()

    composeTestRule.onNodeWithText(string(R.string.profile_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.profile_subtitle)).assertIsDisplayed()
  }

  /** Verifies that the personal space label sits above the cards. */
  @Test
  fun userProfileScreen_displaysPersonalSpaceLabel() {
    setProfileContent()

    composeTestRule
        .onNodeWithText(string(R.string.profile_personal_space))
        .performScrollTo()
        .assertIsDisplayed()
  }

  /** Verifies that each personal space card shows its title and subtitle. */
  @Test
  fun personalSpaceCards_displayTheirTitlesAndSubtitles() {
    setProfileContent()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME)
        .assertTextContains(string(R.string.profile_name))
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .assertTextContains(string(R.string.profile_preferences_subtitle))
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .assertTextContains(string(R.string.profile_saved_artwork_subtitle))
  }

  /** Verifies that the Name card shows the username as its subtitle instead of the prompt. */
  @Test
  fun nameCard_showsUsernameAsSubtitle_whenUserHasUsername() {
    setProfileContentFor(userNamed("Alice"))

    val nameCard = composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME)
    nameCard.assertTextContains("Alice")
    nameCard.assert(!hasText(string(R.string.profile_name_subtitle)))
  }

  /** Verifies that the Name card falls back to the prompt when the user has no username. */
  @Test
  fun nameCard_showsPrompt_whenUserHasNoUsername() {
    setProfileContentFor(userNamed(null))

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME)
        .assertTextContains(string(R.string.profile_name_subtitle))
  }

  /** Verifies that the Name card subtitle follows the state of the ViewModel. */
  @Test
  fun nameCard_updatesSubtitle_whenUserChanges() {
    val viewModel = signedOutViewModel()
    setProfileContent(viewModel = viewModel)

    composeTestRule.runOnIdle { viewModel.setUser(userNamed("Bob")) }

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextContains("Bob")
  }

  /** Verifies that a username saved from the dialog shows up on the Name card. */
  @Test
  fun usernameDialog_save_showsNewUsernameOnNameCard() {
    setProfileContentFor(userNamed("Alice"))

    openUsernameDialog()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).performTextReplacement("Bob")
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_SAVE).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).assertTextContains("Bob")
  }

  /** Verifies that the edit photo button is shown on the banner with its label. */
  @Test
  fun editPhotoButton_isDisplayedWithItsLabel() {
    setProfileContent()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.EDIT_PHOTO_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.profile_edit_photo))
  }

  /** Verifies that the change photo sheet stays closed until the user asks for it. */
  @Test
  fun changePhotoSheet_isClosedByDefault() {
    setProfileContent()

    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SHEET).assertDoesNotExist()
  }

  /** Verifies that clicking the edit photo button opens the sheet with all its elements. */
  @Test
  fun editPhotoButton_click_opensChangePhotoSheet() {
    setProfileContent()

    openChangePhotoSheet()

    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SHEET).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.profile_change_photo_title)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.profile_change_photo_subtitle))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_LIBRARY_OPTION)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SAVED_ARTWORK_OPTION)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_CANCEL).assertIsDisplayed()
  }

  /** Verifies that each option of the sheet shows its title and subtitle. */
  @Test
  fun changePhotoSheet_optionsDisplayTheirTitlesAndSubtitles() {
    setProfileContent()

    openChangePhotoSheet()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_LIBRARY_OPTION)
        .assertTextContains(string(R.string.profile_change_photo_library))
        .assertTextContains(string(R.string.profile_change_photo_library_subtitle))
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SAVED_ARTWORK_OPTION)
        .assertTextContains(string(R.string.profile_change_photo_saved_artwork))
        .assertTextContains(string(R.string.profile_change_photo_saved_artwork_subtitle))
  }

  /**
   * Verifies that the photo source options are displayed but not clickable, since picking a photo
   * is not implemented yet.
   */
  @Test
  fun changePhotoSheet_optionsAreNotClickable() {
    setProfileContent()

    openChangePhotoSheet()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_LIBRARY_OPTION)
        .assertHasNoClickAction()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SAVED_ARTWORK_OPTION)
        .assertHasNoClickAction()
  }

  /** Verifies that the cancel button closes the sheet. */
  @Test
  fun changePhotoSheet_cancel_closesSheet() {
    setProfileContent()
    openChangePhotoSheet()

    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_CANCEL).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SHEET).assertDoesNotExist()
  }

  /** Verifies that the sheet can be opened again after being closed. */
  @Test
  fun changePhotoSheet_canBeReopenedAfterCancel() {
    setProfileContent()
    openChangePhotoSheet()
    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_CANCEL).performClick()

    openChangePhotoSheet()

    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SHEET).assertIsDisplayed()
  }

  /** Verifies that the sheet stays open when the screen state is restored, as on a rotation. */
  @Test
  fun changePhotoSheet_staysOpen_afterStateRestoration() {
    val restorationTester = StateRestorationTester(composeTestRule)
    val viewModel = signedOutViewModel()
    restorationTester.setContent {
      MaterialTheme { UserProfileScreen(onLogoutClick = {}, userProfileViewModel = viewModel) }
    }
    openChangePhotoSheet()

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_SHEET).assertIsDisplayed()
  }

  /** Verifies that opening the sheet does not trigger any of the screen's callbacks. */
  @Test
  fun editPhotoButton_click_invokesNoCallback() {
    var callbackInvoked = false
    setProfileContent(
        onLogoutClick = { callbackInvoked = true },
        onPreferencesClick = { callbackInvoked = true },
        onSavedArtworkClick = { callbackInvoked = true },
        onTabSelected = { callbackInvoked = true },
    )

    openChangePhotoSheet()
    composeTestRule.onNodeWithTag(UserProfileTestTags.CHANGE_PHOTO_CANCEL).performClick()

    composeTestRule.runOnIdle { assertFalse(callbackInvoked) }
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

    // The button sits below the cards, so it may start off screen on small displays.
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON)
        .performScrollTo()
        .performClick()

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

  /** Verifies that clicking the Name card opens the dialog used to edit the username. */
  @Test
  fun username_click_opensEditDialog() {
    setProfileContent()

    openUsernameDialog()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertIsDisplayed()
  }

  /** Verifies that the dialog starts from the current username, so a small change is quick. */
  @Test
  fun usernameDialog_prefillsCurrentUsername() {
    val viewModel = signedOutViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    openUsernameDialog()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
        .assertEditableTextEquals("Alice")
  }

  /** Verifies that the field starts empty when the user has no username yet. */
  @Test
  fun usernameDialog_startsEmpty_whenUserHasNoUsername() {
    setProfileContent()

    openUsernameDialog()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).assertEditableTextEquals("")
  }

  /** Verifies that saving closes the dialog and keeps the entered username for the next edit. */
  @Test
  fun usernameDialog_save_displaysNewUsername() {
    val viewModel = signedOutViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    openUsernameDialog()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).performTextReplacement("Bob")
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_SAVE).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertDoesNotExist()
    // The profile screen no longer shows the username, so the reopened dialog is where it appears.
    openUsernameDialog()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
        .assertEditableTextEquals("Bob")
  }

  /** Verifies that cancelling closes the dialog and discards the entered username. */
  @Test
  fun usernameDialog_cancel_keepsPreviousUsername() {
    val viewModel = signedOutViewModel().apply { setUser(userNamed("Alice")) }
    setProfileContent(viewModel = viewModel)

    openUsernameDialog()
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_FIELD).performTextReplacement("Bob")
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_CANCEL).performClick()

    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME_DIALOG).assertDoesNotExist()
    openUsernameDialog()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME_FIELD)
        .assertEditableTextEquals("Alice")
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

  /** Checks that the picture of the user replaces the placeholder once it has been downloaded. */
  @Test
  fun profileWithPicture_displaysTheImage() {
    installPictureImageLoader()
    setProfileContentFor(userWithPicture(TEST_PICTURE_URL))

    scrollToPictureArea()
    composeTestRule.waitUntilFallbackDisappears(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER)

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_IMAGE, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that a user without a picture gets the placeholder and no image. */
  @Test
  fun profileWithoutPicture_displaysThePlaceholder() {
    setProfileContentFor(userWithPicture(null))

    scrollToPictureArea()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_IMAGE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /**
   * Checks that the picture is drawn inside the picture area, so that it is neither offset nor
   * clipped away by the shape of the area.
   */
  @Test
  fun profilePicture_isDisplayedInsideThePictureArea() {
    installPictureImageLoader()
    setProfileContentFor(userWithPicture(TEST_PICTURE_URL))

    scrollToPictureArea()
    composeTestRule.waitUntilFallbackDisappears(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER)

    val area =
        composeTestRule
            .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE)
            .fetchSemanticsNode()
            .boundsInRoot
    val picture =
        composeTestRule
            .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_IMAGE, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

    assertTrue("the picture starts before its area", picture.left >= area.left)
    assertTrue("the picture starts above its area", picture.top >= area.top)
    assertTrue("the picture ends after its area", picture.right <= area.right)
    assertTrue("the picture ends below its area", picture.bottom <= area.bottom)
  }

  /**
   * Checks that a picture whose download fails leaves the placeholder on screen, without reporting
   * an error of its own: a missing picture is not something the user must act on.
   */
  @Test
  fun profilePicture_whenTheDownloadFails_showsThePlaceholder() {
    var imageRequested = false
    installFakeImageLoader(failingImageEngine(TEST_PICTURE_URL) { imageRequested = true })
    val viewModel = setProfileContentFor(userWithPicture(TEST_PICTURE_URL))

    scrollToPictureArea()
    // Waits for the attempt, so the assertions below run once the failure is known.
    composeTestRule.waitUntil(PICTURE_TIMEOUT_MILLIS) { imageRequested }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER, useUnmergedTree = true)
        .assertIsDisplayed()
    assertNull(viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that a loaded picture is announced to accessibility services. */
  @Test
  fun profilePicture_whenItLoads_hasAnAccessibilityDescription() {
    installPictureImageLoader()
    setProfileContentFor(userWithPicture(TEST_PICTURE_URL))

    scrollToPictureArea()
    composeTestRule.waitUntilFallbackDisappears(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER)

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_IMAGE, useUnmergedTree = true)
        .assertContentDescriptionEquals(
            composeTestRule.activity.getString(R.string.profile_picture_description),
        )
  }

  /** Checks that no download is attempted while the user has no picture. */
  @Test
  fun profileWithoutPicture_requestsNoImage() {
    val requested = mutableListOf<Any?>()
    installFakeImageLoader(recordingImageEngine(requested))
    setProfileContentFor(userWithPicture(null))

    scrollToPictureArea()
    composeTestRule.waitForIdle()

    assertTrue("an image was requested: $requested", requested.isEmpty())
  }

  /** Checks that the picture on screen follows the profile it is displaying. */
  @Test
  fun changingUser_replacesTheDisplayedPicture() {
    val requested = mutableListOf<Any?>()
    installFakeImageLoader(recordingImageEngine(requested))
    val viewModel = setProfileContentFor(userWithPicture(TEST_PICTURE_URL))

    scrollToPictureArea()
    composeTestRule.waitUntilFallbackDisappears(UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER)

    composeTestRule.runOnIdle { viewModel.setUser(userWithPicture(TEST_OTHER_PICTURE_URL)) }
    composeTestRule.waitUntil(PICTURE_TIMEOUT_MILLIS) { TEST_OTHER_PICTURE_URL in requested }

    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE_IMAGE, useUnmergedTree = true)
        .assertIsDisplayed()
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

  /**
   * Builds a profile carrying the given picture link, so that the picture tests drive the screen
   * through the state its ViewModel really exposes.
   *
   * @param photoUrl link of the picture, or null for a user without one
   */
  private fun userWithPicture(photoUrl: String?): User =
      userNamed("Alice").copy(photoUrl = photoUrl)

  /**
   * Replaces Coil's process-wide loader with one serving a picture for [TEST_PICTURE_URL], so the
   * avatar loads without reaching the network.
   */
  private fun installPictureImageLoader() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder()
            .intercept(TEST_PICTURE_URL, ColorImage(Color.Red.toArgb()))
            .build()
    )
  }

  /**
   * Builds a fake engine that answers every request with an image and records what it was asked
   * for, so a test can tell which links the screen requested.
   *
   * @param requested collects the data of each request
   */
  private fun recordingImageEngine(requested: MutableList<Any?>): FakeImageLoaderEngine =
      FakeImageLoaderEngine.Builder()
          .intercept(
              predicate = { data ->
                requested += data
                true
              },
              image = ColorImage(Color.Red.toArgb()),
          )
          .build()

  /**
   * Displays a profile screen whose ViewModel holds the given user, with nobody signed in so the
   * tests never reach Firebase.
   *
   * @param user the profile displayed by the screen
   * @return the ViewModel driving the screen
   */
  private fun setProfileContentFor(user: User): UserProfileViewModel {
    val viewModel = signedOutViewModel().apply { setUser(user) }
    setProfileContent(viewModel = viewModel)
    return viewModel
  }

  /**
   * Resolves a string resource, so the tests compare against the same text as the screen.
   *
   * @param resId the string resource to resolve
   * @return the resolved text
   */
  private fun string(resId: Int): String = composeTestRule.activity.getString(resId)

  /** Simulates the user clicking the edit photo button, which opens the change photo sheet. */
  private fun openChangePhotoSheet() {
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.EDIT_PHOTO_BUTTON)
        .performScrollTo()
        .performClick()
    // The sheet animates in, so the assertions wait for it to settle.
    composeTestRule.waitForIdle()
  }

  /** Scrolls the picture area into view, since it starts off screen on small displays. */
  private fun scrollToPictureArea() {
    composeTestRule.onNodeWithTag(UserProfileTestTags.PROFILE_PICTURE).performScrollTo()
  }

  /**
   * Simulates the user clicking the Name card, which opens the dialog used to edit the username.
   * Shared by the dialog tests so that each of them starts from an open dialog.
   */
  private fun openUsernameDialog() {
    // The profile content is scrollable: on a small screen the card may be off screen, and a click
    // on a node that is not displayed fails.
    composeTestRule.onNodeWithTag(UserProfileTestTags.USERNAME).performScrollTo().performClick()
  }

  /**
   * Builds a ViewModel with nobody signed in, so these tests drive the displayed username through
   * [UserProfileViewModel.setUser] and never reach Firebase.
   */
  private fun signedOutViewModel(): UserProfileViewModel =
      UserProfileViewModel(FakeAuthRepository(), UserRepositoryLocal(), UsernameCacheInMemory())

  private fun setProfileContent(
      viewModel: UserProfileViewModel = signedOutViewModel(),
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
