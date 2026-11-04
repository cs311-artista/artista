package com.artista.artista.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.ui.theme.ArtistaTheme

/** Previews the overview screen with a sample set of artworks in Android Studio. */
@Preview(showBackground = true)
@Composable
private fun OverviewScreenPreview() {
  val viewModel = remember {
    val repository = ArtworkRepositoryLocal()
    mockArtworks.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }
    OverviewViewModel(repository)
  }
  ArtistaTheme { OverviewScreen(viewModel = viewModel) }
}
