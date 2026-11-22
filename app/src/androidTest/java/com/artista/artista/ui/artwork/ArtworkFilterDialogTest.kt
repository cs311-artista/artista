// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the type criterion of the shared artwork filter dialog.
 *
 * The other criteria are covered through the Overview and Saved artworks screen tests. These tests
 * are instrumented because the dialog never gets idle under Robolectric.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class ArtworkFilterDialogTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var appliedFilter: ArtworkFilter? = null

  /** Checks that the dialog offers one row per type, labelled with its display name. */
  @Test
  fun typeField_showsARowPerTypeWithItsDisplayName() {
    setDialogContent()
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    ArtworkType.entries.forEach { type ->
      typeOption(type).assertIsOff().assertTextEquals(context.getString(type.displayNameRes()))
    }
  }

  /** Checks that the list shows three types at once and scrolls to reveal the others. */
  @Test
  fun typeField_showsThreeTypesAndScrollsForTheOthers() {
    setDialogContent()
    // Brings the list itself into view first, in case the dialog content is taller than the screen.
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.TYPE).performScrollTo()

    ArtworkType.entries.take(3).forEach { type ->
      composeTestRule.onNodeWithTag(ArtworkFilterTestTags.typeOption(type)).assertIsDisplayed()
    }
    val fourthType = ArtworkType.entries[3]
    composeTestRule
        .onNodeWithTag(ArtworkFilterTestTags.typeOption(fourthType))
        .assertIsNotDisplayed()

    typeOption(ArtworkType.entries.last()).assertIsDisplayed()
  }

  /** Checks that the types of the applied filter are shown as selected when the dialog opens. */
  @Test
  fun typeField_showsTheTypesOfTheCurrentFilterAsSelected() {
    setDialogContent(ArtworkFilter(types = setOf(ArtworkType.SCULPTURE)))

    typeOption(ArtworkType.SCULPTURE).assertIsOn()
    typeOption(ArtworkType.PAINTING).assertIsOff()
  }

  /** Checks that the selected types reach the caller when the filter is applied. */
  @Test
  fun selectingTypes_andApplying_passesThemWithTheFilter() {
    setDialogContent()

    typeOption(ArtworkType.PAINTING).performClick().assertIsOn()
    typeOption(ArtworkType.MOSAIC).performClick().assertIsOn()
    applyButton().performClick()

    assertEquals(setOf(ArtworkType.PAINTING, ArtworkType.MOSAIC), appliedFilter?.types)
  }

  /** Checks that tapping a selected row removes its type from the filter. */
  @Test
  fun tappingASelectedType_deselectsIt() {
    setDialogContent(ArtworkFilter(types = setOf(ArtworkType.PAINTING, ArtworkType.MURAL)))

    typeOption(ArtworkType.PAINTING).performClick().assertIsOff()
    applyButton().performClick()

    assertEquals(setOf(ArtworkType.MURAL), appliedFilter?.types)
  }

  /** Checks that selecting a type keeps the other criteria entered in the same dialog. */
  @Test
  fun selectingAType_keepsTheOtherCriteria() {
    setDialogContent(ArtworkFilter(artists = setOf("Vincent van Gogh")))

    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.FROM_YEAR).performTextInput("1880")
    typeOption(ArtworkType.PAINTING).performClick()
    applyButton().performClick()

    assertEquals(
        ArtworkFilter(
            artists = setOf("Vincent van Gogh"),
            fromYear = 1880,
            types = setOf(ArtworkType.PAINTING),
        ),
        appliedFilter,
    )
  }

  private fun setDialogContent(filter: ArtworkFilter = ArtworkFilter()) {
    composeTestRule.setContent {
      ArtistaTheme {
        ArtworkFilterDialog(
            filter = filter,
            artistOptions = listOf("Vincent van Gogh"),
            museumOptions = emptyList(),
            locationOptions = emptyList(),
            onApply = { appliedFilter = it },
            onClear = {},
            onDismiss = {},
        )
      }
    }
  }

  private fun typeOption(type: ArtworkType) =
      composeTestRule.onNodeWithTag(ArtworkFilterTestTags.typeOption(type)).performScrollTo()

  private fun applyButton() = composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON)
}
