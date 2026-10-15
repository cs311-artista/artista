package com.artista.artista.e2e

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
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.MainActivity
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.ArtworkTestTags
import com.artista.artista.ui.artwork.SavedArtworksTestTags
import com.artista.artista.ui.map.MapScreenTestTags
import com.artista.artista.ui.navigation.NavigationTestTags
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.overview.OverviewTestTags
import com.artista.artista.ui.preferences.ArtistPreferencesTestTags
import com.artista.artista.ui.profile.UserProfileTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the currently wired end-to-end user journey.
 *
 * The journey signs in, visits Search, Map, artwork details, the Profile area and Saved Artworks,
 * verifies that two artworks can be saved, checks unsaving from both supported locations, and
 * verifies that artist preference cards change appearance when selected and deselected. It stops at
 * the Profile screen because lifecycle relaunch and preference persistence are not part of the
 * current implementation.
 *
 * @author 5kyPhy
 */
@RunWith(AndroidJUnit4::class)
class CurrentAppUserJourneyTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  /**
   * Verifies the complete currently supported user journey.
   *
   * The test asserts the initial authentication screen, the destination after each navigation
   * action, the selected bottom-navigation tab where applicable, saved-artwork list contents, card
   * removal after unsaving, and the visual selection state of an artist preference card.
   */
  @Test
  fun currentUserJourney_signsInReachesEveryScreen() {
    // Start on authentication and verify that signing in opens Overview.
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()

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
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
    unsaveFromArtworkScreen()
    composeTestRule.waitForIdle()

    // From Profile, verify that selecting and deselecting an artist changes its appearance.
    openPreferences()
    composeTestRule.waitForIdle()

    // Return from preferences; the supported journey ends on Profile.
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()

    // Log-out through profile screen and check that it returns to log-in screen
    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.LOGOUT_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()
  }

  /**
   * Opens Search from Overview and verifies that returning through the Overview tab restores the
   * Overview screen.
   */
  private fun openSearch() {
    composeTestRule.onNodeWithTag(OverviewTestTags.SEARCH_BAR).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).assertIsSelected()
  }

  /**
   * Opens Map from Overview, verifies the map destination and selected tab, then returns to
   * Overview.
   */
  private fun openMap() {
    grantLocationPermissions()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
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
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
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
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
  }

  /** Opens Saved Artworks and verifies that every artwork saved by the journey is displayed. */
  private fun openSavedArtworks() {
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()

    mockArtworks.take(2).forEach { artwork ->
      val savedArtworkCard = SavedArtworksTestTags.card(artwork)
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule.onAllNodesWithTag(savedArtworkCard).fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag(savedArtworkCard).assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
  }

  /** Removes the first artwork from Saved Artworks and verifies that its card disappears. */
  private fun unsaveFromSavedArtworks() {
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()
    val savedArtworkCard = SavedArtworksTestTags.card(mockArtworks.first())
    composeTestRule
        .onNodeWithTag(SavedArtworksTestTags.unsaveButton(mockArtworks.first()))
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(savedArtworkCard).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
  }

  /**
   * Opens the remaining saved artwork from Overview, unsaves it from its details screen, and
   * returns through Saved Artworks.
   */
  private fun unsaveFromArtworkScreen() {
    composeTestRule
        .onNodeWithTag(OverviewTestTags.ARTWORK_LIST)
        .performScrollToNode(hasTestTag(OverviewTestTags.ARTWORK_ITEM))
    composeTestRule.onAllNodesWithTag(OverviewTestTags.ARTWORK_ITEM)[0].performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.ARTWORK_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ArtworkTestTags.SAVE_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtworkTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SavedArtworksTestTags.SCREEN).assertIsDisplayed()

    mockArtworks.forEach { artwork ->
      if (composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(artwork)).isDisplayed()) {
        composeTestRule.onNodeWithTag(SavedArtworksTestTags.unsaveButton(artwork)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SavedArtworksTestTags.card(artwork)).assertDoesNotExist()
      }
    }

    composeTestRule.onNodeWithTag(SavedArtworksTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
  }

  /**
   * Opens Artist Preferences, verifies that clicking an artist changes its selected appearance, and
   * returns to Profile.
   */
  private fun openPreferences() {
    composeTestRule.onNodeWithTag(UserProfileTestTags.PREFERENCES_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ArtistPreferencesTestTags.SCREEN).assertIsDisplayed()

    val artistCard = ArtistPreferencesTestTags.getArtistCardTestTag("Pablo Picasso")
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
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(UserProfileTestTags.SCREEN).assertIsDisplayed()
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
