// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friends

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.R
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme

/**
 * Displays the friend list of the user. For now it only shows a message inviting the user to
 * connect with their friends; the list itself comes with the ViewModel.
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
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = stringResource(R.string.friends_empty_message),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(FriendListTestTags.EMPTY_MESSAGE),
      )
    }
  }
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
  const val EMPTY_MESSAGE = "friend_list_empty_message"
}

/** Previews the friend list screen in Android Studio. */
@Preview(showBackground = true)
@Composable
internal fun FriendListScreenPreview() {
  ArtistaTheme { FriendListScreen(onBackClick = {}) }
}
