// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [AuthViewModel].
 *
 * Drives the ViewModel through a [FakeAuthRepository] injected at construction, covering both the
 * sign-in and sign-out flows: state, loading, error messages, re-entrancy guards and retries.
 *
 * @author timo-by
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val userId = "user-1"
  private val token1 = "token-1"
  private val token2 = "token-2"
  private val signInErrorMessage = "boom"
  private val signOutErrorMessage = "sign-out failed"

  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: AuthViewModel

  @Before
  fun setUp() {
    repository = FakeAuthRepository()
    viewModel = AuthViewModel(repository)
  }

  @Test
  fun initialStateIsSignedOutAndNotLoading() {
    runTest {
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun uiStateTracksAuthStateChanges() {
    runTest {
      givenSignedInUser()
      assertEquals(userId, viewModel.uiState.value.user?.uid)

      repository.setAuthState(null)
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
    }
  }

  @Test
  fun authStateFailureIsReportedInTheUiState() {
    runTest {
      repository = FakeAuthRepository().apply { authStateError = IllegalStateException("stream") }
      viewModel = AuthViewModel(repository)
      advanceUntilIdle()

      // Reported even though no collector was listening: the message is state, not an event.
      assertEquals(R.string.error_unknown, viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signInOnSuccessSignsInForwardsTokenAndShowsNoError() {
    runTest {
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(userId, viewModel.uiState.value.user?.uid)
      assertEquals(token1, repository.lastIdToken)
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signInTogglesLoadingAroundTheOperation() {
    runTest {
      viewModel.signIn(token1)
      assertTrue(viewModel.uiState.value.isLoading)

      advanceUntilIdle()
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun signInLeavesTheUserSignedOutOnFailure() {
    runTest {
      repository.signInError = RuntimeException(signInErrorMessage)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun signInReportsAFailureThatIsNotAnAppExceptionAsUnknown() {
    runTest {
      repository.signInError = RuntimeException(signInErrorMessage)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(R.string.error_unknown, viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signInShowsTheMessageMatchingTheAppExceptionOfTheRepository() {
    runTest {
      repository.signInError = AppException.Network("offline")
      viewModel.signIn(token1)
      advanceUntilIdle()
      // The repository's AppException is not re-classified as unknown: its own message is shown.
      assertEquals(R.string.error_network, viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signInIsIgnoredWhileAlreadyInProgress() {
    runTest {
      viewModel.signIn(token1)
      viewModel.signIn(token2)
      advanceUntilIdle()
      assertEquals(1, repository.signInCallCount)
      assertEquals(token1, repository.lastIdToken)
    }
  }

  @Test
  fun signInIsIgnoredWhenAlreadySignedIn() {
    runTest {
      givenSignedInUser()
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(0, repository.signInCallCount)
    }
  }

  @Test
  fun signInRetrySucceedsAfterAFailure() {
    runTest {
      repository.signInError = RuntimeException(signInErrorMessage)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)

      repository.signInError = null
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token2)
      advanceUntilIdle()
      assertEquals(userId, viewModel.uiState.value.user?.uid)
      assertEquals(token2, repository.lastIdToken)
      assertEquals(2, repository.signInCallCount)
    }
  }

  @Test
  fun signOutSignsOutAndShowsNoError() {
    runTest {
      givenSignedInUser()
      viewModel.signOut()
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signOutTogglesLoadingAroundTheOperation() {
    runTest {
      givenSignedInUser()
      viewModel.signOut()
      assertTrue(viewModel.uiState.value.isLoading)

      advanceUntilIdle()
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun signOutIsIgnoredWhenAlreadySignedOut() {
    runTest {
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(0, repository.signOutCallCount)
    }
  }

  @Test
  fun signOutKeepsUserSignedInAndShowsTheErrorOnFailure() {
    runTest {
      givenSignedInUser()
      repository.signOutError = RuntimeException(signOutErrorMessage)
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(userId, viewModel.uiState.value.user?.uid)
      assertEquals(R.string.error_unknown, viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signOutIsIgnoredWhileAnOperationIsInProgress() {
    runTest {
      viewModel.signIn(token1)
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(0, repository.signOutCallCount)
    }
  }

  @Test
  fun signOutRetrySucceedsAfterAFailure() {
    runTest {
      givenSignedInUser()
      repository.signOutError = RuntimeException(signOutErrorMessage)
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(userId, viewModel.uiState.value.user?.uid)

      repository.signOutError = null
      viewModel.signOut()
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertEquals(2, repository.signOutCallCount)
    }
  }

  @Test
  fun aNewSignInAttemptDismissesTheMessageOfThePreviousOne() {
    runTest {
      repository.signInError = RuntimeException(signInErrorMessage)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(R.string.error_unknown, viewModel.uiState.value.repositoryErrorMessageRes)

      repository.signInError = null
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token2)
      // Cleared together with the loading flag, so a retry never shows a stale message.
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun clearRepositoryErrorDismissesTheMessageOfTheLastAttempt() {
    runTest {
      repository.signInError = RuntimeException(signInErrorMessage)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(R.string.error_unknown, viewModel.uiState.value.repositoryErrorMessageRes)

      // The screen clears it as soon as the user starts a new attempt, before the credential
      // interaction has resolved.
      viewModel.clearRepositoryError()
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  @Test
  fun signInSignOutSignInCycleStaysConsistent() {
    runTest {
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(userId, viewModel.uiState.value.user?.uid)

      viewModel.signOut()
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)

      viewModel.signIn(token2)
      advanceUntilIdle()
      assertEquals(userId, viewModel.uiState.value.user?.uid)
      assertEquals(token2, repository.lastIdToken)
    }
  }

  /**
   * Rebuilds the repository and ViewModel with a user already signed in, and lets the ViewModel
   * settle so it has observed the initial authentication state.
   *
   * @param uid the uid of the signed-in user
   * @author timo-by
   */
  private fun TestScope.givenSignedInUser(uid: String = userId) {
    repository = FakeAuthRepository(FakeAuthUser(uid))
    viewModel = AuthViewModel(repository)
    advanceUntilIdle()
  }
}
