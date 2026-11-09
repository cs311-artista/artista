// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.artista.artista.ui.theme.Dimensions

/**
 * Displays an artwork image, with a fallback icon while it loads, if the download fails, or if
 * there is no image.
 *
 * @param imageUrl link of the image to download, or null if the artwork has no image
 * @param contentDescription description of the image for accessibility services
 * @param contentScale how the image is scaled inside the available area
 * @param imageTestTag test tag applied to the image
 * @param fallbackTestTag test tag applied to the fallback icon
 * @param modifier modifier applied to the container
 * @author Timz3rr
 */
@Composable
fun ArtworkImage(
    imageUrl: String?,
    contentDescription: String,
    contentScale: ContentScale,
    imageTestTag: String,
    fallbackTestTag: String,
    modifier: Modifier = Modifier,
) {
  // Reset when the link changes, so a recycled card never keeps the previous artwork's state.
  var isImageLoaded by remember(imageUrl) { mutableStateOf(false) }

  Box(
      modifier = modifier,
      contentAlignment = Alignment.Center,
  ) {
    // Case 1: the artwork has an image link. Coil downloads it and reports each state change, so
    // the fallback icon below can hide once the image is ready. Without a link, nothing is
    // requested.
    if (imageUrl != null) {
      AsyncImage(
          model = imageUrl,
          contentDescription = contentDescription,
          modifier = Modifier.fillMaxSize().testTag(imageTestTag),
          contentScale = contentScale,
          onState = { state -> isImageLoaded = state is AsyncImagePainter.State.Success },
      )
    }
    // Case 2: no image to show yet. The Box stacks this icon over the image area, and it covers
    // loading, failed downloads and missing links alike: in every case the user has nothing to
    // look at yet, and an empty grey box would look broken.
    if (!isImageLoaded) {
      Icon(
          imageVector = Icons.Outlined.Image,
          contentDescription = null,
          modifier =
              Modifier.size(Dimensions.artworkImageFallbackIconSize).testTag(fallbackTestTag),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
