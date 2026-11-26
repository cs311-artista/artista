// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: qwen3.5
package com.artista.artista.model.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.artista.artista.R
import com.artista.artista.resources.C

/**
 * Object responsible for managing notification channels within the Artista application.
 *
 * @author 5kyPhy
 */
object NotificationChannels {

  /**
   * Creates a notification channel for nearby artworks.
   *
   * This function creates a notification channel with the identifier
   * [C.NotificationChannels.NEARBY_ARTWORKS], using the provided context to set the channel name
   * and description. The channel importance is set to [NotificationManager.IMPORTANCE_DEFAULT].
   *
   * @param context The context used to access system services and string resources.
   * @author 5kyPhy
   */
  fun createNearbyNotificationChannel(context: Context) {
    val channel =
        NotificationChannel(
                C.NotificationChannels.NEARBY_ARTWORKS,
                context.getString(R.string.nearby_alerts_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            .apply { description = context.getString(R.string.nearby_alerts_channel_description) }

    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
}
