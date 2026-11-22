// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.artista.artista.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for [ArtworkLocationField].
 *
 * The field is tested on its own because a located artwork makes [ArtworkScreen] compose a
 * GoogleMap, which Robolectric cannot render.
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
class ArtworkLocationFieldTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Checks that a found address is displayed under the location label. */
  @Test
  fun artworkLocationField_whenFound_displaysAddress() {
    setField(ArtworkAddressState.Found("77 Rue de Varenne, 75007 Paris, France"))

    assertFieldShows(string(R.string.artwork_label_location))
    assertFieldShows("77 Rue de Varenne, 75007 Paris, France")
  }

  /** Checks that a lookup in progress is announced instead of an empty value. */
  @Test
  fun artworkLocationField_whenLoading_displaysLoadingMessage() {
    setField(ArtworkAddressState.Loading)

    assertFieldShows(string(R.string.artwork_address_loading))
  }

  /** Checks that a failed lookup displays a message instead of an address. */
  @Test
  fun artworkLocationField_whenUnavailable_displaysUnavailableMessage() {
    setField(ArtworkAddressState.Unavailable)

    assertFieldShows(string(R.string.artwork_address_unavailable))
  }

  /** Checks that an artwork without coordinates says that its location is not specified. */
  @Test
  fun artworkLocationField_whenNoLocation_displaysNoLocationMessage() {
    setField(ArtworkAddressState.NoLocation)

    assertFieldShows(string(R.string.artwork_no_location))
  }

  private fun setField(addressState: ArtworkAddressState) {
    composeTestRule.setContent { ArtworkLocationField(addressState = addressState) }
  }

  // The unmerged tree keeps the texts as separate children of the tagged card.
  private fun assertFieldShows(text: String) {
    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_ADDRESS, useUnmergedTree = true)
        .assert(hasAnyDescendant(hasText(text)))
  }

  private fun string(resId: Int): String = RuntimeEnvironment.getApplication().getString(resId)
}
