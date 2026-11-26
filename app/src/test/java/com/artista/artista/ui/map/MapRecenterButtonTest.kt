// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

package com.artista.artista.ui.map

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) Compose UI tests for the map's recenter control.
 *
 * The camera animation itself needs a real Google Map, so it stays manual; these tests cover the
 * control's semantics and its click wiring.
 */
@RunWith(AndroidJUnit4::class)
class MapRecenterButtonTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Verifies that the control carries its stable tag and accessible description. */
  @Test
  fun mapRecenterButton_whenShown_thenExposesTagAndDescription() {
    val description =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.map_recenter)

    composeTestRule.setContent { ArtistaTheme { MapRecenterButton(onClick = {}) } }

    composeTestRule.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON)
        .assertContentDescriptionEquals(description)
  }

  /** Verifies that tapping the control invokes the recenter callback. */
  @Test
  fun mapRecenterButton_whenClicked_thenInvokesRecenter() {
    var recentered = false

    composeTestRule.setContent {
      ArtistaTheme { MapRecenterButton(onClick = { recentered = true }) }
    }

    composeTestRule.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertTrue(recentered)
  }
}
