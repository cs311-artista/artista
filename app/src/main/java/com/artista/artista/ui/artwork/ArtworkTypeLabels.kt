// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.annotation.StringRes
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkType

/**
 * Returns the string resource holding the display name of this artwork type.
 *
 * It lives in the UI layer rather than on [ArtworkType] so the model does not depend on Android
 * resources, and it is shared so every screen names the types the same way.
 *
 * @return the string resource of the type's display name
 * @author IJJA3141, hixeum
 */
@StringRes
fun ArtworkType.displayNameRes(): Int =
    when (this) {
      ArtworkType.PAINTING -> R.string.artwork_type_painting
      ArtworkType.DRAWING -> R.string.artwork_type_drawing
      ArtworkType.SCULPTURE -> R.string.artwork_type_sculpture
      ArtworkType.PRINT -> R.string.artwork_type_print
      ArtworkType.PHOTOGRAPH -> R.string.artwork_type_photograph
      ArtworkType.MURAL -> R.string.artwork_type_mural
      ArtworkType.OIL_PAINTING -> R.string.artwork_type_oil_painting
      ArtworkType.WATERCOLOR_PAINTING -> R.string.artwork_type_watercolor_painting
      ArtworkType.MOSAIC -> R.string.artwork_type_mosaic
    }
