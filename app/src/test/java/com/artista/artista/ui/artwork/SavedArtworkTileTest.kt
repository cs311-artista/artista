// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Unit tests for [SavedArtworkTile].
 *
 * @author Timz3rr
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class SavedArtworkTileTest {

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
  private var unsavedArtwork: Artwork? = null
  private var mappedArtwork: Artwork? = null

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /** Checks that the tile shows the artwork's title and artist. */
  @Test
  fun savedArtworkTile_displaysTitleAndArtist() {
    setTileContent(artwork)

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.title(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Mona Lisa")
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.artist(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Leonardo da Vinci")
  }

  /** Checks that an artwork without a name or an artist shows the unknown title and artist. */
  @Test
  fun savedArtworkTile_withoutNameAndArtist_displaysFallbackTexts() {
    val unknownArtwork = artwork.copy(name = null, artist = null)
    setTileContent(unknownArtwork)

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.title(unknownArtwork), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.artwork_unknown_title))
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.artist(unknownArtwork), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.artwork_unknown_artist))
  }

  /** Checks that tapping the tile opens the artwork and triggers no other action. */
  @Test
  fun savedArtworkTile_whenTileIsClicked_callsOnlyOnClick() {
    setTileContent(artwork)

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(artwork)).performClick()

    assertEquals(artwork, clickedArtwork)
    assertNull(unsavedArtwork)
    assertNull(mappedArtwork)
  }

  /** Checks that tapping the bookmark unsaves the artwork without opening it. */
  @Test
  fun savedArtworkTile_whenBookmarkIsClicked_callsOnlyOnUnsaveClick() {
    setTileContent(artwork)

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(artwork)).performClick()

    assertEquals(artwork, unsavedArtwork)
    assertNull(clickedArtwork)
    assertNull(mappedArtwork)
  }

  /** Checks that tapping the map button of a located artwork shows it on the map only. */
  @Test
  fun savedArtworkTile_whenMapButtonIsClicked_callsOnlyOnMapClick() {
    setTileContent(artwork)

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.mapButton(artwork))
        .assertIsDisplayed()
        .performClick()

    assertEquals(artwork, mappedArtwork)
    assertNull(clickedArtwork)
    assertNull(unsavedArtwork)
  }

  /** Checks that an artwork without a location has no map button but keeps its bookmark. */
  @Test
  fun savedArtworkTile_withoutLocation_hidesMapButton() {
    val unlocatedArtwork = artwork.copy(location = null)
    setTileContent(unlocatedArtwork)

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.unsaveButton(unlocatedArtwork))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.mapButton(unlocatedArtwork))
        .assertDoesNotExist()
  }

  /** Checks that an artwork without an image link shows the fallback icon and no image. */
  @Test
  fun savedArtworkTile_withoutImageUrl_displaysFallbackOnly() {
    setTileContent(artwork.copy(imageUrl = null))

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.imageFallback(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.image(artwork), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that once the image has loaded, the tile shows it instead of the fallback icon. */
  @Test
  fun savedArtworkTile_withLoadedImage_displaysImage() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setTileContent(artwork.copy(imageUrl = IMAGE_URL))
    composeTestRule.waitUntilFallbackDisappears(SavedArtworksTestTags.imageFallback(artwork))

    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.image(artwork), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /**
   * Displays a [SavedArtworkTile] for [tileArtwork] whose callbacks record the artwork they get.
   *
   * @param tileArtwork artwork shown by the tile
   */
  private fun setTileContent(tileArtwork: Artwork) {
    composeTestRule.setContent {
      MaterialTheme {
        SavedArtworkTile(
            artwork = tileArtwork,
            onClick = { clickedArtwork = it },
            onUnsaveClick = { unsavedArtwork = it },
            onMapClick = { mappedArtwork = it },
        )
      }
    }
  }
}
