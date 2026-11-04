package com.artista.artista.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.ui.theme.ArtistaTheme

/**
 * Previews the map screen layout.
 *
 * @author Felix Burchardt
 */
@Preview(widthDp = 1080, heightDp = 2424)
@Composable
fun MapScreenPreview() {
  ArtistaTheme {
    MapScreen(
        mapViewModel =
            viewModel(
                factory =
                    MapViewModel.provideFactory(
                        wikiDataRepository = ArtworkRepositoryProvider.mockRepository,
                        savedArtworksRepository = ArtworkRepositoryProvider.mockRepository,
                    )
            )
    )
  }
}
