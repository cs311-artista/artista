package com.artista.artista.model.authentication

// Co-authored-by: DeepSeek Flash
/**
 * Minimal representation of an authenticated user.
 *
 * Kept thin and decoupled from the `User` profile so the sign-in logic can be written before that
 * class exists.
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
   * Name the identity provider reports for this account, so a new profile can default its username
   * to it instead of starting empty.
   *
   * Deliberately not the profile's username: `User.userName` stays the name the user owns and can
   * change. `null` when the provider reports no name, or only blank characters.
   *
   * @author timo-by
   */
  val displayName: String?
}
