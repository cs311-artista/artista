// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artist.ArtistId
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Thread-safe LRU cache whose entries also expire after [ttl].
 *
 * Null values are not supported on purpose: callers cache only successful, non-null results.
 *
 * @param maxEntries maximum number of entries; the least recently used one is evicted first
 * @param ttl how long an entry stays valid
 * @param clock monotonic time source in nanoseconds, replaceable in tests
 */
internal class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttl: Duration,
    private val clock: () -> Long = System::nanoTime,
) {
  private class Entry<V>(val value: V, val storedAtNanos: Long)

  private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true) // access order = LRU

  init {
    require(maxEntries > 0) { "maxEntries must be positive" }
    require(ttl.isPositive()) { "ttl must be positive" }
  }

  @Synchronized
  fun get(key: K): V? {
    val entry = entries[key] ?: return null
    if (clock() - entry.storedAtNanos >= ttl.inWholeNanoseconds) {
      entries.remove(key)
      return null
    }
    return entry.value
  }

  @Synchronized
  fun put(key: K, value: V) {
    entries[key] = Entry(value, clock())
    while (entries.size > maxEntries) {
      entries.remove(entries.keys.first())
    }
  }

  @Synchronized fun clear() = entries.clear()
}

/**
 * Returns the cached value for [key], or runs [load] and caches its result.
 *
 * The lock is never held while [load] runs, so a slow request cannot block other cache users. If
 * [load] throws, nothing is cached.
 */
internal inline fun <K : Any, V : Any> TtlCache<K, V>.getOrLoad(key: K, load: () -> V): V {
  get(key)?.let {
    return it
  }
  return load().also { put(key, it) }
}

internal data class AreaKey(val area: SearchArea, val types: Set<ArtworkType>, val limit: Int)

internal data class ArtworkQueryKey(val text: String, val types: Set<ArtworkType>, val limit: Int)

internal data class ArtistQueryKey(val text: String, val limit: Int)

private val WHITESPACE_RUN = Regex("\\s+")

/** Case-insensitive, whitespace-normalized text for use in a cache key. Accents are kept. */
internal fun cacheText(value: String): String =
    value.trim().lowercase(Locale.ROOT).replace(WHITESPACE_RUN, " ")

/**
 * All caches used by [WikiDataRepositoryRemote], one per operation.
 *
 * The class is public only because the repository's public constructor takes it; its members are
 * internal. Sizes and lifetimes are starting values to tune: facts about a given artwork or artist
 * change rarely, while search results go stale faster.
 */
class WikiDataCaches(clock: () -> Long = System::nanoTime) {
  internal val areaArtworks =
      TtlCache<AreaKey, List<Artwork>>(maxEntries = 20, ttl = 1.hours, clock = clock)
  internal val searchedArtworks =
      TtlCache<ArtworkQueryKey, List<Artwork>>(maxEntries = 50, ttl = 30.minutes, clock = clock)
  internal val artworkCandidates =
      TtlCache<ArtworkQueryKey, List<ArtworkCandidate>>(
          maxEntries = 200,
          ttl = 10.minutes,
          clock = clock,
      )
  internal val artworksById =
      TtlCache<ArtworkId, Artwork>(maxEntries = 500, ttl = 24.hours, clock = clock)

  internal val artistCandidates =
      TtlCache<ArtistQueryKey, List<ArtistCandidate>>(
          maxEntries = 200,
          ttl = 10.minutes,
          clock = clock,
      )
  internal val searchedArtists =
      TtlCache<ArtistQueryKey, List<Artist>>(maxEntries = 50, ttl = 30.minutes, clock = clock)
  internal val artistsById =
      TtlCache<ArtistId, Artist>(maxEntries = 200, ttl = 24.hours, clock = clock)

  /** Lets a later [WikiDataRepositoryRemote.getArtwork] be answered from a list result. */
  internal fun rememberArtworks(artworks: List<Artwork>) {
    artworks.forEach { artworksById.put(it.uid, it) }
  }

  /** Drops everything, for example on pull-to-refresh. */
  fun clear() {
    areaArtworks.clear()
    searchedArtworks.clear()
    artworkCandidates.clear()
    artworksById.clear()
    artistCandidates.clear()
    searchedArtists.clear()
    artistsById.clear()
  }
}
