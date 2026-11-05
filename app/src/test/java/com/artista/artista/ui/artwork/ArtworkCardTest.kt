// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.request.ErrorResult
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.map.Location
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [ArtworkCard].
 *
 * @author IJJA3141, Timz3rr
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtworkCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private companion object {
    const val IMAGE_URL =
        "https://commons.wikimedia.org/wiki/Special:FilePath/Mona%20Lisa.jpg?width=800"
    const val BROKEN_IMAGE_URL =
        "https://commons.wikimedia.org/wiki/Special:FilePath/Missing.jpg?width=800"
    const val IMAGE_TIMEOUT_MS = 5_000L
  }

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /** Checks that the card shows the artwork details and calls the right callback on each action. */
  /** Verifies that the artwork-card preview renders its sample card. */
  @Test
  fun artworkCardPreview_displaysSampleArtwork() {
    composeTestRule.setContent { ArtworkCardPreview() }

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).assertIsDisplayed()
  }

  @Test
  fun artworkCard_displaysArtworkMetadataAndActions() {
    var selectedArtwork: Artwork? = null
    var mappedArtwork: Artwork? = null
    var saveRequest: Artwork? = null

    val artwork =
        Artwork(
            uid = "monaLisa",
            name = "Mona Lisa",
            artist = Artist("test-da-vinci", "Leonardo da Vinci"),
            description = "A portrait of Lisa Gherardini",
            conceptionDate = null,
            museum = "Louvre",
            location = Location(latitude = 48.8606, longitude = 2.3376, name = "Louvre"),
            dimension = null,
        )

    composeTestRule.setContent {
      MaterialTheme {
        ArtworkCard(
            artwork = artwork,
            isSaved = false,
            onArtworkClick = { selectedArtwork = it },
            onMapClick = { mappedArtwork = it },
            onSaveClick = { requestedArtwork -> saveRequest = requestedArtwork },
            modifier = Modifier,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_IMAGE_FALLBACK, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Mona Lisa")
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Leonardo da Vinci")

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Map")
        .assertIsEnabled()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Save")
        .assertIsEnabled()

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD).performClick()
    assertEquals(artwork, selectedArtwork)

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON).performClick()
    assertEquals(artwork, mappedArtwork)

    composeTestRule.onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON).performClick()
    assertEquals(artwork, saveRequest)
  }

  /** Checks that the map action is disabled without a location and that a saved artwork says so. */
  @Test
  fun artworkCard_withoutLocation_disablesMapActionAndShowsSavedState() {
    var saveRequest: Artwork? = null
    val artwork =
        Artwork(
            uid = "nightWatch",
            name = "The Night Watch",
            artist = Artist("test-rembrandt", "Rembrandt"),
            description = "An iconic Dutch Golden Age painting",
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        )

    composeTestRule.setContent {
      MaterialTheme {
        ArtworkCard(
            artwork = artwork,
            isSaved = true,
            onArtworkClick = {},
            onMapClick = {},
            onSaveClick = { requestedArtwork -> saveRequest = requestedArtwork },
            modifier = Modifier,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)
        .assertIsDisplayed()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Saved")
        .performClick()
    assertEquals(artwork, saveRequest)
  }

  /** Checks that an artwork without an artist shows the unknown artist text. */
  @Test
  fun artworkCard_withoutArtist_displaysUnknownArtistFallback() {
    val artwork =
        Artwork(
            uid = "unknownArtist",
            name = "Untitled Study",
            artist = null,
            description = null,
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        )

    composeTestRule.setContent {
      MaterialTheme {
        ArtworkCard(
            artwork = artwork,
            isSaved = false,
            onArtworkClick = {},
            onMapClick = {},
            onSaveClick = { _ -> },
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Unknown Artist")
  }

  /**
   * Checks that once the image has loaded, the card shows it with a description naming the artwork,
   * and hides the fallback icon.
   */
  @Test
  fun artworkCard_withLoadedImage_displaysImageWithoutFallback() {
    installImageLoader(FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build())

    setCardContent(artworkWithImage(IMAGE_URL))
    composeTestRule.waitUntil(IMAGE_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(ArtworkCardTestTag.ARTWORK_CARD_IMAGE_FALLBACK, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isEmpty()
    }

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_THUMBNAIL, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Image of Mona Lisa")
  }

  /** Checks that an artwork without an image link shows the fallback icon and no image. */
  @Test
  fun artworkCard_withoutImageUrl_displaysFallbackOnly() {
    setCardContent(artworkWithImage(imageUrl = null))

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_IMAGE_FALLBACK, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_THUMBNAIL, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that the fallback icon stays visible when the image fails to download. */
  @Test
  fun artworkCard_whenImageFailsToLoad_keepsFallback() {
    var imageRequested = false
    installImageLoader(
        FakeImageLoaderEngine.Builder()
            .intercept({ it == BROKEN_IMAGE_URL }) { chain ->
              imageRequested = true
              ErrorResult(image = null, request = chain.request, throwable = IOException("404"))
            }
            .build()
    )

    setCardContent(artworkWithImage(BROKEN_IMAGE_URL))
    composeTestRule.waitUntil(IMAGE_TIMEOUT_MS) { imageRequested }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_IMAGE_FALLBACK, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Checks that the image and fallback tags take the suffix, so repeated cards stay distinct. */
  @Test
  fun artworkCard_withTestTagSuffix_suffixesImageTags() {
    installImageLoader(FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build())

    // One card with an image and one without, so each tag is checked in a state that cannot
    // change while the test runs.
    composeTestRule.setContent {
      MaterialTheme {
        Column {
          TestArtworkCard(artworkWithImage(IMAGE_URL), testTagSuffix = "1")
          TestArtworkCard(artworkWithImage(imageUrl = null), testTagSuffix = "2")
        }
      }
    }

    composeTestRule
        .onNodeWithTag("${ArtworkCardTestTag.ARTWORK_CARD_THUMBNAIL}_1", useUnmergedTree = true)
        .assertExists()
    composeTestRule
        .onNodeWithTag(
            "${ArtworkCardTestTag.ARTWORK_CARD_IMAGE_FALLBACK}_2",
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  private fun installImageLoader(engine: FakeImageLoaderEngine) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
  }

  private fun setCardContent(artwork: Artwork) {
    composeTestRule.setContent { MaterialTheme { TestArtworkCard(artwork) } }
  }

  @Composable
  private fun TestArtworkCard(artwork: Artwork, testTagSuffix: String? = null) {
    ArtworkCard(
        artwork = artwork,
        isSaved = false,
        onArtworkClick = {},
        onMapClick = {},
        onSaveClick = { _ -> },
        testTagSuffix = testTagSuffix,
    )
  }

  private fun artworkWithImage(imageUrl: String?): Artwork =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
          imageUrl = imageUrl,
      )
}
