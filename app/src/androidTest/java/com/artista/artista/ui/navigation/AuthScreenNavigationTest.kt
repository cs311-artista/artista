// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash
package com.artista.artista.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.ArtistaApp
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.resources.C
import com.artista.artista.ui.authentication.AuthViewModel
import com.artista.artista.ui.authentication.FakeGoogleCredentialProvider
import com.artista.artista.ui.overview.OverviewTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the root flow exposed by the application: authentication gates everything, and a
 * successful sign-in opens the Overview.
 *
 * The application root is composed with its authentication dependencies replaced by fakes, so the
 * sign-in tap reaches the repository instead of Credential Manager's Google account chooser, which
 * no test can drive. Everything else - the real [ArtistaApp] gate, the real graph - is exercised as
 * shipped.
 *
 * @author hixeum, timo-by
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // Built outside the composition: a ViewModel belongs to a ViewModelStoreOwner, and the root only
  // needs the state it exposes.
  private val authViewModel = AuthViewModel(FakeAuthRepository())

  private val credentialProvider = FakeGoogleCredentialProvider()

  /**
   * Verifies that authentication is the initial destination and sign-in opens the overview.
   *
   * @author hixeum
   */
  @Test
  fun authIsInitialDestination_andSignInOpensOverview() {
    composeTestRule.setContent {
      ArtistaApp(
          authViewModel = authViewModel,
          credentialProvider = credentialProvider,
          wikiDataRepository = ArtworkRepositoryProvider.mockRepository,
          savedArtworksRepository = ArtworkRepositoryProvider.mockRepository,
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()

    composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithTag(OverviewTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
