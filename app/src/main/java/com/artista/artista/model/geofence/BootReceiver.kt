// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.ui.error.launchCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Re-registers geofences for saved artworks after the device finishes booting.
 *
 * @param savedArtworksRepository repository used to retrieve the saved artworks. Must be handed
 *   down by the caller/instantiator, the default is only provided as receivers need default
 *   constructors.
 * @author 5kyPhy
 */
class BootReceiver(
    private val savedArtworksRepository: SavedArtworksRepository =
        ArtworkRepositoryProvider.savedArtworksRepository
) : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val appContext = context.applicationContext
    val pending = goAsync()
    receiverScope
        .launchCatching(
            tag = TAG,
            onError = { error -> Log.e(TAG, "Error occurred while processing boot event", error) },
        ) {
          GeofenceManager(appContext).register(savedArtworksRepository.getSavedArtworks())
        }
        .invokeOnCompletion { pending.finish() }
  }

  private companion object {
    private var TAG = BootReceiver::class.java.simpleName
    val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }
}
