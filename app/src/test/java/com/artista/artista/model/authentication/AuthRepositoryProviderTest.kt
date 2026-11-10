package com.artista.artista.model.authentication

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [AuthRepositoryProvider].
 *
 * Only the override is covered here: the default is the Firebase-backed repository, which cannot be
 * built on the JVM because it needs an initialised Firebase app. That default is exercised against
 * the Authentication emulator by `FirebaseAuthRepositoryTest`, and the swap covered here is what
 * the screen tests in the shell PR depend on to run without Firebase.
 *
 * @author timo-by
 */
class AuthRepositoryProviderTest {

  /**
   * Puts the application's repository back, so the swap of a test does not outlive it.
   *
   * @author timo-by
   */
  @After
  fun tearDown() {
    AuthRepositoryProvider.resetToApplicationRepository()
  }

  /**
   * Verifies that a repository set on the provider is the one the application reads back, which is
   * how every test (and every caller of the provider) points the app at an in-memory repository.
   *
   * @author timo-by
   */
  @Test
  fun anOverriddenRepositoryIsReturned() {
    val fake: AuthRepository = FakeAuthRepository()

    AuthRepositoryProvider.repository = fake

    assertSame(fake, AuthRepositoryProvider.repository)
  }

  /**
   * Verifies that resetting the provider discards the installed repository, which is what lets a
   * test swap in a fake without deciding the session of the tests that follow it.
   *
   * @author timo-by
   */
  @Test
  fun resettingToTheApplicationRepositoryDiscardsTheOverride() {
    AuthRepositoryProvider.repository = FakeAuthRepository()

    AuthRepositoryProvider.resetToApplicationRepository()

    assertFalse(AuthRepositoryProvider.isOverridden)
  }
}
