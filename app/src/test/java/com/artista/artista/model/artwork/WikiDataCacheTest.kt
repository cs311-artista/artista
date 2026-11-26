// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>

package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manually advanced monotonic clock so expiry tests never sleep. */
private class FakeClock {
  var nanos = 0L
  val now: () -> Long = { nanos }

  fun advance(duration: Duration) {
    nanos += duration.inWholeNanoseconds
  }
}

private fun artwork(uid: String) = Artwork(uid, "Name $uid", null, null, null, null, null, null)

class TtlCacheTest {
  private val clock = FakeClock()

  private fun newCache(maxEntries: Int = 10, ttl: Duration = 1.hours) =
      TtlCache<String, String>(maxEntries, ttl, clock.now)

  @Test
  fun get_returnsNullWhenKeyWasNeverStored() {
    assertNull(newCache().get("missing"))
  }

  @Test
  fun get_returnsStoredValue() {
    val cache = newCache()
    cache.put("k", "v")
    assertEquals("v", cache.get("k"))
  }

  @Test
  fun get_keepsDifferentKeysSeparate() {
    val cache = newCache()
    cache.put("a", "1")
    cache.put("b", "2")
    assertEquals("1", cache.get("a"))
    assertEquals("2", cache.get("b"))
  }

  @Test
  fun get_returnsValueJustBeforeTtlAndNullExactlyAtTtl() {
    val cache = newCache(ttl = 10.minutes)
    cache.put("k", "v")

    clock.advance(10.minutes - 1.nanoseconds)
    assertEquals("v", cache.get("k"))

    clock.advance(1.nanoseconds)
    assertNull(cache.get("k"))
  }

  @Test
  fun get_doesNotExtendTheLifetimeOfAnEntry() {
    val cache = newCache(ttl = 1.hours)
    cache.put("k", "v")

    clock.advance(50.minutes)
    assertEquals("v", cache.get("k")) // a hit must not restart the timer

    clock.advance(20.minutes)
    assertNull(cache.get("k"))
  }

  @Test
  fun put_afterExpiryStoresAFreshValue() {
    val cache = newCache(ttl = 10.minutes)
    cache.put("k", "old")
    clock.advance(11.minutes)
    assertNull(cache.get("k"))

    cache.put("k", "new")
    assertEquals("new", cache.get("k"))
  }

  @Test
  fun put_overwritingAKeyReplacesTheValueAndRestartsItsLifetime() {
    val cache = newCache(ttl = 1.hours)
    cache.put("k", "old")
    clock.advance(40.minutes)
    cache.put("k", "new")

    clock.advance(40.minutes) // 80 minutes after the first put, 40 after the second
    assertEquals("new", cache.get("k"))
  }

  @Test
  fun put_overwritingAKeyDoesNotCountAsANewEntry() {
    val cache = newCache(maxEntries = 2)
    cache.put("a", "1")
    cache.put("a", "1 again")
    cache.put("b", "2")

    assertEquals("1 again", cache.get("a"))
    assertEquals("2", cache.get("b"))
  }

  @Test
  fun put_evictsTheOldestEntryWhenOverCapacity() {
    val cache = newCache(maxEntries = 2)
    cache.put("a", "1")
    cache.put("b", "2")
    cache.put("c", "3")

    assertNull(cache.get("a"))
    assertEquals("2", cache.get("b"))
    assertEquals("3", cache.get("c"))
  }

  @Test
  fun put_evictsTheLeastRecentlyUsedEntryNotTheOldest() {
    val cache = newCache(maxEntries = 2)
    cache.put("a", "1")
    cache.put("b", "2")
    cache.get("a") // a becomes the most recently used
    cache.put("c", "3")

    assertEquals("1", cache.get("a"))
    assertNull(cache.get("b"))
    assertEquals("3", cache.get("c"))
  }

  @Test
  fun put_worksWithACapacityOfOne() {
    val cache = newCache(maxEntries = 1)
    cache.put("a", "1")
    cache.put("b", "2")

    assertNull(cache.get("a"))
    assertEquals("2", cache.get("b"))
  }

  @Test
  fun clear_removesEveryEntryAndTheCacheStaysUsable() {
    val cache = newCache()
    cache.put("a", "1")
    cache.put("b", "2")

    cache.clear()

    assertNull(cache.get("a"))
    assertNull(cache.get("b"))
    cache.put("c", "3")
    assertEquals("3", cache.get("c"))
  }

  @Test
  fun constructor_rejectsANonPositiveCapacity() {
    assertThrows(IllegalArgumentException::class.java) { TtlCache<String, String>(0, 1.hours) }
    assertThrows(IllegalArgumentException::class.java) { TtlCache<String, String>(-1, 1.hours) }
  }

  @Test
  fun constructor_rejectsANonPositiveTtl() {
    assertThrows(IllegalArgumentException::class.java) {
      TtlCache<String, String>(10, Duration.ZERO)
    }
    assertThrows(IllegalArgumentException::class.java) {
      TtlCache<String, String>(10, (-1).minutes)
    }
  }

