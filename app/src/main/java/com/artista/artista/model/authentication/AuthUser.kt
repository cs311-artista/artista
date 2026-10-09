package com.artista.artista.model.authentication

/**
 * Minimal representation of an authenticated user.
 *
 * Intentionally a thin abstraction, decoupled from the future `User` data class: the sign-in and
 * sign-out logic (and their tests) can be written before that class exists, then extended later
 * without rewriting existing tests.
 *
 * @author timo-by
 */
interface AuthUser {

  /**
   * Unique identifier of the authenticated account.
   *
   * @author timo-by
   */
  val uid: String
}
