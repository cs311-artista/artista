package com.artista.artista.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
private val FieldShape = RoundedCornerShape(percent = 50)

/**
 * Displays the profile of the current user with access to their preferences and saved artworks.
 *
 * [onLogoutClick] has no default value, so that integrating the screen without wiring it fails to
 * compile instead of leaving a logout button that silently does nothing.
 *
 * @param onLogoutClick Callback invoked when the user asks to sign out.
 * @param userProfileViewModel ViewModel providing the profile state.
 * @param onPreferencesClick Callback invoked when the user opens their artist preferences.
 * @param onSavedArtworkClick Callback invoked when the user opens their saved artworks.
 * @param onTabSelected Callback invoked with the destination selected in the bottom navigation.
 * @author Timz3rr
 */
@Composable
fun UserProfileScreen(
    onLogoutClick: () -> Unit,
    userProfileViewModel: UserProfileViewModel = viewModel(),
    onPreferencesClick: () -> Unit = {},
    onSavedArtworkClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by userProfileViewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(UserProfileTestTags.SCREEN),
      // The mockup uses a brand-tinted background behind white cards, unlike the usual surface.
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      topBar = { ProfileTopBar(onLogoutClick = onLogoutClick) },
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Profile, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    ProfileContent(
        username = uiState.username,
        onPreferencesClick = onPreferencesClick,
        onSavedArtworkClick = onSavedArtworkClick,
        modifier = Modifier.padding(innerPadding),
    )
  }
}

/**
 * Top bar of the profile screen, holding the logout button on its right.
 *
 * @param onLogoutClick Callback invoked when the logout button is clicked.
 */
@Composable
private fun ProfileTopBar(onLogoutClick: () -> Unit) {
  // The app draws edge to edge, and unlike the content, the top bar slot of the Scaffold does not
  // receive the status bar insets, so the bar applies them itself.
  Row(
      modifier =
          Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.End,
  ) {
    LogoutButton(onClick = onLogoutClick)
  }
}

/**
 * Profile picture and fields of the profile screen, centered between the top and the bottom bar.
 *
 * @param username Name of the user, or null to display the placeholder.
 * @param onPreferencesClick Callback invoked when the preferences button is clicked.
 * @param onSavedArtworkClick Callback invoked when the saved artwork button is clicked.
 * @param modifier Modifier applied to the content, used to apply the padding of the Scaffold.
 */
@Composable
private fun ProfileContent(
    username: String?,
    onPreferencesClick: () -> Unit,
    onSavedArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      // The top bar already separates the picture from the top of the screen, so the top padding is
      // smaller than the bottom one.
      modifier =
          modifier.fillMaxSize().padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 45.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ProfilePicturePlaceholder()
    // Equal weights center the fields between the picture and the bottom bar on any screen size.
    Spacer(modifier = Modifier.weight(1f))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      // Styled like the buttons on purpose: the username will become editable on click (#55),
      // which will also remove the read-only branch of ProfileField.
      ProfileField(
          text = username ?: stringResource(R.string.profile_username_placeholder),
          textModifier = Modifier.testTag(UserProfileTestTags.USERNAME),
      )
      ProfileField(
          text = stringResource(R.string.profile_preferences),
          modifier = Modifier.testTag(UserProfileTestTags.PREFERENCES_BUTTON),
          onClick = onPreferencesClick,
      )
      ProfileField(
          text = stringResource(R.string.profile_saved_artwork),
          modifier = Modifier.testTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON),
          onClick = onSavedArtworkClick,
      )
    }
    Spacer(modifier = Modifier.weight(1f))
  }
}

/**
 * Pill-shaped button with an icon and a label, signing the user out.
 *
 * @param onClick Callback invoked when the button is clicked.
 */
@Composable
private fun LogoutButton(onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      modifier = Modifier.testTag(UserProfileTestTags.LOGOUT_BUTTON),
      shape = FieldShape,
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // The label already names the action, so describing the icon would make screen readers
      // announce it twice.
      Icon(
          imageVector = Icons.AutoMirrored.Outlined.Logout,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
      Text(
          text = stringResource(R.string.profile_logout),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/** Static placeholder shown until users can upload a profile picture. */
@Composable
private fun ProfilePicturePlaceholder() {
  Box(
      modifier =
          Modifier.fillMaxWidth(0.75f)
              .aspectRatio(1f)
              .clip(RoundedCornerShape(30.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLowest)
              .testTag(UserProfileTestTags.PROFILE_PICTURE),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = stringResource(R.string.profile_picture_placeholder),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary,
    )
  }
}

/**
 * Pill-shaped field of the profile screen, clickable only when [onClick] is provided.
 *
 * @param text Text displayed in the field.
 * @param modifier Modifier applied to the field container.
 * @param textModifier Modifier applied to the text, used to tag non-clickable fields whose text is
 *   not merged into the container semantics.
 * @param onClick Callback invoked when the field is clicked, or null for a read-only field.
 */
@Composable
private fun ProfileField(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
  val content: @Composable () -> Unit = {
    Box(
        modifier = Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = text,
          modifier = textModifier,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
  val fieldModifier = modifier.fillMaxWidth()
  val fieldColor = MaterialTheme.colorScheme.surfaceContainerLowest

  if (onClick == null) {
    Surface(modifier = fieldModifier, shape = FieldShape, color = fieldColor, content = content)
  } else {
    Surface(
        onClick = onClick,
        modifier = fieldModifier,
        shape = FieldShape,
        color = fieldColor,
        content = content,
    )
  }
}

/** Previews the user profile screen with a sample user in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun UserProfileScreenPreview() {
  val viewModel = remember {
    UserProfileViewModel().apply {
      setUser(User(uid = "preview", userName = "Artista Fan", preference = UserPreference()))
    }
  }
  ArtistaTheme { UserProfileScreen(onLogoutClick = {}, userProfileViewModel = viewModel) }
}
