package com.artista.artista.ui.authentication

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
/**
 * Local (Robolectric) Compose test for [rememberGoogleCredentialProvider].
 *
 * The composable is the production wiring of the sign-in screen: it is the only place that binds a
 * provider to the current context and to the web client ID generated out of `google-services.json`.
 * Both are read from the composition, so they can only be observed by composing the function.
 *
 * It is also the only test that puts the composable's own lines under coverage; everything the
 * provider does once built is covered by `CredentialManagerGoogleCredentialProviderTest`.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class RememberGoogleCredentialProviderTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Verifies that composing the function yields the Credential Manager backed provider, i.e. that
   * the production screen signs in through the real implementation and not through a stand-in.
   *
   * @author timo-by
   */
  @Test
  fun providersTheCredentialManagerBackedProvider() {
    var provider: GoogleCredentialProvider? = null

    composeTestRule.setContent { provider = rememberGoogleCredentialProvider() }
    composeTestRule.waitForIdle()

    assertTrue(provider is CredentialManagerGoogleCredentialProvider)
  }
}
