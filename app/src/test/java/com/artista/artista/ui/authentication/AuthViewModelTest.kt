// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.utils.TEST_OTHER_PICTURE_URL
import com.artista.artista.utils.TEST_PICTURE_URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
      assertTrue(viewModel.uiState.value.profileInitializationFailed)
    }
  }

  @Test
  fun signInWhenProfileLookupFailsDoesNotAttemptCreation() {
    runTest {
      repository.user = FakeAuthUser(userId)
      userRepository.readFailure = AppException.Network("offline")

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(userId, viewModel.signedInUid())
      assertEquals(0, userRepository.writeCount)
      assertTrue(viewModel.uiState.value.profileInitializationFailed)
    }
  }

  /**
   * Checks that cancellation during profile creation is propagated without becoming a sign-in
   * error.
   *
   * @author hixeum
   */
  @Test
  fun signInWhenProfileCreationIsCancelledPropagatesCancellation() {
    runTest {
      repository.user = FakeAuthUser(userId)
      userRepository.writeFailure = CancellationException("profile creation cancelled")

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(userId, viewModel.signedInUid())
      assertFalse(viewModel.uiState.value.isLoading)
      assertNull(viewModel.uiState.value.repositoryErrorMessageRes)
    }
  }

  /**
   * Checks that the factory uses the user repository supplied by its caller.
   *
   * The factory gets its own authentication repository: a session is now initialized by every
   * ViewModel that observes it, so sharing the one of this test with the second ViewModel would let
   * it write to [userRepository] as well, and the assertions below would no longer say which
   * repository the factory used.
   *
   * @author hixeum
   */
  @Test
  fun provideFactoryUsesThePassedUserRepository() {
    runTest {
      val providedUserRepository = FakeUserRepository()
      val factoryRepository = FakeAuthRepository()
      val viewModelStore = ViewModelStore()
      val factory =
          AuthViewModel.provideFactory(
              repository = factoryRepository,
              userRepository = providedUserRepository,
          )
      val factoryViewModel = ViewModelProvider(viewModelStore, factory)[AuthViewModel::class.java]

      factoryRepository.user = FakeAuthUser(userId)
      factoryViewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(
          User(uid = userId, preference = UserPreference()),
          providedUserRepository.storedUser(userId),
      )
      assertNull(userRepository.storedUser(userId))
      viewModelStore.clear()
    }
  }

  /** Checks that a first sign-in creates the profile with the picture the provider reported. */
  @Test
  fun signIn_withoutStoredProfile_createsItWithTheProviderPicture() {
    runTest {
      givenProviderPicture(TEST_PICTURE_URL)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
    }
  }

  /**
   * Checks that a first sign-in creates the profile without a picture when the provider reports
   * none.
   */
  @Test
  fun signIn_withoutStoredProfileAndNoProviderPicture_createsItWithoutPicture() {
    runTest {
      givenProviderPicture(photoUrl = null)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertNull(userRepository.storedUser(userId)?.photoUrl)
    }
  }

  /** Checks that a stored profile without a picture is given the one the provider reports. */
  @Test
  fun signIn_withStoredProfileWithoutPicture_storesTheProviderPicture() {
    runTest {
      userRepository.store(User(uid = userId, userName = "Alice", preference = UserPreference()))
      givenProviderPicture(TEST_PICTURE_URL)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertEquals(1, userRepository.writeCount)
    }
  }

  /** Checks that a stored picture the provider disagrees with is replaced. */
  @Test
  fun signIn_withDifferentStoredPicture_replacesIt() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      givenProviderPicture(TEST_PICTURE_URL)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
    }
  }

  /**
   * Checks that a provider reporting no picture leaves the stored one untouched.
   *
   * @author timo-by
   */
  @Test
  fun signIn_whenProviderReportsNoPicture_keepsTheStoredOne() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_PICTURE_URL)
      )
      givenProviderPicture(photoUrl = null)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertEquals(0, userRepository.writeCount)
    }
  }

  /** Checks that refreshing the picture keeps the username and the preferences of the user. */
  @Test
  fun signIn_refreshingThePicture_keepsUsernameAndPreferences() {
    runTest {
      val preference = UserPreference(artists = listOf("VanGogh"), type = listOf("Painting"))
      userRepository.store(
          User(
              uid = userId,
              userName = "Alice",
              preference = preference,
              photoUrl = TEST_OTHER_PICTURE_URL,
          )
      )
      givenProviderPicture(TEST_PICTURE_URL)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(
          User(
              uid = userId,
              userName = "Alice",
              preference = preference,
              photoUrl = TEST_PICTURE_URL,
          ),
          userRepository.storedUser(userId),
      )
    }
  }

  /** Checks that a picture refresh still counts as a completed profile initialization. */
  @Test
  fun signIn_withPictureChange_stillCompletesProfileInitialization() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      givenProviderPicture(TEST_PICTURE_URL)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertFalse(viewModel.uiState.value.profileInitializationFailed)
      assertFalse(viewModel.uiState.value.isProfileInitializationInProgress)
    }
  }

  /** Checks that a failed picture refresh is retryable and keeps the session signed in. */
  @Test
  fun signIn_whenPictureRefreshFails_reportsRetryableFailureWithoutSigningOut() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      givenProviderPicture(TEST_PICTURE_URL)
      userRepository.writeFailure = AppException.Network("offline")

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertTrue(viewModel.uiState.value.profileInitializationFailed)
      assertEquals(userId, viewModel.signedInUid())
      assertEquals(0, repository.signOutCallCount)
    }
  }

  /**
   * Checks that the backend notifying the same user again does not make the profile be read again.
   *
   * The read is the cost a repeated emission would pay: a write it would not, since a picture that
   * already matches the provider is never written, so counting writes here would pass whether the
   * per-session guard works or not.
   */
  @Test
  fun signIn_withRepeatedAuthEmissionsOfSameUser_readsTheProfileOnce() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      givenProviderPicture(TEST_PICTURE_URL)
      viewModel.signIn(token1)
      advanceUntilIdle()

      repeat(3) { repository.reemitAuthState() }
      advanceUntilIdle()

      assertEquals(1, userRepository.readCount)
      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
    }
  }

  /**
   * Checks that a new session refreshes the picture again.
   *
   * The stored picture is stale at the second sign-in, so a refresh that did not run would leave it
   * as it was: the per-session guard of the first one must not outlive the session.
   */
  @Test
  fun signIn_afterARevokedSession_refreshesThePictureAgain() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      givenProviderPicture(TEST_PICTURE_URL)
      viewModel.signIn(token1)
      advanceUntilIdle()

      viewModel.signOut()
      advanceUntilIdle()
      // The provider reports another picture by the time the user signs in again.
      givenProviderPicture(TEST_OTHER_PICTURE_URL)
      viewModel.signIn(token2)
      advanceUntilIdle()

      assertEquals(TEST_OTHER_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertEquals(2, userRepository.writeCount)
    }
  }

  /**
   * Checks that a session restored from the device cache stores the provider's picture, without a
   * sign-in to drive it.
   */
  @Test
  fun restoredSession_withoutStoredProfile_storesTheProviderPicture() {
    runTest {
      givenRestoredSession(TEST_PICTURE_URL)

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertEquals(0, repository.signInCallCount)
    }
  }

  /** Checks that a restored session whose stored picture already matches does not write. */
  @Test
  fun restoredSession_withStoredPictureAlreadyMatching_doesNotWrite() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_PICTURE_URL)
      )

      givenRestoredSession(TEST_PICTURE_URL)

      assertEquals(0, userRepository.writeCount)
    }
  }

  /**
   * Checks that the refresh does not run again when the provider reports another picture during the
   * session: it happens once per session, not on every emission.
   */
  @Test
  fun restoredSession_whenTheProviderPictureChangesMidSession_doesNotWriteAgain() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      givenRestoredSession(TEST_PICTURE_URL)

      repository.setAuthState(FakeAuthUser(uid = userId, photoUrl = TEST_OTHER_PICTURE_URL))
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertEquals(1, userRepository.writeCount)
    }
  }

  /**
   * Checks that a refresh failing because the profile disappeared is retried, and never answered by
   * creating a default profile over the name and the preferences already stored.
   *
   * A NotFound from the lookup means the profile is absent; the same failure from the write means
   * the document vanished between the read and the write, which is a failure to retry, not a fresh
   * start.
   */
  @Test
  fun signIn_whenThePictureWriteFailsAsNotFound_doesNotRecreateTheProfile() {
    runTest {
      val storedProfile =
          User(
              uid = userId,
              userName = "Alice",
              preference = UserPreference(artists = listOf("VanGogh")),
              photoUrl = TEST_OTHER_PICTURE_URL,
          )
      userRepository.store(storedProfile)
      givenProviderPicture(TEST_PICTURE_URL)
      userRepository.writeFailure = AppException.NotFound("deleted by another device")

      viewModel.signIn(token1)
      advanceUntilIdle()

      // Only the refresh was attempted: creating a profile would have been a second write.
      assertEquals(1, userRepository.writeCount)
      assertEquals(storedProfile, userRepository.storedUser(userId))
      assertTrue(viewModel.uiState.value.profileInitializationFailed)
      assertEquals(userId, viewModel.signedInUid())
    }
  }

  /**
   * Checks that retrying after a failed refresh at launch stores the picture and keeps the rest of
   * the profile.
   *
   * A restored session never goes through [AuthViewModel.signIn], so the retry action is the only
   * way out of a failed initialization, which is what makes it worth a test of its own.
   */
  @Test
  fun restoredSession_whenTheRefreshFails_retryingStoresThePicture() {
    runTest {
      val preference = UserPreference(artists = listOf("VanGogh"), type = listOf("Painting"))
      userRepository.store(
          User(
              uid = userId,
              userName = "Alice",
              preference = preference,
              photoUrl = TEST_OTHER_PICTURE_URL,
          )
      )
      userRepository.writeFailure = AppException.InvalidData("corrupt profile")
      givenRestoredSession(TEST_PICTURE_URL)
      assertTrue(viewModel.uiState.value.profileInitializationFailed)

      userRepository.writeFailure = null
      viewModel.retryProfileInitialization()
      advanceUntilIdle()

      assertEquals(
          User(
              uid = userId,
              userName = "Alice",
              preference = preference,
              photoUrl = TEST_PICTURE_URL,
          ),
          userRepository.storedUser(userId),
      )
      assertFalse(viewModel.uiState.value.profileInitializationFailed)
      assertFalse(viewModel.uiState.value.isProfileInitializationInProgress)
      assertEquals(0, repository.signInCallCount)
    }
  }

  /** Checks that retrying while nobody is signed in neither reads nor writes a profile. */
  @Test
  fun retryProfileInitialization_whenSignedOut_doesNothing() {
    runTest {
      advanceUntilIdle()

      viewModel.retryProfileInitialization()
      advanceUntilIdle()

      assertEquals(0, userRepository.readCount)
      assertEquals(0, userRepository.writeCount)
    }
  }

  /**
   * Checks that a retry failing again runs the initialization again and raises the failure again,
   * so the snackbar offering the retry is shown once more.
   */
  @Test
  fun retryProfileInitialization_failingAgain_raisesTheFailureAgain() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      userRepository.writeFailure = AppException.InvalidData("corrupt profile")
      givenRestoredSession(TEST_PICTURE_URL)
      assertTrue(viewModel.uiState.value.profileInitializationFailed)

      viewModel.retryProfileInitialization()
      advanceUntilIdle()

      assertEquals(2, userRepository.writeCount)
      assertTrue(viewModel.uiState.value.profileInitializationFailed)
      assertFalse(viewModel.uiState.value.isProfileInitializationInProgress)
    }
  }

  /**
   * Checks that a refresh failing for lack of a connection during a restored session raises no
   * failure, and that the picture is left to the retry to heal.
   *
   * Being offline at launch is the normal state of a returning user rather than something they did,
   * and the profile screen stays quiet about it for the same reason: a snackbar at every start
   * without a connection would only be noise. The stored picture is healed by the next session that
   * has a connection, or by the retry.
   */
  @Test
  fun restoredSession_whenTheRefreshFailsWithNetwork_staysSilent() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      userRepository.writeFailure = AppException.Network("offline")

      givenRestoredSession(TEST_PICTURE_URL)

      assertFalse(viewModel.uiState.value.profileInitializationFailed)
      assertEquals(userId, viewModel.signedInUid())

      // The retry is a deliberate action, so it reports; here it succeeds, which heals the picture.
      userRepository.writeFailure = null
      viewModel.retryProfileInitialization()
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertFalse(viewModel.uiState.value.profileInitializationFailed)
    }
  }

  /**
   * Checks that signing out cancels a refresh still in flight, so it neither writes the profile nor
   * reports a failure for a session that is over.
   */
  @Test
  fun signOut_duringAnInFlightRefresh_cancelsTheWrite() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      userRepository.writeDelays[userId] = 1_000

      givenRestoredSessionPending(TEST_PICTURE_URL)
      viewModel.signOut()
      advanceUntilIdle()

      // The cancelled write never reached the backend, so it cannot store a picture for a session
      // that has ended, nor fail and raise the snackbar for the next one.
      assertEquals(0, userRepository.writeCount)
      assertFalse(viewModel.uiState.value.profileInitializationFailed)
      assertNull(viewModel.signedInUid())
    }
  }

  /**
   * Checks that a session signed in after an interrupted one initializes its profile in its turn.
   */
  @Test
  fun signOutDuringTheRefresh_thenSigningInAgain_refreshesThePicture() {
    runTest {
      userRepository.store(
          User(uid = userId, preference = UserPreference(), photoUrl = TEST_OTHER_PICTURE_URL)
      )
      userRepository.writeDelays[userId] = 1_000
      givenRestoredSessionPending(TEST_PICTURE_URL)

      viewModel.signOut()
      advanceUntilIdle()
      userRepository.writeDelays.clear()
      givenProviderPicture(TEST_PICTURE_URL)

      viewModel.signIn(token1)
      advanceUntilIdle()

      assertEquals(TEST_PICTURE_URL, userRepository.storedUser(userId)?.photoUrl)
      assertEquals(1, userRepository.writeCount)
      assertFalse(viewModel.uiState.value.profileInitializationFailed)
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

  /**
   * Makes the next sign-in succeed as a user whose provider reports the given picture.
   *
   * @param photoUrl the picture URL the provider reports, or `null` when it reports none
   * @author timo-by
   */
  private fun givenProviderPicture(photoUrl: String?) {
    repository.user = FakeAuthUser(uid = userId, photoUrl = photoUrl)
  }

  /**
   * Rebuilds the repository and ViewModel as if a session had been restored from the on-device
   * cache, so [AuthViewModel.signIn] is never called.
   *
   * @param photoUrl the picture URL the restored session carries
   * @author timo-by
   */
  private fun TestScope.givenRestoredSession(photoUrl: String?) {
    repository = FakeAuthRepository(FakeAuthUser(uid = userId, photoUrl = photoUrl))
    viewModel = AuthViewModel(repository, userRepository)
    advanceUntilIdle()
  }

  /**
   * Rebuilds the repository and ViewModel as if a session had been restored, and stops before the
   * initialization it starts completes, so that a test can act while it is in flight.
   *
   * Only what is due now runs: the collector receives the session and the refresh reaches the delay
   * of its write, which is not advanced past.
   *
   * @param photoUrl the picture URL the restored session carries
   * @author timo-by
   */
  private fun TestScope.givenRestoredSessionPending(photoUrl: String?) {
    repository = FakeAuthRepository(FakeAuthUser(uid = userId, photoUrl = photoUrl))
    viewModel = AuthViewModel(repository, userRepository)
    runCurrent()
  }
}
