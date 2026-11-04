package com.artista.artista.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.ui.theme.ArtistaTheme

/** Previews the complete search screen in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
  ArtistaTheme {
    SearchScreen(
        viewModel =
            viewModel(
                factory =
                    SearchScreenViewModel.provideFactory(
                        ArtworkRepositoryProvider.mockRepository,
                        ArtworkRepositoryProvider.mockRepository,
                    )
            ),
        onTabSelected = {},
    )
  }
}
