package com.artista.artista.ui.shared

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.artista.artista.ui.theme.ArtistaColors
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker

/**
 * Identifies the artwork source represented by a map marker.
 *
 * @property color ARGB color used to render the marker icon.
 * @author 5kyPhy
 */
enum class MarkerType(val color: Int) {
  SavedArtwork(ArtistaColors.SavedArtworkMarker.toArgb()),
  RemoteArtwork(ArtistaColors.RemoteArtworkMarker.toArgb()),
}

/** Provides a map-compatible pin icon for artwork markers. */
object ArtworkMarker {
  private val DEFAULT_MARKER_PATH =
      Path().apply {
        moveTo(24f, 64f)
        cubicTo(20f, 56f, 4f, 42f, 4f, 24f)
        cubicTo(4f, 11f, 13f, 2f, 24f, 2f)
        cubicTo(35f, 2f, 44f, 11f, 44f, 24f)
        cubicTo(44f, 42f, 28f, 56f, 24f, 64f)
        close()
      }
  private val SAVED_ARTWORK_BITMAP =
      createBitmap(48, 64).also { bitmap ->
        Canvas(bitmap).apply {
          val paint =
              Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = MarkerType.SavedArtwork.color }
          // Draw the pin manually so its color comes from the shared Compose palette.
          drawPath(DEFAULT_MARKER_PATH, paint)
          drawCircle(
              24f,
              24f,
              8f,
              Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE },
          )
        }
      }
  private val DEFAULT_ARTWORK_MARKER_BITMAP =
      createBitmap(48, 64).also { bitmap ->
        Canvas(bitmap).apply {
          val paint =
              Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = MarkerType.RemoteArtwork.color }
          // Draw the pin manually so its color comes from the shared Compose palette.
          drawPath(DEFAULT_MARKER_PATH, paint)
          drawCircle(
              24f,
              24f,
              8f,
              Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE },
          )
        }
      }

  /**
   * Creates a map-compatible pin icon using the supplied centralized artwork color.
   *
   * Google Maps requires a [com.google.android.gms.maps.model.BitmapDescriptor] for marker icons,
   * so the Compose color cannot be passed directly to [Marker].
   *
   * @param markerType The type of marker to create.
   * @return bitmap descriptor containing a colored pin with a white center.
   * @author 5kyPhy
   */
  fun artworkMarkerIcon(markerType: MarkerType = MarkerType.RemoteArtwork) =
      when (markerType) {
        MarkerType.SavedArtwork -> BitmapDescriptorFactory.fromBitmap(SAVED_ARTWORK_BITMAP)
        MarkerType.RemoteArtwork ->
            BitmapDescriptorFactory.fromBitmap(DEFAULT_ARTWORK_MARKER_BITMAP)
      }
}
