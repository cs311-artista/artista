// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.map.Location
import com.artista.artista.ui.theme.ArtistaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [ArtworkScreen].
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class ArtworkScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val repository = ArtworkRepositoryProvider.mockRepository
  private lateinit var viewModel: ArtworkScreenViewModel
  private val milliseconds = 3000L

  private val sampleArtwork =
      Artwork(
          uid = "monaId",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = "A portrait of Lisa Gherardini, wife of Francesco del Giocondo.",
          conceptionDate = null,
          museum = "Musée du Louvre",
          location = null,
          dimension = null,
      )

  private val sampleArtworkWithLocation =
      sampleArtwork.copy(
          location =
              Location(
                  25.0,
                  25.0,
                  "testLocation",
              )
      )

  @Before
  fun setUp() {
    repository.clear()
    viewModel = ArtworkScreenViewModel(repository)
  }

  @Test
  fun artworkScreen_whenNotSaved_clickingSaveButtonSavesArtworkInRepository() {
    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = milliseconds) {
      runBlocking { repository.isArtworkSaved(sampleArtwork.uid) }
    }

    runBlocking { assertTrue(repository.isArtworkSaved(sampleArtwork.uid)) }
  }

  @Test
  fun artworkScreen_whenAlreadySaved_clickingSaveButtonRemovesArtworkFromRepository() {
    runBlocking { repository.saveArtwork(sampleArtwork) }

    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = milliseconds) {
      runBlocking { !repository.isArtworkSaved(sampleArtwork.uid) }
    }

    runBlocking { assertFalse(repository.isArtworkSaved(sampleArtwork.uid)) }
  }

  @Test
  fun artworkScreen_whenLocationIsProvided_mapTileIsDisplayed() {
    setScreenContent(artwork = sampleArtworkWithLocation)

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_MAP_TILE).assertExists()

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_MAP_TILE).performScrollTo()

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_MAP_TILE).assertIsDisplayed()
  }

  @Test
  fun artworkScreen_whenLocationIsNotProvided_mapTileIsNotDisplayed() {
    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_MAP_TILE).assertDoesNotExist()
  }

  @Test
  fun artworkScreen_whenNameIsUnknown_displaysFallbackTitle() {
    setScreenContent(artwork = sampleArtwork.copy(name = null))

    composeTestRule
        .onNodeWithTag(ArtworkTestTags.ARTWORK_TITLE)
        .assertTextEquals(composeTestRule.activity.getString(R.string.artwork_unknown_title))
  }

  private fun setScreenContent(artwork: Artwork = sampleArtwork, onBackClick: () -> Unit = {}) {
    composeTestRule.setContent {
      ArtistaTheme {
        ArtworkScreen(
            artwork = artwork,
            artworkScreenViewModel = viewModel,
            onBackClick = onBackClick,
        )
      }
    }
  }

  @Test
  fun testArtworkPreview() {
    composeTestRule.setContent { ArtworkPreview() }

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_TITLE).assertIsDisplayed()
  }
}
