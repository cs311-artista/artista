package com.artista.artista.model.authentication

// Co-authored-by: DeepSeek Flash
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

  /**
   * Name the identity provider reports for this account.
   *
   * For a Google account this is the name the user set on their Google profile, and it is the only
   * name the application knows before asking the user for one. That is why it is exposed: so that
   * the profile of a newly signed-in user can default to it instead of starting empty.
   *
   * Deliberately not the profile's username. `User.userName` stays the name the user owns and can
   * change, which may later differ from this one, and reading it here would make the authentication
   * layer depend on the profile layer for a value it already has.
   *
   * `null` when the provider reports no name, or only blank characters, so a caller that uses it as
   * a default never stores an empty name.
   *
   * @author timo-by
   */
  val displayName: String?
}
