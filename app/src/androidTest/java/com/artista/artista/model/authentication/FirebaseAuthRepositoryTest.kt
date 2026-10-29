package com.artista.artista.model.authentication

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.utils.FakeJwtGenerator
import com.artista.artista.utils.FirebaseEmulator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
/**
 * Instrumented tests for [FirebaseAuthRepository], run against the local Authentication emulator.
 *
 * Covers what the in-memory fake cannot: that a session created elsewhere is observed through
 * [FirebaseAuthRepository.authState], that [FirebaseAuthRepository.signIn] exchanges a Google ID
 * token for a session, and that [FirebaseAuthRepository.signOut] really clears it.
 *
 * The exchange is reachable because the emulator accepts the unsigned tokens [FakeJwtGenerator]
 * produces. The account chooser still needs a device; the provider's unit tests cover the request
 * it builds. The emulator mints its own uid, so a sign-in assertion compares uids, never the
 * token's subject.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthRepositoryTest {

  /** Prevents emulator operations from leaving a test blocked indefinitely. */
  @get:Rule val testTimeout: Timeout = Timeout.seconds(30)

  private lateinit var repository: AuthRepository

  /** Starts every test from a signed-out session, with no account left by an earlier run. */
  @Before
  fun setUp() {
    FirebaseEmulator.auth.signOut()
    FirebaseEmulator.clearAccounts()
    repository = FirebaseAuthRepository(FirebaseEmulator.auth)
  }

  /** Leaves neither a session nor an account behind for the next test. */
  @After
  fun tearDown() {
    FirebaseEmulator.auth.signOut()
    FirebaseEmulator.clearAccounts()
  }

  /** Verifies that a repository with no session reports a signed-out state. */
  @Test
  fun authState_emitsSignedOut_whenThereIsNoSession() = runTest {
    assertNull(repository.authState.first())
  }

  /** Verifies that the state flow observes a session that was created without the repository. */
  @Test
  fun authState_emitsTheUserOfASessionCreatedElsewhere() = runTest {
    val signedInUid = FirebaseEmulator.auth.signInAnonymously().await().user?.uid

    val observed = repository.authState.first { it != null }

    assertEquals(signedInUid, observed?.uid)
  }

  /**
   * Verifies that signing in twice with the same token reaches the same account, and that the state
   * flow reports the session the sign-in returned.
   *
   * Account continuity is what a returning user depends on. The uid is not compared with the
   * token's subject because the emulator mints its own.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withTheSameTokenTwice_returnsTheSameAccount() = runTest {
    val token = FakeJwtGenerator.generateFakeJwt()

    val first = repository.signIn(token).getOrThrow()
    val second = repository.signIn(token).getOrThrow()

    assertTrue(first.uid.isNotBlank())
    assertEquals(first.uid, second.uid)
    assertEquals(first.uid, repository.authState.first { it != null }?.uid)
  }

  /**
   * Verifies that the name the Google account reports is exposed as the signed-in account's display
   * name, the value a new profile defaults its username to.
   *
   * The mapping itself is unit-tested by `GoogleAccountMappingTest`; what only the emulator can
   * check is that the name read from the Firebase account is what the mapping receives, rather than
   * another field such as the e-mail address.
   *
   * @author timo-by
   */
  @Test
  fun signIn_exposesTheDisplayName() = runTest {
    val name = "Alice Martin"

    val signedIn = repository.signIn(FakeJwtGenerator.generateFakeJwt(name = name)).getOrThrow()

    assertEquals(name, signedIn.displayName)
  }

  /**
   * Verifies that two different Google accounts do not collapse into one session.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withTwoDifferentTokens_reachesTwoAccounts() = runTest {
    val first = repository.signIn(FakeJwtGenerator.generateFakeJwt()).getOrThrow()
    val second = repository.signIn(FakeJwtGenerator.generateFakeJwt()).getOrThrow()

    assertNotEquals(first.uid, second.uid)
  }

  /**
   * Verifies that an account whose Google profile carries no name exposes none, so a profile screen
   * asks for a name instead of defaulting to an empty one.
   *
   * The named account is signed in first on purpose: a `null` on its own also holds for a
   * repository that never reads the name.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withoutANameClaim_exposesNoDisplayName() = runTest {
    repository.signIn(FakeJwtGenerator.generateFakeJwt(name = "Alice")).getOrThrow()

    val nameless = repository.signIn(FakeJwtGenerator.generateFakeJwt()).getOrThrow()

    assertNull(nameless.displayName)
  }

  /**
   * Verifies that a name made only of spaces reaches the caller as none rather than as a blank
   * string.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withABlankNameClaim_exposesNoDisplayName() = runTest {
    val unnamed = repository.signIn(FakeJwtGenerator.generateFakeJwt(name = "   ")).getOrThrow()

    assertNull(unnamed.displayName)
  }

  /**
   * Verifies that the padding a real Google profile sometimes carries is gone by the time the name
   * is exposed.
   *
   * @author timo-by
   */
  @Test
  fun signIn_trimsTheReportedName() = runTest {
    val padded =
        repository.signIn(FakeJwtGenerator.generateFakeJwt(name = "  Alice  ")).getOrThrow()

    assertEquals("Alice", padded.displayName)
  }

  /**
   * Verifies that a name containing characters JSON has to escape arrives intact, which is what
   * lets every other test in this class trust the generator's escaping.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withANameContainingQuotes_exposesItVerbatim() = runTest {
    val quoted = "Alice \"Al\" Martin"

    val signedIn = repository.signIn(FakeJwtGenerator.generateFakeJwt(name = quoted)).getOrThrow()

    assertEquals(quoted, signedIn.displayName)
  }

  /**
   * Verifies that a non-ASCII name survives the token round trip unchanged.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withANonAsciiName_exposesItVerbatim() = runTest {
    val name = "Amélie Müller"

    val signedIn = repository.signIn(FakeJwtGenerator.generateFakeJwt(name = name)).getOrThrow()

    assertEquals(name, signedIn.displayName)
  }

  /**
   * Verifies that the state flow carries the display name, not only the sign-in result, so a screen
   * collecting a restored session has the name to default to.
   *
   * @author timo-by
   */
  @Test
  fun authState_carriesTheDisplayNameOfTheSession() = runTest {
    val name = "Alice Martin"

    repository.signIn(FakeJwtGenerator.generateFakeJwt(name = name)).getOrThrow()

    assertEquals(name, repository.authState.first { it != null }?.displayName)
  }

  /**
   * Verifies that each session exposes the name of its own account, which a repository reading one
   * cached name would get wrong once a second account signed in.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withTwoAccounts_exposesEachAccountsOwnName() = runTest {
    val alice = repository.signIn(FakeJwtGenerator.generateFakeJwt(name = "Alice")).getOrThrow()
    val bob = repository.signIn(FakeJwtGenerator.generateFakeJwt(name = "Bob")).getOrThrow()

    assertEquals("Alice", alice.displayName)
    assertEquals("Bob", bob.displayName)
    assertEquals("Bob", repository.authState.first { it != null }?.displayName)
  }

  /**
   * Verifies that signing in again with the same account reports the same name.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withTheSameAccountTwice_exposesTheSameDisplayName() = runTest {
    val token = FakeJwtGenerator.generateFakeJwt(name = "Alice Martin")

    val first = repository.signIn(token).getOrThrow()
    val second = repository.signIn(token).getOrThrow()

    assertEquals(first.displayName, second.displayName)
  }

  /**
   * Verifies that a token the emulator rejects reaches the caller as a domain error rather than an
   * SDK exception. The emulator answers a request it cannot decode with `INVALID_ID_TOKEN`, which
   * the SDK raises as a rejected credential.
   *
   * @author timo-by
   */
  @Test
  fun signIn_withATokenTheEmulatorRejects_failsWithInvalidCredentials() = runTest {
    val error = (repository.signIn(NOT_A_TOKEN).exceptionOrNull() as AuthException).error

    assertEquals(AuthError.InvalidCredentials, error)
  }

  /** Verifies that signing out clears the session the state flow reports. */
  @Test
  fun signOut_clearsTheSession() = runTest {
    FirebaseEmulator.auth.signInAnonymously().await()

    assertTrue(repository.signOut().isSuccess)

    assertNull(repository.authState.first())
  }

  /** Verifies that signing out without a session succeeds instead of failing. */
  @Test
  fun signOut_succeedsWhenAlreadySignedOut() = runTest {
    assertTrue(repository.signOut().isSuccess)
    assertNull(repository.authState.first())
  }

  private companion object {
    /** A string the emulator cannot decode as a JWT. */
    const val NOT_A_TOKEN = "not-a-json-web-token"
  }
}
