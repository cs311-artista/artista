package com.artista.artista.utils

import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.model.user.USER_COLLECTION_PATH
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/**
 * Provides the Firebase services used by instrumented tests.
 *
 * The services are configured once for the Android emulator and all test data is removed through
 * [clearUsers] between tests.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
object FirebaseEmulator {

  /** Android-emulator alias for the host machine. */
  const val HOST = "10.0.2.2"

  /** Port used by the local Firestore emulator. */
  const val FIRESTORE_PORT = 8080

  /** Firestore service configured to use the local emulator. */
  val firestore: FirebaseFirestore by lazy {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }

    FirebaseFirestore.getInstance().also { it.useEmulator(HOST, FIRESTORE_PORT) }
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
}
