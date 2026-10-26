// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    val viewModel = createViewModel()

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).assertIsFocused()
  }

  @Test
  fun searchBar_restoresCursorPositionWhenScreenIsRecreated() = runTest {
    val viewModel = createViewModel()
    val showSearchScreen = mutableStateOf(true)

    composeTestRule.setContent {
      MaterialTheme {
        if (showSearchScreen.value) {
          SearchScreen(onTabSelected = {}, viewModel = viewModel)
        } else {
          Text("Artwork details")
        }
      }
    }

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT)
        .performTextInputSelection(TextRange(2))
    composeTestRule.waitForIdle()
    assertEquals("star", viewModel.uiState.value.query)
    assertEquals(TextRange(2), viewModel.uiState.value.selection)

    composeTestRule.runOnIdle { showSearchScreen.value = false }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { showSearchScreen.value = true }
    composeTestRule.waitForIdle()

    val fieldSemantics =
        composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).fetchSemanticsNode().config
    assertEquals(TextRange(2), fieldSemantics[SemanticsProperties.TextSelectionRange])
  }

  @Test
  fun searchBar_displaysMatchingRecommendationsFromSavedArtworks() = runTest {
    val viewModel = createViewModel()

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assert(hasText("The Starry Night"))
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION).assertIsDisplayed()

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextClearance()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("pearl")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assert(hasText("Girl with a Pearl Earring"))
        .assertIsDisplayed()

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextClearance()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("unmatched")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assertCountEquals(0)
  }

  @Test
  fun searchBar_hidesDropdownAndSubmitsOnlyWhenQueryIsSubmitted() = runTest {
    val wikiRepository = SearchTestData.wikiDataRepository()
    val viewModel = SearchScreenViewModel(SearchTestData.savedArtworksRepository(), wikiRepository)

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule.waitForIdle()
    advanceUntilIdle()
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performTextInput("star")
    advanceUntilIdle()

    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assertCountEquals(1)
    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION).assertIsDisplayed()
    assertTrue(wikiRepository.searchedQueries.isEmpty())

    composeTestRule.onNodeWithTag(SearchScreenTestTags.SEARCH_INPUT).performImeAction()
    advanceUntilIdle()

    assertEquals(listOf("star"), wikiRepository.searchedQueries)
    composeTestRule
        .onAllNodesWithTag(SearchScreenTestTags.SEARCH_RECOMMENDATION)
        .assertCountEquals(0)
  }

  @Test
  fun searchResults_displayFallbackArtistWhenArtistIsMissing() = runTest {
    val viewModel = createViewModel()

    advanceUntilIdle()
    viewModel.onSearchSubmitted("untitled")
    advanceUntilIdle()

    composeTestRule.setContent {
      MaterialTheme { SearchScreen(onTabSelected = {}, viewModel = viewModel) }
    }

    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
        .assert(hasText("Untitled study"))
    composeTestRule
        .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST, useUnmergedTree = true)
        .assert(hasText("Unknown Artist"))
  }

  @Test
  fun toggleSavedArtwork_usesSavedArtworkListToSaveOrRemoveArtwork() = runTest {
    val artwork = SearchTestData.girlWithPearlEarring.copy(uid = "savedId", name = "Saved artwork")
    val repository = SearchTestData.savedArtworksRepository(listOf(artwork))
    val viewModel = SearchScreenViewModel(repository, SearchTestData.wikiDataRepository())
    advanceUntilIdle()
    assertEquals(listOf(artwork), viewModel.uiState.value.savedArtworks)

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(emptyList<Artwork>(), repository.savedArtworks)
    assertEquals(emptyList<Artwork>(), viewModel.uiState.value.savedArtworks)

    viewModel.toggleSavedArtwork(artwork)
    advanceUntilIdle()

    assertEquals(listOf(artwork), repository.savedArtworks)
    assertEquals(listOf(artwork), viewModel.uiState.value.savedArtworks)
  }

  private fun createViewModel() =
      SearchScreenViewModel(
          SearchTestData.savedArtworksRepository(),
          SearchTestData.wikiDataRepository(),
      )
}
