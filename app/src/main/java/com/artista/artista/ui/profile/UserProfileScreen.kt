// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.profile

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artista.artista.R
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.model.user.UserRepositoryLocal
import com.artista.artista.model.user.UsernameCacheInMemory
import com.artista.artista.ui.components.RemoteImage
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaColors
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val PillShape = RoundedCornerShape(percent = 50)
private val CardShape = RoundedCornerShape(Dimensions.cornerRadiusMedium)
private val IconBoxShape = RoundedCornerShape(Dimensions.cornerRadiusSmall)

/**
 * Displays the profile of the current user with access to their preferences and saved artworks.
 *
 * [onLogoutClick] has no default value, so that integrating the screen without wiring it fails to
 * compile instead of leaving a logout button that silently does nothing.
 *
 * @param onLogoutClick Callback invoked when the user asks to sign out.
 * @param userProfileViewModel ViewModel providing the profile state. It is built with an auth
 *   repository, to know who is signed in, and a user repository, to read and save their username;
 *   its failures are shown in a toast. The app builds it from the shared providers, in
 *   [com.artista.artista.ArtistaApp].
 * @param onPreferencesClick Callback invoked when the user opens their artist preferences.
 * @param onSavedArtworkClick Callback invoked when the user opens their saved artworks.
 * @param onTabSelected Callback invoked with the destination selected in the bottom navigation.
 * @author Timz3rr, krfpn
 */
@Composable
fun UserProfileScreen(
    onLogoutClick: () -> Unit,
    userProfileViewModel: UserProfileViewModel,
    onPreferencesClick: () -> Unit = {},
    onSavedArtworkClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by userProfileViewModel.uiState.collectAsStateWithLifecycle()
  // A toast needs an Android context to be displayed.
  val context = LocalContext.current
  // The ViewModel only exposes a string resource id, so it stays free of Android resources; the
  // text is resolved here, in the UI.
  val errorMessage = uiState.errorMessageResId?.let { stringResource(it) }

  // Errors are shown in a toast: a short message that disappears by itself and does not block the
  // screen. LaunchedEffect runs only when the message changes, not on every recomposition, so the
  // toast is shown once per error.
  LaunchedEffect(errorMessage) {
    errorMessage?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      // Cleared once shown, so a recomposition or a return to the screen does not show it again.
      userProfileViewModel.clearErrorMessage()
    }
  }

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(UserProfileTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Profile, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    ProfileContent(
        username = uiState.username,
        photoUrl = uiState.photoUrl,
        onUsernameSave = userProfileViewModel::updateUsername,
        onPreferencesClick = onPreferencesClick,
        onSavedArtworkClick = onSavedArtworkClick,
        onLogoutClick = onLogoutClick,
        modifier = Modifier.padding(innerPadding),
    )
  }
}

/**
 * Header, picture banner, personal space cards and logout button of the profile screen, scrollable
 * when they do not fit on the screen.
 *
 * @param username Name of the user, shown under the Name card title and used to prefill the edit
 *   dialog, or null when they have none.
 * @param photoUrl Link of the picture of the user, or null to display the placeholder.
 * @param onUsernameSave Callback invoked with the username saved from the edit dialog.
 * @param onPreferencesClick Callback invoked when the artist preferences card is clicked.
 * @param onSavedArtworkClick Callback invoked when the saved artwork card is clicked.
 * @param onLogoutClick Callback invoked when the logout button is clicked.
 * @param modifier Modifier applied to the content, used to apply the padding of the Scaffold.
 */
