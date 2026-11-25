// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.ui.theme.ArtistaColors
import com.artista.artista.ui.theme.Dimensions

/**
 * Displays a saved artwork as a grid tile: its image, an unsave button, a map button when the
 * artwork has a location, and its title and artist.
 *
 * @param artwork artwork shown by the tile
 * @param onClick called with [artwork] when the user taps the tile to open its details
 * @param onUnsaveClick called with [artwork] when the user taps the bookmark to unsave it
 * @param onMapClick called with [artwork] when the user taps the map button
 * @param modifier modifier applied to the tile
 * @author Timz3rr
 */
@Composable
fun SavedArtworkTile(
    artwork: Artwork,
    onClick: (Artwork) -> Unit,
    onUnsaveClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The title also names the artwork in the image and button descriptions, so they always match.
  val title = artwork.name ?: stringResource(R.string.artwork_unknown_title)

  Box(
      modifier =
          modifier
              .aspectRatio(Dimensions.SAVED_ARTWORK_TILE_ASPECT_RATIO)
              .clip(RoundedCornerShape(Dimensions.savedArtworkTileCornerRadius))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onClick(artwork) }
              .testTag(SavedArtworksTestTags.card(artwork))
  ) {
    // Crop fills the whole tile: paintings come in many aspect ratios, and the grid should stay
    // even.
    ArtworkImage(
        imageUrl = artwork.imageUrl,
        contentDescription = stringResource(R.string.artwork_card_image_description, title),
        contentScale = ContentScale.Crop,
        imageTestTag = SavedArtworksTestTags.image(artwork),
        fallbackTestTag = SavedArtworksTestTags.imageFallback(artwork),
        modifier = Modifier.fillMaxSize(),
    )

    SavedArtworkTileButton(
        icon = Icons.Filled.Bookmark,
        contentDescription = stringResource(R.string.saved_artworks_unsave, title),
        onClick = { onUnsaveClick(artwork) },
        // A plain white bookmark, without a backdrop, as in the V2 mockup.
        containerColor = Color.Transparent,
        contentColor = ArtistaColors.OnSavedArtworkTile,
        modifier =
            Modifier.align(Alignment.TopStart).testTag(SavedArtworksTestTags.unsaveButton(artwork)),
    )

    // Hidden rather than disabled: a greyed-out icon on top of an image is hard to see and looks
    // like a bug.
    if (artwork.location != null) {
      SavedArtworkTileButton(
          icon = Icons.Outlined.LocationOn,
          contentDescription = stringResource(R.string.saved_artworks_show_on_map, title),
          onClick = { onMapClick(artwork) },
          // Translucent pin matching the Figma map button shared with the overview screen.
          containerColor = ArtistaColors.SavedArtworkTileMapButtonScrim,
          contentColor = ArtistaColors.OnSavedArtworkTile,
          modifier =
              Modifier.align(Alignment.TopEnd).testTag(SavedArtworksTestTags.mapButton(artwork)),
      )
    }

    Column(
        modifier =
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(ArtistaColors.SavedArtworkTileBand)
                .padding(
                    horizontal = Dimensions.savedArtworkTileBandHorizontalPadding,
                    vertical = Dimensions.savedArtworkTileBandVerticalPadding,
                )
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = ArtistaColors.OnSavedArtworkTile,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(SavedArtworksTestTags.title(artwork)),
      )
      Text(
          text = artwork.artist?.name ?: stringResource(R.string.artwork_unknown_artist),
          style = MaterialTheme.typography.bodySmall,
          color = ArtistaColors.OnSavedArtworkTile,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(SavedArtworksTestTags.artist(artwork)),
      )
    }
  }
}

/**
 * Displays a small round icon button drawn over a saved artwork tile's image.
 *
 * @param icon icon shown in the button
 * @param contentDescription description of the action for accessibility services
 * @param onClick called when the user taps the button
 * @param containerColor color of the circle behind the icon
 * @param contentColor color of the icon
 * @param modifier modifier applied to the button
 * @author Timz3rr
 */
@Composable
private fun SavedArtworkTileButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier
              .padding(Dimensions.savedArtworkTileButtonPadding)
              .size(Dimensions.savedArtworkTileButtonSize),
      colors =
          IconButtonDefaults.iconButtonColors(
              containerColor = containerColor,
              contentColor = contentColor,
          ),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(Dimensions.savedArtworkTileIconSize),
    )
  }
}
