package com.artista.artista.ui.preferences

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies the user-visible behavior of the artist preferences screen.
 *
 * @author Timz3rr, patrickmcdan
 */
@RunWith(AndroidJUnit4::class)
class ArtistPreferencesScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /** Verifies that every element of the artist preferences screen is displayed. */
  @Test
  fun artistPreferencesScreen_displaysAllElements() {
    setPreferencesContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.POPULAR_ARTISTS_TITLE)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /** Verifies that the title, description and section title display their texts. */
  @Test
  fun artistPreferencesScreen_displaysTexts() {
    setPreferencesContent()

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.TITLE)
        .assertTextEquals(stringOf(R.string.artist_preferences_title))
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.DESCRIPTION)
        .assertTextEquals(stringOf(R.string.artist_preferences_description))
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.POPULAR_ARTISTS_TITLE)
        .assertTextEquals(stringOf(R.string.artist_preferences_popular_artists))
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON)
        .assertTextContains(stringOf(R.string.artist_preferences_save))
  }

  /**
   * Verifies that the search bar cannot be used yet, since searching is not implemented.
   *
   * TODO(#105): replace this test with tests of the search behavior once search is implemented.
   */
  @Test
  fun searchBar_isNotEnabled() {
    setPreferencesContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertIsNotEnabled()
  }

  /** Verifies that every popular artist has a card displaying its name. */
  @Test
  fun artistsGrid_displaysCardForEachPopularArtist() {
    val viewModel = createViewModel()
    setPreferencesContent(viewModel = viewModel)

    viewModel.uiState.value.popularArtists.forEach { artist ->
      val cardTag = ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid)
      // The grid is lazy, so cards below the fold only exist once scrolled into view.
      composeTestRule
          .onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID)
          .performScrollToNode(hasTestTag(cardTag))
      composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed().assertTextContains(artist.name)
    }
  }

  /** Verifies that clicking an artist card toggles its selection. */
  @Test
  fun artistCard_click_togglesSelection() {
    val viewModel = createViewModel()
    setPreferencesContent(viewModel = viewModel)
    val card =
        composeTestRule.onNodeWithTag(
            ArtistPreferencesTestTags.getArtistCardTestTag(
                viewModel.uiState.value.popularArtists.first().uid
            )
        )

    card.assertIsOff()
    card.performClick()
    card.assertIsOn()
    card.performClick()
    card.assertIsOff()
  }

  /**
   * Verifies that clicking the back button invokes the back callback.
   *
   * The test passes its own callback and only checks that it is called: where going back leads is
   * decided by the navigation, so this test stays valid once the callback is wired to it.
   */
  @Test
  fun backButton_click_invokesOnBackClick() {
    // Only the callback below can flip this flag, so it proves the click reached the callback.
    var backClicked = false
    setPreferencesContent(onBackClick = { backClicked = true })

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).performClick()

    // Fails if the button is wired to another callback, such as onSaveClick.
    assertTrue(backClicked)
  }

  /**
   * Verifies that clicking the save button invokes the save callback.
   *
   * The test passes its own callback and only checks that it is called: what saving does is decided
   * by the caller, so this test stays valid once the callback persists the preferences.
   */
  @Test
  fun saveButton_click_invokesOnSaveClick() {
    // Only the callback below can flip this flag, so it proves the click reached the callback.
    var saveClicked = false
    setPreferencesContent(onSaveClick = { saveClicked = true })

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).performClick()

    // Fails if the button is wired to another callback, such as onBackClick.
    assertTrue(saveClicked)
  }

  /** Verifies that the profile tab stays selected, since preferences are part of the profile. */
  @Test
  fun artistPreferencesScreen_selectsProfileTab() {
    setPreferencesContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
  }

  /** Verifies that two artists with the same name get their own cards and toggle independently. */
  @Test
  fun artistsWithSameName_haveIndependentCards() {
    val first = Artist("test-same-1", "Same Name")
    val second = Artist("test-same-2", "Same Name")
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getPopularArtists(): List<Artist> = listOf(first, second)
        }
    setPreferencesContent(viewModel = ArtistPreferencesViewModel(repository))
    val firstCard =
        composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(first.uid))
    val secondCard =
        composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(second.uid))

    firstCard.performClick()

    firstCard.assertIsOn()
    secondCard.assertIsOff()
  }

  private fun setPreferencesContent(
      viewModel: ArtistPreferencesViewModel = createViewModel(),
      onBackClick: () -> Unit = {},
      onSaveClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MaterialTheme {
        ArtistPreferencesScreen(
            artistPreferencesViewModel = viewModel,
            onBackClick = onBackClick,
            onSaveClick = onSaveClick,
            onTabSelected = onTabSelected,
        )
      }
    }
  }

  /**
   * Reads a string resource, so the tests follow the displayed texts instead of duplicating them.
   *
   * @param resId the identifier of the string resource.
   * @return the resolved string.
   */
  private fun stringOf(resId: Int): String = composeTestRule.activity.getString(resId)

  /** Artists served by the fake repository, so the tests do not depend on the mock list. */
  private val testArtists =
      listOf(
          Artist("test-picasso", "Pablo Picasso"),
          Artist("test-van-gogh", "Vincent van Gogh"),
          Artist("test-monet", "Claude Monet"),
      )

  /**
   * Creates a catalog whose popular artists are [testArtists].
   *
   * Every other method is forwarded to an empty [ArtworkRepositoryLocal], since this screen only
   * needs the popular artists.
   *
   * @return the fake repository
   */
  private fun fakeWikiRepository(): WikiDataRepository =
      object : WikiDataRepository by ArtworkRepositoryLocal() {
        override suspend fun getPopularArtists(): List<Artist> = testArtists
      }

  /**
   * Creates the ViewModel under test with fake dependencies.
   *
   * @return a ViewModel backed by [fakeWikiRepository]
   */
  private fun createViewModel(): ArtistPreferencesViewModel =
      ArtistPreferencesViewModel(fakeWikiRepository())
}
