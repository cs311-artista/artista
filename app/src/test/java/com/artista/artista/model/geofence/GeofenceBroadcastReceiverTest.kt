// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.notifications.NotificationHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GeofenceBroadcastReceiverTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val repository = mockk<SavedArtworksRepository>()
  private val broadcast = AsyncBroadcast()
  private val receiver = GeofenceBroadcastReceiver(repository).asyncOn(broadcast)

  @Before
  fun setUp() {
    mockkStatic(GeofencingEvent::class)
    mockkObject(NotificationHelper)
    every { NotificationHelper.showProximityNotification(any(), any(), any(), any()) } just Runs
  }

  @After fun tearDown() = unmockkAll()

  private fun event(
      transition: Int = Geofence.GEOFENCE_TRANSITION_DWELL,
      failed: Boolean = false,
      ids: List<String>? = listOf("a"),
  ): GeofencingEvent = mockk {
    every { hasError() } returns failed
    every { errorCode } returns GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE
    every { geofenceTransition } returns transition
    every { triggeringGeofences } returns
        ids?.map { id -> mockk<Geofence> { every { requestId } returns id } }
  }

  private fun deliver(event: GeofencingEvent?) {
    every { GeofencingEvent.fromIntent(any()) } returns event
    receiver.onReceive(context, Intent())
  }

  private fun verifyNotifications(count: Int) =
      verify(exactly = count) {
        NotificationHelper.showProximityNotification(any(), any(), any(), any())
      }

  private fun assertIgnored() {
    verify(exactly = 0) { receiver.goAsync() }
    verifyNotifications(0)
  }

  @Test
  fun `ignores intents without a geofencing event`() {
    deliver(null)
    assertIgnored()
  }

  @Test
  fun `ignores events that carry an error`() {
    deliver(event(failed = true))
    assertIgnored()
  }

  @Test
  fun `ignores transitions other than dwell`() {
    deliver(event(transition = Geofence.GEOFENCE_TRANSITION_ENTER))
    assertIgnored()
  }

  @Test
  fun `ignores events without triggering geofences`() {
    deliver(event(ids = null))
    assertIgnored()
  }

  @Test
  fun `posts a proximity notification for a saved artwork`() {
    coEvery { repository.getSavedArtworks() } returns
        listOf(testArtwork("a", name = "Mona Lisa"), testArtwork("b"))

    deliver(event(ids = listOf("a")))
    broadcast.awaitFinished()

    verify(exactly = 1) {
      NotificationHelper.showProximityNotification(
          any(),
          "a",
          context.getString(R.string.notification_proximity_title, "Mona Lisa"),
          context.getString(R.string.notification_proximity_text, "Mona Lisa"),
      )
    }
  }

  @Test
  fun `posts one notification per triggering geofence`() {
    coEvery { repository.getSavedArtworks() } returns listOf(testArtwork("a"), testArtwork("b"))

    deliver(event(ids = listOf("a", "b")))
    broadcast.awaitFinished()

    verifyNotifications(2)
  }

  @Test
  fun `skips geofences that are no longer saved`() {
    coEvery { repository.getSavedArtworks() } returns listOf(testArtwork("a"))

    deliver(event(ids = listOf("gone")))
    broadcast.awaitFinished()

    verifyNotifications(0)
  }

  @Test
  fun `a repository failure posts nothing but still finishes the broadcast`() {
    coEvery { repository.getSavedArtworks() } throws IllegalStateException("boom")

    deliver(event())
    broadcast.awaitFinished()

    verifyNotifications(0)
  }
}
