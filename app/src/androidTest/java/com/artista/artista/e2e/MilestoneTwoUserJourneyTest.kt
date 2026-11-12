// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.e2e

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.ArtistaApp
import com.artista.artista.model.artist.failingWikiDataRepository
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.authentication.AuthRepositoryProvider
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.user.UserRepositoryProvider
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.ArtworkCardTestTag
import com.artista.artista.ui.artwork.ArtworkFilterTestTags
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
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the Milestone 2 journey without repeating the saved-artwork journey.
 *
 * @author patrickmcdan
 */
@RunWith(AndroidJUnit4::class)
class MilestoneTwoUserJourneyTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val timeoutMillis = 15_000L
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var userId: String

  private val credentialProvider = FakeGoogleCredentialProvider()
  /** Title of the artwork saved before going offline, so it can be looked up afterwards. */
  private var savedArtworkTitle: String = ""
  private val walkingRouteRepository =
      object : WalkingRouteRepository {
        override suspend fun getWalkingRoute(
            origin: Coordinate,
            destination: Coordinate,
        ): WalkingRoute =
            WalkingRoute(
                path = listOf(origin, destination),
                duration = 8.minutes,
                distanceMeters = 600,
            )
      }

  @Before
  fun setUp() {
    userId = "m2-e2e-${System.nanoTime()}"
    authRepository = FakeAuthRepository().apply { user = FakeAuthUser(userId) }
    AuthRepositoryProvider.repository = authRepository
    UserRepositoryProvider.resetFakeRepository()

    val mockRepository = ArtworkRepositoryProvider.mockRepository
    mockRepository.clear()
    ArtworkRepositoryProvider.populateAPI()

    grantLocationPermissions()
    composeTestRule.setContent { AppContent(offline = false) }
  }

  /**
   * Builds the app with mock repositories, as online or offline.
   *
   * Offline only swaps the WikiData repository for a failing one, so the Overview shows its offline
   * state while saved artworks are still served by the mock, standing in for a local cache.
   *
   * @param offline true to make the WikiData repository fail
   * @author patrickmcdan
   */
  @SuppressLint("ViewModelConstructorInComposable")
  @Composable
  private fun AppContent(offline: Boolean) {
    val mockRepository = ArtworkRepositoryProvider.mockRepository
    ArtistaApp(
        authViewModel = AuthViewModel(authRepository),
        credentialProvider = credentialProvider,
        wikiDataRepository = if (offline) failingWikiDataRepository() else mockRepository,
        savedArtworksRepository = mockRepository,
        walkingRouteRepository = walkingRouteRepository,
    )
  }

  @After
  fun tearDown() {
    AuthRepositoryProvider.resetToApplicationRepository()
    UserRepositoryProvider.resetToApplicationRepository()
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /** Verifies the complete Milestone 2 user journey. */
  @Test
  fun milestoneTwoJourney_persistsProfileAndArtistPreference() {
    signIn()
    applyOverviewFilter()
    saveAnArtwork()
    openArtworkAndVerifyDetails()
    startWalkingRoute()
    editUsername()
    saveArtistPreference()
    verifyArtistPreferenceAfterReopening()
    signOutAndSignBackIn()
    verifyPersistedProfileAndArtistPreference()
    // "Offline" phase
    reopenActivityOffline()
    verifySavedArtworkAvailableWithFailingWikiData()
  }

  private fun saveAnArtwork() {
    scrollOverviewToArtwork()
    savedArtworkTitle =
        composeTestRule
            .onNodeWithTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .first()
            .text
    onDisplayed(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON).performClick()
  }

  /** Recreates the activity with failing network-backed repositories. */
  private fun reopenActivityOffline() {
    val scenario = composeTestRule.activityRule.scenario
    scenario.recreate()
    scenario.onActivity { activity -> activity.setContent { AppContent(offline = true) } }
    composeTestRule.waitForIdle()
  }

  /** Signs in through the same fake Google credential seam as the existing end-to-end test. */
  private fun signIn() {
    onDisplayed(C.Tag.auth_screen_container)
    onDisplayed(C.Tag.auth_google_sign_in_button).performClick()
    waitForDisplayed(OverviewTestTags.SCREEN)
  }

  /** Applies a real Overview filter and verifies that the list is reduced to one artwork. */
  private fun applyOverviewFilter() {
    onDisplayed(OverviewTestTags.FILTER_BUTTON).performClick()
    onDisplayed(ArtworkFilterTestTags.DIALOG)

    onDisplayed("${ArtworkFilterTestTags.ARTIST}-input").performTextInput("Auguste Rodin")
    waitForDisplayed("${ArtworkFilterTestTags.ARTIST}-option-Auguste Rodin")
    onDisplayed("${ArtworkFilterTestTags.ARTIST}-option-Auguste Rodin").performClick()
    onDisplayed(ArtworkFilterTestTags.APPLY_BUTTON).performClick()

    waitUntil {
      composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).fetchSemanticsNodes().size ==
          1
    }
    onDisplayed(OverviewTestTags.FILTER_ACTIVE, useUnmergedTree = true)
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM).assertCountEquals(1)
  }

  /** Opens the filtered artwork and verifies its map tile and image state. */
  private fun openArtworkAndVerifyDetails() {
    scrollOverviewToArtwork()
    onDisplayed(OverviewTestTags.ARTWORK_ITEM).performClick()
    waitForDisplayed(ArtworkTestTags.ARTWORK_SCREEN)
    waitForDisplayed(ArtworkTestTags.ARTWORK_MAP_TILE)
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(ArtworkTestTags.ARTWORK_IMAGE)
          .fetchSemanticsNodes()
          .isNotEmpty() ||
          composeTestRule
              .onAllNodesWithTag(ArtworkTestTags.ARTWORK_IMAGE_FALLBACK)
              .fetchSemanticsNodes()
              .isNotEmpty()
    }
    if (
        composeTestRule
            .onAllNodesWithTag(ArtworkTestTags.ARTWORK_IMAGE)
            .fetchSemanticsNodes()
            .isNotEmpty()
    ) {
      onDisplayed(ArtworkTestTags.ARTWORK_IMAGE)
    } else {
      onDisplayed(ArtworkTestTags.ARTWORK_IMAGE_FALLBACK)
    }
    onDisplayed(ArtworkTestTags.BACK_BUTTON).performClick()
    waitForDisplayed(OverviewTestTags.SCREEN)
  }

  /** Starts a route from the filtered artwork card and verifies the started route state. */
  private fun startWalkingRoute() {
    scrollOverviewToArtwork()
    onDisplayed(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON).performClick()
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(MapScreenTestTags.ROUTE_SUMMARY_CARD)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    onDisplayed(MapScreenTestTags.MAP)
    onDisplayed(MapScreenTestTags.ROUTE_SUMMARY_CARD)
    onDisplayed(MapScreenTestTags.ROUTE_SUMMARY)
  }

  /** Edits the username in Profile and verifies the saved value is rendered. */
  private fun editUsername() {
    onDisplayed(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    waitForDisplayed(UserProfileTestTags.SCREEN)
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.USERNAME)
        .performScrollTo()
        .assertExists()
        .assertIsDisplayed()
        .performClick()
    onDisplayed(UserProfileTestTags.USERNAME_FIELD).performTextInput("MilestoneTwo")
    onDisplayed(UserProfileTestTags.USERNAME_SAVE).performClick()
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(UserProfileTestTags.USERNAME)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    onDisplayed(UserProfileTestTags.USERNAME).assertTextEquals("MilestoneTwo")
    // The UI shows the new username before the Firestore write completes. Wait until the backend
    // really holds it, otherwise signing out right after could race with (or cancel) the save and
    // make the persistence check flaky.
    waitUntil {
      runCatching {
            runBlocking {
              UserRepositoryProvider.repository.getUser(userId).userName == "MilestoneTwo"
            }
          }
          .getOrDefault(false)
    }
  }

  /** Searches for Monet, selects it, and saves the preference. */
  private fun saveArtistPreference() {
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .performScrollTo()
        .assertExists()
        .assertIsDisplayed()
        .performClick()
    waitForDisplayed(ArtistPreferencesTestTags.SCREEN)
    onDisplayed(ArtistPreferencesTestTags.SEARCH_BAR).performTextInput("Monet")

    val monetTag = ArtistPreferencesTestTags.getArtistCardTestTag("mock-artist-004")
    onDisplayed(ArtistPreferencesTestTags.ARTISTS_GRID).performScrollToNode(hasTestTag(monetTag))
    onDisplayed(monetTag).performClick()
    onDisplayed(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsEnabled().performClick()
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(ArtistPreferencesTestTags.SAVE_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    onDisplayed(ArtistPreferencesTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  /** Reopens preferences and verifies that the saved artist is selected. */
  private fun verifyArtistPreferenceAfterReopening() {
    onDisplayed(ArtistPreferencesTestTags.BACK_BUTTON).performClick()
    waitForDisplayed(UserProfileTestTags.SCREEN)
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .performScrollTo()
        .assertExists()
        .assertIsDisplayed()
        .performClick()
    waitForDisplayed(ArtistPreferencesTestTags.SCREEN)

    val monetTag = ArtistPreferencesTestTags.getArtistCardTestTag("mock-artist-004")
    waitForDisplayed(monetTag)
    onDisplayed(monetTag).assertIsOn()
  }

  /** Signs out and signs back in through the same authentication state. */
  private fun signOutAndSignBackIn() {
    onDisplayed(ArtistPreferencesTestTags.BACK_BUTTON).performClick()
    waitForDisplayed(UserProfileTestTags.SCREEN)
    onDisplayed(UserProfileTestTags.LOGOUT_BUTTON).performClick()
    waitForDisplayed(C.Tag.auth_screen_container)
    onDisplayed(C.Tag.auth_google_sign_in_button).performClick()
    waitForDisplayed(OverviewTestTags.SCREEN)
  }

  /** Verifies that both profile values survived the authentication cycle. */
  private fun verifyPersistedProfileAndArtistPreference() {
    onDisplayed(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    waitForDisplayed(UserProfileTestTags.SCREEN)
    onDisplayed(UserProfileTestTags.USERNAME).assertTextEquals("MilestoneTwo")
    composeTestRule
        .onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON)
        .performScrollTo()
        .assertExists()
        .assertIsDisplayed()
        .performClick()
    waitForDisplayed(ArtistPreferencesTestTags.SCREEN)

    val monetTag = ArtistPreferencesTestTags.getArtistCardTestTag("mock-artist-004")
    waitForDisplayed(monetTag)
    onDisplayed(monetTag).assertIsOn()
  }

  /** Verifies the artwork saved online is still listed in Saved Artworks without a network. */
  private fun verifySavedArtworkAvailableWithFailingWikiData() {
    onDisplayed(OverviewTestTags.SAVED_ARTWORKS_BUTTON).performClick()
    waitForDisplayed(SavedArtworksTestTags.SCREEN)
    waitUntil {
      composeTestRule
          .onAllNodesWithText(savedArtworkTitle, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  /** Scrolls the Overview list until its single filtered artwork is displayed. */
  private fun scrollOverviewToArtwork() {
    onDisplayed(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(OverviewTestTags.ARTWORK_ITEM))
    onDisplayed(OverviewTestTags.ARTWORK_ITEM)
  }

  /** Returns a displayed node after checking that it exists. */
  private fun onDisplayed(tag: String, useUnmergedTree: Boolean = false) =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree).assertExists().assertIsDisplayed()

  /** Waits for an asynchronous UI state to expose the requested tag. */
  private fun waitForDisplayed(tag: String) {
    waitUntil { composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    onDisplayed(tag)
  }

  /** Waits for an asynchronous condition without sleeping the test thread. */
  private fun waitUntil(condition: () -> Boolean) {
    composeTestRule.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
  }

  /** Grants location permissions before the map destination is composed. */
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
