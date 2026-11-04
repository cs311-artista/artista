package com.artista.artista.ui.authentication

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.ui.theme.ArtistaTheme

/** Preview of the signed-out sign-in screen. */
@Preview(showBackground = true)
@Composable
private fun AuthScreenContentPreview() {
  ArtistaTheme {
    AuthScreenContent(
        state = AuthUiState(),
        snackbarHostState = remember { SnackbarHostState() },
        onSignInClick = {},
        onSignedIn = {},
    )
  }
}
