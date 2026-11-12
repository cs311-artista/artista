// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests the theme-backed colors shared by the search bars. */
@RunWith(RobolectricTestRunner::class)
class SearchBarPaletteTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Verifies that invalid-input colors resolve from the active Material theme. */
  @Test
  fun invalidInputColors_matchMaterialTheme() {
    val actualColors = mutableStateOf<Pair<Color, Color>?>(null)
    val expectedColors = mutableStateOf<Pair<Color, Color>?>(null)

    composeTestRule.setContent {
      MaterialTheme {
        val colorScheme = MaterialTheme.colorScheme
        val paletteColors = SearchBarPalette.invalidContainer to SearchBarPalette.error
        val themeColors = colorScheme.errorContainer to colorScheme.error
        SideEffect {
          actualColors.value = paletteColors
          expectedColors.value = themeColors
        }
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(expectedColors.value, actualColors.value)
  }
}
