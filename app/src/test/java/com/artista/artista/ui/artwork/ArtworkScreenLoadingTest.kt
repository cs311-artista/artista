// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for loading states rendered by [ArtworkScreen].
 *
 * @author hixeum
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtworkScreenLoadingTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = StandardTestDispatcher()
  private val savedRepository = ArtworkRepositoryProvider.mockRepository

  private val sampleArtwork =
      Artwork(
          uid = "monaId",
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
    Dispatchers.setMain(testDispatcher)
    savedRepository.clear()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun artworkScreen_whileArtworkIsLoading_displaysLoadingIndicator() = runTest {
    val artworkResult = CompletableDeferred<Artwork>()
    val viewModel = createViewModel(DelayedWikiDataRepository(artworkResult))

    composeTestRule.setContent {
      MaterialTheme { ArtworkScreen(artworkScreenViewModel = viewModel, onBackClick = {}) }
    }
    advanceUntilIdle()

    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(ArtworkTestTags.ARTWORK_ERROR_MESSAGE).assertCountEquals(0)
  }

  @Test
  fun artworkScreen_afterArtworkLoads_hidesLoadingIndicatorAndDisplaysArtwork() = runTest {
    val artworkResult = CompletableDeferred<Artwork>()
    val viewModel = createViewModel(DelayedWikiDataRepository(artworkResult))

    composeTestRule.setContent {
      MaterialTheme { ArtworkScreen(artworkScreenViewModel = viewModel, onBackClick = {}) }
    }
    advanceUntilIdle()
    artworkResult.complete(sampleArtwork)
    advanceUntilIdle()
    composeTestRule.waitForIdle()

    composeTestRule
        .onAllNodesWithTag(ArtworkTestTags.ARTWORK_LOADING_INDICATOR)
        .assertCountEquals(0)
    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_TITLE).assertIsDisplayed()
  }

  private fun createViewModel(repository: WikiDataRepository) =
      ArtworkScreenViewModel(sampleArtwork.uid, repository, savedRepository)

  private class DelayedWikiDataRepository(private val artworkResult: CompletableDeferred<Artwork>) :
      WikiDataRepository by ArtworkRepositoryLocal() {
    override suspend fun getArtwork(uid: ArtworkId): Artwork = artworkResult.await()

    override suspend fun getArtworksNearLocation(
        searchArea: SearchArea,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = throw NotImplementedError()

    override suspend fun getPopularArtists(): List<Artist> = throw NotImplementedError()

    override suspend fun searchArtworks(
        artworkTitle: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = throw NotImplementedError()
  }
}
