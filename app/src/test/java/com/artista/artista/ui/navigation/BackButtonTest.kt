// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.navigation

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.artista.artista.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [BackButton].
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@RunWith(RobolectricTestRunner::class)
class BackButtonTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val backLabel =
      ApplicationProvider.getApplicationContext<Context>().getString(R.string.back)

  /**
   * Displays a [BackButton] tagged with [TAG].
   *
   * @param onClick callback invoked when the back button is tapped
   */
  private fun setBackButton(onClick: () -> Unit = {}) {
    composeTestRule.setContent {
      MaterialTheme { BackButton(onClick = onClick, modifier = Modifier.testTag(TAG)) }
    }
  }

  @Test
  fun backButton_isDisplayedWithTheTagFromItsModifier() {
    setBackButton()

    // Screens rely on the modifier to keep their own test tag on the shared button.
    composeTestRule.onNodeWithTag(TAG).assertIsDisplayed().assertHasClickAction()
  }

  @Test
  fun backButton_hasBackContentDescription() {
    setBackButton()

    composeTestRule.onNodeWithContentDescription(backLabel).assertIsDisplayed()
  }

  @Test
  fun backButton_click_invokesOnClickOnce() {
    var clicks = 0
    setBackButton(onClick = { clicks++ })

    composeTestRule.onNodeWithTag(TAG).performClick()

    assertEquals(1, clicks)
  }

  private companion object {
    const val TAG = "test_back_button"
  }
}
