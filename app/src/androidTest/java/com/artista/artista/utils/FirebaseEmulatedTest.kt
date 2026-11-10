// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.utils

import com.artista.artista.model.user.UserRepository
import com.artista.artista.model.user.UserRepositoryFirestore
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout

/**
 * Base class for tests that exercise a Firestore-backed user repository.
 *
 * It creates the repository against the local emulator and isolates each test by clearing the user
 * database before and after execution. Each test runs signed in to a fresh account, because the
 * security rules only open `Users/{uid}` to its owner.
 *
 * @author krfpn
 */
open class FirebaseEmulatedTest {

  /** Prevents emulator operations from leaving a test blocked indefinitely. */
  @get:Rule val testTimeout: Timeout = Timeout.seconds(30)

  /** Repository connected to the local Firestore emulator. */
  protected lateinit var repository: UserRepository

  /** Uid of the account signed in for the current test. */
  protected lateinit var testUserId: String

  /** Connects the repository to the emulator and clears stale test data. */
  @Before
  open fun setUp() {
    FirebaseEmulator.auth.signOut()
    FirebaseEmulator.clearFirestore()
    FirebaseEmulator.clearAccounts()
    val signIn = FirebaseEmulator.auth.signInAnonymously()
    testUserId = checkNotNull(Tasks.await(signIn, 10, TimeUnit.SECONDS).user).uid
    repository = UserRepositoryFirestore(FirebaseEmulator.firestore)
  }

  /** Removes test data after the test completes. */
  @After
  open fun tearDown() {
    FirebaseEmulator.auth.signOut()
    FirebaseEmulator.clearFirestore()
    FirebaseEmulator.clearAccounts()
  }
}
