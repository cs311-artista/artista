// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.notifications.NotificationHelper
import com.artista.artista.ui.error.launchCatching
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Handles geofence dwell events and shows proximity notifications for matching saved artworks.
 *
 * @param savedArtworksRepository repository used to retrieve saved artworks. Must be handed down by
 *   the caller/instantiator, the default is only provided as receivers need default constructors.
 * @author 5kyPhy
 */
class GeofenceBroadcastReceiver(
    private val savedArtworksRepository: SavedArtworksRepository? = null
) : BroadcastReceiver() {

  override fun onReceive(
      context: Context,
      intent: Intent,
  ) {
    val event = GeofencingEvent.fromIntent(intent) ?: return
    if (event.hasError()) {
      Log.e("Geofence", GeofenceStatusCodes.getStatusCodeString(event.errorCode))
      return
    }
    if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) return

    val ids = event.triggeringGeofences?.map { it.requestId } ?: return
    val appContext = context.applicationContext
    val pending = goAsync() // safely enables short async work for DB lookup

    receiverScope
        .launchCatching(
            tag = TAG,
            onError = { error ->
              Log.e(TAG, "Error occurred while processing geofence event", error)
            },
        ) {
          val repository =
              savedArtworksRepository ?: ArtworkRepositoryProvider.savedArtworksRepository
          val artworks = repository.getSavedArtworks()
          ids.forEach { id ->
            val artwork = artworks.find { it.uid == id } ?: return@forEach
            NotificationHelper.showProximityNotification(
                appContext,
                artwork.uid,
                context.getString(R.string.notification_proximity_title, artwork.name),
                context.getString(R.string.notification_proximity_text, artwork.name),
            )
          }
        }
        .invokeOnCompletion { pending.finish() }
  }

  private companion object {
    private var TAG = GeofenceBroadcastReceiver::class.java.simpleName
    val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }
}
