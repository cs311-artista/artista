package com.artista.artista.ui.profile

import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [UserProfileViewModel].
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

  /**
   * Builds a test user that differs only by its name, since the profile only displays the name.
   *
   * @param userName the name of the user, or null for a user without a name.
   * @return a user with a fixed uid and default preferences.
   */
  private fun userNamed(userName: String?): User =
      User(uid = "uid-1", userName = userName, preference = UserPreference())
}
