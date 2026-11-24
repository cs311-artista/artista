// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.artista.artista.R
import com.artista.artista.resources.C

object NotificationChannels {
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
