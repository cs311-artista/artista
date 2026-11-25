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
import androidx.compose.material.icons.outlined.BookmarkBorder
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
 * Provides stable semantics test tags for [ArtworkTile].
 *
 * Tags contain the artwork uid so that tests can target one tile among many.
 *
 * @author Timz3rr, hixeum
 */
object ArtworkTileTestTags {
  /** Start of every bookmark button tag, to match a tile's button without knowing its artwork. */
  const val SAVE_BUTTON_PREFIX = "artwork_tile_save_button_"

  /** Start of every map button tag, to match a tile's button without knowing its artwork. */
  const val MAP_BUTTON_PREFIX = "artwork_tile_map_button_"

  /** Tag of the clickable tile showing [artwork]. */
  fun tile(artwork: Artwork): String = "artwork_tile_${artwork.uid}"

  /** Tag of the bookmark button that saves or unsaves [artwork]. */
  fun saveButton(artwork: Artwork): String = "$SAVE_BUTTON_PREFIX${artwork.uid}"

  /** Tag of the map button of [artwork]'s tile. */
  fun mapButton(artwork: Artwork): String = "$MAP_BUTTON_PREFIX${artwork.uid}"

  /** Tag of the title text of [artwork]'s tile. */
  fun title(artwork: Artwork): String = "artwork_tile_title_${artwork.uid}"

  /** Tag of the artist text of [artwork]'s tile. */
  fun artist(artwork: Artwork): String = "artwork_tile_artist_${artwork.uid}"

  /** Tag of the image of [artwork]'s tile. */
  fun image(artwork: Artwork): String = "artwork_tile_image_${artwork.uid}"

  /** Tag of the icon shown instead of [artwork]'s image while it is missing or loading. */
  fun imageFallback(artwork: Artwork): String = "artwork_tile_image_fallback_${artwork.uid}"
}

/**
 * Displays an artwork as a grid tile: its image, a bookmark button, a map button when the artwork
 * has a location, and its title and artist.
 *
 * Shared by every screen that shows artworks as a grid, such as the saved artworks and the overview
 * screens.
 *
 * @param artwork artwork shown by the tile
 * @param isSaved whether [artwork] is currently saved, which selects the bookmark icon and its
 *   description
 * @param onClick called with [artwork] when the user taps the tile to open its details
 * @param onSaveClick called with [artwork] when the user taps the bookmark to save or unsave it
 * @param onMapClick called with [artwork] when the user taps the map button
 * @param modifier modifier applied to the tile; a test tag set here replaces
 *   [ArtworkTileTestTags.tile]
 * @author Timz3rr, hixeum
 */
@Composable
fun ArtworkTile(
    artwork: Artwork,
    isSaved: Boolean,
    onClick: (Artwork) -> Unit,
    onSaveClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The title also names the artwork in the image and button descriptions, so they always match.
  val title = artwork.name ?: stringResource(R.string.artwork_unknown_title)

  Box(
      modifier =
          modifier
              .aspectRatio(Dimensions.ARTWORK_TILE_ASPECT_RATIO)
              .clip(RoundedCornerShape(Dimensions.artworkTileCornerRadius))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onClick(artwork) }
              .testTag(ArtworkTileTestTags.tile(artwork))
  ) {
    // Crop fills the whole tile: paintings come in many aspect ratios, and the grid should stay
    // even.
    ArtworkImage(
        imageUrl = artwork.imageUrl,
        contentDescription = stringResource(R.string.artwork_card_image_description, title),
        contentScale = ContentScale.Crop,
        imageTestTag = ArtworkTileTestTags.image(artwork),
        fallbackTestTag = ArtworkTileTestTags.imageFallback(artwork),
        modifier = Modifier.fillMaxSize(),
    )

    // Same translucent circle as the map button: a bare white bookmark disappears on light images
    // and on the no-image fallback.
    ArtworkTileButton(
        icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription =
            stringResource(if (isSaved) R.string.artwork_unsave else R.string.artwork_save, title),
        onClick = { onSaveClick(artwork) },
        modifier =
            Modifier.align(Alignment.TopStart).testTag(ArtworkTileTestTags.saveButton(artwork)),
    )

    // Hidden rather than disabled: a greyed-out icon on top of an image is hard to see and looks
    // like a bug.
    if (artwork.location != null) {
      ArtworkTileButton(
          icon = Icons.Outlined.LocationOn,
          contentDescription = stringResource(R.string.artwork_show_on_map, title),
          onClick = { onMapClick(artwork) },
          modifier =
              Modifier.align(Alignment.TopEnd).testTag(ArtworkTileTestTags.mapButton(artwork)),
      )
    }

    Column(
        modifier =
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(ArtistaColors.ArtworkTileBand)
                .padding(
                    horizontal = Dimensions.artworkTileBandHorizontalPadding,
                    vertical = Dimensions.artworkTileBandVerticalPadding,
                )
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = ArtistaColors.OnArtworkTile,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(ArtworkTileTestTags.title(artwork)),
      )
      Text(
          text = artwork.artist?.name ?: stringResource(R.string.artwork_unknown_artist),
          style = MaterialTheme.typography.bodySmall,
          color = ArtistaColors.OnArtworkTile,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(ArtworkTileTestTags.artist(artwork)),
      )
    }
  }
}

/**
 * Displays a small white icon in a translucent circle, drawn over an artwork tile's image.
 *
 * @param icon icon shown in the button
 * @param contentDescription description of the action for accessibility services
 * @param onClick called when the user taps the button
 * @param modifier modifier applied to the button
 * @author Timz3rr
 */
@Composable
private fun ArtworkTileButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier
              .padding(Dimensions.artworkTileButtonPadding)
              .size(Dimensions.artworkTileButtonSize),
      colors =
          IconButtonDefaults.iconButtonColors(
              containerColor = ArtistaColors.ArtworkTileButtonScrim,
              contentColor = ArtistaColors.OnArtworkTile,
          ),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(Dimensions.artworkTileIconSize),
    )
  }
}
