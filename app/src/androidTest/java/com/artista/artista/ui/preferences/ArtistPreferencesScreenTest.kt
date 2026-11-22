package com.artista.artista.ui.preferences

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
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

  /** Artists saved by the signed-in user, so the tests do not depend on the mock list. */
  private val testArtists =
      listOf(
          Artist("test-picasso", "Pablo Picasso"),
          Artist("test-van-gogh", "Vincent van Gogh"),
          Artist("test-monet", "Claude Monet"),
      )

  /** Verifies that the preferences preview renders its screen and artist grid. */
  @Test
  fun artistPreferencesScreenPreview_displaysArtistGrid() {
    composeTestRule.setContent { ArtistPreferencesScreenPreview() }
    waitForTag(ArtistPreferencesTestTags.ARTISTS_GRID)

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertIsDisplayed()
  }

  /** Verifies that every element of the artist preferences screen is displayed. */
  @Test
  fun artistPreferencesScreen_displaysAllElements() {
    setLoadedContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /** Verifies that the title and description display their texts. */
  @Test
  fun artistPreferencesScreen_displaysTexts() {
    setLoadedContent()

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.TITLE)
        .assertTextEquals(stringOf(R.string.artist_preferences_title))
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.DESCRIPTION)
        .assertTextEquals(stringOf(R.string.artist_preferences_description))
  }

  /** Verifies that every saved artist has a card displaying its name, shown as saved. */
  @Test
  fun artistsGrid_displaysCardForEachSavedArtist() {
    setLoadedContent()

    testArtists.forEach { artist ->
      val cardTag = ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid)
      scrollToCard(cardTag)
      composeTestRule
          .onNodeWithTag(cardTag)
          .assertIsDisplayed()
          .assertTextContains(artist.name)
          .assertIsOn()
    }
  }

  /** Verifies that a saved artist the catalog does not know gets a card named after its id. */
  @Test
  fun artistsGrid_unknownArtist_displaysPlaceholderCard() {
    setLoadedContent(savedIds = listOf(UNKNOWN_ARTIST_ID))

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(UNKNOWN_ARTIST_ID))
        .assertIsDisplayed()
        .assertTextContains(UNKNOWN_ARTIST_ID)
  }

  /** Verifies that clicking a card removes it from the screen and from the stored profile. */
  @Test
  fun artistCard_click_removesArtist() {
    val userRepository = setLoadedContent()
    val (picasso, vanGogh, monet) = testArtists

    clickCard(picasso)

    // The write runs in a coroutine that the compose rule does not wait for, hence the polling.
    composeTestRule.waitUntil(TIMEOUT_MS) { userRepository.writeCount == 1 }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid))
        .assertDoesNotExist()
    assertEquals(
        listOf(vanGogh.uid, monet.uid),
        userRepository.storedUser(UID)?.preference?.artists,
    )
    // The user stays on the screen to remove other artists.
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()
  }

  /** Verifies that a card whose removal fails comes back, so the user can try again. */
  @Test
  fun artistCard_removalFails_bringsCardBack() {
    val userRepository = setLoadedContent()
    userRepository.writeFailure = AppException.Network()
    val picasso = testArtists.first()

    clickCard(picasso)

    // The failed write is the last step of the removal, so once it is counted the removal is over.
    composeTestRule.waitUntil(TIMEOUT_MS) { userRepository.writeCount == 1 }
    waitForTag(ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid))
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid))
        .assertIsDisplayed()
    assertEquals(testArtists.map { it.uid }, userRepository.storedUser(UID)?.preference?.artists)
  }

  /** Verifies that the card is hidden as soon as it is clicked, before the write ends. */
  @Test
  fun artistCard_click_hidesCardBeforeWriteEnds() {
    val userRepository = setLoadedContent()
    // The write never ends, so the removal stays running for the rest of the test.
    userRepository.writeDelays[UID] = Long.MAX_VALUE
    val picasso = testArtists.first()

    clickCard(picasso)

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid))
        .assertDoesNotExist()
  }

  /** Verifies that two artists with the same name get their own cards and are removed apart. */
  @Test
  fun artistsWithSameName_haveIndependentCards() {
    val first = Artist("test-same-1", "Same Name")
    val second = Artist("test-same-2", "Same Name")
    setLoadedContent(catalog = listOf(first, second), savedIds = listOf(first.uid, second.uid))

    clickCard(first)

    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(first.uid))
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(second.uid))
        .assertIsDisplayed()
  }

  /** Verifies that removing the last artist replaces the grid with the empty state. */
  @Test
  fun artistCard_removingLastArtist_showsEmptyState() {
    val artist = testArtists.first()
    setLoadedContent(savedIds = listOf(artist.uid))

    clickCard(artist)

    waitForTag(ArtistPreferencesTestTags.EMPTY_STATE)
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.EMPTY_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertDoesNotExist()
  }

  /** Verifies that a user without saved artists sees the empty state and its message. */
  @Test
  fun noSavedArtist_showsEmptyState() {
    setSignedInContent(FakeUserRepository())

    waitForTag(ArtistPreferencesTestTags.EMPTY_STATE)
    composeTestRule
        .onNodeWithText(stringOf(R.string.artist_preferences_empty_state))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.LOAD_ERROR_STATE).assertDoesNotExist()
  }

  /** Verifies that the empty state is shown when nobody is signed in. */
  @Test
  fun signedOut_showsEmptyState() {
    setContent(
        ArtistPreferencesViewModel(
            CatalogWikiDataRepository(testArtists),
            FakeAuthRepository(),
            FakeUserRepository(),
        )
    )

    waitForTag(ArtistPreferencesTestTags.EMPTY_STATE)
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.EMPTY_STATE).assertIsDisplayed()
  }

  /** Verifies that the search bar accepts input and displays the typed text. */
  @Test
  fun searchBar_typing_displaysQuery() {
    setLoadedContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("mon")

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertTextContains("mon")
  }

  /** Verifies that typing a query leaves only the matching saved artists in the grid. */
  @Test
  fun searchBar_typing_showsOnlyMatchingArtists() {
    setLoadedContent()
    val (picasso, vanGogh, monet) = testArtists

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("mon")

    val monetCardTag = ArtistPreferencesTestTags.getArtistCardTestTag(monet.uid)
    scrollToCard(monetCardTag)
    composeTestRule.onNodeWithTag(monetCardTag).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(picasso.uid))
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(vanGogh.uid))
        .assertDoesNotExist()
  }

  /**
   * Verifies that a query matching no artist empties the grid without showing the empty state,
   * since the user still has saved artists.
   */
  @Test
  fun searchBar_noMatch_showsNoArtistButNoEmptyState() {
    setLoadedContent()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("xyz")

    testArtists.forEach { artist ->
      composeTestRule
          .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid))
          .assertDoesNotExist()
    }
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.EMPTY_STATE).assertDoesNotExist()
  }

  /** Verifies that clearing the query brings every saved artist back. */
  @Test
  fun searchBar_clearing_showsAllArtistsAgain() {
    setLoadedContent()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("mon")

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextClearance()

    testArtists.forEach { artist ->
      val cardTag = ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid)
      scrollToCard(cardTag)
      composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed()
    }
  }

  /** Verifies that an artist found through the search can be removed. */
  @Test
  fun searchBar_matchingCardClick_removesArtist() {
    val userRepository = setLoadedContent()
    val monet = testArtists.last()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("monet")

    clickCard(monet)

    composeTestRule.waitUntil(TIMEOUT_MS) { userRepository.writeCount == 1 }
    assertEquals(
        testArtists.dropLast(1).map { it.uid },
        userRepository.storedUser(UID)?.preference?.artists,
    )
  }

  /**
   * Verifies that the spinner replaces the cards while the saved artists load, and that the search
   * bar is disabled.
   */
  @Test
  fun savedArtists_whileLoading_showsSpinnerAndDisablesSearch() {
    // The load never returns, so the screen stays loading for the whole test.
    setSignedInContent(FakeUserRepository().apply { readDelays[UID] = Long.MAX_VALUE })

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.LOADING_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.EMPTY_STATE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertIsNotEnabled()
  }

  /**
   * Verifies that a failed load replaces the grid with the error state, its message and its retry
   * button, and keeps the search bar disabled.
   */
  @Test
  fun savedArtists_loadFails_showsErrorStateAndDisablesSearch() {
    setSignedInContent(FakeUserRepository().apply { readFailure = AppException.Network() })

    waitForTag(ArtistPreferencesTestTags.LOAD_ERROR_STATE)
    composeTestRule
        .onNodeWithText(stringOf(R.string.artist_preferences_saved_artists_load_error))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.RETRY_BUTTON)
        .assertIsDisplayed()
        .assertTextContains(stringOf(R.string.try_again))
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.EMPTY_STATE).assertDoesNotExist()
    // The load is over, so the spinner must not stay next to the error state.
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.LOADING_STATE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertIsNotEnabled()
  }

  /**
   * Verifies that tapping Try again after a failed load shows the saved artists and enables the
   * search bar again.
   */
  @Test
  fun retryButton_afterLoadFails_showsSavedArtists() {
    val userRepository = savedArtistsRepository().apply { readFailure = AppException.Network() }
    setSignedInContent(userRepository)
    waitForTag(ArtistPreferencesTestTags.LOAD_ERROR_STATE)

    userRepository.readFailure = null
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.RETRY_BUTTON).performClick()

    waitForTag(ArtistPreferencesTestTags.ARTISTS_GRID)
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.getArtistCardTestTag(testArtists.first().uid))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.LOAD_ERROR_STATE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SEARCH_BAR).assertIsEnabled()
  }

  /** Verifies that tapping Try again hides the error state and shows the spinner while retrying. */
  @Test
  fun retryButton_click_showsSpinnerWhileRetrying() {
    val userRepository = savedArtistsRepository().apply { readFailure = AppException.Network() }
    setSignedInContent(userRepository)
    waitForTag(ArtistPreferencesTestTags.LOAD_ERROR_STATE)

    // The retry never ends, so the spinner stays for the rest of the test.
    userRepository.readFailure = null
    userRepository.readDelays[UID] = Long.MAX_VALUE
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.RETRY_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.LOADING_STATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.LOAD_ERROR_STATE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.RETRY_BUTTON).assertDoesNotExist()
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
    setLoadedContent(onBackClick = { backClicked = true })

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).performClick()

    assertTrue(backClicked)
  }

  /** Verifies that the profile tab stays selected, since preferences are part of the profile. */
  @Test
  fun artistPreferencesScreen_selectsProfileTab() {
    setLoadedContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
  }

  /**
   * Shows the screen for a user signed in as [UID] who saved [savedIds], and waits until their
   * cards are shown.
   *
   * @param catalog the artists the catalog knows, [testArtists] by default
   * @param savedIds the identifiers saved in the user's profile, those of [testArtists] by default
   * @param onBackClick callback invoked when the back button is clicked
   * @return the user storage, to check what the screen wrote
   */
  private fun setLoadedContent(
      catalog: List<Artist> = testArtists,
      savedIds: List<ArtistId> = testArtists.map { it.uid },
      onBackClick: () -> Unit = {},
  ): FakeUserRepository {
    val userRepository = savedArtistsRepository(savedIds)
    setSignedInContent(userRepository, catalog, onBackClick)
    waitForTag(ArtistPreferencesTestTags.ARTISTS_GRID)
    return userRepository
  }

  /**
   * Shows the screen for a user signed in as [UID], without waiting for the load.
   *
   * @param userRepository the storage the saved artists are read from and removed in
   * @param catalog the artists the catalog knows
   * @param onBackClick callback invoked when the back button is clicked
   */
  private fun setSignedInContent(
      userRepository: FakeUserRepository,
      catalog: List<Artist> = testArtists,
      onBackClick: () -> Unit = {},
  ) {
    setContent(
        ArtistPreferencesViewModel(
            CatalogWikiDataRepository(catalog),
            FakeAuthRepository(initialUser = FakeAuthUser(UID)),
            userRepository,
        ),
        onBackClick,
    )
  }

  /**
   * Shows the screen backed by the given ViewModel.
   *
   * @param viewModel the ViewModel under test
   * @param onBackClick callback invoked when the back button is clicked
   */
  private fun setContent(viewModel: ArtistPreferencesViewModel, onBackClick: () -> Unit = {}) {
    composeTestRule.setContent {
      MaterialTheme {
        ArtistPreferencesScreen(
            artistPreferencesViewModel = viewModel,
            onBackClick = onBackClick,
            onTabSelected = {},
        )
      }
    }
  }

  /**
   * Creates a user storage holding the profile of [UID] with the given saved artists.
   *
   * @param savedIds the identifiers saved in the profile
   * @return the storage
   */
  private fun savedArtistsRepository(
      savedIds: List<ArtistId> = testArtists.map { it.uid }
  ): FakeUserRepository =
      FakeUserRepository().apply {
        store(User(uid = UID, preference = UserPreference(artists = savedIds)))
      }

  /**
   * Reads a string resource, so the tests follow the displayed texts instead of duplicating them.
   *
   * @param resId the identifier of the string resource.
   * @return the resolved string.
   */
  private fun stringOf(resId: Int): String = composeTestRule.activity.getString(resId)

  /**
   * Waits until a node with the given tag is in the tree, since the loads and writes run in
   * coroutines the compose rule does not wait for.
   *
   * @param tag the test tag to wait for
   */
  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /**
   * Scrolls the grid to a card, since the grid is lazy and cards below the fold only exist once
   * scrolled into view.
   *
   * @param cardTag the tag of the card to bring into view
   */
  private fun scrollToCard(cardTag: String) {
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID)
        .performScrollToNode(hasTestTag(cardTag))
  }

  /**
   * Clicks the card of an artist, to remove them.
   *
   * @param artist the artist whose card is clicked
   */
  private fun clickCard(artist: Artist) {
    val cardTag = ArtistPreferencesTestTags.getArtistCardTestTag(artist.uid)
    scrollToCard(cardTag)
    composeTestRule.onNodeWithTag(cardTag).performClick()
  }

  /**
   * Catalog that knows only [artists]; every other method is forwarded to an empty
   * [ArtworkRepositoryLocal], since this screen only looks artists up by identifier.
   */
  private class CatalogWikiDataRepository(private val artists: List<Artist>) :
      WikiDataRepository by ArtworkRepositoryLocal() {
    override suspend fun getArtist(qid: ArtistId): Artist? = artists.find { it.uid == qid }
  }

  private companion object {
    /** Identifier of the signed-in user. */
    const val UID = "uid-1"

    /** Identifier saved in the profile but unknown to the catalog. */
    const val UNKNOWN_ARTIST_ID = "unknown-artist"

    /** Longest wait, in milliseconds, for a load or a write to finish before a test fails. */
    const val TIMEOUT_MS = 5_000L
  }
}
