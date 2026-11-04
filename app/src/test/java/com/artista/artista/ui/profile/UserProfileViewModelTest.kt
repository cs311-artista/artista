package com.artista.artista.ui.profile

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserId
import com.artista.artista.model.user.UserPreference
import com.artista.artista.utils.userNamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [UserProfileViewModel], the saving of the username for the signed-in
 * user, and the username validation rule.
 *
 * @author Timz3rr
 */
class UserProfileViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private lateinit var viewModel: UserProfileViewModel

  private companion object {
    /** Identifier of the users built by [userNamed]. */
    const val UID = "uid-1"
    const val OTHER_UID = "uid-2"
  }

  @Before
  fun setUp() {
    viewModel = UserProfileViewModel(authRepository, userRepository)
  }

  /** Checks that the username stored for the signed-in user is displayed once loaded. */
  @Test
  fun signIn_displaysStoredUsername() {
    userRepository.store(userNamed("Alice"))

    signIn()

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /**
   * Checks that a first sign-in, with no profile stored yet, shows the placeholder and no error.
   */
  @Test
  fun signIn_withoutStoredUser_displaysNoUsernameAndNoError() {
    signIn()

    assertNull(viewModel.uiState.value.username)
    assertNull(viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that a failed load reports the message of its failure. */
  @Test
  fun signIn_whenLoadFails_showsErrorMessage() {
    userRepository.readFailure = AppException.Network()

    signIn()

    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that signing in as another user replaces the username with theirs. */
  @Test
  fun signIn_asAnotherUser_displaysTheirUsername() {
    userRepository.store(userNamed("Alice"))
    userRepository.store(User(uid = OTHER_UID, userName = "Bob", preference = UserPreference()))

    signIn()
    signIn(OTHER_UID)

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /** Checks that signing out clears the username of the user who left. */
  @Test
  fun signOut_clearsUsername() {
    userRepository.store(userNamed("Alice"))
    signIn()

    authRepository.setAuthState(null)
    runPendingCoroutines()

    assertNull(viewModel.uiState.value.username)
  }

  /**
   * Checks that being signed out from the start does not clear a username already displayed, since
   * no user left.
   */
  @Test
  fun signedOutFromStart_keepsDisplayedUsername() {
    viewModel.setUser(userNamed("Alice"))

    runPendingCoroutines()

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Checks that a new username is saved for the signed-in user. */
  @Test
  fun updateUsername_whenSignedIn_savesUsername() {
    userRepository.store(userNamed("Alice"))
    signIn()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Bob", userRepository.storedUser(UID)?.userName)
  }

  /** Checks that saving the username keeps the other stored fields, such as the preferences. */
  @Test
  fun updateUsername_keepsStoredPreferences() {
    val preference = UserPreference(artists = listOf("VanGogh"))
    userRepository.store(userNamed("Alice").copy(preference = preference))
    signIn()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals(preference, userRepository.storedUser(UID)?.preference)
  }

  /** Checks that the first username of a user with no stored profile creates that profile. */
  @Test
  fun updateUsername_withoutStoredUser_createsUser() {
    signIn()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals(
        User(uid = UID, userName = "Bob", preference = UserPreference()),
        userRepository.storedUser(UID),
    )
  }

  /** Checks that without a signed-in user the username is displayed but not saved. */
  @Test
  fun updateUsername_whenSignedOut_displaysButDoesNotSave() {
    runPendingCoroutines()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Bob", viewModel.uiState.value.username)
    assertEquals(0, userRepository.writeCount)
  }

  /** Checks that a failed save puts the previous username back and reports the failure. */
  @Test
  fun updateUsername_whenSaveFails_restoresPreviousUsernameAndShowsError() {
    userRepository.store(userNamed("Alice"))
    signIn()
    userRepository.writeFailure = AppException.Network()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Alice", viewModel.uiState.value.username)
    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that an invalid username is not saved. */
  @Test
  fun updateUsername_withInvalidName_doesNotSave() {
    userRepository.store(userNamed("Alice"))
    signIn()

    viewModel.updateUsername("Alice!")
    runPendingCoroutines()

    assertEquals(0, userRepository.writeCount)
  }

  /** Checks that the error message is removed once the screen has shown it. */
  @Test
  fun clearErrorMessage_removesErrorMessage() {
    userRepository.readFailure = AppException.Network()
    signIn()

    viewModel.clearErrorMessage()

    assertNull(viewModel.uiState.value.errorMessageResId)
  }

  /** Verifies that no username is displayed before a user is provided. */
  @Test
  fun initialState_hasNoUsername() {
    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that the username of the provided user is exposed. */
  @Test
  fun setUser_exposesUsername() {
    viewModel.setUser(userNamed("Alice"))

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Verifies that a user without a username exposes no username. */
  @Test
  fun setUser_withoutUsername_exposesNoUsername() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.setUser(userNamed(null))

    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that a blank username is treated as missing so the placeholder is shown instead. */
  @Test
  fun setUser_withBlankUsername_exposesNoUsername() {
    viewModel.setUser(userNamed("   "))

    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that providing another user replaces the previously displayed username. */
  @Test
  fun setUser_replacesPreviousUsername() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.setUser(userNamed("Bob"))

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /** Verifies that a valid username replaces the placeholder. */
  @Test
  fun updateUsername_exposesNewUsername() {
    viewModel.updateUsername("Alice")

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Verifies that a username entered by the user replaces the one of the provided user. */
  @Test
  fun updateUsername_replacesUsernameSetByUser() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.updateUsername("Bob")

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /** Verifies that surrounding whitespace typed by mistake is not stored in the username. */
  @Test
  fun updateUsername_trimsWhitespace() {
    viewModel.updateUsername("  John Doe  ")

    assertEquals("John Doe", viewModel.uiState.value.username)
  }

  /** Verifies that an invalid username leaves the current username unchanged. */
  @Test
  fun updateUsername_withInvalidName_keepsPreviousUsername() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.updateUsername("Alice!")

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Verifies that an invalid username does not replace the placeholder. */
  @Test
  fun updateUsername_withInvalidName_keepsPlaceholder() {
    viewModel.updateUsername("   ")

    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that the username is stored in composed form, whatever form it was typed in. */
  @Test
  fun updateUsername_storesComposedForm() {
    viewModel.updateUsername("Timothe\u0301e")

    assertEquals("Timothée", viewModel.uiState.value.username)
  }

  /** Verifies that letters, digits and spaces are accepted. */
  @Test
  fun isValidUsername_acceptsLettersDigitsAndSpaces() {
    assertTrue(isValidUsername("John Doe 42"))
  }

  /** Verifies that accented letters are accepted, so users can write their real name. */
  @Test
  fun isValidUsername_acceptsAccentedLetters() {
    assertTrue(isValidUsername("Timothée"))
  }

  /** Verifies that accents typed in decomposed form (letter + combining mark) are accepted. */
  @Test
  fun isValidUsername_acceptsDecomposedAccents() {
    assertTrue(isValidUsername("Timothe\u0301e"))
  }

  /** Verifies that a combining accent does not count as an extra character for the length limit. */
  @Test
  fun isValidUsername_countsDecomposedAccentAsOneCharacter() {
    assertTrue(isValidUsername("e\u0301".repeat(MAX_USERNAME_LENGTH)))
  }

  /** Verifies that surrounding whitespace is ignored, since it is trimmed before saving. */
  @Test
  fun isValidUsername_ignoresSurroundingWhitespace() {
    assertTrue(isValidUsername("  Alice  "))
  }

  /** Verifies that an empty or blank username is rejected. */
  @Test
  fun isValidUsername_rejectsBlankName() {
    assertFalse(isValidUsername(""))
    assertFalse(isValidUsername("   "))
  }

  /** Verifies that a username of exactly the maximum length is accepted. */
  @Test
  fun isValidUsername_acceptsMaxLength() {
    assertTrue(isValidUsername("a".repeat(MAX_USERNAME_LENGTH)))
  }

  /** Verifies that a username longer than the maximum length is rejected. */
  @Test
  fun isValidUsername_rejectsTooLongName() {
    assertFalse(isValidUsername("a".repeat(MAX_USERNAME_LENGTH + 1)))
  }

  /** Verifies that the length is measured after trimming, as the trimmed name is what is saved. */
  @Test
  fun isValidUsername_measuresLengthAfterTrimming() {
    assertTrue(isValidUsername("  " + "a".repeat(MAX_USERNAME_LENGTH) + "  "))
  }

  /** Verifies that symbols and punctuation are rejected. */
  @Test
  fun isValidUsername_rejectsSpecialCharacters() {
    assertFalse(isValidUsername("Tim_42"))
    assertFalse(isValidUsername("tim@epfl"))
    assertFalse(isValidUsername("Alice!"))
  }

  /** Verifies that tabs and line breaks are rejected: only plain spaces may separate words. */
  @Test
  fun isValidUsername_rejectsTabsAndLineBreaks() {
    assertFalse(isValidUsername("John\tDoe"))
    assertFalse(isValidUsername("John\nDoe"))
  }

  /**
   * Signs in as the user with the given identifier and lets the ViewModel load their profile.
   *
   * @param uid identifier of the user to sign in as
   */
  private fun signIn(uid: UserId = UID) {
    authRepository.setAuthState(FakeAuthUser(uid))
    runPendingCoroutines()
  }

  /** Runs the coroutines the ViewModel launched, such as loading or saving the profile. */
  private fun runPendingCoroutines() {
    mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
  }
}
