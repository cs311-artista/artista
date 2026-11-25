// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.map

import com.artista.artista.model.error.AppException

/**
 * Turns geographic coordinates into a human-readable postal address.
 *
 * @author Timz3rr
 */
interface AddressRepository {
  /**
   * Retrieves the address closest to a coordinate.
   *
   * @param coordinate the point to look up
   * @return the full address on one line, e.g. "77 Rue de Varenne, 75007 Paris, France"
   * @throws AppException.NotFound if no address is known for this point
   * @throws AppException.Network if the geocoding service cannot be reached
   * @throws AppException.Unknown for any other failure, such as no geocoding service on the device
   * @author Timz3rr
   */
  suspend fun getAddress(coordinate: Coordinate): String
}
