// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friends

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.R
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import com.artista.artista.ui.theme.SearchBarPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Delay before the landing hint starts, so the user first sees the screen at rest. */
internal const val SEARCH_HINT_START_DELAY_MS = 600L

/** Time the landing hint stays pulled down before going back up. */
internal const val SEARCH_HINT_HOLD_MS = 500L

/** Duration of every reveal animation: the hint, and settling after a drag. */
internal const val SEARCH_REVEAL_ANIMATION_MS = 300

/**
 * Displays the friend list of the user. For now it only shows a message inviting the user to
 * connect with their friends, and a search bar hidden above it that the user opens by pulling the
 * content down. On the first visit, the content is pulled down a little and back up by itself, to
 * hint where the search bar is.
 *
 * @param onBackClick Callback invoked when the user taps the back arrow.
 * @param modifier Modifier applied to the screen container.
 * @param onTabSelected Callback invoked with the destination selected in the bottom navigation.
 * @author hixeum
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTabSelected: (Tab) -> Unit = {},
) {
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(FriendListTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.friends_title),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.testTag(FriendListTestTags.TITLE),
              )
            },
            navigationIcon = {
              BackButton(
                  onClick = onBackClick,
                  modifier = Modifier.testTag(FriendListTestTags.BACK_BUTTON),
              )
            },
            // Blends the bar with the screen background, as on the other profile sub-screens.
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
        )
      },
      // Friends are reached from the profile, so the Profile tab stays selected.
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Profile, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    FriendListContent(modifier = Modifier.padding(innerPadding))
  }
}

/**
 * Content of the friend list: the pull-down search bar above the message.
 *
 * The search bar is revealed by the distance the content is pulled down. Released past half of the
 * bar, it opens and takes the focus; otherwise it slides back up. Pulling up, or pressing back,
 * closes it again.
 *
 * @param modifier Modifier applied to the content, used to apply the padding of the Scaffold.
 */
@Composable
private fun FriendListContent(modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  val revealHeightPx = with(density) { Dimensions.friendSearchRevealHeight.toPx() }
  val hintOffsetPx = with(density) { Dimensions.friendSearchHintOffset.toPx() }
  val focusManager = LocalFocusManager.current
  val scope = rememberCoroutineScope()

  // Saveable so that a rotation neither closes the search nor replays the hint.
  var isSearchOpen by rememberSaveable { mutableStateOf(false) }
  var hasShownHint by rememberSaveable { mutableStateOf(false) }
  var revealPx by remember { mutableFloatStateOf(if (isSearchOpen) revealHeightPx else 0f) }
  val queryState = rememberTextFieldState()
  val focusRequester = remember { FocusRequester() }
  // A new animation or a drag cancels the running one, so the hint never fights the user's finger.
  val animationMutex = remember { MutatorMutex() }

  suspend fun animateRevealTo(target: Float) = animationMutex.mutate {
    animate(revealPx, target, animationSpec = tween(SEARCH_REVEAL_ANIMATION_MS)) { value, _ ->
      revealPx = value
    }
  }

  fun setSearchOpen(open: Boolean) {
    isSearchOpen = open
    // Cleared before the field leaves the composition, so the keyboard does not stay up.
    if (!open) focusManager.clearFocus()
    scope.launch {
      animateRevealTo(if (open) revealHeightPx else 0f)
      if (open) focusRequester.requestFocus()
    }
  }

  LaunchedEffect(Unit) {
    if (hasShownHint || isSearchOpen) return@LaunchedEffect
    // Marked first: if the screen is rotated during the hint, it is not worth replaying.
    hasShownHint = true
    delay(SEARCH_HINT_START_DELAY_MS)
    animateRevealTo(hintOffsetPx)
    delay(SEARCH_HINT_HOLD_MS)
    animateRevealTo(0f)
  }

  BackHandler(enabled = isSearchOpen) { setSearchOpen(false) }

  val openLabel = stringResource(R.string.friends_search_placeholder)
  val closeLabel = stringResource(R.string.friends_search_close)

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .testTag(FriendListTestTags.CONTENT)
              .draggable(
                  orientation = Orientation.Vertical,
                  state =
                      rememberDraggableState { delta ->
                        revealPx = (revealPx + delta).coerceIn(0f, revealHeightPx)
                      },
                  // An empty mutation only cancels the hint or settle animation still running.
                  onDragStarted = { animationMutex.mutate {} },
                  onDragStopped = { setSearchOpen(revealPx >= revealHeightPx / 2) },
              )
  ) {
    if (revealPx > 0f) {
      // Clipped so that the bar seems to slide in from behind the top bar as it is revealed.
      Box(
          modifier =
              Modifier.fillMaxWidth().height(with(density) { revealPx.toDp() }).clipToBounds(),
          contentAlignment = Alignment.BottomCenter,
      ) {
        FriendSearchField(
            state = queryState,
            modifier =
                Modifier
                    // Keeps the bar at full size while only part of it is revealed.
                    .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                    .padding(
                        horizontal = Dimensions.friendSearchHorizontalPadding,
                        vertical = Dimensions.friendSearchVerticalPadding,
                    )
                    .focusRequester(focusRequester),
        )
      }
    }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .weight(1f)
                .testTag(FriendListTestTags.EMPTY_MESSAGE)
                // Screen reader users cannot pull the content down, so they get actions instead.
                .semantics(mergeDescendants = true) {
                  customActions =
                      listOf(
                          if (isSearchOpen)
                              CustomAccessibilityAction(closeLabel) {
                                setSearchOpen(false)
                                true
                              }
                          else
                              CustomAccessibilityAction(openLabel) {
                                setSearchOpen(true)
                                true
                              }
                      )
                },
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = stringResource(R.string.friends_empty_message),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}

/**
 * Search field for friends, styled like the search bars of the Overview and Search screens. The
 * query is not used yet: searching comes with the ViewModel.
 *
 * @param state Text typed in the field.
 * @param modifier Modifier applied to the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendSearchField(state: TextFieldState, modifier: Modifier = Modifier) {
  val containerColor = CardDefaults.cardColors().containerColor
  SearchBarDefaults.InputField(
      state = state,
      onSearch = {},
      expanded = false,
      onExpandedChange = {},
      modifier = modifier.fillMaxWidth().testTag(FriendListTestTags.SEARCH_FIELD),
      placeholder = { Text(stringResource(R.string.friends_search_placeholder)) },
      leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
      shape = MaterialTheme.shapes.extraLarge,
      colors =
          SearchBarDefaults.inputFieldColors(
              focusedContainerColor = containerColor,
              unfocusedContainerColor = containerColor,
              cursorColor = SearchBarPalette.cursor,
              focusedLeadingIconColor = SearchBarPalette.content,
              unfocusedLeadingIconColor = SearchBarPalette.content,
          ),
  )
}

/**
 * Provides stable semantics test tags for the friend list screen.
 *
 * @author hixeum
 */
object FriendListTestTags {
  const val SCREEN = "friend_list_screen"
  const val TITLE = "friend_list_title"
  const val BACK_BUTTON = "friend_list_back_button"
  const val CONTENT = "friend_list_content"
  const val SEARCH_FIELD = "friend_list_search_field"
  const val EMPTY_MESSAGE = "friend_list_empty_message"
}

/** Previews the friend list screen in Android Studio. */
@Preview(showBackground = true)
@Composable
internal fun FriendListScreenPreview() {
  ArtistaTheme { FriendListScreen(onBackClick = {}) }
}
