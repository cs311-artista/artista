// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.ui.artwork.ArtworkImage
import com.artista.artista.ui.theme.Dimensions

/**
 * Colors of an [ArtistCard] in its selected and unselected states.
 *
 * @property selectedContainerColor background of the card while the artist is selected
 * @property unselectedContainerColor background of the card while the artist is not selected
 * @property contentColor color of the artist's name
 * @property imagePlaceholderColor background shown behind the picture while it loads or is missing
 * @property selectedIndicatorColor fill of the corner indicator while the artist is selected
 * @property unselectedIndicatorColor fill of the corner indicator while the artist is not selected
 * @property checkmarkColor color of the check drawn in the indicator of a selected artist
 * @author krfpn
 */
@Immutable
data class ArtistCardColors(
    val selectedContainerColor: Color,
    val unselectedContainerColor: Color,
    val contentColor: Color,
    val imagePlaceholderColor: Color,
    val selectedIndicatorColor: Color,
    val unselectedIndicatorColor: Color,
    val checkmarkColor: Color,
)

/** Default values used by [ArtistCard]. */
object ArtistCardDefaults {

  /**
   * Returns the V2 colors of an artist card, taken from the theme so they follow light and dark
   * mode. Any color can be overridden, the others keep their default.
   *
   * @param selectedContainerColor background of the card while the artist is selected
   * @param unselectedContainerColor background of the card while the artist is not selected; dimmer
   *   than the selected one, since the V2 card has no border to show the state
   * @param contentColor color of the artist's name
   * @param imagePlaceholderColor background shown behind the picture while it loads or is missing
   * @param selectedIndicatorColor fill of the corner indicator while the artist is selected
   * @param unselectedIndicatorColor fill of the corner indicator while the artist is not selected
   * @param checkmarkColor color of the check drawn in the indicator of a selected artist
   * @return the colors to pass to [ArtistCard]
   * @author krfpn
   */
  @Composable
  fun colors(
      selectedContainerColor: Color = MaterialTheme.colorScheme.surface,
      unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
      contentColor: Color = MaterialTheme.colorScheme.onSurface,
      imagePlaceholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
      selectedIndicatorColor: Color = MaterialTheme.colorScheme.primary,
      unselectedIndicatorColor: Color = MaterialTheme.colorScheme.surface,
      checkmarkColor: Color = MaterialTheme.colorScheme.onPrimary,
  ): ArtistCardColors =
      ArtistCardColors(
          selectedContainerColor = selectedContainerColor,
          unselectedContainerColor = unselectedContainerColor,
          contentColor = contentColor,
          imagePlaceholderColor = imagePlaceholderColor,
          selectedIndicatorColor = selectedIndicatorColor,
          unselectedIndicatorColor = unselectedIndicatorColor,
          checkmarkColor = checkmarkColor,
      )
}

/**
 * Card showing an artist's picture and name, which the user clicks to select or unselect the
 * artist. It is meant to be placed in a grid, e.g. the artist preferences or the artist search
 * results.
 *
 * The card is a checkbox for accessibility services and tests (`assertIsOn` / `assertIsOff`), and
 * shows its state with its container color and a check in its top-right corner.
 *
 * The test tags are parameters so that each screen keeps its own tags, as for [ArtworkImage].
 *
 * @param artist artist displayed by the card
 * @param selected whether the artist is currently selected
 * @param onToggle called when the card is clicked, to select or unselect the artist
 * @param cardTestTag test tag applied to the card
 * @param imageTestTag test tag applied to the artist's picture
 * @param fallbackTestTag test tag applied to the icon shown while the picture is missing or loading
 * @param modifier modifier applied to the card
 * @param colors colors of the card, see [ArtistCardDefaults.colors]
 * @author Timz3rr, krfpn
 */
@Composable
fun ArtistCard(
    artist: Artist,
    selected: Boolean,
    onToggle: () -> Unit,
    cardTestTag: String,
    imageTestTag: String,
    fallbackTestTag: String,
    modifier: Modifier = Modifier,
    colors: ArtistCardColors = ArtistCardDefaults.colors(),
) {
  // A checkable Surface, unlike a clickable one, exposes the on/off state used by assertIsOn.
  Surface(
      checked = selected,
      onCheckedChange = { onToggle() },
      modifier =
          modifier
              .fillMaxWidth()
              // Lets accessibility services announce the card as a checkbox with its state.
              .semantics { role = Role.Checkbox }
              .testTag(cardTestTag),
      shape = RoundedCornerShape(Dimensions.cornerRadiusMedium),
      color = if (selected) colors.selectedContainerColor else colors.unselectedContainerColor,
      contentColor = colors.contentColor,
  ) {
    Column(
        modifier = Modifier.padding(Dimensions.artistCardContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box {
        // Reuses the artwork image, which already handles loading, failed downloads and missing
        // links by showing a fallback icon, so artist pictures behave like artwork pictures.
        ArtworkImage(
            imageUrl = artist.imageUrl,
            contentDescription =
                stringResource(R.string.artist_card_picture_description, artist.name),
            contentScale = ContentScale.Crop,
            imageTestTag = imageTestTag,
            fallbackTestTag = fallbackTestTag,
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall))
                    .background(colors.imagePlaceholderColor),
        )
        SelectionIndicator(
            selected = selected,
            colors = colors,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(Dimensions.artistCardSelectionIndicatorPadding),
        )
      }
      Text(
          text = artist.name,
          modifier = Modifier.padding(top = Dimensions.artistCardNameTopSpacing),
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          // Two lines for every name keeps all the cards of a row at the same height.
          minLines = 2,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Round indicator in the corner of an artist card, checked when the artist is selected.
 *
 * @param selected whether the artist is currently selected
 * @param colors colors of the card the indicator belongs to
 * @param modifier modifier applied to the indicator
 */
@Composable
private fun SelectionIndicator(
    selected: Boolean,
    colors: ArtistCardColors,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .size(Dimensions.iconSizeLarge)
              .clip(CircleShape)
              .background(
                  if (selected) colors.selectedIndicatorColor else colors.unselectedIndicatorColor
              ),
      contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          modifier = Modifier.size(Dimensions.iconSizeSmall),
          tint = colors.checkmarkColor,
      )
    }
  }
}
