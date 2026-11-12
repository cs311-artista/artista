// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.ArtistaApp
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.ArtworkTestTags
import com.artista.artista.ui.artwork.SavedArtworksTestTags
import com.artista.artista.ui.authentication.AuthViewModel
import com.artista.artista.ui.authentication.FakeGoogleCredentialProvider
import com.artista.artista.ui.map.MapScreenTestTags
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.overview.OverviewTestTags
import com.artista.artista.ui.preferences.ArtistPreferencesTestTags
import com.artista.artista.ui.profile.UserProfileTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the currently wired end-to-end user journey.
 *
 * The journey signs in, visits Search, Map, artwork details, the Profile area and Saved Artworks,
 * verifies that two artworks can be saved, removes the first one from Saved Artworks, and verifies
 * that an artist preference card changes appearance when selected and deselected. It ends by
 * logging back out to the authentication screen.
 *
 * @author 5kyPhy
 */
@RunWith(AndroidJUnit4::class)
class CurrentAppUserJourneyTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private val timeoutMillis = 10_000L

  // The journey signs in and out for real, so it goes through the real AuthViewModel; only the two
  // seams behind it are faked, since no test can drive Credential Manager's account chooser and
  // Firebase would need a network. Both are built outside the composition, as the application root
  // only needs the state the ViewModel exposes.
  private val authViewModel = AuthViewModel(FakeAuthRepository())

  private val credentialProvider = FakeGoogleCredentialProvider()

  @Before
  fun setUp() {
    val mockRepository = ArtworkRepositoryProvider.mockRepository
    mockRepository.clear()
    ArtworkRepositoryProvider.populateAPI()
    runBlocking { ArtworkRepositoryProvider.populateSaved(mockArtworks.drop(2).take(3)) }

    composeTestRule.setContent {
      ArtistaApp(
          authViewModel = authViewModel,
          credentialProvider = credentialProvider,
          wikiDataRepository = mockRepository,
          savedArtworksRepository = mockRepository,
      )
    }
  }

  /**
   * Verifies the currently executed user journey.
   *
   * The test asserts the initial authentication screen, the destination after each navigation
   * action, the selected bottom-navigation tab where applicable, the two saved-artwork cards, the
   * removal of the first card from Saved Artworks, and the visual selection state of an artist
   * preference card.
   */
  @Test
  fun currentUserJourney_signsInReachesEveryScreen() {
    // Start on authentication and verify that signing in opens Overview. Signing in is asynchronous
    // now that it goes through the repository, so the assertion waits for the gate to swap screens.
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    waitUntilDisplayed(OverviewTestTags.SCREEN)

    // Visit the screens reachable from Overview and verify each return path.
    openSearch()
    composeTestRule.waitForIdle()
    openMap()
    composeTestRule.waitForIdle()
    openArtworkDetails()
    composeTestRule.waitForIdle()

    // Save two artworks from their details screens and verify both appear in Saved Artworks.
    saveArtworkAt(0)
    composeTestRule.waitForIdle()
    saveArtworkAt(1)
    composeTestRule.waitForIdle()
    openSavedArtworks()
    composeTestRule.waitForIdle()
    unsaveFromSavedArtworks()
    composeTestRule.waitForIdle()

    // From Profile, verify that selecting and deselecting an artist changes its appearance.
    openPreferences()
    composeTestRule.waitForIdle()

    // Return from preferences; the supported journey ends on Profile.
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()

    // Log-out through profile screen and check that it returns to log-in screen. The logout signs
    // out through the repository, so the gate, not a navigation action, brings the sign-in screen
    // back.
    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).performClick()
    waitUntilDisplayed(C.Tag.auth_screen_container)
  }

  /**
   * Opens Search from Overview and verifies that returning through the Overview tab restores the
   * Overview screen.
   */
  private fun openSearch() {
    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()
    waitUntilDisplayed("search_input")
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
    waitUntilDisplayed(OverviewTestTags.SCREEN)
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).assertIsSelected()
  }

  /**
   * Opens Map from Overview, verifies the map destination and selected tab, then returns to
   * Overview.
   */
  private fun openMap() {
    grantLocationPermissions()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).performClick()
    waitUntilDisplayed(MapScreenTestTags.MAP)
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
    waitUntilDisplayed(OverviewTestTags.SCREEN)
  }

  /**
   * Opens the first artwork from Overview, verifies the details destination, and returns to
   * Overview.
   */
  private fun openArtworkDetails() {
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(OverviewTestTags.ARTWORK_ITEM))
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM)[0].performClick()
    waitUntilDisplayed(ArtworkTestTags.ARTWORK_SCREEN)
    composeTestRule.onNodeWithTag(ArtworkTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(OverviewTestTags.SCREEN)
  }

  /**
   * Opens the artwork at [index] from Overview, saves it and returns to Overview.
   *
   * @param index position of the artwork in the Overview list
   */
  private fun saveArtworkAt(index: Int) {
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(OverviewTestTags.ARTWORK_ITEM))
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM)[index].performClick()
    waitUntilDisplayed(ArtworkTestTags.ARTWORK_SCREEN)
    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(OverviewTestTags.SCREEN)
  }

  /** Opens Saved Artworks and verifies that every artwork saved by the journey is displayed. */
  private fun openSavedArtworks() {
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    waitUntilDisplayed(SavedArtworksTestTags.SCREEN)

    mockArtworks.take(2).forEach { artwork ->
      waitUntilDisplayed(SavedArtworksTestTags.card(artwork))
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
  }

  /** Removes the first artwork from Saved Artworks and verifies that its card disappears. */
  private fun unsaveFromSavedArtworks() {
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    waitUntilDisplayed(SavedArtworksTestTags.SCREEN)
    val savedArtworkCard = SavedArtworksTestTags.card(mockArtworks.first())
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.unsaveButton(mockArtworks.first()))
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
      composeTestRule.onAllNodesWithTag(savedArtworkCard).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
  }

  /**
   * Provides an alternative cleanup path that unsaves artworks from their details screen.
   *
   * This helper is currently not called by [currentUserJourney_signsInReachesEveryScreen].
   */
  private fun unsaveFromArtworkScreen() {
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(OverviewTestTags.ARTWORK_ITEM))
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM)[0].performClick()
    waitUntilDisplayed(ArtworkTestTags.ARTWORK_SCREEN)
    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    waitUntilDisplayed(SavedArtworksTestTags.SCREEN)

    mockArtworks.forEach { artwork ->
      if (composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(artwork)).isDisplayed()) {
        composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(artwork)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(artwork)).assertDoesNotExist()
      }
    }

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
  }

  /**
   * Opens Artist Preferences, verifies that clicking an artist changes its selected appearance, and
   * returns to Profile.
   */
  private fun openPreferences() {
    composeTestRule.onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON).performClick()
    waitUntilDisplayed(ArtistPreferencesTestTags.SCREEN)

    val artistCard = ArtistPreferencesTestTags.getArtistCardTestTag("mock-artist-001")
    composeTestRule
        .onNodeWithTag(ArtistPreferencesTestTags.ARTISTS_GRID)
        .performScrollToNode(hasTestTag(artistCard))
    composeTestRule.onNodeWithTag(artistCard).assertIsOff().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(artistCard).assertIsOn()
    composeTestRule.onNodeWithTag(artistCard).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(artistCard).assertIsOff()

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
  }

  /**
   * Waits until the node with [tag] is displayed, then asserts it.
   *
   * CI's emulator is still settling right after boot and can briefly have no Compose root, so that
   * state is polled like a missing node instead of failing the journey at once.
   *
   * @param tag test tag of the node to wait for
   * @author krfpn
   */
  private fun waitUntilDisplayed(tag: String) {
    composeTestRule.waitUntil(timeoutMillis) {
      try {
        composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      } catch (e: IllegalStateException) {
        // Thrown as "No compose hierarchies found" while no root is attached
        false
      }
    }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  /**
   * Grants the location permissions required for Map to render without a runtime permission dialog
   * interrupting the navigation journey.
   */
  private fun grantLocationPermissions() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    try {
      instrumentation.uiAutomation.grantRuntimePermission(
          instrumentation.targetContext.packageName,
          android.Manifest.permission.ACCESS_FINE_LOCATION,
      )
      instrumentation.uiAutomation.grantRuntimePermission(
          instrumentation.targetContext.packageName,
          android.Manifest.permission.ACCESS_COARSE_LOCATION,
      )
    } finally {
      instrumentation.uiAutomation.dropShellPermissionIdentity()
    }
  }
}
