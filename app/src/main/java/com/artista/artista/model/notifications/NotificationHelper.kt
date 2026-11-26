// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.artista.artista.MainActivity
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.resources.C
import com.artista.artista.ui.navigation.Screen

object NotificationHelper {
  fun showProximityNotification(
      context: Context,
      artworkId: ArtworkId,
      title: String,
      text: String,
  ) {
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    )
        return

    val intent =
        Intent(
                Intent.ACTION_VIEW,
                Screen.ArtworkDetails.createRoute(artworkId).toUri(),
                context,
                MainActivity::class.java,
            )
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }

    val pendingIntent =
        PendingIntent.getActivity(
            context,
            artworkId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val notification =
        NotificationCompat.Builder(context, C.NotificationChannels.NEARBY_ARTWORKS)
            .setSmallIcon(
                R.drawable.ic_google_logo
            ) // todo create notification logo ic_notification and link here
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

    NotificationManagerCompat.from(context).notify(artworkId.hashCode(), notification)
  }
}
