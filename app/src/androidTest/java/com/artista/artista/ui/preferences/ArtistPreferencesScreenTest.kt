package com.artista.artista.ui.preferences

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.UserRepositoryLocal
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies the user-visible behavior of the artist preferences screen.
 *
 * @author Timz3rr, patrickmcdan, krfpn
 */
@RunWith(AndroidJUnit4::class)
class ArtistPreferencesScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /** Verifies that the preferences preview renders its screen and artist grid. */
  @Test
  fun artistPreferencesScreenPreview_displaysArtistGrid() {
    composeTestRule.setContent { ArtistPreferencesScreenPreview() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertIsDisplayed()
  }

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

  /** Verifies that the search bar accepts input and displays the typed text. */
  @Test
  fun searchBar_typing_displaysQuery() {
    setPreferencesContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("mon")

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertTextContains("mon")
  }

  /** Verifies that typing a query leaves only the matching artists in the grid. */
  @Test
  fun searchBar_typing_showsOnlyMatchingArtists() {
    setPreferencesContent()
    val (picasso, vanGogh, monet) = testArtists

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("mon")

    val monetCardTag = ArtistPreferencesTestTags.getArtistCardTestTag(monet.uid)
    // The grid is lazy, so cards below the fold only exist once scrolled into view.
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID)
        .performScrollToNode(hasTestTag(monetCardTag))
    composeTestRule.onNodeWithTag(monetCardTag).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid))
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(vanGogh.uid))
        .assertDoesNotExist()
  }

  /** Verifies that a query matching no artist empties the grid. */
  @Test
  fun searchBar_noMatch_showsNoArtist() {
    setPreferencesContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("xyz")

    testArtists.forEach { artist ->
      composeTestRule
          .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid))
          .assertDoesNotExist()
    }
  }

  /** Verifies that clearing the query brings every artist back. */
  @Test
  fun searchBar_clearing_showsAllArtistsAgain() {
    setPreferencesContent()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("mon")

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextClearance()

    testArtists.forEach { artist ->
      val cardTag = ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid)
      // The grid is lazy, so cards below the fold only exist once scrolled into view.
      composeTestRule
          .onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID)
          .performScrollToNode(hasTestTag(cardTag))
      composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed()
    }
  }

  /** Verifies that an artist hidden by the search is still selected when it comes back. */
  @Test
  fun searchBar_hidingSelectedArtist_keepsSelection() {
    setPreferencesContent()
    val (picasso, _, monet) = testArtists
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(monet.uid))
        .performClick()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("pablo")
    val picassoCardTag = ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid)
    // The grid is lazy, so cards below the fold only exist once scrolled into view.
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID)
        .performScrollToNode(hasTestTag(picassoCardTag))
    composeTestRule.onNodeWithTag(picassoCardTag).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(monet.uid))
        .assertDoesNotExist()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextClearance()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(monet.uid))
        .assertIsOn()
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

    // Fails if the button is wired to another action, such as saving.
    assertTrue(backClicked)
  }

  /**
   * Verifies that saving when nobody is signed in keeps the selection in memory, so the button is
   * disabled until the next change.
   */
  @Test
  fun saveButton_signedOut_click_disablesButton() {
    setPreferencesContent()

    clickArtistCard(testArtists.first())
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  /**
   * Verifies that a signed-in user's selection is saved, and that the user stays on the screen with
   * the button disabled until the next change.
   */
  @Test
  fun saveButton_signedIn_saveSucceeds_storesSelectionAndDisablesButton() {
    val userRepository = FakeUserRepository()
    setPreferencesContent(viewModel = signedInViewModel(userRepository))

    clickArtistCard(testArtists.first())
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).performClick()

    // The save runs in a coroutine that the compose rule does not wait for, hence the polling.
    composeTestRule.waitUntil(TIMEOUT_MS) { userRepository.writeCount == 1 }
    composeTestRule.waitForIdle()
    assertEquals(
        listOf(testArtists.first().uid),
        userRepository.storedUser(UID)?.preference?.artists,
    )
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  /** Verifies that a failed save keeps the user on the screen with the button enabled. */
  @Test
  fun saveButton_signedIn_saveFails_keepsButtonEnabled() {
    val userRepository = FakeUserRepository().apply { writeFailure = AppException.Network() }
    setPreferencesContent(viewModel = signedInViewModel(userRepository))

    clickArtistCard(testArtists.first())
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).performClick()

    // The failed write is the last step of the save, so once it is counted the save is over.
    composeTestRule.waitUntil(TIMEOUT_MS) { userRepository.writeCount == 1 }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
    // Enabled again, so the user can retry.
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsEnabled()
  }

  /** Verifies that the save button cannot be clicked again while a save is running. */
  @Test
  fun saveButton_whileSaving_isDisabled() {
    val userRepository = FakeUserRepository()
    setPreferencesContent(viewModel = signedInViewModel(userRepository))
    // Set only once the stored artists are loaded, since the cards cannot be clicked while they
    // load. From now on, the read before the write never returns, so the save stays running.
    composeTestRule.waitForIdle()
    userRepository.readDelays[UID] = Long.MAX_VALUE
    // Without a change the button is already disabled, which would make this test pass for nothing.
    clickArtistCard(testArtists.first())
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsEnabled()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  /**
   * Verifies that the artist cards cannot be clicked while the stored artists load, so a tap is not
   * replaced by the loaded selection.
   */
  @Test
  fun artistCards_whileLoading_areDisabled() {
    // The load never returns, so the screen stays loading for the whole test.
    val userRepository = FakeUserRepository().apply { readDelays[UID] = Long.MAX_VALUE }
    setPreferencesContent(viewModel = signedInViewModel(userRepository))

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(testArtists.first().uid))
        .assertIsNotEnabled()
  }

  /** Verifies that the save button is disabled when the screen opens, as nothing has changed. */
  @Test
  fun saveButton_initially_isDisabled() {
    setPreferencesContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  /** Verifies that selecting then deselecting an artist disables the save button again. */
  @Test
  fun saveButton_afterUndoingToggle_isDisabled() {
    setPreferencesContent()

    clickArtistCard(testArtists.first())
    // Enabled in between, so the final check proves the undo disabled it.
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsEnabled()
    clickArtistCard(testArtists.first())

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsNotEnabled()
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
    setPreferencesContent(
        viewModel =
            ArtistPreferencesViewModel(repository, FakeAuthRepository(), UserRepositoryLocal())
    )
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
      onTabSelected: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MaterialTheme {
        ArtistPreferencesScreen(
            artistPreferencesViewModel = viewModel,
            onBackClick = onBackClick,
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
      ArtistPreferencesViewModel(fakeWikiRepository(), FakeAuthRepository(), UserRepositoryLocal())

  /**
   * Creates the ViewModel under test for a user signed in as [UID].
   *
   * @param userRepository the storage the selection is saved to
   * @return a ViewModel backed by [fakeWikiRepository] and [userRepository]
   */
  private fun signedInViewModel(userRepository: FakeUserRepository): ArtistPreferencesViewModel =
      ArtistPreferencesViewModel(
          fakeWikiRepository(),
          FakeAuthRepository(initialUser = FakeAuthUser(UID)),
          userRepository,
      )

  /**
   * Clicks the card of an artist, to select or deselect them.
   *
   * @param artist the artist whose card is clicked
   */
  private fun clickArtistCard(artist: Artist) {
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid))
        .performClick()
  }

  private companion object {
    /** Identifier of the signed-in user in the save tests. */
    const val UID = "uid-1"

    /** Longest wait, in milliseconds, for a save to finish before a test fails. */
    const val TIMEOUT_MS = 5_000L
  }
}