  @Test
  fun concurrentUse_doesNotThrowAndLeavesTheCacheUsable() {
    val cache = TtlCache<Int, Int>(maxEntries = 10, ttl = 1.hours) // real clock
    val failures = ConcurrentLinkedQueue<Throwable>()

    val threads =
        List(8) { id ->
          thread {
            try {
              repeat(2_000) { i ->
                val key = (id * 31 + i) % 25
                cache.put(key, i)
                cache.get(key)
                if (i % 500 == 0) cache.clear()
              }
            } catch (t: Throwable) {
              failures.add(t)
            }
          }
        }
    threads.forEach { it.join(10_000) }

    assertTrue("a worker thread did not finish", threads.none { it.isAlive })
    assertTrue("unexpected failures: $failures", failures.isEmpty())
    cache.put(1_000, 1)
    assertEquals(1, cache.get(1_000))
  }
}

class GetOrLoadTest {
  private val clock = FakeClock()

  private fun newCache(ttl: Duration = 1.hours) = TtlCache<String, String>(10, ttl, clock.now)

  @Test
  fun getOrLoad_runsTheLoaderOnAMissAndCachesTheResult() {
    val cache = newCache()
    var calls = 0

    val first =
        cache.getOrLoad("k") {
          calls++
          "loaded"
        }
    val second =
        cache.getOrLoad("k") {
          calls++
          "should not run"
        }

    assertEquals("loaded", first)
    assertEquals("loaded", second)
    assertEquals(1, calls)
  }

  @Test
  fun getOrLoad_returnsAnEntryStoredWithPutWithoutLoading() {
    val cache = newCache()
    cache.put("k", "stored")

    val result = cache.getOrLoad("k") { error("loader must not run") }

    assertEquals("stored", result)
  }

  @Test
  fun getOrLoad_doesNotCacheWhenTheLoaderThrows() {
    val cache = newCache()
    var calls = 0

    assertThrows(IllegalStateException::class.java) {
      cache.getOrLoad("k") {
        calls++
        error("network down")
      }
    }
    assertNull(cache.get("k"))

    val retry =
        cache.getOrLoad("k") {
          calls++
          "recovered"
        }

    assertEquals("recovered", retry)
    assertEquals(2, calls)
  }

  @Test
  fun getOrLoad_loadsAgainAfterTheEntryExpired() {
    val cache = newCache(ttl = 10.minutes)
    var calls = 0

    cache.getOrLoad("k") { "v${++calls}" }
    clock.advance(11.minutes)
    val reloaded = cache.getOrLoad("k") { "v${++calls}" }

    assertEquals("v2", reloaded)
    assertEquals(2, calls)
  }

  @Test
  fun getOrLoad_doesNotHoldTheLockWhileLoading() {
    val cache = newCache()
    val otherThreadFinished = CountDownLatch(1)

    cache.getOrLoad("slow") {
      thread {
        cache.get("other") // blocks here if the loader holds the cache lock
        otherThreadFinished.countDown()
      }
      assertTrue(
          "another thread was blocked while the loader was running",
          otherThreadFinished.await(2, TimeUnit.SECONDS),
      )
      "value"
    }
  }
}

class CacheKeysTest {
  @Test
  fun cacheText_trimsLowercasesAndCollapsesWhitespace() {
    assertEquals("mona lisa", cacheText("  Mona   LISA\t\n"))
  }

  @Test
  fun cacheText_returnsAnEmptyStringForBlankInput() {
    assertEquals("", cacheText("   "))
    assertEquals("", cacheText(""))
  }

  @Test
  fun cacheText_keepsAccents() {
    assertEquals("monét", cacheText("Monét"))
    assertNotEquals(cacheText("Monet"), cacheText("Monét"))
  }

  @Test
  fun cacheText_doesNotDependOnTheDefaultLocale() {
    val original = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR")) // "I".lowercase() is "ı" in Turkish
      assertEquals("title icon", cacheText("TITLE ICON"))
    } finally {
      Locale.setDefault(original)
    }
  }

  @Test
  fun artworkQueryKey_ignoresTheOrderOfTheTypes() {
    val a =
        ArtworkQueryKey(
            "mona lisa",
            listOf(ArtworkType.PAINTING, ArtworkType.DRAWING).toSet(),
            50,
        )
    val b =
        ArtworkQueryKey(
            "mona lisa",
            listOf(ArtworkType.DRAWING, ArtworkType.PAINTING).toSet(),
            50,
        )

    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun artworkQueryKey_differsByTextTypesAndLimit() {
    val base = ArtworkQueryKey("mona lisa", setOf(ArtworkType.PAINTING), 50)

    assertNotEquals(base, base.copy(text = "mona"))
    assertNotEquals(base, base.copy(types = setOf(ArtworkType.DRAWING)))
    assertNotEquals(base, base.copy(limit = 20))
  }

  @Test
  fun artistQueryKey_differsByTextAndLimit() {
    val base = ArtistQueryKey("picasso", 5)

    assertEquals(base, ArtistQueryKey("picasso", 5))
    assertNotEquals(base, base.copy(text = "monet"))
    assertNotEquals(base, base.copy(limit = 10))
  }

  // The two tests below rely on SearchArea snapping its center to a 0.01 degree grid.

  @Test
  fun areaKey_matchesForPositionsInTheSameGridCell() {
    val types = setOf(ArtworkType.PAINTING)

    val here = SearchAreaKey(SearchArea(46.5197, 6.6323), types, 50)
    val aBitFurther = SearchAreaKey(SearchArea(46.5231, 6.6349), types, 50)

    assertEquals(here, aBitFurther)
  }

  @Test
  fun areaKey_differsAcrossGridCellsRadiiAndLimits() {
    val types = setOf(ArtworkType.PAINTING)
    val here = SearchAreaKey(SearchArea(46.5197, 6.6323, 5.0), types, 50)

    assertNotEquals(here, SearchAreaKey(SearchArea(46.5351, 6.6323, 5.0), types, 50))
    assertNotEquals(here, SearchAreaKey(SearchArea(46.5197, 6.6323, 10.0), types, 50))
    assertNotEquals(here, here.copy(limit = 20))
  }
}

