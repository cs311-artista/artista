package com.artista.artista.ui.profile

import com.artista.artista.utils.userNamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [UserProfileViewModel] and the username validation rule.
 *
 * @author Timz3rr
 */
class UserProfileViewModelTest {

  private lateinit var viewModel: UserProfileViewModel

  @Before
  fun setUp() {
    viewModel = UserProfileViewModel()
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
}
