// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.resources

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

// Like R, but C
object C {
  object Tag {
    const val auth_screen_container = "auth_screen_container"
    const val auth_hero_image = "auth_hero_image"
    const val auth_google_sign_in_button = "auth_google_sign_in_button"
    const val auth_loading_indicator = "auth_loading_indicator"
    const val auth_error_message = "auth_error_message"
  }

  object Constants {
    /**
     * Route of the nested graph holding the Overview tab and the screens opened from it.
     *
     * Named rather than repeated as a literal because it is both the route of that graph and the
     * destination the signed-in application starts on.
     */
    const val OVERVIEW_GRAPH_ROUTE = "overview_graph"

    /** Route of the nested graph holding the Map tab. */
    const val MAP_GRAPH_ROUTE = "map_graph"

    /** Route of the nested graph holding the Profile tab and the screens opened from it. */
    const val PROFILE_GRAPH_ROUTE = "profile_graph"

    const val DEFAULT_MAP_ZOOM = 12f
    const val DEFAULT_MAP_LAT = 46.5197
    const val DEFAULT_MAP_LONG = 6.6323
    /** Firestore subcollection containing a user's saved artworks. */
    const val SAVED_ARTWORKS_COLLECTION_PATH = "saved_artworks"
    val DEFAULT_MAP_POSITION =
        CameraPosition.fromLatLngZoom(LatLng(DEFAULT_MAP_LAT, DEFAULT_MAP_LONG), DEFAULT_MAP_ZOOM)
  }
}
