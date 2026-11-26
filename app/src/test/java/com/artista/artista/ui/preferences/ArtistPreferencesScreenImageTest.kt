// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.preferences

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.request.ErrorResult
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.ui.artwork.BROKEN_IMAGE_URL
import com.artista.artista.ui.artwork.IMAGE_TIMEOUT_MS
import com.artista.artista.ui.artwork.IMAGE_URL
import com.artista.artista.ui.artwork.failingImageEngine
import com.artista.artista.ui.artwork.installFakeImageLoader
import com.artista.artista.ui.artwork.waitUntilFallbackDisappears
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the artist pictures shown in the cards of [ArtistPreferencesScreen].
 *
 * @author Timz3rr, krfpn
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtistPreferencesScreenImageTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val artistWithPicture = Artist("test-picasso", "Pablo Picasso", imageUrl = IMAGE_URL)

  private val userRepository = FakeUserRepository()

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /**
   * Checks that a loaded artist picture is shown with a description naming the artist, without
   * fallback.
   */
  @Test
  fun artistCard_withLoadedPicture_displaysPictureWithoutFallback() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setScreenContent(listOf(artistWithPicture))
    composeTestRule.waitUntilFallbackDisappears(
        ArtistPreferencesTestTags.getArtistImageFallbackTestTag(artistWithPicture.uid)
    )

    composeTestRule
        .onNodeWithTag(
            ArtistPreferencesTestTags.getArtistImageTestTag(artistWithPicture.uid),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
        .assertContentDescriptionEquals(
            context.getString(
                R.string.artist_card_picture_description,
                artistWithPicture.name,
            )
        )
  }

  /** Checks that an artist without a picture link shows the fallback icon and requests nothing. */
  @Test
  fun artistCard_withoutImageUrl_displaysFallbackOnly() {
    var imageRequested = false
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder()
            .intercept({ true }) { chain ->
              imageRequested = true
              ErrorResult(image = null, request = chain.request, throwable = IOException("404"))
            }
            .build()
    )
    val artist = Artist("test-monet", "Claude Monet", imageUrl = null)

    setScreenContent(listOf(artist))

    composeTestRule
        .onNodeWithTag(
            ArtistPreferencesTestTags.getArtistImageFallbackTestTag(artist.uid),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(
            ArtistPreferencesTestTags.getArtistImageTestTag(artist.uid),
            useUnmergedTree = true,
        )
        .assertDoesNotExist()
    assertFalse(imageRequested)
  }

  /** Checks that the fallback icon stays visible when the artist picture fails to download. */
  @Test
  fun artistCard_whenPictureFailsToLoad_keepsFallback() {
    var imageRequested = false
    installFakeImageLoader(failingImageEngine(BROKEN_IMAGE_URL) { imageRequested = true })
    val artist = Artist("test-van-gogh", "Vincent van Gogh", imageUrl = BROKEN_IMAGE_URL)

    setScreenContent(listOf(artist))
    composeTestRule.waitUntil(IMAGE_TIMEOUT_MS) { imageRequested }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(
            ArtistPreferencesTestTags.getArtistImageFallbackTestTag(artist.uid),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  /** Checks that clicking a card whose picture has loaded still untoggles the artist. */
  @Test
  fun artistCard_withLoadedPicture_clickUntogglesArtist() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setScreenContent(listOf(artistWithPicture))
    composeTestRule.waitUntilFallbackDisappears(
        ArtistPreferencesTestTags.getArtistImageFallbackTestTag(artistWithPicture.uid)
    )
    // The picture must really be shown, so the click lands on a card covered by it.
    composeTestRule
        .onNodeWithTag(
            ArtistPreferencesTestTags.getArtistImageTestTag(artistWithPicture.uid),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(artistWithPicture.uid))
        .performClick()

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(artistWithPicture.uid))
        .assertIsOff()
  }

  /**
   * Shows the screen with [artists] saved by the signed-in user, and waits until they have loaded.
   *
   * @param artists the saved artists, whose details are served by the repository
   */
  private fun setScreenContent(artists: List<Artist>) {
    userRepository.store(
        User(uid = USER_ID, preference = UserPreference(artists = artists.map { it.uid }))
    )
    val viewModel =
        ArtistPreferencesViewModel(
            wikiDataRepository = ArtistsWikiDataRepository(artists),
            authRepository = FakeAuthRepository(FakeAuthUser(uid = USER_ID)),
            userRepository = userRepository,
        )
    composeTestRule.setContent {
      MaterialTheme {
        ArtistPreferencesScreen(
            onBackClick = {},
            onTabSelected = {},
            artistPreferencesViewModel = viewModel,
        )
      }
    }
    // Until the load ends, the loading state replaces the grid.
    composeTestRule.waitUntil(IMAGE_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(ArtistPreferencesTestTags.LOADING_STATE)
          .fetchSemanticsNodes()
          .isEmpty()
    }
  }

  /**
   * Catalog that knows only [artists]; every other method is forwarded to an empty
   * [ArtworkRepositoryLocal], since this screen only looks artists up by identifier.
   */
  private class ArtistsWikiDataRepository(private val artists: List<Artist>) :
      WikiDataRepository by ArtworkRepositoryLocal() {
    override suspend fun getArtist(qid: ArtistId): Artist? = artists.find { it.uid == qid }
  }

  private companion object {
    const val USER_ID = "test-user"
  }
}
