// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.geofence

import android.content.BroadcastReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/**
 * Stand-in for the `PendingResult` the system hands to `goAsync()`.
 *
 * Calling `onReceive` directly makes `goAsync()` return null, and the receivers work on a
 * background dispatcher. Both receivers finish the broadcast in every outcome, so waiting for
 * `finish()` is a reliable way to know the work is done.
 */
internal class AsyncBroadcast {
  private val finished = CountDownLatch(1)

  val pendingResult: BroadcastReceiver.PendingResult = mockk {
    every { finish() } answers { finished.countDown() }
  }

  fun awaitFinished() {
    assertTrue("goAsync() was never finished", finished.await(5, TimeUnit.SECONDS))
  }
}

/** A spy of this receiver whose `goAsync()` hands out [broadcast]'s pending result. */
internal inline fun <reified T : BroadcastReceiver> T.asyncOn(broadcast: AsyncBroadcast): T =
    spyk(this) { every { goAsync() } returns broadcast.pendingResult }
