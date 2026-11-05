// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.profile

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/**
 * Tests how the profile screen reports a failure to the user.
 *
 * It runs on Robolectric rather than with the instrumented profile tests because the fake that
 * simulates failures, [FakeUserRepository], belongs to the unit test sources.
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
class UserProfileScreenErrorTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Checks that a failed load is shown in a toast, then cleared so that it is not shown again when
   * the screen is recomposed.
   */
  @Test
  fun userProfileScreen_whenLoadFails_showsErrorToastOnce() {
    val userRepository = FakeUserRepository().apply { readFailure = AppException.Network() }
    val viewModel = UserProfileViewModel(FakeAuthRepository(FakeAuthUser("uid-1")), userRepository)

    composeTestRule.setContent {
      MaterialTheme { UserProfileScreen(onLogoutClick = {}, userProfileViewModel = viewModel) }
    }
    composeTestRule.waitForIdle()

    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(context.getString(R.string.error_network), ShadowToast.getTextOfLatestToast())
    assertNull(viewModel.uiState.value.errorMessageResId)
  }
}
