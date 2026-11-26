// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
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
import com.artista.artista.model.location.FakeLocationProvider
import com.artista.artista.model.user.UserRepositoryLocal
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.ArtworkTestTags
import com.artista.artista.ui.artwork.ArtworkTileTestTags
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the currently wired end-to-end user journey.
 *
 * The journey signs in, visits Search, Map, artwork details, the Profile area and Saved Artworks,
 * verifies that two artworks can be saved, removes the first one from Saved Artworks, and opens
 * Artist Preferences once its saved artists have loaded. It ends by logging back out to the
 * authentication screen.
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
  private val authViewModel = AuthViewModel(FakeAuthRepository(), UserRepositoryLocal())

  private val credentialProvider = FakeGoogleCredentialProvider()

  @Before
  fun setUp() {
    grantLocationPermissions()
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
          locationProvider = FakeLocationProvider(),
      )
    }
  }

  @After
  fun tearDown() {
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /**
   * Verifies the currently executed user journey.
   *
   * The test asserts the initial authentication screen, the destination after each navigation
   * action, the selected bottom-navigation tab where applicable, the two saved-artwork cards, the
   * removal of the first card from Saved Artworks, and that Artist Preferences finishes loading.
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

    // From Profile, verify that either the grid or empty state appears on ArtistPreferencesScreen
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
      waitUntilDisplayed(ArtworkTileTestTags.tile(artwork))
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
    val savedArtworkCard = ArtworkTileTestTags.tile(mockArtworks.first())
    composeTestRule
        .onNodeWithTag(ArtworkTileTestTags.saveButton(mockArtworks.first()))
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
      if (composeTestRule.onNodeWithTag(ArtworkTileTestTags.tile(artwork)).isDisplayed()) {
        composeTestRule.onNodeWithTag(ArtworkTileTestTags.saveButton(artwork)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ArtworkTileTestTags.tile(artwork)).assertDoesNotExist()
      }
    }

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
  }

  /**
   * Opens Artist Preferences, waits until its saved artists have loaded, and returns to Profile.
   *
   * The screen reads the user from the application's auth and user repositories, not from the fakes
   * of this journey, so it may show either saved artists or the empty state; both mean the load
   * ended. Removing an artist is covered by MilestoneTwoUserJourneyTest, which controls them.
   */
  private fun openPreferences() {
    composeTestRule.onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON).performClick()
    waitUntilDisplayed(ArtistPreferencesTestTags.SCREEN)
    composeTestRule.waitUntil(timeoutMillis) {
      listOf(ArtistPreferencesTestTags.ARTISTS_GRID, ArtistPreferencesTestTags.EMPTY_STATE).any {
        composeTestRule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty()
      }
    }

    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.BACK_BUTTON).performClick()
    waitUntilDisplayed(UserProfileTestTags.SCREEN)
  }

  /**
   * Waits until the node with [tag] is displayed, then asserts it.
   *
   * [waitUntil][androidx.compose.ui.test.junit4.ComposeTestRule.waitUntil] checks again and again
   * whether a node with [tag] exists, letting the app run between checks, until one appears or
   * [timeoutMillis] runs out. A single check right after `waitForIdle()` was too early on CI's
   * slow, freshly booted emulator and failed the whole journey.
   *
   * While the Activity briefly has no Compose content, the check throws `IllegalStateException: No
   * compose hierarchies found`. That case is caught and treated as "not ready yet", so polling
   * continues instead of failing the test at once.
   *
   * Once the node exists, it is asserted to be displayed, so the check is as strict as before.
   *
   * @param tag test tag of the node to wait for
   * @throws androidx.compose.ui.test.ComposeTimeoutException if no node with [tag] appears within
   *   [timeoutMillis]
   * @throws AssertionError if the node exists but is not displayed
   * @author krfpn
   */
  private fun waitUntilDisplayed(tag: String) {
    waitUntil("$tag to be displayed") { composeTestRule.onNodeWithTag(tag).isDisplayed() }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  private fun waitUntil(description: String, condition: () -> Boolean) {
    try {
      composeTestRule.waitUntil(timeoutMillis) {
        try {
          condition()
        } catch (e: IllegalStateException) {
          // Thrown as "No compose hierarchies found" while no root is attached
          false
        }
      }
    } catch (e: ComposeTimeoutException) {
      throw AssertionError("Timed out waiting for: $description", e)
    }
  }

  /** Grants the location permissions. */
  private fun grantLocationPermissions() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
    )
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )
  }
}
