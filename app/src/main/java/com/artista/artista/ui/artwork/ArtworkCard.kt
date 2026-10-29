// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.Dimension
import com.artista.artista.model.map.Location
import com.artista.artista.ui.theme.ArtistaTheme
import java.util.Date

/** Stable semantics tags for the artwork card UI tests. */
object ArtworkCardTestTag {
  const val ARTWORK_CARD = "artwork_card"
  const val ARTWORK_CARD_THUMBNAIL = "artwork_card_thumbnail"
  const val ARTWORK_CARD_TITLE = "artwork_card_title"
  const val ARTWORK_CARD_ARTIST = "artwork_card_artist"
  const val ARTWORK_CARD_MAP_BUTTON = "artwork_card_map_button"
  const val ARTWORK_CARD_SAVE_BUTTON = "artwork_card_save_button"
}

/**
 * Displays an artwork thumbnail, its name and creator, and actions for navigation and saving.
 *
 * @param artwork artwork whose details should be displayed
 * @param isSaved whether this artwork is currently saved (responsibility of the caller to ensure
 *   this reflects the current state)
 * @param onArtworkClick called when the card is selected
 * @param onMapClick called when the map action is selected for an artwork with a location
 * @param onSaveClick called with the artwork when the save action is selected
 * @param modifier modifier applied to the card
 * @param testTagSuffix optional suffix for distinguishing repeated cards in a list
 * @author IJJA3141
 */
@Composable
fun ArtworkCard(
    artwork: Artwork,
    isSaved:
        Boolean, // should always reflect the current saved state of the artwork (responsibility of
    // the caller)
    onArtworkClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    onSaveClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
    testTagSuffix: String? = null,
) {
  fun testTag(tag: String): String =
      if (testTagSuffix.isNullOrBlank()) tag else "${tag}_$testTagSuffix"

  Card(
      onClick = { onArtworkClick(artwork) },
      modifier = modifier.fillMaxWidth().testTag(testTag(ArtworkCardTestTag.ARTWORK_CARD)),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Surface(
          modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
          color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
          // Placeholder for artwork thumbnail or image
          // TODO: Load and display the artwork image
          Text(
              text = "Picture/thumbnail Placeholder",
              modifier = Modifier.testTag(testTag(ArtworkCardTestTag.ARTWORK_CARD_THUMBNAIL)),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = artwork.name,
            modifier =
                Modifier.fillMaxWidth().testTag(testTag(ArtworkCardTestTag.ARTWORK_CARD_TITLE)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = artwork.artistName ?: stringResource(R.string.artwork_unknown_artist),
            modifier =
                Modifier.fillMaxWidth().testTag(testTag(ArtworkCardTestTag.ARTWORK_CARD_ARTIST)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
            onClick = { onMapClick(artwork) },
            enabled = artwork.location != null,
            modifier = Modifier.testTag(testTag(ArtworkCardTestTag.ARTWORK_CARD_MAP_BUTTON)),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
          Icon(imageVector = Icons.Outlined.LocationOn, contentDescription = null)
          Text(stringResource(R.string.artwork_card_map))
        }

        Button(
            onClick = { onSaveClick(artwork) },
            modifier = Modifier.testTag(testTag(ArtworkCardTestTag.ARTWORK_CARD_SAVE_BUTTON)),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
          Icon(
              imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = null,
          )
          Text(
              stringResource(
                  if (isSaved) R.string.artwork_card_saved else R.string.artwork_card_save
              )
          )
        }
      }
    }
  }
}

/** Previews [ArtworkCard] with representative artwork details. */
@Preview(showBackground = true)
@Composable
private fun ArtworkCardPreview() {
  val artwork =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artistName = "Leonardo da Vinci",
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
        modifier = Modifier.padding(16.dp),
    )
  }
}
