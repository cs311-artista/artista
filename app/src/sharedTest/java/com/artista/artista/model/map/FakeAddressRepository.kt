// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.map

import com.artista.artista.model.error.AppException

/**
 * A fake [AddressRepository] for tests, answering every lookup with the same address or error.
 *
 * @param address the address returned when [error] is null
 * @param error the failure thrown by every lookup, if any
 * @author Timz3rr
 */
class FakeAddressRepository(
    private val address: String = DEFAULT_ADDRESS,
    private val error: AppException? = null,
) : AddressRepository {

  /** Coordinates received so far, in call order, to check what was looked up and how often. */
  val requestedCoordinates = mutableListOf<Coordinate>()

  override suspend fun getAddress(coordinate: Coordinate): String {
    requestedCoordinates += coordinate
    error?.let { throw it }
    return address
  }

  companion object {
    /** Address returned by default, so tests can assert on it without repeating the text. */
    const val DEFAULT_ADDRESS = "77 Rue de Varenne, 75007 Paris, France"
  }
}
