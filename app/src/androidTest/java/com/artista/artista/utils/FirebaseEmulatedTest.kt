package com.artista.artista.utils

import com.artista.artista.model.user.UserRepository
import com.artista.artista.model.user.UserRepositoryFirestore
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout

/**
 * Base class for tests that exercise a Firestore-backed user repository.
 *
 * It creates the repository against the local emulator and isolates each test by clearing the user
 * collection before and after execution.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
open class FirebaseEmulatedTest {

  /** Prevents emulator operations from leaving a test blocked indefinitely. */
  @get:Rule val testTimeout: Timeout = Timeout.seconds(30)

  /** Repository connected to the local Firestore emulator. */
  protected lateinit var repository: UserRepository

  /** Identifier reserved for the current test. */
  protected lateinit var testUserId: String

  /** Connects the repository to the emulator and clears stale test data. */
  @Before
  open fun setUp() {
    FirebaseEmulator.clearUsers()
    repository = UserRepositoryFirestore(FirebaseEmulator.firestore)
    testUserId = "repository-test-${UUID.randomUUID()}"
  }

  /** Removes test data after the test completes. */
  @After
  open fun tearDown() {
    FirebaseEmulator.clearUsers()
  }
}
