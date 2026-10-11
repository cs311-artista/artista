// Co-authored-by: Gemini & Copilot
package com.artista.artista.model.artwork

import com.artista.artista.model.map.Location

/**
 * Contract defining persistent storage operations for artworks saved by the user.
 *
 * ViewModels interact with this abstraction rather than concrete implementations to maintain clean
 * separation between UI state management and persistence
 *
 * @author hixeum, IJJA3141, 5kyPhy
 */
interface ArtworkRepository {

  /**
   * Returns the [Artwork] specified by [uid]
   *
   * @param uid unique identifier of the artwork
   * @return the [Artwork] requested via the [uid]
   * @throws NoSuchElementException if no [Artwork] matching [uid] was found
   * @throws IllegalArgumentException if the [uid] is invalid
   * @author 5kyPhy
   */
  suspend fun getArtwork(uid: Artwork_ID): Artwork = throw NotImplementedError()

  /**
   * Searches the repositories sources for an [Artwork] matching the given artwork's [name]
   *
   * @param name the [Artwork.name] associated to the [Artwork] being searched
   * @return a [List] of [Artwork] matching the searched for [name]. If no match was found,
   *   [emptyList]
   * @author 5kyPhy
   */
  suspend fun searchArtworkByArtworkName(name: String): List<Artwork> = throw NotImplementedError()

  /**
   * Searches the repositories sources for an [Artwork] matching the given artwork's [artistName]
   *
   * @param artistName the [Artwork.artistName] associated to the [Artwork] being searched
   * @return a [List] of [Artwork] matching the searched for [artistName]. If no match was found,
   *   [emptyList]
   * @author 5kyPhy
   */
  suspend fun searchArtworkByArtistName(artistName: String): List<Artwork> =
      throw NotImplementedError()

  /**
   * Searches the repositories sources for an [Artwork]s matching the given artwork's [location]
   *
   * @param location the [Location] around which [Artwork]s should be searched for
   * @param radius the search radius around the [location] in which artworks should be searched for
   * @return a [List] of [Artwork] which match the [location] for where to search for. If no match
   *   was found, [emptyList]
   * @author 5kyPhy
   */
  suspend fun searchArtworkByLocation(location: Location, radius: Double): List<Artwork> =
      throw NotImplementedError()

  /**
   * Checks whether an artwork with the specified identifier is currently bookmarked or saved.
   *
   * @param uid unique identifier of the artwork
   * @return true if the artwork is already stored, false otherwise
   * @throws IllegalArgumentException if [uid] is blank
   * @author hixeum,
   */
  suspend fun isArtworkSaved(uid: Artwork_ID): Boolean

  /**
   * Persists an artwork to storage, updating any existing record with the same identifier.
   *
   * @param artwork the complete artwork entity to persist
   * @throws IllegalArgumentException if the artwork title is invalid
   * @author hixeum,
   */
  suspend fun saveArtwork(artwork: Artwork)

  /**
   * Deletes an artwork from storage using its unique identifier.
   *
   * @param uid unique identifier of the artwork to remove
   * @throws IllegalArgumentException if [uid] is blank
   * @throws NoSuchElementException if no artwork matches [uid]
   * @author hixeum,
   */
  suspend fun removeArtwork(uid: Artwork_ID)

  /**
   * Retrieves artworks saved by the current user.
   *
   * @return the current user's saved artworks
   * @author IJJA3141
   */
  suspend fun getSavedArtworks(): List<Artwork>
}
