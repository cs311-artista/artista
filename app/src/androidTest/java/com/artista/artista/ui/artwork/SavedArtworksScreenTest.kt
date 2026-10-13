// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [SavedArtworksScreen].
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@RunWith(AndroidJUnit4::class)
class SavedArtworksScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var fakeRepository: FakeArtworkRepository
  private lateinit var viewModel: SavedArtworksViewModel
  private var isScreenVisible by mutableStateOf(true)

  private val savedArtwork =
      Artwork(
          uid = "starryId",
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

  /** Verifies that saved artworks are rendered with their tagged actions. */
  @Test
  fun savedArtworksScreen_displaysSavedArtworkList() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.detailsButton(savedArtwork))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork))
        .assertIsDisplayed()
  }

  /** Verifies that a saved artwork card displays its artist name and title. */
  @Test
  fun savedArtworksScreen_displaysArtworkArtistNameAndTitle() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()

    val artistName = checkNotNull(savedArtwork.artistName)
    composeTestRule.onNodeWithText(artistName).assertIsDisplayed()
    composeTestRule.onNodeWithText(savedArtwork.name).assertIsDisplayed()
  }

  /** Verifies that an artwork without an artist name displays the shared fallback text. */
  @Test
  fun savedArtworksScreen_whenArtistIsUnknown_displaysFallbackText() {
    fakeRepository.savedArtworks.add(savedArtwork.copy(artistName = null))
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.artwork_unknown_artist))
        .assertIsDisplayed()
  }

  /** Verifies that the saved artworks list can scroll to cards outside the initial viewport. */
  @Test
  fun savedArtworksScreen_savedArtworkListIsScrollable() {
    val artworks =
        (1..10).map { index -> savedArtwork.copy(uid = "artwork$index", name = "Artwork $index") }
    fakeRepository.savedArtworks.addAll(artworks)
    setScreenContent()

    composeTestRule.waitForIdle()

    val lastArtwork = artworks.last()
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.LIST)
        .performScrollToNode(hasTestTag(SavedArtworksTestTags.card(lastArtwork)))
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(lastArtwork)).assertIsDisplayed()
  }

  /** Verifies that an empty repository renders the empty state instead of a list. */
  @Test
  fun savedArtworksScreen_whenNoArtworkIsSaved_displaysEmptyState() {
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.EMPTY_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LIST).assertDoesNotExist()
  }

  /** Verifies that the loading state is shown until the repository answers. */
  @Test
  fun savedArtworksScreen_whileLoading_displaysLoadingState() {
    fakeRepository.savedArtworks.add(savedArtwork)
    // Holds the fake's load paused, like a slow network call, until the gate is completed.
    val gate = CompletableDeferred<Unit>()
    fakeRepository.loadGate = gate
    setScreenContent()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LOADING_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LIST).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.EMPTY_STATE).assertDoesNotExist()

    // Opens the gate so the paused load resumes and delivers the artworks.
    gate.complete(Unit)
    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.card(savedArtwork))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LOADING_STATE).assertDoesNotExist()
  }

  /** Verifies that repository loading failures display a retryable snackbar. */
  @Test
  fun savedArtworksScreen_whenLoadingFails_displaysRetrySnackbar() {
    fakeRepository.shouldThrowError = true
    setScreenContent()
    // The user sees the friendly resource message, never the repository's exception text.
    val loadErrorMessage = composeTestRule.activity.getString(R.string.saved_artworks_load_error)

    composeTestRule.waitUntil(3_000) {
      composeTestRule.onAllNodesWithText(loadErrorMessage).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithText(loadErrorMessage).assertIsDisplayed()
    composeTestRule.onNodeWithText("Simulated database failure").assertDoesNotExist()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.saved_artworks_retry))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LOAD_ERROR_STATE).assertIsDisplayed()
  }

  /** Verifies that the snackbar's Retry action reloads the saved artworks and hides the error. */
  @Test
  fun savedArtworksScreen_whenRetryIsClicked_reloadsSavedArtworks() {
    fakeRepository.savedArtworks.add(savedArtwork)
    fakeRepository.shouldThrowError = true
    setScreenContent()
    val loadErrorMessage = composeTestRule.activity.getString(R.string.saved_artworks_load_error)

    composeTestRule.waitUntil(3_000) {
      composeTestRule.onAllNodesWithText(loadErrorMessage).fetchSemanticsNodes().isNotEmpty()
    }
    // The connection is back, so the next load succeeds.
    fakeRepository.shouldThrowError = false
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.saved_artworks_retry))
        .performClick()

    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.card(savedArtwork))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.waitUntil(3_000) {
      composeTestRule.onAllNodesWithText(loadErrorMessage).fetchSemanticsNodes().isEmpty()
    }
  }

  /** Verifies that the error state's Retry button reloads the list once the snackbar is gone. */
  @Test
  fun savedArtworksScreen_whenLoadFails_retryFromErrorStateShowsList() {
    fakeRepository.savedArtworks.add(savedArtwork)
    fakeRepository.shouldThrowError = true
    setScreenContent()

    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.LOAD_ERROR_STATE)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    // The connection is back, so the next load succeeds.
    fakeRepository.shouldThrowError = false
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.RETRY_BUTTON).performClick()

    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.card(savedArtwork))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LOAD_ERROR_STATE).assertDoesNotExist()
  }

  /** Verifies that tapping the back arrow invokes the callback. */
  @Test
  fun savedArtworksScreen_whenBackButtonIsClicked_invokesCallback() {
    var backClicked = false
    setScreenContent(onBackClick = { backClicked = true })

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()

    assertTrue(backClicked)
  }

  /** Verifies that opening an artwork forwards the selected artwork to the callback. */
  @Test
  fun savedArtworksScreen_whenDetailsButtonIsClicked_invokesCallback() {
    fakeRepository.savedArtworks.add(savedArtwork)
    var selectedArtwork: Artwork? = null
    setScreenContent(onArtworkDetailsClick = { selectedArtwork = it })

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.detailsButton(savedArtwork)).performClick()

    assertEquals(savedArtwork, selectedArtwork)
  }

  /** Verifies that unsaving removes the artwork from both the repository and the UI. */
  @Test
  fun savedArtworksScreen_whenUnsaveButtonIsClicked_removesArtworkFromListAndRepository() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork)).performClick()

    composeTestRule.waitUntil(3_000) { !fakeRepository.savedArtworks.contains(savedArtwork) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.EMPTY_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertDoesNotExist()
  }

  /** Verifies that a failed unsave keeps the artwork in both the repository and the UI. */
  @Test
  fun savedArtworksScreen_whenUnsaveFails_keepsArtworkInListAndRepository() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    fakeRepository.shouldThrowError = true
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork)).performClick()

    composeTestRule.waitForIdle()
    assertEquals(listOf(savedArtwork), fakeRepository.savedArtworks)
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertIsDisplayed()
  }

  /** Verifies that the card disappears before the repository confirms the removal. */
  @Test
  fun savedArtworksScreen_whenUnsaveButtonIsClicked_removesCardBeforeRepositoryCompletes() {
    fakeRepository.savedArtworks.add(savedArtwork)
    // Holds the fake's removal paused, like a slow network call, until the gate is completed.
    val gate = CompletableDeferred<Unit>()
    fakeRepository.removeGate = gate
    setScreenContent()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork)).performClick()
    composeTestRule.waitForIdle()

    // The repository has not removed it yet, but the card and its button are already gone.
    assertEquals(listOf(savedArtwork), fakeRepository.savedArtworks)
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork))
        .assertDoesNotExist()

    // Opens the gate so the paused removal resumes and finishes.
    gate.complete(Unit)
    composeTestRule.waitUntil(3_000) { fakeRepository.savedArtworks.isEmpty() }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.EMPTY_STATE).assertIsDisplayed()
  }

  /** Verifies that unsaving an artwork already absent from the repository is not an error. */
  @Test
  fun savedArtworksScreen_whenArtworkIsAlreadyAbsentFromRepository_keepsCardRemoved() {
    val otherArtwork = savedArtwork.copy(uid = "girlId", name = "Girl with a Pearl Earring")
    fakeRepository.savedArtworks.addAll(listOf(savedArtwork, otherArtwork))
    setScreenContent()

    composeTestRule.waitForIdle()
    // Simulates the artwork having been unsaved from another screen or device.
    fakeRepository.savedArtworks.remove(savedArtwork)
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork)).performClick()
    composeTestRule.waitForIdle()

    // A failed unsave would restore the card, so its absence shows no error path was taken.
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(otherArtwork)).assertIsDisplayed()
    assertEquals(listOf(otherArtwork), fakeRepository.savedArtworks)
  }

  /** Verifies that a failed unsave restores the card at the position it had before. */
  @Test
  fun savedArtworksScreen_whenUnsaveFails_restoresCardAtOriginalPosition() {
    val artworks =
        listOf("First", "Second", "Third").map {
          savedArtwork.copy(uid = "${it.lowercase()}Id", name = it)
        }
    fakeRepository.savedArtworks.addAll(artworks)
    setScreenContent()

    composeTestRule.waitForIdle()
    fakeRepository.shouldThrowError = true
    // On small screens (such as the CI emulator) the second card's button can be outside the
    // visible part of the list, and a click there would land on another element.
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.LIST)
        .performScrollToNode(hasTestTag(SavedArtworksTestTags.unsaveButton(artworks[1])))
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(artworks[1])).performClick()
    composeTestRule.waitForIdle()
    // Scrolling may have removed the first card from the list's children, so go back to the top
    // before checking positions.
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.LIST).performScrollToIndex(0)

    assertEquals(artworks, fakeRepository.savedArtworks)
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.LIST)
        .onChildAt(1)
        .assert(hasTestTag(SavedArtworksTestTags.card(artworks[1])))
  }

  /** Verifies that a failed unsave does not duplicate a card that a reload already restored. */
  @Test
  fun savedArtworksScreen_whenUnsaveFailsAfterReload_doesNotDuplicateCard() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    // Keeps the removal paused so a reload can happen before it fails.
    val gate = CompletableDeferred<Unit>()
    fakeRepository.removeGate = gate
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(savedArtwork)).performClick()
    composeTestRule.waitForIdle()

    // The repository still holds the artwork, so the reload brings its card back.
    composeTestRule.runOnIdle { isScreenVisible = false }
    composeTestRule.runOnIdle { isScreenVisible = true }
    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.card(savedArtwork))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // Set only now: the fake uses the same flag for loading, which would fail the reload above.
    fakeRepository.shouldThrowError = true
    gate.complete(Unit)
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithTag(SavedArtworksTestTags.card(savedArtwork)).assertCountEquals(1)
    assertEquals(listOf(savedArtwork), fakeRepository.savedArtworks)
  }

  /** Verifies that artworks sharing a title are told apart by their uid. */
  @Test
  fun savedArtworksScreen_whenTwoArtworksShareATitle_unsavesOnlyTheSelectedOne() {
    val sameTitleArtwork = savedArtwork.copy(uid = "starryCopyId")
    fakeRepository.savedArtworks.addAll(listOf(savedArtwork, sameTitleArtwork))
    setScreenContent()

    composeTestRule.waitForIdle()
    // Both cards render: list keys and test tags come from the uid, not the shared title.
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(sameTitleArtwork)).assertIsDisplayed()

    // On small screens (such as the CI emulator) the second card's button can be outside the
    // visible part of the list, and a click there would land on another element.
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.LIST)
        .performScrollToNode(hasTestTag(SavedArtworksTestTags.unsaveButton(sameTitleArtwork)))
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.unsaveButton(sameTitleArtwork))
        .performClick()
    composeTestRule.waitUntil(3_000) { fakeRepository.savedArtworks.size == 1 }

    assertEquals(listOf(savedArtwork), fakeRepository.savedArtworks)
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(sameTitleArtwork)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertIsDisplayed()
  }

  /** Verifies that coming back to the screen reloads the list kept by the ViewModel. */
  @Test
  fun savedArtworksScreen_whenScreenIsShownAgain_reloadsSavedArtworks() {
    fakeRepository.savedArtworks.add(savedArtwork)
    setScreenContent()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertIsDisplayed()

    // Simulates opening the details screen, unsaving the artwork there, and pressing back.
    composeTestRule.runOnIdle { isScreenVisible = false }
    fakeRepository.savedArtworks.clear()
    composeTestRule.runOnIdle { isScreenVisible = true }

    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.EMPTY_STATE)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(savedArtwork)).assertDoesNotExist()
  }

  /** Verifies that a load canceled by a newer one is not reported as a loading error. */
  @Test
  fun savedArtworksScreen_whenReloadedWhileLoading_doesNotShowError() {
    fakeRepository.savedArtworks.add(savedArtwork)
    // Keeps the first load paused so the second refresh starts while it is still running.
    val gate = CompletableDeferred<Unit>()
    fakeRepository.loadGate = gate
    setScreenContent()

    composeTestRule.waitForIdle()
    // Showing the screen again starts a new load, which cancels the paused one.
    composeTestRule.runOnIdle { isScreenVisible = false }
    composeTestRule.runOnIdle { isScreenVisible = true }
    composeTestRule.waitForIdle()

    gate.complete(Unit)
    composeTestRule.waitUntil(3_000) {
      composeTestRule
          .onAllNodesWithTag(SavedArtworksTestTags.card(savedArtwork))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.saved_artworks_load_error))
        .assertDoesNotExist()
  }

  /**
   * Sets the saved artworks screen content with the test ViewModel and callbacks.
   *
   * @param onBackClick callback invoked when the back arrow is tapped
   * @param onArtworkDetailsClick callback invoked when an artwork details action is selected
   * @param onTabSelected callback invoked when a bottom-navigation tab is selected
   */
  private fun setScreenContent(
      onBackClick: () -> Unit = {},
      onArtworkDetailsClick: (Artwork) -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    viewModel = SavedArtworksViewModel(fakeRepository)
    composeTestRule.setContent {
      ArtistaTheme {
        // Toggling this mimics NavHost removing the screen and composing it again.
        if (isScreenVisible) {
          SavedArtworksScreen(
              viewModel = viewModel,
              onBackClick = onBackClick,
              onArtworkDetailsClick = onArtworkDetailsClick,
              onTabSelected = onTabSelected,
          )
        }
      }
    }
  }

  /** In-memory test double implementing [SavedArtworksRepository]. */
  private class FakeArtworkRepository : SavedArtworksRepository {
    val savedArtworks = mutableListOf<Artwork>()
    var shouldThrowError = false

    /**
     * When set, [removeArtworkFromSaved] suspends until it completes, to observe the screen
     * meanwhile.
     *
     * A [CompletableDeferred] is a value the test completes by hand: `await()` suspends the caller
     * without blocking the main thread, and `complete(Unit)` resumes it. `null` means no delay.
     */
    var removeGate: CompletableDeferred<Unit>? = null

    /**
     * When set, [getSavedArtworks] suspends until it completes, to observe the loading state.
     *
     * Works like [removeGate]: `await()` pauses the load and `complete(Unit)` resumes it. `null`
     * means no delay.
     */
    var loadGate: CompletableDeferred<Unit>? = null

    override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
      return savedArtworks.any { it.uid == uid }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      // Match repository upsert behavior by replacing an existing artwork with the same uid.
      savedArtworks.removeAll { it.uid == artwork.uid }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
      // Suspends here while the test keeps the gate closed; skipped when no gate is set.
      removeGate?.await()
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      if (!savedArtworks.removeAll { it.uid == uid }) {
        throw NoSuchElementException("No saved artwork with uid $uid")
      }
    }

    override suspend fun getSavedArtworks(): List<Artwork> {
      // Suspends here while the test keeps the gate closed; skipped when no gate is set.
      loadGate?.await()
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      return savedArtworks.toList()
    }
  }
}
