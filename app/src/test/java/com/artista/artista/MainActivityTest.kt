// Co-authored-by: DeepSeek Flash
package com.artista.artista

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.model.authentication.AuthRepositoryProvider
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.resources.C
import com.google.firebase.FirebaseApp
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) tests for [MainActivity].
 *
 * Launches the real activity, so the root composition the application ships with is the one under
 * test: the image loader it installs for Coil, the theme and surface it wraps the application in,
 * and [ArtistaApp] wired to its production defaults rather than to the arguments the other tests
 * inject. The session, and therefore what is drawn, is driven through [AuthRepositoryProvider], the
 * seam the application itself reads, so no network, no Firestore and no account chooser is
 * involved.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  // The activity is launched by the test rather than by the rule: the providers have to point at
  // the fake before onCreate reads them. An empty rule still gives the Compose assertions a clock
  // and an idling resource bound to the launched activity.
  @get:Rule val composeTestRule = createEmptyComposeRule()

  /**
   * Points the application at a signed-out fake session.
   *
   * The Firebase app is created first because [ArtistaApp]'s default arguments read the
   * repositories of the application, and constructing those needs an initialised [FirebaseApp] -
   * which Robolectric does not provide on its own. Calling [FirebaseApp.initializeApp] again
   * returns the app created by a previous test instead of replacing it, so this is safe to repeat.
   *
   * @author timo-by
   */
  @Before
  fun setUp() {
    FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext)
    AuthRepositoryProvider.repository = FakeAuthRepository()
  }

  /**
   * Puts the application's repository back: the provider is process-wide, and leaving the fake in
   * place would decide the session of whichever test runs next.
   *
   * @author timo-by
   */
  @After
  fun tearDown() {
    AuthRepositoryProvider.resetToApplicationRepository()
  }

  /**
   * Verifies that creating the activity draws the session gate, here on the sign-in screen, so the
   * activity's own content - and not only the composables below it - is exercised.
   *
   * @author timo-by
   */
  @Test
  fun onCreateDisplaysTheSessionGate() {
    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    }
  }
}
