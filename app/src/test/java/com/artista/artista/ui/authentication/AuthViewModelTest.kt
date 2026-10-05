package com.artista.artista.ui.authentication

import com.artista.artista.MainDispatcherRule
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * sign-in and sign-out flows: state, loading, error events, re-entrancy guards and retries.
 *
 * @author timo-by
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

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
      assertEquals("user-1", viewModel.uiState.value.user?.uid)

      repository.setAuthState(null)
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
    }
  }

  @Test
  fun signInOnSuccessSignsInForwardsTokenAndEmitsNoEvent() {
    runTest {
      repository.user = FakeAuthUser("user-1")
      val events = collectEvents()
      viewModel.signIn("token-123")
      advanceUntilIdle()
      assertEquals("user-1", viewModel.uiState.value.user?.uid)
      assertEquals("token-123", repository.lastIdToken)
      assertTrue(events.isEmpty())
    }
  }

  @Test
  fun signInTogglesLoadingAroundTheOperation() {
    runTest {
      viewModel.signIn("token")
      assertTrue(viewModel.uiState.value.isLoading)

      advanceUntilIdle()
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun signInLeavesTheUserSignedOutOnFailure() {
    runTest {
      repository.signInError = RuntimeException("boom")
      viewModel.signIn("token")
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun signInEmitsErrorEventWithMessageOnFailure() {
    runTest {
      repository.signInError = RuntimeException("boom")
      val events = collectEvents()
      viewModel.signIn("token")
      advanceUntilIdle()
      assertEquals(1, events.size)
      val error = events.first() as AuthUiEvent.ShowError
      assertEquals("boom", error.message)
    }
  }

  @Test
  fun signInIsIgnoredWhileAlreadyInProgress() {
    runTest {
      viewModel.signIn("token-1")
      viewModel.signIn("token-2")
      advanceUntilIdle()
      assertEquals(1, repository.signInCallCount)
      assertEquals("token-1", repository.lastIdToken)
    }
  }

  @Test
  fun signInIsIgnoredWhenAlreadySignedIn() {
    runTest {
      givenSignedInUser()
      viewModel.signIn("token")
      advanceUntilIdle()
      assertEquals(0, repository.signInCallCount)
    }
  }

  @Test
  fun signInRetrySucceedsAfterAFailure() {
    runTest {
      repository.signInError = RuntimeException("boom")
      viewModel.signIn("token-1")
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)

      repository.signInError = null
      repository.user = FakeAuthUser("user-1")
      viewModel.signIn("token-2")
      advanceUntilIdle()
      assertEquals("user-1", viewModel.uiState.value.user?.uid)
      assertEquals("token-2", repository.lastIdToken)
      assertEquals(2, repository.signInCallCount)
    }
  }

  @Test
  fun signOutSignsOutAndEmitsNoEvent() {
    runTest {
      givenSignedInUser()
      val events = collectEvents()
      viewModel.signOut()
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertTrue(events.isEmpty())
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
  fun signOutKeepsUserSignedInAndEmitsErrorOnFailure() {
    runTest {
      givenSignedInUser()
      repository.signOutError = RuntimeException("sign-out failed")
      val events = collectEvents()
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals("user-1", viewModel.uiState.value.user?.uid)
      assertEquals(1, events.size)
      val error = events.first() as AuthUiEvent.ShowError
      assertEquals("sign-out failed", error.message)
    }
  }

  @Test
  fun signOutIsIgnoredWhileAnOperationIsInProgress() {
    runTest {
      viewModel.signIn("token")
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals(0, repository.signOutCallCount)
    }
  }

  @Test
  fun signOutRetrySucceedsAfterAFailure() {
    runTest {
      givenSignedInUser()
      repository.signOutError = RuntimeException("sign-out failed")
      viewModel.signOut()
      advanceUntilIdle()
      assertEquals("user-1", viewModel.uiState.value.user?.uid)

      repository.signOutError = null
      viewModel.signOut()
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)
      assertEquals(2, repository.signOutCallCount)
    }
  }

  @Test
  fun errorEventsAreNotReplayedToLateCollectors() {
    runTest {
      repository.signInError = RuntimeException("boom")
      val events = collectEvents()
      viewModel.signIn("token")
      advanceUntilIdle()
      assertEquals(1, events.size)

      val lateEvents = collectEvents()
      advanceUntilIdle()
      assertTrue(lateEvents.isEmpty())
    }
  }

  @Test
  fun signInSignOutSignInCycleStaysConsistent() {
    runTest {
      repository.user = FakeAuthUser("user-1")
      viewModel.signIn("token-1")
      advanceUntilIdle()
      assertEquals("user-1", viewModel.uiState.value.user?.uid)

      viewModel.signOut()
      advanceUntilIdle()
      assertNull(viewModel.uiState.value.user)

      viewModel.signIn("token-2")
      advanceUntilIdle()
      assertEquals("user-1", viewModel.uiState.value.user?.uid)
      assertEquals("token-2", repository.lastIdToken)
    }
  }

  /**
   * Rebuilds the repository and ViewModel with a user already signed in, and lets the ViewModel
   * settle so it has observed the initial authentication state.
   *
   * @param uid the uid of the signed-in user
   * @author timo-by
   */
  private fun TestScope.givenSignedInUser(uid: String = "user-1") {
    repository = FakeAuthRepository(FakeAuthUser(uid))
    viewModel = AuthViewModel(repository)
    advanceUntilIdle()
  }

  /**
   * Starts collecting the ViewModel's one-shot [AuthUiEvent]s into a list for assertion.
   *
   * The collector runs in the test's [backgroundScope] so it is cancelled automatically when the
   * test ends, and is started eagerly with an unconfined dispatcher so no event is missed.
   *
   * @return a live list of the events received so far
   * @author timo-by
   */
  private fun TestScope.collectEvents(): List<AuthUiEvent> {
    val events = mutableListOf<AuthUiEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.events.toList(events)
    }
    return events
  }
}
