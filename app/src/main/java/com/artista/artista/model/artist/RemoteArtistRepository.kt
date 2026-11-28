// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artist

import android.util.Log
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * A matching artist returned as a recommendation for search.
 *
 * @property qid item identifier, such as `Q5593`
 * @property label artist's display name
 * @property description optional description of the matching item
 * @author IJJA3141
 */
data class ArtistCandidate(
    val qid: ArtistId,
    val label: String,
    val description: String?,
)

/**
 * Repository for retrieving artist data from remote source.
 *
 * Failures are reported as [AppException]. Besides the cases documented on each function, any
 * operation can fail with another [AppException] type when the backend cannot be reached or refuses
 * the request.
 *
 * @author IJJA3141
 */
interface RemoteArtistRepository {

  /**
   * Returns suggestion for [query].
   *
   * @param query search string
   * @param limit max candidate returned
   * @return candidates to the suggestion
   * @author IJJA3141
   */
  suspend fun getArtistCandidates(query: String, limit: Int = 5): List<ArtistCandidate>

  /**
   * Searches remote source for artists matching the provided text.
   *
   * Implementations may restrict matching to English labels to keep results stable across locales,
   * while still allowing additional filters to be added later.
   *
   * @param query the title fragment to search for in source
   * @param limit maximum number of matches returned
   * @return matching artists mapped from valid source results
   * @author IJJA3141
   */
  suspend fun getArtistsFromQuery(query: String, limit: Int = 50): List<Artist>

  /**
   * Returns the [Artist] associated with the provided identifier.
   *
   * Wikidata may return duplicate rows for the same item when multiple labels or measurements are
   * associated with the same record. Implementations may therefore normalize the result by keeping
   * a single representative artist for the requested [qid].
   *
   * @param qid unique identifier of the artist
   * @return the [Artist] requested via the [qid]
   * @throws AppException.NotFound if no [Artist] matching [qid] was found
   * @throws AppException.InvalidInput if the [qid] is blank or otherwise invalid
   * @author IJJA3141
   */
  suspend fun getArtist(qid: ArtistId): Artist?
}

/** Log tag identifying messages emitted while resolving artists. */
private const val TAG = "RemoteArtistRepository"

/**
 * Fetches the details of the given artists, all at once rather than one after the other.
 *
 * @param ids identifiers of the artists, in the order they are returned
 * @return the artists that could be resolved, in the same order; the others are left out
 * @throws AppException if the catalog cannot be reached or refuses the request
 * @author krfpn
 */
suspend fun RemoteArtistRepository.getArtistsFromList(ids: List<ArtistId>): List<Artist> =
    coroutineScope {
      ids.map { id ->
            async {
              val artist =
                  try {
                    getArtist(id)
                  } catch (_: AppException.InvalidInput) {
                    null
                  } catch (_: AppException.NotFound) {
                    null
                  }
              if (artist == null) Log.w(TAG, "Skipped artist $id, which could not be resolved.")
              artist
            }
          }
          .awaitAll()
          .filterNotNull()
    }
