package com.artista.artista.ui.artwork

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.ui.theme.ArtistaTheme
import java.util.Calendar
import java.util.Date

/**
 * Compose preview displaying a sample Leonardo da Vinci artwork entry inside [ArtworkScreen][cite:
 * 1].
 *
 * @author hixeum
 */
@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun ArtworkPreview() {
  val monaLisaDate: Date =
      Calendar.getInstance()
          .apply {
            clear()
            set(Calendar.YEAR, 1503)
          }
          .time

  val sampleArtwork =
      Artwork(
          name = "Mona Lisa",
          artist = Artist(uid = "preview-da-vinci", name = "Leonardo da Vinci"),
          description = "A portrait of Lisa Gherardini, wife of Francesco del Giocondo.",
          conceptionDate = monaLisaDate,
          museum = "Musée du Louvre",
          location = null,
          dimension = null,
          uid = "Q1234567",
      )

  ArtistaTheme {
    ArtworkScreen(
        artwork = sampleArtwork,
        artworkScreenViewModel = ArtworkScreenViewModel(ArtworkRepositoryProvider.mockRepository),
        modifier = Modifier,
        onBackClick = {},
    )
  }
}
