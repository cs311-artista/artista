// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.RemoteArtistRepository
import com.artista.artista.model.error.AppException

/**
 * Repository for retrieving artwork metadata from Wikidata.
 *
 * Implementations translate user input into SPARQL requests, keep the result set anchored to
 * English-language labels for a consistent UI, and map valid rows into [Artwork] instances.
 *
 * Failures are reported as [AppException]. Besides the cases documented on each function, any
 * operation can fail with another [AppException] type when the backend cannot be reached or refuses
 * the request.
 *
 * @author IJJA3141
 */
interface WikiDataRepository : RemoteArtworkRepository, RemoteArtistRepository
