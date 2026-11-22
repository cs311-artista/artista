// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.map.FakeAddressRepository
import com.artista.artista.model.map.Location
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for [ArtworkLocationField].
 *
 * Each state is checked on the field alone, which is simpler than driving the ViewModel into it.
 * One test renders the whole [ArtworkScreen] to check that it passes the ViewModel state to the
 * field.
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

  /** Checks that a modifier given by the caller is applied to the field, along with its tag. */
  @Test
  fun artworkLocationField_appliesCallerModifier() {
    composeTestRule.setContent {
      ArtworkLocationField(
          addressState = ArtworkAddressState.Loading,
          modifier = Modifier.semantics { contentDescription = CALLER_DESCRIPTION },
      )
    }

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_ADDRESS, useUnmergedTree = true)
        .assertContentDescriptionEquals(CALLER_DESCRIPTION)
  }

  /** Checks that the details screen shows the address looked up for a located artwork. */
  @Test
  fun artworkScreen_withLocatedArtwork_displaysLookedUpAddress() {
    val artwork =
        Artwork(
            uid = "thinker",
            name = "The Thinker",
            artist = null,
            description = null,
            conceptionDate = null,
            museum = null,
            location = Location(longitude = 2.3158, latitude = 48.8553, name = null),
            dimension = null,
        )
    val repository =
        ArtworkRepositoryLocal().apply { putArtworkInRepositoryUnsavedSection(artwork) }
    val viewModel =
        ArtworkScreenViewModel(artwork.uid, repository, repository, FakeAddressRepository())

    composeTestRule.setContent {
      ArtworkScreen(artworkScreenViewModel = viewModel, onBackClick = {})
    }
    composeTestRule.waitUntil(SCREEN_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(ArtworkTestTags.ARTWORK_ADDRESS, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    assertFieldShows(FakeAddressRepository.DEFAULT_ADDRESS)
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

  private companion object {
    const val SCREEN_TIMEOUT_MS = 5_000L
    const val CALLER_DESCRIPTION = "Location of the artwork"
  }
}
