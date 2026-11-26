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
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.map.Location
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [ArtworkTile].
 *
 * @author Timz3rr, hixeum
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtworkTileTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val artwork =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = null,
          conceptionDate = null,
          museum = "Louvre",
          location = Location(latitude = 48.8606, longitude = 2.3376, name = "Louvre"),
          dimension = null,
      )

  private var clickedArtwork: Artwork? = null
  private var bookmarkedArtwork: Artwork? = null
  private var mappedArtwork: Artwork? = null

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /** Checks that the tile shows the artwork's title and artist. */
  @Test
  fun artworkTile_displaysTitleAndArtist() {
    setTileContent(artwork)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.title(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Mona Lisa")
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.artist(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Leonardo da Vinci")
  }

  /** Checks that an artwork without a name or an artist shows the unknown title and artist. */
  @Test
  fun artworkTile_withoutNameAndArtist_displaysFallbackTexts() {
    val unknownArtwork = artwork.copy(name = null, artist = null)
    setTileContent(unknownArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.title(unknownArtwork), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.artwork_unknown_title))
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.artist(unknownArtwork), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.artwork_unknown_artist))
  }

  /** Checks that tapping the tile opens the artwork and triggers no other action. */
  @Test
  fun artworkTile_whenTileIsClicked_callsOnlyOnClick() {
    setTileContent(artwork)

    composeTestRule.onNodeWithTag(ArtworkTileTestTags.tile(artwork)).performClick()

    assertEquals(artwork, clickedArtwork)
    assertNull(bookmarkedArtwork)
    assertNull(mappedArtwork)
  }

  /** Checks that tapping the bookmark reports the artwork without opening it. */
  @Test
  fun artworkTile_whenBookmarkIsClicked_callsOnlyOnSaveClick() {
    setTileContent(artwork)

    composeTestRule.onNodeWithTag(ArtworkTileTestTags.saveButton(artwork)).performClick()

    assertEquals(artwork, bookmarkedArtwork)
    assertNull(clickedArtwork)
    assertNull(mappedArtwork)
  }

  /** Checks that tapping the map button of a located artwork shows it on the map only. */
  @Test
  fun artworkTile_whenMapButtonIsClicked_callsOnlyOnMapClick() {
    setTileContent(artwork)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.mapButton(artwork))
        .assertIsDisplayed()
        .performClick()

    assertEquals(artwork, mappedArtwork)
    assertNull(clickedArtwork)
    assertNull(bookmarkedArtwork)
  }

  /** Checks that an artwork without a location has no map button but keeps its bookmark. */
  @Test
  fun artworkTile_withoutLocation_hidesMapButton() {
    val unlocatedArtwork = artwork.copy(location = null)
    setTileContent(unlocatedArtwork)

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.saveButton(unlocatedArtwork))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.mapButton(unlocatedArtwork))
        .assertDoesNotExist()
  }

  /** Checks that an artwork without an image link shows the fallback icon and no image. */
  @Test
  fun artworkTile_withoutImageUrl_displaysFallbackOnly() {
    setTileContent(artwork.copy(imageUrl = null))

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.imageFallback(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.image(artwork), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that once the image has loaded, the tile shows it instead of the fallback icon. */
  @Test
  fun artworkTile_withLoadedImage_displaysImage() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setTileContent(artwork.copy(imageUrl = IMAGE_URL))
    composeTestRule.waitUntilFallbackDisappears(ArtworkTileTestTags.imageFallback(artwork))

    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.image(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Checks that the bookmark of a saved artwork offers to remove it from saved artworks. */
  @Test
  fun artworkTile_whenSaved_describesBookmarkAsUnsave() {
    setTileContent(artwork, isSaved = true)

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_unsave, "Mona Lisa"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_save, "Mona Lisa"))
        .assertDoesNotExist()
  }

  /** Checks that the bookmark of an unsaved artwork offers to save it. */
  @Test
  fun artworkTile_whenNotSaved_describesBookmarkAsSave() {
    setTileContent(artwork, isSaved = false)

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_save, "Mona Lisa"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.artwork_unsave, "Mona Lisa"))
        .assertDoesNotExist()
  }

  /**
   * Displays an [ArtworkTile] for [tileArtwork] whose callbacks record the artwork they get.
   *
   * @param tileArtwork artwork shown by the tile
   * @param isSaved whether the tile shows [tileArtwork] as saved
   */
  private fun setTileContent(tileArtwork: Artwork, isSaved: Boolean = true) {
    composeTestRule.setContent {
      MaterialTheme {
        ArtworkTile(
            artwork = tileArtwork,
            isSaved = isSaved,
            onClick = { clickedArtwork = it },
            onSaveClick = { bookmarkedArtwork = it },
            onMapClick = { mappedArtwork = it },
        )
      }
    }
  }
}
