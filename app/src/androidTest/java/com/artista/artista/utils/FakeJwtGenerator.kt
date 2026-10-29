package com.artista.artista.utils

import android.util.Base64
import java.util.concurrent.atomic.AtomicInteger

// Co-authored-by: DeepSeek Flash
/**
 * Generates the unsigned Google ID tokens the local Authentication emulator accepts.
 *
 * The emulator verifies neither the signature nor the issuer of an ID token: it decodes the payload
 * and creates the account its claims describe. That is what lets the token exchange be tested
 * without a real Google account or the account chooser, which no instrumented test can drive. A
 * signed token would be needed against the production project, where the signature is checked.
 *
 * The claims mirror what Google issues: issuer, subject, audience, validity window, and a verified
 * e-mail address.
 *
 * The subject identifies the Google account, not the Firebase one: the emulator mints its own
 * opaque uid for the account it creates, and two tokens carrying the same subject resolve to the
 * same account, and therefore the same uid, across sign-ins. Tests must assert that continuity
 * rather than comparing a uid with a subject.
 *
 * @author timo-by
 */
object FakeJwtGenerator {

  /** Used to give each generated token a distinct subject when the caller does not pick one. */
  private val counter = AtomicInteger(0)

  /** Header of an unsigned token, which is what the emulator accepts. */
  private const val HEADER = """{"alg":"none","typ":"JWT"}"""

  /** Audience claim; the emulator does not compare it with the project's client ID. */
  private const val AUDIENCE = "fake-audience"

  /** Claim issuer, matching the one Google sets on the tokens it issues. */
  private const val ISSUER = "https://accounts.google.com"

  /** How long a generated token stays valid, in seconds. */
  private const val LIFETIME_SECONDS = 3600L

  /**
   * Builds an unsigned Google ID token.
   *
   * @param userId the subject claim; it identifies the Google account, and signing in again with
   *   the same subject reuses the Firebase account created the first time. The account's uid is
   *   minted by the emulator and is not this value
   * @param email the e-mail claim, which the emulator requires to be verified
   * @return a three-segment JWT, signed with an empty signature
   * @author timo-by
   */
  fun generateFakeJwt(
      userId: String = nextUserId(),
      email: String = "$userId@example.com",
  ): String {
    val issuedAt = System.currentTimeMillis() / 1000
    val payload =
        """{"iss":"$ISSUER","sub":"$userId","aud":"$AUDIENCE","iat":$issuedAt,""" +
            """"exp":${issuedAt + LIFETIME_SECONDS},"email":"$email","email_verified":true}"""

    return "${encode(HEADER)}.${encode(payload)}.${encode("")}"
  }

  /**
   * Produces the next unused subject.
   *
   * A distinct subject per token keeps an account created by one test from being reused by another.
   *
   * @return a subject unique within this test process
   * @author timo-by
   */
  private fun nextUserId(): String = "fake-user-${counter.incrementAndGet()}"

  /**
   * Encodes one JWT segment.
   *
   * @param segment the raw header or payload
   * @return the URL-safe base64 form without padding
   * @author timo-by
   */
  private fun encode(segment: String): String =
      Base64.encodeToString(
          segment.toByteArray(),
          Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
      )
}