class WikiDataCachesTest {
  private val clock = FakeClock()
  private val caches = WikiDataCaches(clock.now)

  private val textKey = ArtworkQueryKey("mona", setOf(ArtworkType.PAINTING), 50)
  private val areaKey = SearchAreaKey(SearchArea(46.5197, 6.6323), setOf(ArtworkType.PAINTING), 50)

  @Test
  fun rememberArtworks_makesEveryArtworkAvailableById() {
    val first = artwork("Q1")
    val second = artwork("Q2")

    caches.rememberArtworks(listOf(first, second))

    assertSame(first, caches.artworksById.get("Q1"))
    assertSame(second, caches.artworksById.get("Q2"))
    assertNull(caches.artworksById.get("Q3"))
  }

  @Test
  fun rememberArtworks_replacesAnOlderCopyOfTheSameArtwork() {
    caches.rememberArtworks(listOf(artwork("Q1")))
    val updated = artwork("Q1").copy(description = "Updated")

    caches.rememberArtworks(listOf(updated))

    assertSame(updated, caches.artworksById.get("Q1"))
  }

  @Test
  fun rememberArtworks_withNoArtworksChangesNothing() {
    caches.rememberArtworks(emptyList())

    assertNull(caches.artworksById.get("Q1"))
  }

  // Update the durations below if you tune the lifetimes in WikiDataCaches.
  @Test
  fun eachCacheExpiresAfterItsOwnLifetime() {
    val artworks = listOf(artwork("Q1"))
    caches.artworkCandidates.put(textKey, listOf(ArtworkCandidate("Q1", "Mona Lisa", null)))
    caches.searchedArtworks.put(textKey, artworks)
    caches.nearLocationArtworks.put(areaKey, artworks)
    caches.artworksById.put("Q1", artworks.single())

    clock.advance(11.minutes)
    assertNull("candidates last 10 minutes", caches.artworkCandidates.get(textKey))
    assertNotNull(caches.searchedArtworks.get(textKey))
    assertNotNull(caches.nearLocationArtworks.get(areaKey))
    assertNotNull(caches.artworksById.get("Q1"))

    clock.advance(20.minutes) // 31 minutes
    assertNull("searches last 30 minutes", caches.searchedArtworks.get(textKey))
    assertNotNull(caches.nearLocationArtworks.get(areaKey))
    assertNotNull(caches.artworksById.get("Q1"))

    clock.advance(30.minutes) // 61 minutes
    assertNull("areas last 1 hour", caches.nearLocationArtworks.get(areaKey))
    assertNotNull(caches.artworksById.get("Q1"))

    clock.advance(24.hours)
    assertNull("entities last 24 hours", caches.artworksById.get("Q1"))
  }

  @Test
  fun clear_emptiesEveryCache() {
    val artworks = listOf(artwork("Q1"))
    val artists = listOf(Artist("Q2", "picasso"))
    val artistKey = ArtistQueryKey("picasso", 5)
    caches.nearLocationArtworks.put(areaKey, artworks)
    caches.artistsById.put("Q2", artists.single())
    caches.searchedArtworks.put(textKey, artworks)
    caches.searchedArtists.put(artistKey, emptyList())
    caches.artworkCandidates.put(textKey, listOf(ArtworkCandidate("Q1", "Mona Lisa", null)))
    caches.artworksById.put("Q1", artworks.single())
    caches.artistCandidates.put(artistKey, emptyList<ArtistCandidate>())
    caches.searchedArtists.put(artistKey, emptyList())

    caches.clear()

    assertNull(caches.nearLocationArtworks.get(areaKey))
    assertNull(caches.searchedArtworks.get(textKey))
    assertNull(caches.artworkCandidates.get(textKey))
    assertNull(caches.artworksById.get("Q1"))
    assertNull(caches.artistCandidates.get(artistKey))
    assertNull(caches.searchedArtists.get(artistKey))
    assertNull(caches.artistsById.get("Q2"))
  }
}
