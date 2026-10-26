// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.error

import com.artista.artista.R
import com.artista.artista.model.error.AppException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [toMessageRes], the default user-facing message of each [AppException].
 *
 * @author Timz3rr
 */
class ErrorMessagesTest {

  @Test
  fun toMessageRes_forNotFound_returnsNotFoundMessage() {
    assertEquals(R.string.error_not_found, AppException.NotFound().toMessageRes())
  }

  @Test
  fun toMessageRes_forAlreadyExists_returnsAlreadyExistsMessage() {
    assertEquals(R.string.error_already_exists, AppException.AlreadyExists().toMessageRes())
  }

  @Test
  fun toMessageRes_forInvalidInput_returnsInvalidInputMessage() {
    assertEquals(R.string.error_invalid_input, AppException.InvalidInput().toMessageRes())
  }

  @Test
  fun toMessageRes_forNetwork_returnsNetworkMessage() {
    assertEquals(R.string.error_network, AppException.Network().toMessageRes())
  }

  @Test
  fun toMessageRes_forUnauthorized_returnsUnauthorizedMessage() {
    assertEquals(R.string.error_unauthorized, AppException.Unauthorized().toMessageRes())
  }

  @Test
  fun toMessageRes_forInvalidData_returnsInvalidDataMessage() {
    assertEquals(R.string.error_invalid_data, AppException.InvalidData().toMessageRes())
  }

  @Test
  fun toMessageRes_forUnknown_returnsUnknownMessage() {
    assertEquals(R.string.error_unknown, AppException.Unknown().toMessageRes())
  }
}
