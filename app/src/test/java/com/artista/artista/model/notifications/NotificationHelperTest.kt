// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.MainActivity
import com.artista.artista.resources.C
import com.artista.artista.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationHelperTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private val manager = app.getSystemService(NotificationManager::class.java)

  private fun show(id: String, title: String = "Title", text: String = "Text") =
      NotificationHelper.showProximityNotification(app, id, title, text)

  private fun posted(id: String): Notification = shadowOf(manager).getNotification(id.hashCode())

  private fun allowNotifications() =
      shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

  @Test
  fun `posts nothing without the notification permission`() {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    show("a")
    assertEquals(0, shadowOf(manager).size())
  }

  @Test
  fun `posts a dismissable notification on the nearby channel`() {
    allowNotifications()
    show("a", title = "Near you", text = "Mona Lisa")

    val notification = posted("a")
    assertEquals(C.NotificationChannels.NEARBY_ARTWORKS, notification.channelId)
    assertEquals("Near you", notification.extras.getString(Notification.EXTRA_TITLE))
    assertEquals("Mona Lisa", notification.extras.getString(Notification.EXTRA_TEXT))
    assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
  }

  @Test
  fun `tapping the notification opens the artwork in MainActivity`() {
    allowNotifications()
    show("a")

    val pendingIntent = shadowOf(posted("a").contentIntent)
    assertTrue(pendingIntent.isActivityIntent)
    assertTrue(pendingIntent.flags and PendingIntent.FLAG_IMMUTABLE != 0)

    val intent = pendingIntent.savedIntent
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals(Screen.ArtworkDetails.createRoute("a").toUri(), intent.data)
    assertEquals(MainActivity::class.java.name, intent.component?.className)
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
  }

  @Test
  fun `each artwork gets its own notification and a repeat updates in place`() {
    allowNotifications()
    show("a")
    show("b")
    show("a", title = "Updated")

    assertEquals(2, shadowOf(manager).size())
    assertEquals("Updated", posted("a").extras.getString(Notification.EXTRA_TITLE))
  }

  @Test
  @Config(sdk = [30])
  fun `below Android 13 the system notification switch decides`() {
    show("a")
    assertEquals(1, shadowOf(manager).size())

    shadowOf(manager).setNotificationsEnabled(false)
    show("b")
    assertEquals(1, shadowOf(manager).size())
  }
}
