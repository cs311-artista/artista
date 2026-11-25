// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
class BootReceiverTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val repository = mockk<SavedArtworksRepository>()
  private val client = mockk<GeofencingClient>()
  private val broadcast = AsyncBroadcast()
  private val receiver = BootReceiver(repository).asyncOn(broadcast)

  @Before
  fun setUp() {
    mockkStatic(LocationServices::class)
    every { LocationServices.getGeofencingClient(any<Context>()) } returns client
    every { client.removeGeofences(any<PendingIntent>()) } returns Tasks.forResult<Void>(null)
    every { client.addGeofences(any(), any()) } returns Tasks.forResult<Void>(null)
  }

  @After fun tearDown() = unmockkAll()

  private fun boot() = receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

  @Test
  fun `re-registers the saved artworks after boot`() {
    coEvery { repository.getSavedArtworks() } returns listOf(testArtwork("a"))

    boot()
    broadcast.awaitFinished()

    verify {
      client.addGeofences(
          match { request -> request.geofences.map { it.requestId } == listOf("a") },
          any(),
      )
    }
  }

  @Test
  fun `ignores every other action`() {
    receiver.onReceive(context, Intent("some.other.ACTION"))

    verify(exactly = 0) { receiver.goAsync() }
    verify(exactly = 0) { client.addGeofences(any(), any()) }
  }

  @Test
  fun `a repository failure registers nothing but still finishes the broadcast`() {
    coEvery { repository.getSavedArtworks() } throws IllegalStateException("boom")

    boot()
    broadcast.awaitFinished()

    verify(exactly = 0) { client.addGeofences(any(), any()) }
  }

  @Test
  fun `a registration failure still finishes the broadcast`() {
    coEvery { repository.getSavedArtworks() } returns listOf(testArtwork("a"))
    every { client.addGeofences(any(), any()) } returns
        Tasks.forException<Void>(IllegalStateException("boom"))

    boot()
    broadcast.awaitFinished()
  }
}
