// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.resources.C
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationChannelsTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager: NotificationManager
    get() = context.getSystemService(NotificationManager::class.java)

  @Test
  fun `creates the nearby artworks channel`() {
    NotificationChannels.createNearbyNotificationChannel(context)

    val channel = manager.getNotificationChannel(C.NotificationChannels.NEARBY_ARTWORKS)
    assertNotNull(channel)
    assertEquals(context.getString(R.string.nearby_alerts_channel_name), channel.name.toString())
    assertEquals(context.getString(R.string.nearby_alerts_channel_description), channel.description)
    assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
  }

  @Test
  fun `creating the channel twice keeps a single channel`() {
    repeat(2) { NotificationChannels.createNearbyNotificationChannel(context) }

    val count =
        manager.notificationChannels.count { it.id == C.NotificationChannels.NEARBY_ARTWORKS }
    assertEquals(1, count)
  }
}
