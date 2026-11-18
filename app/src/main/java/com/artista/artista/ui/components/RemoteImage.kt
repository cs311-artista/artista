// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
package com.artista.artista.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * Displays an image downloaded from a link, over a fallback drawn by the caller.
 *
 * The fallback is stacked on top of the image and shown while it loads, when the download fails,
 * and when there is no link: in each of those cases the user has nothing to look at yet, and an
 * empty area would look broken. Nothing is requested over the network without a link, and the image
 * is described to accessibility services only once it has loaded, so the description never
 * announces something that is not on screen.
 *
 * Every screen showing a downloaded image goes through here, so those three rules are written once
 * instead of once per screen: a fix or a new Coil behaviour reaches all of them together.
 *
 * The rules were extracted from [com.artista.artista.ui.artwork.ArtworkImage], which keeps its icon
 * as a fallback: the loading logic is unchanged, only the fallback became a parameter.
 *
 * @param imageUrl link of the image to download, or null when there is no image to show
 * @param contentDescription description of the image for accessibility services, applied only once
 *   the image has loaded
 * @param contentScale how the image is scaled inside the available area
 * @param imageTestTag test tag applied to the image
 * @param fallbackTestTag test tag applied to the fallback
 * @param modifier modifier applied to the container, which owns the size, the shape and the
 *   background of the area
 * @param fallback draws what the user sees while there is no image, given the modifier carrying
 *   [fallbackTestTag]
 * @author Timz3rr, timo-by
 */
@Composable
fun RemoteImage(
    imageUrl: String?,
    contentDescription: String,
    contentScale: ContentScale,
    imageTestTag: String,
    fallbackTestTag: String,
    modifier: Modifier = Modifier,
    fallback: @Composable (Modifier) -> Unit,
) {
  // Reset when the link changes, so a recycled card or the next user's profile never keeps the
  // previous image's state: the fallback has to come back while the new image downloads.
  var isImageLoaded by remember(imageUrl) { mutableStateOf(false) }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    // Without a link nothing is requested over the network: the fallback below stays alone.
    if (imageUrl != null) {
      AsyncImage(
          model = imageUrl,
          // Described only once loaded: while loading or after a failed download the user sees the
          // fallback, so announcing the image would describe something that is not shown.
          contentDescription = if (isImageLoaded) contentDescription else null,
          modifier = Modifier.fillMaxSize().testTag(imageTestTag),
          contentScale = contentScale,
          onState = { state -> isImageLoaded = state is AsyncImagePainter.State.Success },
      )
    }
    if (!isImageLoaded) {
      fallback(Modifier.testTag(fallbackTestTag))
    }
  }
}