@Composable
private fun ProfileContent(
    username: String?,
    photoUrl: String?,
    onUsernameSave: (String) -> Unit,
    onPreferencesClick: () -> Unit,
    onSavedArtworkClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Saveable so that the dialog and the sheet stay open when the screen is rotated.
  var isEditingUsername by rememberSaveable { mutableStateOf(false) }
  var isChangingPhoto by rememberSaveable { mutableStateOf(false) }

  if (isEditingUsername) {
    EditUsernameDialog(
        // The field starts empty when the user has no name yet.
        initialUsername = username.orEmpty(),
        onSave = { newName ->
          onUsernameSave(newName)
          isEditingUsername = false
        },
        onDismiss = { isEditingUsername = false },
    )
  }

  if (isChangingPhoto) {
    ChangePhotoSheet(onDismiss = { isChangingPhoto = false })
  }

  Column(
      modifier =
          modifier
              .fillMaxSize()
              // On small screens, a non-scrollable column squashes its last items to a zero height,
              // which makes them invisible and unclickable.
              .verticalScroll(rememberScrollState())
              .padding(
                  start = Dimensions.profileContentHorizontalPadding,
                  top = Dimensions.profileContentTopPadding,
                  end = Dimensions.profileContentHorizontalPadding,
                  bottom = Dimensions.profileContentBottomPadding,
              ),
  ) {
    ProfileHeader()
    Spacer(modifier = Modifier.height(Dimensions.profileHeaderBannerSpacing))
    ProfileBanner(photoUrl = photoUrl, onEditPhotoClick = { isChangingPhoto = true })
    Spacer(modifier = Modifier.height(Dimensions.profileBannerSectionSpacing))
    Text(
        text = stringResource(R.string.profile_personal_space),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Dimensions.profileSectionLabelCardsSpacing))
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.profileOptionCardSpacing)) {
      // Name button
      ProfileOptionCard(
          title = stringResource(R.string.profile_name),
          subtitle = username ?: stringResource(R.string.profile_name_subtitle),
          icon = Icons.Outlined.Person,
          onClick = { isEditingUsername = true },
          modifier = Modifier.testTag(UserProfileTestTags.USERNAME),
      )
      // Artist pref button
      ProfileOptionCard(
          title = stringResource(R.string.profile_preferences),
          subtitle = stringResource(R.string.profile_preferences_subtitle),
          icon = Icons.Outlined.ViewAgenda,
          onClick = onPreferencesClick,
          modifier = Modifier.testTag(UserProfileTestTags.PREFERENCES_BUTTON),
      )
      // Saved Artwork button
      ProfileOptionCard(
          title = stringResource(R.string.profile_saved_artwork),
          subtitle = stringResource(R.string.profile_saved_artwork_subtitle),
          icon = Icons.Filled.Bookmark,
          onClick = onSavedArtworkClick,
          modifier = Modifier.testTag(UserProfileTestTags.SAVED_ARTWORK_BUTTON),
      )
    }
    Spacer(modifier = Modifier.height(Dimensions.profileLogoutTopSpacing))
    LogoutButton(
        onClick = onLogoutClick,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
  }
}

