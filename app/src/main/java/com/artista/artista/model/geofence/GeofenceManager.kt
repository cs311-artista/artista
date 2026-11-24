// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.resources.C
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlin.jvm.java
import kotlinx.coroutines.tasks.await

class GeofenceManager(private val context: Context) {
  companion object {
    private const val MILLIS_PER_MINUTE = 60_000
    private const val LOITERING_MINUTES = 2
    private const val MAX_NOTIFIABLE_ARTWORKS = 100
  }

  private val client = LocationServices.getGeofencingClient(context)

  private val pendingIntent: PendingIntent by lazy {
    // Must be explicit (Android 14) and mutable (Android 12+)
    val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
    val flags =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    PendingIntent.getBroadcast(context, 0, intent, flags)
  }

  /** Replaces all currently registered geofences with the given artworks. */
  @SuppressLint("MissingPermission") // caller guarantees permissions
  suspend fun register(artworks: List<Artwork>) {
    if (artworks.isEmpty()) {
      client.removeGeofences(pendingIntent).await()
      return
    }

    val geofences =
        artworks.take(MAX_NOTIFIABLE_ARTWORKS).map { artwork ->
          artwork.location?.let {
            Geofence.Builder()
                .setRequestId(artwork.uid)
                .setCircularRegion(
                    it.latitude,
                    it.longitude,
                    C.Geofence.GEOFENCE_RADIUS,
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(
                    LOITERING_MINUTES * MILLIS_PER_MINUTE
                ) // todo extract loitering delay (user preferences?)
                .build()
          }
        }

    val request =
        GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences.filterNotNull())
            .build()

    client.removeGeofences(pendingIntent).await() // remove old geofences before adding new ones
    client.addGeofences(request, pendingIntent).await()
  }

  suspend fun unregisterAll(): Result<Unit> = runCatching {
    client.removeGeofences(pendingIntent).await()
  }
}
