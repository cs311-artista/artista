// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun searchBar_requestsFocusWhenScreenIsOpened() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_input").assertIsFocused()
  }

  @Test
  fun searchBar_displaysMatchingRecommendationsFromSavedArtworks() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    composeTestRule.onNodeWithTag("search_input").performTextInput("star")
    advanceUntilIdle()

    composeTestRule.onAllNodesWithTag("search_recommendation").assertCountEquals(1)
    composeTestRule.onNodeWithTag("search_recommendation").assert(hasText("The Starry Night"))
    composeTestRule.onNodeWithTag("search_recommendation").assertIsDisplayed()

    composeTestRule.onNodeWithTag("search_input").performTextClearance()
    composeTestRule.onNodeWithTag("search_input").performTextInput("pearl")
    advanceUntilIdle()

    composeTestRule.onAllNodesWithTag("search_recommendation").assertCountEquals(1)
    composeTestRule
        .onNodeWithTag("search_recommendation")
        .assert(hasText("Girl with a Pearl Earring"))
        .assertIsDisplayed()

    composeTestRule.onNodeWithTag("search_input").performTextClearance()
    composeTestRule.onNodeWithTag("search_input").performTextInput("unmatched")
    advanceUntilIdle()

    composeTestRule.onAllNodesWithTag("search_recommendation").assertCountEquals(0)
  }

  @Test
  fun searchBar_hidesDropdownAndSubmitsOnlyWhenQueryIsSubmitted() = runTest {
    var submittedQuery: String? = null
    val wikiRepository =
        object : WikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork {
            throw NotImplementedError()
          }

          override suspend fun searchArtworks(query: String): List<Artwork> {
            submittedQuery = query
            return emptyList()
          }
        }
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), wikiRepository)

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    composeTestRule.onNodeWithTag("search_input").performTextInput("star")
    advanceUntilIdle()

    composeTestRule.onAllNodesWithTag("search_recommendation").assertCountEquals(1)
    composeTestRule.onNodeWithTag("search_recommendation").assertIsDisplayed()
    assertNull(submittedQuery)

    composeTestRule.onNodeWithTag("search_input").performImeAction()
    advanceUntilIdle()

    assertEquals("star", submittedQuery)
    composeTestRule.onAllNodesWithTag("search_recommendation").assertCountEquals(0)
  }

  @Test
  fun searchResults_displayFallbackArtistWhenArtistIsMissing() = runTest {
    val viewModel = SearchScreenViewModel(fakeArtworkRepository(), fakeWikiRepository())

    advanceUntilIdle()
    viewModel.onSearchSubmitted("untitled")
    advanceUntilIdle()

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.onNodeWithText("Untitled study").assert(hasText("Untitled study"))
    composeTestRule.onNodeWithText("Unknown Artist").assert(hasText("Unknown Artist"))
  }

  @Test
  fun toggleSavedArtwork_usesSavedArtworkListToSaveOrRemoveArtwork() = runTest {
    val artwork =
        Artwork(
            uid = "savedId",
            name = "Saved artwork",
            artistName = "Artist",
            location = null,
            conceptionDate = null,
            dimension = null,
            description = null,
            museum = null,
        )
    val savedArtworks = mutableListOf(artwork)
    val repository =
        object : SavedArtworksRepository {
          override suspend fun isArtworkSaved(uid: ArtworkId): Boolean = savedArtworks.any {
            it.uid == uid
          }

          override suspend fun saveArtwork(artwork: Artwork) {
            savedArtworks.add(artwork)
          }

          override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
            savedArtworks.removeAll { it.uid == uid }
          }

          override suspend fun getSavedArtworks(): List<Artwork> = savedArtworks.toList()
        }
    val viewModel = SearchScreenViewModel(repository, fakeWikiRepository())
    advanceUntilIdle()
    assertEquals(listOf(artwork), viewModel.savedArtworks.value)

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(emptyList<Artwork>(), savedArtworks)
    assertEquals(emptyList<Artwork>(), viewModel.savedArtworks.value)

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(listOf(artwork), savedArtworks)
    assertEquals(listOf(artwork), viewModel.savedArtworks.value)
  }

  private fun fakeArtworkRepository(): SavedArtworksRepository =
      object : SavedArtworksRepository {

        private val savedArtworks = mutableListOf<Artwork>()
        var shouldThrowError = false

        override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
          if (shouldThrowError) throw RuntimeException("Simulated error")
          return savedArtworks.any { it.name == uid }
        }

        override suspend fun saveArtwork(artwork: Artwork) {
          if (shouldThrowError) throw RuntimeException("Simulated database failure")
          savedArtworks.removeAll { it.name == artwork.name }
          savedArtworks.add(artwork)
        }

        override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
          if (shouldThrowError) throw RuntimeException("Simulated database failure")
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
                ),
                Artwork(
                    uid = "girlId",
                    name = "Girl with a Pearl Earring",
                    artistName = "Johannes Vermeer",
                    location = null,
                    conceptionDate = null,
                    dimension = null,
                    description = null,
                    museum = null,
                ),
                Artwork(
                    uid = "untitledId",
                    name = "Untitled study",
                    artistName = null,
                    location = null,
                    conceptionDate = null,
                    dimension = null,
                    description = null,
                    museum = null,
                ),
            )
      }

  private fun fakeWikiRepository(): WikiDataRepository =
      object : WikiDataRepository {
        override suspend fun getArtwork(uid: ArtworkId): Artwork {
          throw NotImplementedError()
        }

        override suspend fun searchArtworks(query: String): List<Artwork> =
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
                    ),
                    Artwork(
                        uid = "girlId",
                        name = "Girl with a Pearl Earring",
                        artistName = "Johannes Vermeer",
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                        description = null,
                        museum = null,
                    ),
                    Artwork(
                        uid = "untitledId",
                        name = "Untitled study",
                        artistName = null,
                        location = null,
                        conceptionDate = null,
                        dimension = null,
                        description = null,
                        museum = null,
                    ),
                )
                .filter {
                  it.name.contains(query, ignoreCase = true) ||
                      it.artistName?.contains(query, ignoreCase = true) == true
                }
      }
}
