// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.WikiDataRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the image shown by [ArtworkScreen].
 *
 * The artworks have no location, so that no GoogleMap is composed.
 *
 * @author Timz3rr
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtworkScreenImageTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val repository = ArtworkRepositoryProvider.mockRepository

  private val sampleArtwork =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )

  @Before
  fun setUp() {
    repository.clear()
  }

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /**
   * Checks that a loaded image is shown with a description naming the artwork, without fallback.
   */
  @Test
  fun artworkScreen_withLoadedImage_displaysImageWithoutFallback() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setScreenContent(sampleArtwork.copy(imageUrl = IMAGE_URL))
    composeTestRule.waitUntilFallbackDisappears(ArtworkTestTags.ARTWORK_IMAGE_FALLBACK)

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_IMAGE, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Image of Mona Lisa")
  }

  /** Checks that an artwork without an image link shows the fallback icon and no image. */
  @Test
  fun artworkScreen_withoutImageUrl_displaysFallbackOnly() {
    setScreenContent(sampleArtwork.copy(imageUrl = null))

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_IMAGE_FALLBACK, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_IMAGE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  /** Checks that the fallback icon stays visible when the image fails to download. */
  @Test
  fun artworkScreen_whenImageFailsToLoad_keepsFallback() {
    var imageRequested = false
    installFakeImageLoader(failingImageEngine(BROKEN_IMAGE_URL) { imageRequested = true })

    setScreenContent(sampleArtwork.copy(imageUrl = BROKEN_IMAGE_URL))
    composeTestRule.waitUntil(IMAGE_TIMEOUT_MS) { imageRequested }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_IMAGE_FALLBACK, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Checks that the image description uses the unknown title when the artwork has no name. */
  @Test
  fun artworkScreen_withoutName_describesImageWithUnknownTitle() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setScreenContent(sampleArtwork.copy(name = null, imageUrl = IMAGE_URL))
    composeTestRule.waitUntilFallbackDisappears(ArtworkTestTags.ARTWORK_IMAGE_FALLBACK)

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_IMAGE, useUnmergedTree = true)
        .assertContentDescriptionEquals("Image of Untitled")
  }

  /** Checks that the placeholder text is gone from the screen. */
  @Test
  fun artworkScreen_doesNotDisplayPlaceholderText() {
    setScreenContent(sampleArtwork.copy(imageUrl = null))

    composeTestRule.onNodeWithText("Picture/thumbnail").assertDoesNotExist()
  }

  private fun setScreenContent(artwork: Artwork) {
    val viewModel =
        ArtworkScreenViewModel(artwork.uid, SingleArtworkWikiDataRepository(artwork), repository)
    composeTestRule.setContent {
      MaterialTheme { ArtworkScreen(artworkScreenViewModel = viewModel, onBackClick = {}) }
    }
    // The screen now fetches its artwork, and assertions on an image would be meaningless while
    // the loading indicator is still replacing it.
    composeTestRule.waitUntil(IMAGE_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(ArtworkTestTags.ARTWORK_LOADING_INDICATOR)
          .fetchSemanticsNodes()
          .isEmpty()
    }
  }

  private class SingleArtworkWikiDataRepository(private val artwork: Artwork) :
      WikiDataRepository by ArtworkRepositoryLocal() {
    override suspend fun getArtwork(uid: ArtworkId): Artwork = artwork
  }
}
