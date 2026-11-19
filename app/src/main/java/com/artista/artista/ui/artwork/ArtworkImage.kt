// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
package com.artista.artista.ui.artwork

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.artista.artista.ui.components.RemoteImage
import com.artista.artista.ui.theme.Dimensions

/**
 * Displays an artwork image, with a fallback icon while it loads, if the download fails, or if
 * there is no image.
 *
 * @param imageUrl link of the image to download, or null if the artwork has no image
 * @param contentDescription description of the image for accessibility services, applied only once
 *   the image has loaded
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
  // An icon stands in for the artwork; the profile passes its own text to the same component.
  RemoteImage(
      imageUrl = imageUrl,
      contentDescription = contentDescription,
      contentScale = contentScale,
      imageTestTag = imageTestTag,
      fallbackTestTag = fallbackTestTag,
      modifier = modifier,
      fallback = { fallbackModifier ->
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = fallbackModifier.size(Dimensions.artworkImageFallbackIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
  )
}
