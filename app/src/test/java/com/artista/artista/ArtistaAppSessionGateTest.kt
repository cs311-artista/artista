// Co-authored-by: DeepSeek Flash
package com.artista.artista

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.authentication.AuthRepositoryProvider
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.user.UserRepositoryProvider
import com.artista.artista.resources.C
import com.artista.artista.ui.authentication.AuthViewModel
import com.artista.artista.ui.authentication.FakeGoogleCredentialProvider
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.overview.OverviewTestTags
import com.artista.artista.ui.profile.UserProfileTestTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) tests for the session gate of [ArtistaApp].
 *
 * Drives the gate through a [FakeAuthRepository] and a [FakeGoogleCredentialProvider], so each of
 * the three layouts it chooses from - nothing while the session is restored, the sign-in screen,
 * the graph of the signed-in application - is observed without Firebase and without Credential
 * Manager's account chooser. Elements are located exclusively by test tag.
 *
 * @author timo-by
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ArtistaAppSessionGateTest {

  @get:Rule val composeTestRule = createComposeRule()

  // The ViewModel launches on viewModelScope (Dispatchers.Main). Installing an unconfined test
  // dispatcher makes its coroutines run eagerly, so the assertions observe the resulting state
  // deterministically instead of relying on the real main looper being idled by waitForIdle().
  @get:Rule val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

  private lateinit var repository: FakeAuthRepository
  private lateinit var credentialProvider: FakeGoogleCredentialProvider

  // One repository for both roles keeps the graph's dependencies trivial: the gate is what is
  // under test, not what the screens below it display.
  private val artworks = ArtworkRepositoryLocal()

  @Before
  fun setUp() {
    repository = FakeAuthRepository()
    credentialProvider = FakeGoogleCredentialProvider(idToken = GOOGLE_ID_TOKEN)
  }

  /**
   * Verifies that nothing is drawn while the repository has not reported the persisted session, so
   * a signed-in user is never offered a sign-in.
   */
  @Test
  fun restoringDrawsNeitherTheSignInScreenNorTheGraph() {
    repository.authStateIsPending = true
    showApp()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertDoesNotExist()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertDoesNotExist()
  }

  /**
   * Verifies that a repository reporting no session displays the sign-in screen and nothing else.
   */
  @Test
  fun signedOutDisplaysTheSignInScreen() {
    showApp()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertDoesNotExist()
  }

  /**
   * Verifies that signing in through the credential seam opens the graph on Overview, replacing the
   * sign-in screen instead of stacking on top of it.
   */
  @Test
  fun signInDisplaysTheOverview() {
    showApp()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(GOOGLE_ID_TOKEN, repository.lastIdToken)
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertDoesNotExist()
  }

  /**
   * Verifies that a session the repository drops behind the application's back - the way Firebase
   * reports a revoked or expired session - returns the user to the sign-in screen.
   */
  @Test
  fun revokedSessionReturnsToTheSignInScreen() {
    repository.setAuthState(FakeAuthUser(uid = UID))
    showApp()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()

    repository.setAuthState(null)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertDoesNotExist()
  }

  /**
   * Verifies that signing out clears the ViewModels of the screens the user had opened, instead of
   * leaving them alive in the activity.
   *
   * Dropping the graph pops nothing, and a back stack entry's ViewModel is only cleared when it is
   * popped, so the graph has to own its ViewModelStore for the sign-out to take the destinations'
   * ViewModels down with it. The number of collectors on the session is what makes that visible:
   * the profile screen's ViewModel collects it too, and would keep collecting - on the next user's
   * session - if it survived the sign-out.
   */
  @Test
  fun signOutClearsTheViewModelsOfTheGraph() {
    // The graph builds its own profile screen, so the providers it reads are pointed at fakes. Both
    // are process-wide, and no other unit test reads them.
    AuthRepositoryProvider.repository = repository
    UserRepositoryProvider.resetFakeRepository()

    repository.setAuthState(FakeAuthUser(uid = UID))
    showApp()
    composeTestRule.waitForIdle()

    // The application root collects the session; opening the profile screen adds a second
    // collector, which is the one the sign-out has to stop.
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    assertEquals(2, repository.activeAuthStateCollectors)

    repository.setAuthState(null)

    // Only the application root's collector is left: the profile screen's died with its ViewModel.
    composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      repository.activeAuthStateCollectors == 1
    }
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
  }

  /**
   * Verifies that a session stream that fails shows the sign-in screen with the failure's message
   * rather than leaving the application on the restoring state forever.
   */
  @Test
  fun failingSessionStreamDisplaysTheSignInScreenWithItsMessage() {
    repository.authStateError = RuntimeException(STREAM_ERROR_MESSAGE)
    showApp()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_error_message).assertIsDisplayed()
  }

  /** Composes [ArtistaApp] with the fakes and no Firebase. */
  private fun showApp() {
    // Built once the test has configured the repository: the ViewModel starts collecting the
    // session
    // at construction, so a repository configured afterwards would have reported already. It is
    // also
    // built outside the composition, a ViewModel belonging to a ViewModelStoreOwner rather than to
    // a
    // composable.
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      ArtistaApp(
          authViewModel = viewModel,
          credentialProvider = credentialProvider,
          wikiDataRepository = artworks,
          savedArtworksRepository = artworks,
      )
    }
  }

  private companion object {
    const val UID = "user-1"
    const val GOOGLE_ID_TOKEN = "fake-id-token"
    const val STREAM_ERROR_MESSAGE = "the session stream failed"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
