// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Location
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GeofenceManagerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val client = mockk<GeofencingClient>()
  private val manager = GeofenceManager(context, client)

  private val calls = mutableListOf<String>()
  private val removedWith = mutableListOf<PendingIntent>()
  private val requests = mutableListOf<GeofencingRequest>()

  @Before
  fun setUp() {
    every { client.removeGeofences(any<PendingIntent>()) } answers
        {
          calls += "remove"
          removedWith += firstArg<PendingIntent>()
          Tasks.forResult<Void>(null)
        }
    every { client.addGeofences(any(), any()) } answers
        {
          calls += "add"
          requests += firstArg<GeofencingRequest>()
          Tasks.forResult<Void>(null)
        }
  }

  @Test
  fun `register with no artworks only clears existing geofences`() = runTest {
    manager.register(emptyList())
    assertEquals(listOf("remove"), calls)
  }

  @Test
  fun `register replaces old geofences before adding the new ones`() = runTest {
    manager.register(listOf(testArtwork("a"), testArtwork("b")))

    assertEquals(listOf("remove", "add"), calls)
    val request = requests.single()
    assertEquals(listOf("a", "b"), request.geofences.map { it.requestId })
    assertEquals(GeofencingRequest.INITIAL_TRIGGER_DWELL, request.initialTrigger)
  }

  @Test
  fun `artworks without a location are skipped`() = runTest {
    manager.register(listOf(testArtwork("a"), testArtwork("b", location = null)))
    assertEquals(listOf("a"), requests.single().geofences.map { it.requestId })
  }

  @Test
  fun `nothing is added when no artwork has a location`() = runTest {
    manager.register(listOf(testArtwork("a", location = null)))
    assertEquals(listOf("remove"), calls)
  }

  @Test
  fun `exactly 100 artworks are registered`() = runTest {
    manager.register(List(100) { testArtwork("id$it") })
    assertEquals(100, requests.single().geofences.size)
  }

  @Test
  fun `more than 100 artworks are rejected as invalid input`() = runTest {
    val error = runCatching {
      manager.register(List(101) { testArtwork("id$it") })
    }
        .exceptionOrNull()

    assertTrue(error is AppException.InvalidInput)
    assertTrue(requests.isEmpty()) // nothing was added
  }

  @Test
  fun `the pending intent is an explicit mutable broadcast to the receiver`() = runTest {
    manager.register(listOf(testArtwork("a")))

    val shadow = shadowOf(removedWith.single())
    assertTrue(shadow.isBroadcastIntent)
    assertEquals(
        GeofenceBroadcastReceiver::class.java.name,
        shadow.savedIntent.component?.className,
    )
    assertTrue(shadow.flags and PendingIntent.FLAG_MUTABLE != 0)
  }

  @Test
  fun `a failure from Play Services propagates`() = runTest {
    every { client.addGeofences(any(), any()) } returns
        Tasks.forException<Void>(IllegalStateException("boom"))

    val error = runCatching { manager.register(listOf(testArtwork("a"))) }.exceptionOrNull()
    assertTrue(error is IllegalStateException)
  }

  @Test
  fun `unregisterAll reports success`() = runTest {
    assertTrue(manager.unregisterAll().isSuccess)
    assertEquals(listOf("remove"), calls)
  }

  @Test
  fun `unregisterAll reports failure as a Result`() = runTest {
    every { client.removeGeofences(any<PendingIntent>()) } returns
        Tasks.forException<Void>(IllegalStateException("boom"))
    assertTrue(manager.unregisterAll().isFailure)
  }
}

/**
 * Builds the smallest valid [Artwork]: only what a test cares about is set, every optional field is
 * null. Pass `location = null` for an artwork without coordinates.
 *
 * [Location] takes the longitude first, so the defaults use named arguments.
 */
fun testArtwork(
    uid: ArtworkId,
    name: String? = "Artwork $uid",
    location: Location? = Location(longitude = 6.63, latitude = 46.52, name = null),
): Artwork =
    Artwork(
        uid = uid,
        name = name,
        artist = null,
        description = null,
        conceptionDate = null,
        museum = null,
        location = location,
        dimension = null,
    )
