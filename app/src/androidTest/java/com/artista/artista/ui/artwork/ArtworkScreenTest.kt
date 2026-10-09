// Co-authored-by: Gemini <gemini@google.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
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

  private lateinit var fakeRepository: FakeArtworkRepository
  private lateinit var viewModel: ArtworkScreenViewModel

  private val sampleArtwork =
      Artwork(
          name = "Mona Lisa",
          artistName = "Leonardo da Vinci",
          description = "A portrait of Lisa Gherardini, wife of Francesco del Giocondo.",
          conceptionDate = null,
          museum = "Musée du Louvre",
          location = null,
          dimension = null,
      )

  @Before
  fun setUp() {
    fakeRepository = FakeArtworkRepository()
    viewModel = ArtworkScreenViewModel(fakeRepository)
  }

  @Test
  fun artworkScreen_whenNotSaved_clickingSaveButtonSavesArtworkInRepository() {
    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 3000) {
      runBlocking { fakeRepository.isArtworkSaved(sampleArtwork.name) }
    }

    runBlocking { assertTrue(fakeRepository.isArtworkSaved(sampleArtwork.name)) }
  }

  @Test
  fun artworkScreen_whenAlreadySaved_clickingSaveButtonRemovesArtworkFromRepository() {
    runBlocking { fakeRepository.saveArtwork(sampleArtwork) }

    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 3000) {
      runBlocking { !fakeRepository.isArtworkSaved(sampleArtwork.name) }
    }

    runBlocking { assertFalse(fakeRepository.isArtworkSaved(sampleArtwork.name)) }
  }

  private fun setScreenContent(onBackClick: () -> Unit = {}) {
    composeTestRule.setContent {
      ArtistaTheme {
        ArtworkScreen(
            artwork = sampleArtwork,
            artworkScreenViewModel = viewModel,
            onBackClick = onBackClick,
        )
      }
    }
  }

  /** In-memory test double implementing [ArtworkRepository]. */
  private class FakeArtworkRepository : ArtworkRepository {
    private val savedArtworks = mutableListOf<Artwork>()

    override suspend fun isArtworkSaved(artworkName: String): Boolean {
      return savedArtworks.any { it.name == artworkName }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      savedArtworks.removeAll { it.name == artwork.name }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtwork(artworkName: String) {
      savedArtworks.removeAll { it.name == artworkName }
    }

    override suspend fun getSavedArtworks(): List<Artwork> =
        listOf(
            Artwork(
                name = "The Starry Night",
                artistName = "Vincent van Gogh",
                location = null,
                conceptionDate = null,
                dimension = null,
                description = null,
                museum = null,
            )
        )
  }
}
