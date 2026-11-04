package com.artista.artista.ui.artwork

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.Dimension
import com.artista.artista.model.map.Location
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import java.util.Date

/** Previews [ArtworkCard] with representative artwork details. */
@Preview(showBackground = true)
@Composable
private fun ArtworkCardPreview() {
  val artwork =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artist = Artist(uid = "preview-artist-001", name = "Leonardo da Vinci"),
          description = "A portrait of Lisa Gherardini, painted in the early 16th century.",
          conceptionDate = Date(0L),
          museum = "Musée du Louvre",
          location = Location(longitude = 2.3376, latitude = 48.8606, name = "Louvre"),
          dimension = Dimension(width = 53.0, height = 77.0),
      )

  ArtistaTheme {
    ArtworkCard(
        artwork = artwork,
        isSaved = false,
        onArtworkClick = {},
        onMapClick = {},
        onSaveClick = { _ -> },
        modifier = Modifier.padding(Dimensions.artworkCardPreviewPadding),
    )
  }
}
