// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions

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
        onUsernameSave = userProfileViewModel::updateUsername,
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
          Modifier.fillMaxWidth()
              .statusBarsPadding()
              .padding(
                  horizontal = Dimensions.profileTopBarHorizontalPadding,
                  vertical = Dimensions.profileTopBarVerticalPadding,
              ),
      horizontalArrangement = Arrangement.End,
  ) {
    LogoutButton(onClick = onLogoutClick)
  }
}

/**
 * Profile picture and fields of the profile screen, scrollable when they do not fit on the screen.
 *
 * @param username Name of the user, or null to display the placeholder.
 * @param onUsernameSave Callback invoked with the username saved from the edit dialog.
 * @param onPreferencesClick Callback invoked when the preferences button is clicked.
 * @param onSavedArtworkClick Callback invoked when the saved artwork button is clicked.
 * @param modifier Modifier applied to the content, used to apply the padding of the Scaffold.
 */
@Composable
private fun ProfileContent(
    username: String?,
    onUsernameSave: (String) -> Unit,
    onPreferencesClick: () -> Unit,
    onSavedArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Saveable so that the dialog stays open when the screen is rotated.
  var isEditingUsername by rememberSaveable { mutableStateOf(false) }

  if (isEditingUsername) {
    EditUsernameDialog(
        // The placeholder is not a name, so the field starts empty when the user has none.
        initialUsername = username.orEmpty(),
        onSave = { newName ->
          onUsernameSave(newName)
          isEditingUsername = false
        },
        onDismiss = { isEditingUsername = false },
    )
  }

  Column(
      // The top bar already separates the picture from the top of the screen, so the top padding is
      // smaller than the bottom one.
      modifier =
          modifier
              .fillMaxSize()
              // On small screens, a non-scrollable column squashes its last fields to a zero
              // height,
              // which makes them invisible and unclickable.
              .verticalScroll(rememberScrollState())
              .padding(
                  start = Dimensions.profileContentHorizontalPadding,
                  top = Dimensions.profileContentTopPadding,
                  end = Dimensions.profileContentHorizontalPadding,
                  bottom = Dimensions.profileContentBottomPadding,
              ),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ProfilePicturePlaceholder()
    Spacer(modifier = Modifier.height(Dimensions.profilePictureFieldsSpacing))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.profileFieldSpacing),
    ) {
      ProfileField(
          text = username ?: stringResource(R.string.profile_username_placeholder),
          modifier = Modifier.testTag(UserProfileTestTags.USERNAME),
          onClick = { isEditingUsername = true },
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
        modifier =
            Modifier.padding(
                horizontal = Dimensions.logoutButtonHorizontalPadding,
                vertical = Dimensions.logoutButtonVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.logoutButtonIconLabelSpacing),
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
              .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge))
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
 * Pill-shaped clickable field of the profile screen.
 *
 * @param text Text displayed in the field.
 * @param onClick Callback invoked when the field is clicked.
 * @param modifier Modifier applied to the field container.
 */
@Composable
private fun ProfileField(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = FieldShape,
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
  ) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(Dimensions.profileFieldHeight)
                .padding(horizontal = Dimensions.profileFieldHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = text,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Dialog letting the user enter a new username.
 *
 * The save button stays disabled while the entered name is rejected by [isValidUsername], and the
 * rules are shown under the field so the user knows why.
 *
 * @param initialUsername Text the field starts with.
 * @param onSave Callback invoked with the entered username when the user saves it.
 * @param onDismiss Callback invoked when the dialog is closed without saving.
 */
@Composable
private fun EditUsernameDialog(
    initialUsername: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  // Kept in the dialog rather than in the ViewModel: it is only a draft, and the real username must
  // not change until the user saves. Saveable so that the text being typed survives a screen
  // rotation.
  var draft by rememberSaveable { mutableStateOf(initialUsername) }

  AlertDialog(
      // Also called on a tap outside the dialog or a back press, which behave like Cancel.
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(UserProfileTestTags.USERNAME_DIALOG),
      title = { Text(stringResource(R.string.profile_username_dialog_title)) },
      text = {
        OutlinedTextField(
            value = draft,
            // The field only displays draft, so each keystroke must be written back to it.
            onValueChange = { draft = it },
            modifier = Modifier.testTag(UserProfileTestTags.USERNAME_FIELD),
            label = { Text(stringResource(R.string.profile_username_label)) },
            supportingText = {
              // The limit is passed as an argument so that the text follows MAX_USERNAME_LENGTH.
              Text(stringResource(R.string.profile_username_rules, MAX_USERNAME_LENGTH))
            },
            // A username fits on one line, so Enter must not insert a line break.
            singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
            onClick = { onSave(draft) },
            modifier = Modifier.testTag(UserProfileTestTags.USERNAME_SAVE),
            // Recomputed on each keystroke, so the button reacts while the user types.
            enabled = isValidUsername(draft),
        ) {
          Text(stringResource(R.string.profile_username_save))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(UserProfileTestTags.USERNAME_CANCEL),
        ) {
          Text(stringResource(R.string.profile_username_cancel))
        }
      },
  )
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

/** Previews the username edit dialog in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun EditUsernameDialogPreview() {
  ArtistaTheme { EditUsernameDialog(initialUsername = "Artista Fan", onSave = {}, onDismiss = {}) }
}
