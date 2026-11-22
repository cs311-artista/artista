// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import com.artista.artista.ui.artwork.ArtworkFilterTestTags
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the artwork filter dialog opened from the Search screen.
 *
 * These tests are instrumented because the dialog never gets idle under Robolectric.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenFilterTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var repository: RecordingWikiDataRepository

  @Before
  fun setUp() {
    val localRepository = ArtworkRepositoryLocal()
    localRepository.putArtworkInRepositoryUnsavedSection(starryNight)
    localRepository.putArtworkInRepositoryUnsavedSection(pearlEarring)
    repository = RecordingWikiDataRepository(localRepository)
    val viewModel = SearchScreenViewModel(localRepository, repository)

    composeTestRule.setContent { ArtistaTheme { SearchScreen(viewModel, onTabSelected = {}) } }
  }

  /** Checks that selecting a type re-runs the search for that type and badges the filter. */
  @Test
  fun applyingATypeFilter_reRunsTheSearchWithThatType() {
    search("e")

    composeTestRule.onNodeWithTag(SearchScreenTestTags.FILTER_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(ArtworkFilterTestTags.typeOption(ArtworkType.PAINTING))
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).performClick()

    composeTestRule.runOnIdle {
      assertEquals(
          listOf(ArtworkType.entries, listOf(ArtworkType.PAINTING)),
          repository.searchedTypeValues,
      )
    }
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.FILTER_ACTIVE, useUnmergedTree = true)
        .assertExists()
  }

  /** Checks that filtering by artist narrows the results without a new search. */
  @Test
  fun applyingAnArtistFilter_filtersResultsWithoutNewSearch() {
    search("e")

    composeTestRule.onNodeWithTag(SearchScreenTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-input").performTextInput("ver")
    composeTestRule
        .onNodeWithTag("${ArtworkFilterTestTags.ARTIST}-option-Johannes Vermeer")
        .performClick()
    composeTestRule.onNodeWithTag(ArtworkFilterTestTags.APPLY_BUTTON).performClick()

    composeTestRule.onAllNodesWithTag(ArtworkCardTestTag.ARTWORK_CARD).assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("Girl with a Pearl Earring"))
    composeTestRule.runOnIdle { assertEquals(1, repository.searchedTypeValues.size) }
  }

  /** Submits [query] from the search field and waits for both sample artworks to be listed. */
  private fun search(query: String) {
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput(query)
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performImeAction()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(ArtworkCardTestTag.ARTWORK_CARD)
          .fetchSemanticsNodes()
          .size == 2
    }
  }

  /** Records the types of each search, which [ArtworkRepositoryLocal] otherwise ignores. */
  private class RecordingWikiDataRepository(private val delegate: WikiDataRepository) :
      WikiDataRepository by delegate {
    // Only read and written on the main thread, where the ViewModel searches and runOnIdle runs.
    val searchedTypeValues = mutableListOf<List<ArtworkType>>()

    override suspend fun searchArtworks(
        artworkTitle: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> {
      searchedTypeValues.add(typeValues)
      return delegate.searchArtworks(artworkTitle, typeValues, limit)
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    val starryNight =
        Artwork(
            uid = "starry-night",
            name = "The Starry Night",
            artist = Artist(uid = "van-gogh", name = "Vincent van Gogh"),
            description = null,
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
            types = setOf(ArtworkType.PAINTING),
        )

    val pearlEarring =
        Artwork(
            uid = "pearl-earring",
            name = "Girl with a Pearl Earring",
            artist = Artist(uid = "vermeer", name = "Johannes Vermeer"),
            description = null,
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
            types = setOf(ArtworkType.PAINTING),
        )
  }
}
