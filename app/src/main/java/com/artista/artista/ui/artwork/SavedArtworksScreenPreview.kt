package com.artista.artista.ui.artwork

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.ui.theme.ArtistaTheme

/**
 * Displays a preview of the saved artworks screen with several artworks.
 *
 * To preview other states, change `uiState`:
 * - empty: `SavedArtworksUIState(isLoading = false)`
 * - loading: `SavedArtworksUIState(isLoading = true)`
 *
 * Load and unsave errors are toasts, shown at runtime only.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Preview(showBackground = true)
@Composable
fun SavedArtworksScreenPreview() {
  // Picked by uid to show edge cases: no artist, a non-Latin title and a long punctuated title.
  val artworks = mockArtworks.filter {
    it.uid in setOf("mock-001", "mock-004", "mock-007", "mock-010")
  }

  ArtistaTheme {
    SavedArtworksScreenLayout(
        uiState = SavedArtworksUIState(artworks = artworks, isLoading = false),
        onBackClick = {},
        onArtworkDetailsClick = {},
        onArtworkUnsave = {},
        onTabSelected = {},
        onMapClick = {},
        onRetry = {},
        onLoadErrorHandled = {},
        onUnsaveErrorHandled = {},
    )
  }
}
