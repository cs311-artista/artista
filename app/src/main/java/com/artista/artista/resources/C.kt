package com.artista.artista.resources

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

// Like R, but C
object C {
  object Tag {
    const val greeting = "main_screen_greeting"
    const val greeting_robo = "second_screen_greeting"

    const val main_screen_container = "main_screen_container"
    const val second_screen_container = "second_screen_container"

    const val auth_screen_container = "auth_screen_container"
    const val auth_hero_image = "auth_hero_image"
    const val auth_google_sign_in_button = "auth_google_sign_in_button"
    const val auth_loading_indicator = "auth_loading_indicator"
    const val auth_error_snackbar = "auth_error_snackbar"
  }

  object Constants {
    const val DEFAULT_MAP_ZOOM = 12f
    const val DEFAULT_MAP_LAT = 46.5197
    const val DEFAULT_MAP_LONG = 6.6323
    val DEFAULT_MAP_POSITION =
        CameraPosition.fromLatLngZoom(LatLng(DEFAULT_MAP_LAT, DEFAULT_MAP_LONG), DEFAULT_MAP_ZOOM)
  }
}
