package com.artista.artista.model.authentication

// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
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
   * URL of the picture the identity provider reports for this account, or `null` when it reports
   * none.
   *
   * Declared with a default so that implementations with nothing to say about a picture (fakes,
   * previews) keep compiling without naming it.
   *
   * @author timo-by
   */
  val photoUrl: String?
    get() = null
}