/** Title and subtitle at the top of the profile screen. */
@Composable
private fun ProfileHeader() {
  Text(
      text = stringResource(R.string.profile_title),
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
  )
  Spacer(modifier = Modifier.height(Dimensions.profileTitleSubtitleSpacing))
  Text(
      text = stringResource(R.string.profile_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * Displays the picture of the user in a wide rounded banner, with the placeholder while there is no
 * picture to show, and the button opening the change photo sheet in its bottom-end corner.
 *
 * The banner crops the picture to a wide rectangle, as the design asks. A Google avatar is framed
 * for a circle, so part of it, such as the top of a face, may be cut off.
 *
 * @param photoUrl link of the picture to download, or null when the user has none
 * @param onEditPhotoClick Callback invoked when the edit photo button is clicked.
 */
@Composable
private fun ProfileBanner(photoUrl: String?, onEditPhotoClick: () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth()) {
    RemoteImage(
        imageUrl = photoUrl,
        contentDescription = stringResource(R.string.profile_picture_description),
        // Crop fills the banner with the bitmap instead of letterboxing it inside.
        contentScale = ContentScale.Crop,
        imageTestTag = UserProfileTestTags.PROFILE_PICTURE_IMAGE,
        fallbackTestTag = UserProfileTestTags.PROFILE_PICTURE_PLACEHOLDER,
        // The area owns its size, its shape and its background: the picture and the placeholder
        // must
        // fill the same banner so that loading never changes the outline.
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(Dimensions.PROFILE_BANNER_ASPECT_RATIO)
                // Clipped before the background so the surface fills the rounded shape.
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .testTag(UserProfileTestTags.PROFILE_PICTURE),
        // The words stand in for the picture; the artwork screens pass an icon instead.
        fallback = { fallbackModifier ->
          Text(
              text = stringResource(R.string.profile_picture_placeholder),
              modifier = fallbackModifier,
              style = MaterialTheme.typography.headlineMedium,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.primary,
          )
        },
    )
    // Edit photo button
    Surface(
        onClick = onEditPhotoClick,
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .padding(Dimensions.profileEditPhotoPadding)
                .testTag(UserProfileTestTags.EDIT_PHOTO_BUTTON),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
      Text(
          text = stringResource(R.string.profile_edit_photo),
          modifier =
              Modifier.padding(
                  horizontal = Dimensions.profileEditPhotoHorizontalPadding,
                  vertical = Dimensions.profileEditPhotoVerticalPadding,
              ),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/**
 * White rounded card with an optional icon, a title, a subtitle and a trailing chevron, used both
 * for the personal space entries and for the change photo sheet options.
 *
 * @param title Main text of the card.
 * @param subtitle Secondary text shown under the title.
 * @param modifier Modifier applied to the card container.
 * @param icon Icon shown in a grey box before the texts, or null to show none.
 * @param onClick Callback invoked when the card is clicked, or null to make it not clickable.
 */
@Composable
private fun ProfileOptionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
  val content: @Composable () -> Unit = {
    ProfileOptionCardContent(title = title, subtitle = subtitle, icon = icon)
  }
  // Surface has no overload with a nullable onClick, and a clickable modifier would draw its
  // ripple outside the rounded shape, so the two cases use their own Surface.
  if (onClick != null) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
  } else {
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
  }
}

/**
 * Row inside a [ProfileOptionCard]: the optional icon box, the texts and the chevron.
 *
 * @param title Main text of the card.
 * @param subtitle Secondary text shown under the title.
 * @param icon Icon shown in a grey box before the texts, or null to show none.
 */
@Composable
private fun ProfileOptionCardContent(title: String, subtitle: String, icon: ImageVector?) {
  Row(
      modifier =
          Modifier.padding(
              horizontal = Dimensions.profileOptionCardHorizontalPadding,
              vertical = Dimensions.profileOptionCardVerticalPadding,
          ),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Box(
          modifier =
              Modifier.size(Dimensions.profileOptionIconBoxSize)
                  .clip(IconBoxShape)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
      ) {
        // The title already names the entry, so describing the icon would make screen readers
        // announce it twice.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
      }
      Spacer(modifier = Modifier.width(Dimensions.profileOptionIconTextSpacing))
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Dimensions.profileOptionTitleSubtitleSpacing),
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * Bottom sheet listing where the profile photo can come from.
 *
 * @param onDismiss Callback invoked when the sheet is closed, by Cancel, a swipe down or a tap on
 *   the scrim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePhotoSheet(onDismiss: () -> Unit) {
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(UserProfileTestTags.CHANGE_PHOTO_SHEET),
      containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimensions.profileSheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.profileOptionCardSpacing),
    ) {
      Column {
        Text(
            text = stringResource(R.string.profile_change_photo_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Dimensions.profileTitleSubtitleSpacing))
        Text(
            text = stringResource(R.string.profile_change_photo_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Dimensions.profileSheetSubtitleOptionsSpacing))
      }
      ProfileOptionCard(
          title = stringResource(R.string.profile_change_photo_library),
          subtitle = stringResource(R.string.profile_change_photo_library_subtitle),
          modifier = Modifier.testTag(UserProfileTestTags.CHANGE_PHOTO_LIBRARY_OPTION),
      )
      ProfileOptionCard(
          title = stringResource(R.string.profile_change_photo_saved_artwork),
          subtitle = stringResource(R.string.profile_change_photo_saved_artwork_subtitle),
          modifier = Modifier.testTag(UserProfileTestTags.CHANGE_PHOTO_SAVED_ARTWORK_OPTION),
      )
      Spacer(modifier = Modifier.height(Dimensions.profileSheetCancelTopSpacing))
      Surface(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth().testTag(UserProfileTestTags.CHANGE_PHOTO_CANCEL),
          shape = PillShape,
          color = MaterialTheme.colorScheme.surface,
      ) {
        Text(
            text = stringResource(R.string.profile_change_photo_cancel),
            // Full width so that the centered text sits in the middle of the pill.
            modifier =
                Modifier.fillMaxWidth()
                    .padding(vertical = Dimensions.profileSheetCancelVerticalPadding),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/**
 * Pill-shaped button with an icon and a label, signing the user out.
 *
 * @param onClick Callback invoked when the button is clicked.
 * @param modifier Modifier applied to the button, used to center it in the content.
 */
@Composable
private fun LogoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
      onClick = onClick,
      modifier = modifier.testTag(UserProfileTestTags.LOGOUT_BUTTON),
      shape = PillShape,
      color = MaterialTheme.colorScheme.surface,
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
          tint = MaterialTheme.colorScheme.error,
      )
      Text(
          text = stringResource(R.string.profile_logout),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/**
 * Dialog letting the user enter a new username.
 *
 * The save button stays disabled while the entered name is rejected by [isValidUsername]. Under the
 * field, a hint states the allowed characters and turns into an error naming the broken rule, or
 * into a warning when trailing spaces alone exceed the limit, next to a counter of the characters
 * typed out of [MAX_USERNAME_LENGTH].
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
  // Recomputed on each keystroke, so the hint, the counter and the save button react while the
  // user types.
  val issue = findUsernameIssue(draft)

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
            placeholder = { Text(stringResource(R.string.profile_username_example)) },
            // Turns the outline, the label and the supporting texts to the error color.
            isError = issue != null,
            supportingText = { UsernameSupportingText(draft = draft, issue = issue) },
            // A username fits on one line, so Enter must not insert a line break.
            singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
            onClick = { onSave(draft) },
            modifier = Modifier.testTag(UserProfileTestTags.USERNAME_SAVE),
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

/**
 * Line under the username field: the hint, the error naming the broken rule, or a warning about
 * trailing spaces on the left, and the character counter on the right.
 *
 * @param draft Username typed so far.
 * @param issue Rule broken by [draft], or null when it breaks none.
 */
@Composable
private fun UsernameSupportingText(draft: String, issue: UsernameIssue?) {
  val length = countUsernameCharacters(draft)
  // Only trailing spaces can push a valid name over the counter's limit. They are removed on save,
  // so the name stays valid, but the user is warned instead of seeing a silent "21 / 20".
  val isOverOnlyBySpaces = issue == null && length > MAX_USERNAME_LENGTH
  val message =
      when {
        isOverOnlyBySpaces -> stringResource(R.string.profile_username_warning_trailing_spaces)
        issue == null -> stringResource(R.string.profile_username_hint)
        issue == UsernameIssue.INVALID_CHARACTERS ->
            stringResource(R.string.profile_username_error_characters)
        else -> stringResource(R.string.profile_username_error_too_long, MAX_USERNAME_LENGTH)
      }
  val color = if (isOverOnlyBySpaces) warningColor() else Color.Unspecified
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = message,
        modifier = Modifier.weight(1f).testTag(UserProfileTestTags.USERNAME_SUPPORTING_TEXT),
        color = color,
    )
    Text(
        text = stringResource(R.string.profile_username_counter, length, MAX_USERNAME_LENGTH),
        modifier = Modifier.testTag(UserProfileTestTags.USERNAME_COUNTER),
        color = color,
    )
  }
}

/**
 * Picks the warning color readable on the current theme.
 *
 * @return the warning color for a light or a dark background
 */
@Composable
private fun warningColor(): Color =
    // The background of the theme in use tells light from dark, even when the app overrides the
    // system setting.
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) ArtistaColors.WarningDark
    else ArtistaColors.Warning

/** Previews the user profile screen with a sample user in Android Studio. */
@Preview(showBackground = true)
@Composable
internal fun UserProfileScreenPreview() {
  val viewModel = remember {
    UserProfileViewModel(PreviewAuthRepository, UserRepositoryLocal(), UsernameCacheInMemory())
        .apply {
          setUser(User(uid = "preview", userName = "Artista Fan", preference = UserPreference()))
        }
  }
  ArtistaTheme { UserProfileScreen(onLogoutClick = {}, userProfileViewModel = viewModel) }
}

/**
 * Signed-out [AuthRepository] for the previews.
 *
 * [UserProfileViewModel] needs an [AuthRepository] to know who is signed in, but the previews
 * cannot reach Firebase. This one stands in for it and always answers that nobody is signed in, so
 * the ViewModel loads nothing and the preview shows the user given to
 * [UserProfileViewModel.setUser].
 */
private object PreviewAuthRepository : AuthRepository {
  override val authState: Flow<AuthUser?> = flowOf(null)

  override suspend fun signIn(idToken: String): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("Sign-in is not available in previews"))

  override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

/** Previews the username edit dialog in Android Studio. */
@Preview(showBackground = true)
@Composable
internal fun EditUsernameDialogPreview() {
  ArtistaTheme { EditUsernameDialog(initialUsername = "Artista Fan", onSave = {}, onDismiss = {}) }
}
