// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests rendering [SavedArtworksScreenPreview], the sample saved artworks screen.
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
class SavedArtworksScreenPreviewTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    composeTestRule.setContent { SavedArtworksScreenPreview() }
  }

  /** Checks that the preview shows the artwork grid with the back and filter buttons. */
  @Test
  fun savedArtworksScreenPreview_displaysGridAndTopBarButtons() {
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.FILTER_BUTTON).assertIsDisplayed()
  }

  /** Checks that the bottom bar selects the Profile tab, the section saved artworks belong to. */
  @Test
  fun savedArtworksScreenPreview_selectsProfileTab() {
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()
  }
}
