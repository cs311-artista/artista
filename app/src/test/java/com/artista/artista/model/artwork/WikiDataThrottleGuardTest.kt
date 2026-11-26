// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manually advanced monotonic clock so the tests never sleep. Shared by the throttle tests. */
internal class ManualClock(startNanos: Long = 0L) {
  private var nanos = startNanos
  val now: () -> Long = { nanos }

  fun advance(duration: Duration) {
    nanos += duration.inWholeNanoseconds
  }
}

class ThrottleGuardTest {
  private val clock = ManualClock()
  private val guard = ThrottleGuard(clock.now)

  private fun isBlocked(target: ThrottleGuard = guard): Boolean =
      try {
        target.check()
        false
      } catch (e: AppException.Network) {
        true
      }

  @Test
  fun check_passesWhenNothingWasRecorded() {
    assertFalse(isBlocked())
  }

  @Test
  fun check_passesWhenTheClockStartsAtANegativeValue() {
    // The origin of System.nanoTime() is arbitrary and can be negative.
    val negativeClock = ManualClock(startNanos = -5_000_000_000L)

    assertFalse(isBlocked(ThrottleGuard(negativeClock.now)))
  }

  @Test
  fun record_429BlocksTheNextCheck() {
    guard.record(429, 30)

    assertTrue(isBlocked())
  }

  @Test
  fun record_503BlocksTheNextCheck() {
    guard.record(503, 30)

    assertTrue(isBlocked())
  }

  @Test
  fun record_otherStatusesDoNotBlock() {
    listOf(200, 204, 301, 400, 404, 500).forEach { code ->
      val fresh = ThrottleGuard(clock.now)

      fresh.record(code, 30)

      assertFalse("status $code must not block", isBlocked(fresh))
    }
  }

  @Test
  fun record_aSuccessfulResponseDoesNotLiftAnActiveBlock() {
    guard.record(429, 60)

    guard.record(200, null) // for example a request that was already in flight

    assertTrue(isBlocked())
  }

  @Test
  fun block_endsExactlyWhenRetryAfterHasPassed() {
    guard.record(429, 30)

    clock.advance(30.seconds - 1.nanoseconds)
    assertTrue(isBlocked())

    clock.advance(1.nanoseconds)
    assertFalse(isBlocked())
  }

  @Test
  fun block_usesTheDefaultWhenRetryAfterIsMissing() {
    guard.record(503, null)

    clock.advance((ThrottleGuard.DEFAULT_BLOCK_SECONDS.seconds) - 1.nanoseconds)
    assertTrue(isBlocked())

    clock.advance(1.nanoseconds)
    assertFalse(isBlocked())
  }

  @Test
  fun block_clampsATooSmallRetryAfterToTheMinimum() {
    listOf(0L, -5L).forEach { seconds ->
      val fresh = ThrottleGuard(clock.now)

      fresh.record(429, seconds)
      assertTrue("Retry-After $seconds must still block at once", isBlocked(fresh))

      clock.advance(ThrottleGuard.MIN_BLOCK_SECONDS.seconds)
      assertFalse("Retry-After $seconds must end after the minimum", isBlocked(fresh))
    }
  }

  @Test
  fun block_clampsATooLargeRetryAfterToTheMaximum() {
    guard.record(503, Long.MAX_VALUE) // must not overflow

    clock.advance(ThrottleGuard.MAX_BLOCK_SECONDS.seconds - 1.nanoseconds)
    assertTrue(isBlocked())

    clock.advance(1.nanoseconds)
    assertFalse(isBlocked())
  }

  @Test
  fun record_aShorterRetryAfterNeverShortensAnActiveBlock() {
    guard.record(429, 120)
    clock.advance(10.seconds)

    guard.record(429, 5) // would end at 15 s

    clock.advance(100.seconds) // 110 s in total
    assertTrue(isBlocked())

    clock.advance(10.seconds) // 120 s in total
    assertFalse(isBlocked())
  }

  @Test
  fun record_aLongerRetryAfterExtendsAnActiveBlock() {
    guard.record(429, 10)
    clock.advance(5.seconds)

    guard.record(429, 60) // now ends at 65 s

    clock.advance(59.seconds) // 64 s in total
    assertTrue(isBlocked())

    clock.advance(1.seconds) // 65 s in total
    assertFalse(isBlocked())
  }

  @Test
  fun block_canStartAgainAfterItEnded() {
    guard.record(429, 10)
    clock.advance(10.seconds)
    assertFalse(isBlocked())

    guard.record(503, 20)
    assertTrue(isBlocked())

    clock.advance(20.seconds)
    assertFalse(isBlocked())
  }

  @Test
  fun concurrentUse_onlyEverThrowsTheRateLimitException() {
    val realClockGuard = ThrottleGuard() // default monotonic clock
    val unexpected = ConcurrentLinkedQueue<Throwable>()

    val threads =
        List(8) { id ->
          thread {
            try {
              repeat(1_000) { i ->
                if (i % 3 == 0)
                    realClockGuard.record(if (id % 2 == 0) 429 else 503, (i % 5).toLong())
                try {
                  realClockGuard.check()
                } catch (e: AppException.Network) {
                  // expected while a block is active
                }
              }
            } catch (t: Throwable) {
              unexpected.add(t)
            }
          }
        }
    threads.forEach { it.join(10_000) }

    assertTrue("a worker thread did not finish", threads.none { it.isAlive })
    assertTrue("unexpected failures: $unexpected", unexpected.isEmpty())
  }
}
