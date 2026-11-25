// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.ui.theme.Dimensions
import com.artista.artista.ui.theme.OverviewPalette

/**
 * Provides stable semantics test tags for [ArtworkListCard].
 *
 * Tags contain the artwork uid so that tests can target one card among many.
 *
 * @author hixeum
 */
object ArtworkListCardTestTags {
  /** Tag of the clickable card showing [artwork]. */
  fun card(artwork: Artwork): String = "artwork_list_card_${artwork.uid}"

  /** Tag of the artist text in the header of [artwork]'s card. */
  fun artist(artwork: Artwork): String = "artwork_list_card_artist_${artwork.uid}"

  /** Tag of the title text of [artwork]'s card. */
  fun title(artwork: Artwork): String = "artwork_list_card_title_${artwork.uid}"

  /** Tag of the museum or place text of [artwork]'s card. */
  fun place(artwork: Artwork): String = "artwork_list_card_place_${artwork.uid}"

  /** Tag of the bookmark button that saves or unsaves [artwork]. */
  fun saveButton(artwork: Artwork): String = "artwork_list_card_save_button_${artwork.uid}"

  /** Tag of the map button of [artwork]'s card. */
  fun mapButton(artwork: Artwork): String = "artwork_list_card_map_button_${artwork.uid}"

  /** Tag of the image of [artwork]'s card. */
  fun image(artwork: Artwork): String = "artwork_list_card_image_${artwork.uid}"

  /** Tag of the icon shown instead of [artwork]'s image while it is missing or loading. */
  fun imageFallback(artwork: Artwork): String = "artwork_list_card_image_fallback_${artwork.uid}"
}

/**
 * Displays an artwork as a full-width list card: a header with its artist, its image, and a footer
 * with its title, map and bookmark actions, and the museum or place where it can be seen.
 *
 * @param artwork artwork shown by the card
 * @param isSaved whether [artwork] is currently saved, which selects the bookmark icon and its
 *   description
 * @param onClick called with [artwork] when the user taps the card to open its details
 * @param onSaveClick called with [artwork] when the user taps the bookmark to save or unsave it
 * @param onMapClick called with [artwork] when the user taps the map button
 * @param modifier modifier applied to the card; a test tag set here replaces
 *   [ArtworkListCardTestTags.card]
 * @author hixeum
 */
@Composable
fun ArtworkListCard(
    artwork: Artwork,
    isSaved: Boolean,
    onClick: (Artwork) -> Unit,
    onSaveClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The title also names the artwork in the image and button descriptions, so they always match.
  val title = artwork.name ?: stringResource(R.string.artwork_unknown_title)
  val place = artwork.museum ?: artwork.location?.name

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(Dimensions.artworkListCardCornerRadius))
              .background(OverviewPalette.container)
              .clickable { onClick(artwork) }
              .testTag(ArtworkListCardTestTags.card(artwork))
  ) {
    ArtworkListCardHeader(artwork = artwork)

    // Crop keeps every card of the list the same height whatever the painting's proportions.
    ArtworkImage(
        imageUrl = artwork.imageUrl,
        contentDescription = stringResource(R.string.artwork_card_image_description, title),
        contentScale = ContentScale.Crop,
        imageTestTag = ArtworkListCardTestTags.image(artwork),
        fallbackTestTag = ArtworkListCardTestTags.imageFallback(artwork),
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(Dimensions.ARTWORK_LIST_CARD_IMAGE_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = Dimensions.artworkListCardFooterHorizontalPadding,
                    vertical = Dimensions.artworkListCardFooterVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(Dimensions.artworkListCardFooterTextSpacing),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = OverviewPalette.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(ArtworkListCardTestTags.title(artwork)),
        )
        // Hidden rather than disabled, like on the artwork tiles.
        if (artwork.location != null) {
          IconButton(
              onClick = { onMapClick(artwork) },
              modifier = Modifier.testTag(ArtworkListCardTestTags.mapButton(artwork)),
          ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = stringResource(R.string.artwork_show_on_map, title),
                tint = OverviewPalette.content,
            )
          }
        }
        IconButton(
            onClick = { onSaveClick(artwork) },
            modifier = Modifier.testTag(ArtworkListCardTestTags.saveButton(artwork)),
        ) {
          Icon(
              imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription =
                  stringResource(
                      if (isSaved) R.string.artwork_unsave else R.string.artwork_save,
                      title,
                  ),
              tint = OverviewPalette.content,
          )
        }
      }
      if (place != null) {
        Text(
            text = place,
            style = MaterialTheme.typography.bodyLarge,
            color = OverviewPalette.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(ArtworkListCardTestTags.place(artwork)),
        )
      }
    }
  }
}

/**
 * Displays the header of an artwork list card: an avatar placeholder and the artist's name.
 *
 * @param artwork artwork whose artist is shown
 * @author hixeum
 */
@Composable
private fun ArtworkListCardHeader(artwork: Artwork) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(
                  horizontal = Dimensions.artworkListCardHeaderHorizontalPadding,
                  vertical = Dimensions.artworkListCardHeaderVerticalPadding,
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Dimensions.artworkListCardHeaderSpacing),
  ) {
    // Artists have no picture yet, so the mockup's generic person icon stands in for one.
    Box(
        modifier =
            Modifier.size(Dimensions.artworkListCardAvatarSize)
                .clip(CircleShape)
                .background(OverviewPalette.avatarContainer),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Outlined.Person,
          contentDescription = null,
          tint = OverviewPalette.content,
          modifier = Modifier.size(Dimensions.artworkListCardAvatarIconSize),
      )
    }
    Text(
        text = artwork.artist?.name ?: stringResource(R.string.artwork_unknown_artist),
        style = MaterialTheme.typography.headlineSmall,
        color = OverviewPalette.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(ArtworkListCardTestTags.artist(artwork)),
    )
  }
}
