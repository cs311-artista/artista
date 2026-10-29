package com.artista.artista.utils

import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.model.user.USER_COLLECTION_PATH
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

// Co-authored-by: DeepSeek Flash
/**
 * Provides the Firebase services used by instrumented tests.
 *
 * The services are configured once for the Android emulator and all test data is removed between
 * tests through [clearUsers] and [clearAccounts].
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
object FirebaseEmulator {

  /** Android-emulator alias for the host machine. */
  const val HOST = "10.0.2.2"

  /** Port used by the local Firestore emulator. */
  const val FIRESTORE_PORT = 8080

  /** Port used by the local Authentication emulator. */
  const val AUTH_PORT = 9099

  /** How long [clearAccounts] waits for the emulator before the test fails instead of hanging. */
  private const val CLEAR_TIMEOUT_MILLIS = 10_000

  /** Firestore service configured to use the local emulator. */
  val firestore: FirebaseFirestore by lazy {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }

    FirebaseFirestore.getInstance().also { it.useEmulator(HOST, FIRESTORE_PORT) }
  }

  /**
   * Authentication service configured to use the local emulator.
   *
   * Sessions are isolated by signing out between tests. Accounts created against the emulator
   * outlive the process and pile up across runs; [clearAccounts] removes them.
   */
  val auth: FirebaseAuth by lazy {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }

    FirebaseAuth.getInstance().also { it.useEmulator(HOST, AUTH_PORT) }
  }

  /** Deletes every user document from the emulator. */
  fun clearUsers() {
    val documents =
        Tasks.await(
            firestore.collection(USER_COLLECTION_PATH).get(),
            10,
            TimeUnit.SECONDS,
        )
    documents.documents.forEach { Tasks.await(it.reference.delete(), 10, TimeUnit.SECONDS) }
  }

  /**
   * Deletes every account from the Authentication emulator.
   *
   * The SDK exposes no call for this, so the emulator's own REST endpoint is used. It is the
   * counterpart of [clearUsers]: without it, accounts created by an earlier run stay visible to the
   * next one.
   *
   * This deletes every account in the emulator, not only the ones a test created: it is a side
   * effect shared by every test in the same process, so a test that needs an account has to create
   * it after this has run. Timeouts are set because it is called from both the set-up and the
   * tear-down of a class, where a wedged emulator would hang the run instead of failing it.
   *
   * @author timo-by
   */
  fun clearAccounts() {
    val projectId =
        checkNotNull(FirebaseApp.getInstance().options.projectId) {
          "the Firebase project ID is required to reach the Authentication emulator"
        }
    val connection =
        (URL("http://$HOST:$AUTH_PORT/emulator/v1/projects/$projectId/accounts").openConnection()
                as HttpURLConnection)
            .apply {
              requestMethod = "DELETE"
              connectTimeout = CLEAR_TIMEOUT_MILLIS
              readTimeout = CLEAR_TIMEOUT_MILLIS
            }
    try {
      check((connection.responseCode in 200..299)) {
        "the Authentication emulator refused to delete its accounts: ${connection.responseCode}"
      }
    } finally {
      connection.disconnect()
    }
  }
}
