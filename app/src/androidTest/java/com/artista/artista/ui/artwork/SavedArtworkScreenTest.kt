// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [SavedArtworkScreen].
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@RunWith(AndroidJUnit4::class)
class SavedArtworkScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // ---------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------

  private lateinit var fakeRepository: FakeArtworkRepository
  private lateinit var viewModel: SavedArtworkViewModel

  private val savedArtwork =
      Artwork(
          name = "The Starry Night",
          artistName = "Vincent van Gogh",
          description = "A landscape under a swirling night sky.",
          conceptionDate = null,
          museum = "Museum of Modern Art",
          location = null,
          dimension = null,
      )

  @Before
  fun setUp() {
    fakeRepository = FakeArtworkRepository()
  }

  // ---------------------------------------------------------------------------
  // Screen rendering tests
  // ---------------------------------------------------------------------------

  /** Verifies that saved artworks are rendered with their tagged actions. */
  @Test
  fun savedArtworkScreen_displaysSavedArtworkList() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SavedArtworkTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(savedArtwork)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SavedArtworkTestTags.detailsButton(savedArtwork))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SavedArtworkTestTags.unsaveButton(savedArtwork))
        .assertIsDisplayed()
  }

  /** Verifies that a saved artwork card displays its artist name and title. */
  @Test
  fun savedArtworkScreen_displaysArtworkArtistNameAndTitle() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()

    val artistName = checkNotNull(savedArtwork.artistName)
    composeTestRule.onNodeWithText(artistName).assertIsDisplayed()
    composeTestRule.onNodeWithText(savedArtwork.name).assertIsDisplayed()
  }

  /** Verifies that the saved-artwork list can scroll to cards outside the initial viewport. */
  @Test
  fun savedArtworkScreen_savedArtworkListIsScrollable() {
    val artworks = (1..10).map { index -> savedArtwork.copy(name = "Artwork $index") }
    fakeRepository.savedArtworks.addAll(artworks)
    setScreenContent()

    composeTestRule.waitForIdle()

    val lastArtwork = artworks.last()
    composeTestRule
        .onNodeWithTag(SavedArtworkTestTags.LIST)
        .performScrollToNode(hasTestTag(SavedArtworkTestTags.card(lastArtwork)))
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(lastArtwork)).assertIsDisplayed()
  }

  /** Verifies that an empty repository renders the empty state instead of a list. */
  @Test
  fun savedArtworkScreen_whenNoArtworkIsSaved_displaysEmptyState() {
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SavedArtworkTestTags.EMPTY_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.LIST).assertDoesNotExist()
  }

  /** Verifies that repository failures render the tagged error state. */
  @Test
  fun savedArtworkScreen_whenLoadingFails_displaysErrorState() {
    fakeRepository.shouldThrowError = true
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SavedArtworkTestTags.ERROR_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.EMPTY_STATE).assertDoesNotExist()
  }

  // ---------------------------------------------------------------------------
  // Screen interaction tests
  // ---------------------------------------------------------------------------

  /** Verifies that opening an artwork forwards the selected artwork to the callback. */
  @Test
  fun savedArtworkScreen_whenDetailsButtonIsClicked_invokesCallback() {
    fakeRepository.savedArtworks.add(savedArtwork)
    var selectedArtwork: Artwork? = null
    setScreenContent(onArtworkDetailsClick = { selectedArtwork = it })

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.detailsButton(savedArtwork)).performClick()

    assertEquals(savedArtwork, selectedArtwork)
  }

  /** Verifies that unsaving removes the artwork from both the repository and the UI. */
  @Test
  fun savedArtworkScreen_whenUnsaveButtonIsClicked_removesArtworkFromListAndRepository() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.unsaveButton(savedArtwork)).performClick()

    composeTestRule.waitUntil(3_000) { !fakeRepository.savedArtworks.contains(savedArtwork) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.EMPTY_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(savedArtwork)).assertDoesNotExist()
  }

  // ---------------------------------------------------------------------------
  // Compose content setup
  // ---------------------------------------------------------------------------

  /**
   * Sets the saved-artwork screen content with the test ViewModel and callbacks.
   *
   * @param onArtworkDetailsClick callback invoked when an artwork details action is selected
   * @param onTabSelected callback invoked when a bottom-navigation tab is selected
   */
  private fun setScreenContent(
      onArtworkDetailsClick: (Artwork) -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    // Create the ViewModel after repository setup so its initial asynchronous load sees test data.
    viewModel = SavedArtworkViewModel(fakeRepository)
    composeTestRule.setContent {
      ArtistaTheme {
        SavedArtworkScreen(
            viewModel = viewModel,
            onArtworkDetailsClick = onArtworkDetailsClick,
            onTabSelected = onTabSelected,
        )
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Fake repository
  // ---------------------------------------------------------------------------

  /** In-memory test double implementing [ArtworkRepository]. */
  private class FakeArtworkRepository : ArtworkRepository {
    val savedArtworks = mutableListOf<Artwork>()
    var shouldThrowError = false

    override suspend fun isArtworkSaved(artworkName: String): Boolean {
      return savedArtworks.any { it.name == artworkName }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      // Match repository upsert behavior by replacing an existing artwork with the same name.
      savedArtworks.removeAll { it.name == artwork.name }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtwork(artworkName: String) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.name == artworkName }
    }

    override suspend fun getSavedArtworks(): List<Artwork> {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      return savedArtworks.toList()
    }
  }
}
