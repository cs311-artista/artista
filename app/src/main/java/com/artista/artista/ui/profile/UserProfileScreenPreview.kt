package com.artista.artista.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.ui.theme.ArtistaTheme

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
