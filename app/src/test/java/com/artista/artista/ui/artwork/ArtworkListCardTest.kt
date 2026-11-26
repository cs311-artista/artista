// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.map.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [ArtworkListCard].
 *
 * @author hixeum
 */
@RunWith(RobolectricTestRunner::class)
class ArtworkListCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val artwork =
      Artwork(
          uid = "cube",
          name = "Le Cube²",
          artist = Artist("test-jean", "Jean-Jean"),
          description = null,
          conceptionDate = null,
          museum = "Musée de Morat",
          location = Location(latitude = 46.93, longitude = 7.12, name = "Morat"),
          dimension = null,
      )

  private var clickedArtwork: Artwork? = null
  private var bookmarkedArtwork: Artwork? = null
  private var mappedArtwork: Artwork? = null

  /** Checks that the card shows the artist, the title and the museum. */
  @Test
  fun artworkListCard_displaysArtistTitleAndMuseum() {
    setCardContent(artwork)

    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.artist(artwork), useUnmergedTree = true)
        .assertTextEquals("Jean-Jean")
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.title(artwork), useUnmergedTree = true)
        .assertTextEquals("Le Cube²")
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.place(artwork), useUnmergedTree = true)
        .assertTextEquals("Musée de Morat")
  }

  /** Checks that an artwork without a museum shows its location name instead. */
  @Test
  fun artworkListCard_withoutMuseum_displaysLocationName() {
    val outdoorArtwork = artwork.copy(museum = null)
    setCardContent(outdoorArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.place(outdoorArtwork), useUnmergedTree = true)
        .assertTextEquals("Morat")
  }

  /** Checks that an artwork without a museum or a location shows no place line. */
  @Test
  fun artworkListCard_withoutMuseumAndLocation_hidesPlaceAndMapButton() {
    val unknownPlaceArtwork = artwork.copy(museum = null, location = null)
    setCardContent(unknownPlaceArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.place(unknownPlaceArtwork), useUnmergedTree = true)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.mapButton(unknownPlaceArtwork))
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.saveButton(unknownPlaceArtwork))
        .assertIsDisplayed()
  }

  /** Checks that an artwork without a name or an artist shows the unknown title and artist. */
  @Test
  fun artworkListCard_withoutNameAndArtist_displaysFallbackTexts() {
    val unknownArtwork = artwork.copy(name = null, artist = null)
    setCardContent(unknownArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.title(unknownArtwork), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.artwork_unknown_title))
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.artist(unknownArtwork), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.artwork_unknown_artist))
  }

  /** Checks that an artwork without an image link shows the fallback icon and no image. */
  @Test
  fun artworkListCard_withoutImageUrl_displaysFallbackOnly() {
    setCardContent(artwork)

    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.imageFallback(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkListCardTestTags.image(artwork), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that tapping the card opens the artwork and triggers no other action. */
  @Test
  fun artworkListCard_whenCardIsClicked_callsOnlyOnClick() {
    setCardContent(artwork)

    composeTestRule.onNodeWithTag(ArtworkListCardTestTags.card(artwork)).performClick()

    assertEquals(artwork, clickedArtwork)
    assertNull(bookmarkedArtwork)
    assertNull(mappedArtwork)
  }

  /** Checks that tapping the bookmark reports the artwork without opening it. */
  @Test
  fun artworkListCard_whenBookmarkIsClicked_callsOnlyOnSaveClick() {
    setCardContent(artwork)

    composeTestRule.onNodeWithTag(ArtworkListCardTestTags.saveButton(artwork)).performClick()

    assertEquals(artwork, bookmarkedArtwork)
    assertNull(clickedArtwork)
    assertNull(mappedArtwork)
  }

  /** Checks that tapping the map button reports the artwork without opening it. */
  @Test
  fun artworkListCard_whenMapButtonIsClicked_callsOnlyOnMapClick() {
    setCardContent(artwork)

    composeTestRule.onNodeWithTag(ArtworkListCardTestTags.mapButton(artwork)).performClick()

    assertEquals(artwork, mappedArtwork)
    assertNull(clickedArtwork)
    assertNull(bookmarkedArtwork)
  }

  /** Checks that the bookmark describes the action matching the saved state. */
  @Test
  fun artworkListCard_bookmarkDescription_followsSavedState() {
    setCardContent(artwork, isSaved = true)

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_unsave, "Le Cube²"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_save, "Le Cube²"))
        .assertDoesNotExist()
  }

  /** Checks that the bookmark of an unsaved artwork offers to save it. */
  @Test
  fun artworkListCard_whenNotSaved_describesBookmarkAsSave() {
    setCardContent(artwork, isSaved = false)

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_save, "Le Cube²"))
        .assertIsDisplayed()
  }

  /**
   * Displays an [ArtworkListCard] for [cardArtwork] whose callbacks record the artwork they get.
   *
   * @param cardArtwork artwork shown by the card
   * @param isSaved whether the card shows [cardArtwork] as saved
   */
  private fun setCardContent(cardArtwork: Artwork, isSaved: Boolean = false) {
    composeTestRule.setContent {
      MaterialTheme {
        ArtworkListCard(
            artwork = cardArtwork,
            isSaved = isSaved,
            onClick = { clickedArtwork = it },
            onSaveClick = { bookmarkedArtwork = it },
            onMapClick = { mappedArtwork = it },
        )
      }
    }
  }
}
