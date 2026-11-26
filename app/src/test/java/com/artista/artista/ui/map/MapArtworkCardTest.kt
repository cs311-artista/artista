// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

package com.artista.artista.ui.map

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.ui.artwork.IMAGE_URL
import com.artista.artista.ui.artwork.installFakeImageLoader
import com.artista.artista.ui.theme.ArtistaTheme
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) Compose UI tests for the selected-artwork card rendered by [RouteOverlay].
 *
 * Elements are located by test tag, never by position. The card is exercised directly so the tests
 * do not need a Google Map or an emulator.
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
class MapArtworkCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  // The image loader is a process-wide singleton, so each test that replaces it must restore it.
  @After
  fun resetImageLoader() {
    SingletonImageLoader.reset()
  }

  /** Verifies that selecting an artwork shows its name, the no-image placeholder, Send and X. */
  @Test
  fun artworkCard_whenArtworkSelected_thenShowsNamePlaceholderSendAndDismiss() {
    setOverlay(selectedArtwork = artwork(name = "Mona Lisa"))

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_NAME).assertTextEquals("Mona Lisa")
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_PLACEHOLDER)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_SEND).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_DISMISS).assertIsDisplayed()
  }

  /**
   * Verifies that an artwork without an image URL shows the compact placeholder, not an empty box.
   */
  @Test
  fun artworkCard_whenArtworkHasNoImage_thenShowsPlaceholder() {
    val noImage =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.map_artwork_no_image)

    setOverlay(selectedArtwork = artwork(name = "Mona Lisa", imageUrl = null))

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_PLACEHOLDER)
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(noImage).assertIsDisplayed()
  }

  /** Verifies that the Send action is wired to the navigate callback. */
  @Test
  fun artworkCard_whenSendClicked_thenInvokesNavigate() {
    var navigated = false
    setOverlay(selectedArtwork = artwork(), onNavigate = { navigated = true })

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_SEND).performClick()
    composeTestRule.waitForIdle()

    assertTrue(navigated)
  }

  /** Verifies that the X action is wired to the clear callback. */
  @Test
  fun artworkCard_whenDismissClicked_thenInvokesClear() {
    var cleared = false
    setOverlay(selectedArtwork = artwork(), onClear = { cleared = true })

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_DISMISS).performClick()
    composeTestRule.waitForIdle()

    assertTrue(cleared)
  }

  /** Verifies that the loading card keeps the name and shows the loading line. */
  @Test
  fun artworkCard_whenLoading_thenShowsNameAndLoadingLine() {
    setOverlay(
        routeUiState = RouteUiState.Loading,
        selectedArtwork = artwork(name = "Mona Lisa"),
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_NAME).assertTextEquals("Mona Lisa")
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText("Finding a walking route...").assertIsDisplayed()
  }

  /** Verifies that the image and Send action disappear as soon as routing starts. */
  @Test
  fun artworkCard_whenLoading_thenHidesImageAndSend() {
    setOverlay(
        routeUiState = RouteUiState.Loading,
        selectedArtwork = artwork(name = "Mona Lisa"),
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_FALLBACK)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_PLACEHOLDER)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_SEND).assertDoesNotExist()
  }

  /** Verifies that the success card shows the name and summary and hides the image and Send. */
  @Test
  fun artworkCard_whenSuccess_thenShowsNameAndSummaryWithoutImageOrSend() {
    val destination = artwork(name = "Artwork destination")
    val route = WalkingRoute(listOf(Coordinate(46.52, 6.63)), 12.minutes, 850)

    setOverlay(
        routeUiState = RouteUiState.Success(route, destination),
        selectedArtwork = destination,
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_NAME)
        .assertTextEquals("Artwork destination")
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY)
        .assertTextEquals("12 min · 850 m")
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_FALLBACK)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_PLACEHOLDER)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_SEND).assertDoesNotExist()
  }

  /** Verifies that the success card uses the remaining values once progress exists. */
  @Test
  fun artworkCard_whenProgressExists_thenShowsRemainingSummary() {
    val destination = artwork(name = "Artwork destination")
    val route = WalkingRoute(listOf(Coordinate(46.52, 6.63)), 10.minutes, 1000)

    setOverlay(
        routeUiState = RouteUiState.Success(route, destination),
        routeProgressUiState =
            RouteProgressUiState(
                path = listOf(Coordinate(46.52, 6.63)),
                remainingDistanceMeters = 500.0,
                remainingDurationSeconds = 300.0,
            ),
        selectedArtwork = destination,
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY).assertTextEquals("5 min · 500 m")
  }

  /** Verifies that the existing message card is still used for a failed route. */
  @Test
  fun artworkCard_whenNoRoute_thenShowsExistingMessageCard() {
    setOverlay(routeUiState = RouteUiState.NoRoute, selectedArtwork = artwork())

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_NO_ROUTE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD).assertDoesNotExist()
  }

  /** Verifies that a missing user location keeps the message card over the artwork card. */
  @Test
  fun artworkCard_whenLocationUnavailable_thenShowsMessageCardWithoutArtworkCard() {
    setOverlay(routeUiState = RouteUiState.LocationUnavailable, selectedArtwork = artwork())

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_LOCATION_UNAVAILABLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD).assertDoesNotExist()
  }

  /** Verifies that the X of the location message card clears the route and the selection. */
  @Test
  fun artworkCard_whenLocationUnavailable_thenDismissInvokesClear() {
    var cleared = false

    setOverlay(
        routeUiState = RouteUiState.LocationUnavailable,
        selectedArtwork = artwork(),
        onClear = { cleared = true },
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertTrue(cleared)
  }

  /**
   * Verifies that a failed route keeps the message card over the artwork card and still wires both
   * of its actions.
   */
  @Test
  fun artworkCard_whenError_thenShowsMessageCardWithoutArtworkCardAndInvokesRetryAndClear() {
    var retried = false
    var cleared = false

    setOverlay(
        routeUiState = RouteUiState.Error(RouteErrorKind.NETWORK, R.string.error_network),
        selectedArtwork = artwork(),
        onClear = { cleared = true },
        onRetry = { retried = true },
    )

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD).assertDoesNotExist()

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()
    assertTrue(retried)

    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON).performClick()
    composeTestRule.waitForIdle()
    assertTrue(cleared)
  }

  /** Verifies that an idle map with no selection renders nothing. */
  @Test
  fun artworkCard_whenNoSelectionAndIdle_thenShowsNothing() {
    setOverlay(routeUiState = RouteUiState.Idle, selectedArtwork = null)

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MapScreenTestTags.ROUTE_SUMMARY_CARD).assertDoesNotExist()
  }

  /** Verifies that a missing artwork name falls back to the shared Untitled string. */
  @Test
  fun artworkCard_whenArtworkHasNoName_thenShowsUntitled() {
    val untitled =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.artwork_unknown_title)

    setOverlay(selectedArtwork = artwork(name = null))

    composeTestRule.onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_NAME).assertTextEquals(untitled)
  }

  /** Verifies that both card actions expose content descriptions for accessibility. */
  @Test
  fun artworkCard_whenSelected_thenExposesAccessibleActions() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    setOverlay(selectedArtwork = artwork())

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.map_artwork_navigate))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.map_artwork_dismiss))
        .assertIsDisplayed()
  }

  /** Verifies that a loaded image is described to accessibility services with the artwork name. */
  @Test
  fun artworkCard_whenImageLoads_thenDescribesItWithTheArtworkName() {
    installFakeImageLoader(
        FakeImageLoaderEngine.Builder().intercept(IMAGE_URL, FakeImage()).build()
    )

    setOverlay(selectedArtwork = artwork(name = "Mona Lisa", imageUrl = IMAGE_URL))
    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodesWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_FALLBACK, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isEmpty()
    }

    composeTestRule
        .onNodeWithTag(MapScreenTestTags.ARTWORK_CARD_IMAGE, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Image of Mona Lisa")
  }

  private fun setOverlay(
      routeUiState: RouteUiState = RouteUiState.Idle,
      routeProgressUiState: RouteProgressUiState? = null,
      selectedArtwork: Artwork? = null,
      onNavigate: () -> Unit = {},
      onClear: () -> Unit = {},
      onRetry: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ArtistaTheme {
        RouteOverlay(
            routeUiState = routeUiState,
            routeProgressUiState = routeProgressUiState,
            selectedArtwork = selectedArtwork,
            onNavigate = onNavigate,
            onClearRoute = onClear,
            onRetryRoute = onRetry,
        )
      }
    }
  }

  private fun artwork(name: String? = "Mona Lisa", imageUrl: String? = null): Artwork =
      Artwork(
          uid = "artwork",
          name = name,
          artist = Artist("artist", "Artist"),
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
          imageUrl = imageUrl,
      )
}
