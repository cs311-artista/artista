// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.error.AppException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/**
 * Tests how the Overview screen reports a failure to the user.
 *
 * It runs on Robolectric so that the text of the toast can be read through [ShadowToast].
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
class OverviewScreenErrorTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Checks that a network failure while loading shows the network error in a toast.
   *
   * @author Timz3rr
   */
  @Test
  fun overviewScreen_whenLoadFailsWithNetworkError_showsNetworkErrorToast() {
    val viewModel =
        OverviewViewModel(repositoryFailingWith(AppException.Network()), ArtworkRepositoryLocal())

    composeTestRule.setContent { MaterialTheme { OverviewScreen(viewModel = viewModel) } }
    composeTestRule.waitForIdle()

    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(context.getString(R.string.error_network), ShadowToast.getTextOfLatestToast())
  }
}
