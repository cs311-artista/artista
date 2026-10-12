// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import kotlinx.coroutines.CompletableDeferred
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
  private var isScreenVisible by mutableStateOf(true)

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

  /** Verifies that repository loading failures display a retryable snackbar. */
  @Test
  fun savedArtworkScreen_whenLoadingFails_displaysRetrySnackbar() {
    fakeRepository.shouldThrowError = true
    setScreenContent()
    // The user sees the friendly resource message, never the repository's exception text.
    val loadErrorMessage = composeTestRule.activity.getString(R.string.saved_artworks_load_error)

    composeTestRule.waitUntil(3_000) {
      composeTestRule.onAllNodesWithText(loadErrorMessage).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithText(loadErrorMessage).assertIsDisplayed()
    composeTestRule.onNodeWithText("Simulated database failure").assertDoesNotExist()
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.EMPTY_STATE).assertIsDisplayed()
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

  /** Verifies that a failed unsave keeps the artwork in both the repository and the UI. */
  @Test
  fun savedArtworkScreen_whenUnsaveFails_keepsArtworkInListAndRepository() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    fakeRepository.shouldThrowError = true
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.unsaveButton(savedArtwork)).performClick()

    composeTestRule.waitForIdle()
    assertEquals(listOf(savedArtwork), fakeRepository.savedArtworks)
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(savedArtwork)).assertIsDisplayed()
  }

  /** Verifies that the card disappears before the repository confirms the removal. */
  @Test
  fun savedArtworkScreen_whenUnsaveButtonIsClicked_removesCardBeforeRepositoryCompletes() {
    fakeRepository.savedArtworks.add(savedArtwork)
    // Holds the fake's removal paused, like a slow network call, until the gate is completed.
    val gate = CompletableDeferred<Unit>()
    fakeRepository.removeGate = gate
    setScreenContent()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.unsaveButton(savedArtwork)).performClick()
    composeTestRule.waitForIdle()

    // The repository has not removed it yet, but the card and its button are already gone.
    assertEquals(listOf(savedArtwork), fakeRepository.savedArtworks)
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(savedArtwork)).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(SavedArtworkTestTags.unsaveButton(savedArtwork))
        .assertDoesNotExist()

    // Opens the gate so the paused removal resumes and finishes.
    gate.complete(Unit)
    composeTestRule.waitUntil(3_000) { fakeRepository.savedArtworks.isEmpty() }
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.EMPTY_STATE).assertIsDisplayed()
  }

  /** Verifies that unsaving an artwork already absent from the repository is not an error. */
  @Test
  fun savedArtworkScreen_whenArtworkIsAlreadyAbsentFromRepository_keepsCardRemoved() {
    val otherArtwork = savedArtwork.copy(name = "Girl with a Pearl Earring")
    fakeRepository.savedArtworks.addAll(listOf(savedArtwork, otherArtwork))
    setScreenContent()

    composeTestRule.waitForIdle()
    // Simulates the artwork having been unsaved from another screen or device.
    fakeRepository.savedArtworks.remove(savedArtwork)
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.unsaveButton(savedArtwork)).performClick()
    composeTestRule.waitForIdle()

    // A failed unsave would restore the card, so its absence shows no error path was taken.
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(savedArtwork)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(otherArtwork)).assertIsDisplayed()
    assertEquals(listOf(otherArtwork), fakeRepository.savedArtworks)
  }

  /** Verifies that a failed unsave restores the card at the position it had before. */
  @Test
  fun savedArtworkScreen_whenUnsaveFails_restoresCardAtOriginalPosition() {
    val artworks = listOf("First", "Second", "Third").map { savedArtwork.copy(name = it) }
    fakeRepository.savedArtworks.addAll(artworks)
    setScreenContent()

    composeTestRule.waitForIdle()
    fakeRepository.shouldThrowError = true
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.unsaveButton(artworks[1])).performClick()
    composeTestRule.waitForIdle()

    assertEquals(artworks, fakeRepository.savedArtworks)
    composeTestRule
        .onNodeWithTag(SavedArtworkTestTags.LIST)
        .onChildAt(1)
        .assert(hasTestTag(SavedArtworkTestTags.card(artworks[1])))
  }

  /** Verifies that coming back to the screen reloads the list kept by the ViewModel. */
  @Test
  fun savedArtworkScreen_whenScreenIsShownAgain_reloadsSavedArtworks() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworkTestTags.card(savedArtwork)).assertIsDisplayed()

    // Simulates opening the details screen, unsaving the artwork there, and pressing back.
    composeTestRule.runOnIdle { isScreenVisible = false }
    fakeRepository.savedArtworks.clear()
    composeTestRule.runOnIdle { isScreenVisible = true }

    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworkTestTags.EMPTY_STATE)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
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
    viewModel = SavedArtworkViewModel(fakeRepository)
    composeTestRule.setContent {
      ArtistaTheme {
        // Toggling this mimics NavHost removing the screen and composing it again.
        if (isScreenVisible) {
          SavedArtworkScreen(
              viewModel = viewModel,
              onArtworkDetailsClick = onArtworkDetailsClick,
              onTabSelected = onTabSelected,
          )
        }
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

    /**
     * When set, [removeArtwork] suspends until it completes, to observe the screen meanwhile.
     *
     * A [CompletableDeferred] is a value the test completes by hand: `await()` suspends the caller
     * without blocking the main thread, and `complete(Unit)` resumes it. `null` means no delay.
     */
    var removeGate: CompletableDeferred<Unit>? = null

    override suspend fun isArtworkSaved(artworkName: String): Boolean {
      return savedArtworks.any { it.name == artworkName }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      // Match repository upsert behavior by replacing an existing artwork with the same name.
      savedArtworks.removeAll { it.name == artwork.name }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtwork(artworkName: String) {
      // Suspends here while the test keeps the gate closed; skipped when no gate is set.
      removeGate?.await()
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      if (!savedArtworks.removeAll { it.name == artworkName }) {
        throw NoSuchElementException("No saved artwork named $artworkName")
      }
    }

    override suspend fun getSavedArtworks(): List<Artwork> {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      return savedArtworks.toList()
    }
  }
}
