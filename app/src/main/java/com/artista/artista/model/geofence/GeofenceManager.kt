// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: qwen3.5
package com.artista.artista.model.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.error.AppException
import com.artista.artista.resources.C
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlin.jvm.java
import kotlinx.coroutines.tasks.await

/**
 * Manages geofences for artworks in the application.
 *
 * This class provides functionality to register and unregister geofences for a list of artworks. It
 * uses the Geofencing API to monitor the user's proximity to the artworks and triggers
 * notifications when the user loiters within a specified region.
 */
class GeofenceManager(
    private val context: Context,
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context),
) {
  companion object {
    private const val MILLIS_PER_MINUTE = 60_000
    private const val LOITERING_MINUTES = 2
    private const val MAX_NOTIFIABLE_ARTWORKS = 100
  }

  private val pendingIntent: PendingIntent by lazy {
    // Must be explicit (Android 14) and mutable (Android 12+)
    val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
    val flags =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    PendingIntent.getBroadcast(context, 0, intent, flags)
  }

  /**
   * Registers geofences for a list of artworks.
   *
   * @param artworks List of artworks for which geofences need to be registered.
   * @throws [AppException.InvalidInput] when too many (more than [MAX_NOTIFIABLE_ARTWORKS]) are
   *   expected to be registered for notifications.
   * @author 5kyPhy
   */
  @SuppressLint("MissingPermission") // caller guarantees permissions
  suspend fun register(artworks: List<Artwork>) {
    if (artworks.isEmpty()) {
      client.removeGeofences(pendingIntent).await()
      return
    }

    if (artworks.size > MAX_NOTIFIABLE_ARTWORKS)
        throw AppException.InvalidInput(
            message = "Too many artworks are set-up for geofenced notifications."
        )

    val geofences =
        artworks
            .mapNotNull { artwork ->
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
            .take(MAX_NOTIFIABLE_ARTWORKS)

    unregisterAll() // remove old geofences before adding new ones
    if (geofences.isEmpty()) return // No need to register the geofences when there are none.

    val request =
        GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()

    client.addGeofences(request, pendingIntent).await()
  }

  /**
   * Unregisters all geofences associated with the current pending intent.
   *
   * This method attempts to remove all geofences that were previously registered using the
   * [GeofenceManager] instance. It returns a [Result] indicating the success or failure of the
   * operation.
   *
   * @return A [Result] containing [Unit] on success, or an exception on failure.
   * @author 5kyPhy
   */
  suspend fun unregisterAll(): Result<Unit> = runCatching {
    client.removeGeofences(pendingIntent).await()
  }
}
