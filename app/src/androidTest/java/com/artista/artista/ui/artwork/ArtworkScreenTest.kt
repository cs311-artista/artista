// Co-authored-by: Gemini <gemini@google.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
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

  private lateinit var fakeRepository: FakeArtworkRepository
  private lateinit var viewModel: ArtworkScreenViewModel
  private val milliseconds = 3000L

  private val sampleArtwork =
      Artwork(
          uid = "monaId",
          name = "Mona Lisa",
          artistName = "Leonardo da Vinci",
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
    fakeRepository = FakeArtworkRepository()
    viewModel = ArtworkScreenViewModel(fakeRepository)
  }

  @Test
  fun artworkScreen_whenNotSaved_clickingSaveButtonSavesArtworkInRepository() {
    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = milliseconds) {
      runBlocking { fakeRepository.isArtworkSaved(sampleArtwork.name) }
    }

    runBlocking { assertTrue(fakeRepository.isArtworkSaved(sampleArtwork.name)) }
  }

  @Test
  fun artworkScreen_whenAlreadySaved_clickingSaveButtonRemovesArtworkFromRepository() {
    runBlocking { fakeRepository.saveArtwork(sampleArtwork) }

    setScreenContent()

    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = milliseconds) {
      runBlocking { !fakeRepository.isArtworkSaved(sampleArtwork.name) }
    }

    runBlocking { assertFalse(fakeRepository.isArtworkSaved(sampleArtwork.name)) }
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

  private fun setScreenContent(onBackClick: () -> Unit = {}, artwork: Artwork = sampleArtwork) {
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

  /** In-memory test double implementing [SavedArtworksRepository]. */
  private class FakeArtworkRepository : SavedArtworksRepository {
    private val savedArtworks = mutableListOf<Artwork>()

    override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
      return savedArtworks.any { it.name == uid }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      savedArtworks.removeAll { it.name == artwork.name }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
      savedArtworks.removeAll { it.name == uid }
    }

    override suspend fun getSavedArtworks(): List<Artwork> =
        listOf(
            Artwork(
                uid = "starryId",
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
