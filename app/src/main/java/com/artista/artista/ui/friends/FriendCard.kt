// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.artista.artista.R
import com.artista.artista.ui.components.RemoteImage
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import kotlin.math.sqrt

/**
 * Outlined card showing a user's profile picture and name, used wherever the app lists other users,
 * such as friends and friend search results.
 *
 * An optional [action], typically an add or remove button, is centered on the card's border at its
 * rounded top-end corner, half outside the card. Room for that slot is always kept, with or without
 * an action, so that cards in a list stay aligned whichever of them carries a button.
 *
 * The card has no outer margin of its own: a list places it with
 * [Dimensions.friendCardListHorizontalPadding], so that it is narrower than the search bar.
 *
 * @param name the user's display name, or null when they have not chosen one; a blank or missing
 *   name is replaced by a generic label.
 * @param photoUrl link of the user's profile picture, or null to show the placeholder.
 * @param modifier modifier applied to the card, e.g. to give each card of a list its own test tag.
 * @param action content of the corner slot, sized [Dimensions.friendCardActionSize], or null for no
 *   action. Its icon should fill the slot, e.g. with `Modifier.fillMaxSize()`, since an icon keeps
 *   its default 24dp otherwise.
 * @author hixeum
 */
@Composable
fun FriendCard(
    name: String?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
  // A blank name carries no information, as on the profile screen.
  val displayedName =
      name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.friend_card_unnamed)
  val actionOverhang = actionSlotOverhang()

  Box(modifier = modifier.fillMaxWidth()) {
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = actionOverhang, end = actionOverhang)
                // Picture and name are read as one item; the action stays a separate button.
                .semantics(mergeDescendants = true) {}
                .testTag(FriendCardTestTags.CARD),
        shape = RoundedCornerShape(Dimensions.cornerRadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        // Outlined as in the mocks, so each user stands out as a separate item in the list.
        border = BorderStroke(Dimensions.standardStrokeWidth, MaterialTheme.colorScheme.outline),
    ) {
      Row(
          modifier = Modifier.padding(Dimensions.friendCardContentPadding),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        RemoteImage(
            imageUrl = photoUrl,
            contentDescription =
                stringResource(R.string.friend_card_picture_description, displayedName),
            contentScale = ContentScale.Crop,
            imageTestTag = FriendCardTestTags.PICTURE,
            fallbackTestTag = FriendCardTestTags.PICTURE_PLACEHOLDER,
            modifier =
                Modifier.size(Dimensions.friendCardPictureSize)
                    // Clipped before the background so the placeholder fills the circle.
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            fallback = { fallbackModifier ->
              // The name next to it already identifies the user, so the icon is not announced.
              Icon(
                  imageVector = Icons.Outlined.Person,
                  contentDescription = null,
                  modifier = fallbackModifier,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
        )
        Spacer(modifier = Modifier.width(Dimensions.friendCardPictureNameSpacing))
        Text(
            text = displayedName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (action != null) {
      // A button bigger than the slot, such as an IconButton with its 48dp touch target, is still
      // drawn at the slot size; its touch area extends beyond it, so it stays easy to tap.
      Box(
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .size(Dimensions.friendCardActionSize)
                  // Opaque backdrop in the card's color: icons with see-through parts, such as the
                  // plus cut out of AddCircle, would otherwise let the border show through them.
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface)
                  .testTag(FriendCardTestTags.ACTION),
          contentAlignment = Alignment.Center,
      ) {
        action()
      }
    }
  }
}

/**
 * How far the action slot sticks out above and beyond the card, so that its center lies on the
 * card's border, halfway along the rounded top-end corner.
 *
 * The corner is a quarter circle, so its middle is not the corner of the card's bounding box but
 * lies inside it, on the diagonal. The border is drawn inside the shape, so the line the slot is
 * centered on is a quarter circle of radius `r - w / 2` around the point `(r, r)` from the corner,
 * where `r` is the corner radius and `w` the border width. Its middle is at `r - (r - w / 2) / √2`
 * from the corner along each axis, about 6.6dp with the current values.
 *
 * @return the distance by which the slot exceeds the card's top and end edges
 */
internal fun actionSlotOverhang(): Dp {
  val radius = Dimensions.cornerRadiusMedium
  val borderMiddleRadius = radius - Dimensions.standardStrokeWidth / 2
  val insetFromCorner = radius - borderMiddleRadius / sqrt(2f)
  return Dimensions.friendCardActionSize / 2 - insetFromCorner
}

/**
 * Provides stable semantics test tags for the parts of a [FriendCard].
 *
 * Every card of a list uses the same tags; tests tell cards apart by the tag the screen applies
 * through the card's modifier, or by the user's name.
 *
 * @author hixeum
 */
object FriendCardTestTags {
  const val CARD = "friend_card"
  const val PICTURE = "friend_card_picture"
  const val PICTURE_PLACEHOLDER = "friend_card_picture_placeholder"
  const val ACTION = "friend_card_action"
}

/** Previews a friend card with an action and one without, as they appear in a list. */
@Preview(showBackground = true)
@Composable
internal fun FriendCardPreview() {
  ArtistaTheme {
    Column(modifier = Modifier.padding(horizontal = Dimensions.friendCardListHorizontalPadding)) {
      FriendCard(
          name = "Ivan Shishkin",
          photoUrl = null,
          action = {
            IconButton(onClick = {}) {
              Icon(
                  imageVector = Icons.Filled.AddCircle,
                  contentDescription = null,
                  modifier = Modifier.fillMaxSize(),
                  tint = MaterialTheme.colorScheme.primary,
              )
            }
          },
      )
      FriendCard(
        name = "Claude Monet",
        photoUrl = null,
        action = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.RemoveCircle,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
      FriendCard(name = "Berthe Morisot", photoUrl = null)
    }
  }
}
