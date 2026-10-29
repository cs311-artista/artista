package com.artista.artista.model.authentication

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
}
