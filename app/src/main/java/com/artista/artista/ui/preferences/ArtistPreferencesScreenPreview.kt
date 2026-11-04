package com.artista.artista.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.ui.theme.ArtistaTheme

/** Previews the artist preferences screen with two selected artists in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun ArtistPreferencesScreenPreview() {
  val viewModel = remember {
    ArtistPreferencesViewModel(ArtworkRepositoryProvider.wikiDataRepository).apply {
      toggleArtist("mock-artist-002")
      toggleArtist("mock-artist-004")
    }
  }
  ArtistaTheme {
    ArtistPreferencesScreen(
        onBackClick = {},
        onSaveClick = {},
        onTabSelected = {},
        artistPreferencesViewModel = viewModel,
    )
  }
}
