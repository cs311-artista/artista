// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
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
 * Drives the ViewModel through a [FakeAuthRepository] injected at construction, covering the
 * session it derives from the repository - restoring, signed out and signed in - along with the
 * loading flag, error messages, re-entrancy guards and retries.
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
  private lateinit var userRepository: FakeUserRepository
  private lateinit var viewModel: AuthViewModel

  @Before
  fun setUp() {
    repository = FakeAuthRepository()
    userRepository = FakeUserRepository()
    viewModel = AuthViewModel(repository, userRepository)
  }

  @Test
  fun initialStateIsRestoring() {
    runTest {
      repository = FakeAuthRepository().apply { authStateIsPending = true }
      viewModel = AuthViewModel(repository, userRepository)

      advanceUntilIdle()
      // Nothing has been reported yet, so nothing can be decided: offering a sign-in would
      // contradict the session the repository may be about to restore.
      assertEquals(AuthSession.Restoring, viewModel.uiState.value.session)
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun signedOutOnceTheRepositoryReportsNobodyIsSignedIn() {
    runTest {
      advanceUntilIdle()
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun uiStateTracksAuthStateChanges() {
    runTest {
      givenSignedInUser()
      assertEquals(userId, viewModel.signedInUid())

      repository.setAuthState(null)
      advanceUntilIdle()
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)
    }
  }

  @Test
  fun authStateFailureIsReportedAndFailsClosedToSignedOut() {
    runTest {
      repository = FakeAuthRepository().apply { authStateError = IllegalStateException("stream") }
      viewModel = AuthViewModel(repository, userRepository)
      advanceUntilIdle()

      // Reported even though no collector was listening: the message is state, not an event.
      assertEquals(R.string.error_unknown, viewModel.uiState.value.repositoryErrorMessageRes)
      // Nothing will ever be emitted again, so the session must not stay restoring forever: being
      // signed out is the recoverable situation, and it is also where the message can be shown.
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)
    }
  }

  @Test
  fun signInOnSuccessSignsInForwardsTokenAndShowsNoError() {
    runTest {
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(userId, viewModel.signedInUid())
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
  fun signInLeavesTheSessionSignedOutOnFailure() {
    runTest {
      repository.signInError = RuntimeException(signInErrorMessage)
      viewModel.signIn(token1)
      advanceUntilIdle()
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)
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
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)

      repository.signInError = null
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token2)
      advanceUntilIdle()
      assertEquals(userId, viewModel.signedInUid())
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
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)
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
  fun signOutIsIgnoredWhenNobodyIsSignedIn() {
    runTest {
      advanceUntilIdle()
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(0, repository.signOutCallCount)
    }
  }

  @Test
  fun signOutKeepsTheSessionSignedInAndReportsNothingOnFailure() {
    runTest {
      givenSignedInUser()
      repository.signOutError = RuntimeException(signOutErrorMessage)
      viewModel.signOut()
      advanceUntilIdle()
      // The session still holds the user, which already says the sign-out did not happen. Reporting
      // it would add a message with no screen to carry it: while a user is signed in the sign-in
      // screen is not composed, and this message is only ever shown there.
      assertEquals(userId, viewModel.signedInUid())
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
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
      assertEquals(userId, viewModel.signedInUid())

      repository.signOutError = null
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)
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
      assertEquals(userId, viewModel.signedInUid())

      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(AuthSession.SignedOut, viewModel.uiState.value.session)

      viewModel.signIn(token2)
      advanceUntilIdle()
      assertEquals(userId, viewModel.signedInUid())
      assertEquals(token2, repository.lastIdToken)
    }
  }

  @Test
  fun signInOnFirstSignInCreatesDefaultProfile() {
    runTest {
      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token1)
      advanceUntilIdle()

      val expectedProfile = User(uid = userId, preference = UserPreference())
      assertEquals(expectedProfile, userRepository.storedUser(userId))
    }
  }

  @Test
  fun signInOnExistingUserLeavesProfileUnchanged() {
    runTest {
      val existingProfile =
          User(
              uid = userId,
              userName = "Alice",
              preference = UserPreference(artists = listOf("artist-1")),
          )
      userRepository.store(existingProfile)

      repository.user = FakeAuthUser(userId)
      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(existingProfile, userRepository.storedUser(userId))
    }
  }

  @Test
  fun signInWhenProfileCreationFailsDoesNotBlockSignIn() {
    runTest {
      repository.user = FakeAuthUser(userId)
      userRepository.writeFailure = AppException.Network("offline")

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(userId, viewModel.signedInUid())
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  /**
   * Returns the uid of the user the state says is signed in.
   *
   * @return the uid, or `null` when the session is not [AuthSession.SignedIn]
   * @author timo-by
   */
  private fun AuthViewModel.signedInUid(): String? =
      (uiState.value.session as? AuthSession.SignedIn)?.user?.uid

  /**
   * Rebuilds the repository and ViewModel with a user already signed in, and lets the ViewModel
   * settle so it has observed the initial authentication state.
   *
   * @param uid the uid of the signed-in user
   * @author timo-by
   */
  private fun TestScope.givenSignedInUser(uid: String = userId) {
    repository = FakeAuthRepository(FakeAuthUser(uid))
    viewModel = AuthViewModel(repository, userRepository)
    advanceUntilIdle()
  }
}
