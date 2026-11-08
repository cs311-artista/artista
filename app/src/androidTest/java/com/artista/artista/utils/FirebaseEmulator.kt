// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.utils

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Provides the Firebase services used by instrumented tests.
 *
 * The services are configured once for the Android emulator and all test data is removed between
 * tests through [clearFirestore] and [clearAccounts].
 *
 * @author krfpn
 */
object FirebaseEmulator {

  /** Android-emulator alias for the host machine. */
  const val HOST = "10.0.2.2"

  /** Port used by the local Firestore emulator. */
  const val FIRESTORE_PORT = 8080

  /** Port used by the local Authentication emulator. */
  const val AUTH_PORT = 9099

  /** How long an emulator REST call waits before the test fails instead of hanging. */
  private const val REQUEST_TIMEOUT_MILLIS = 10_000L

  /** Token the Firestore emulator treats as an admin, so the request skips the security rules. */
  private const val OWNER_TOKEN = "Bearer owner"

  /** Content type of the JSON bodies sent to the Firestore emulator. */
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  /** HTTP client shared by every call to the emulators' REST endpoints. */
  private val httpClient: OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
          .readTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
          .build()

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

  /**
   * Deletes every document from the Firestore emulator.
   *
   * The security rules only open a user's own documents, so the collection can't be listed and
   * cleared through the SDK; the emulator's REST endpoint skips the rules.
   *
   * @author krfpn
   */
  fun clearFirestore() {
    sendEmulatorRequest(
        port = FIRESTORE_PORT,
        method = "DELETE",
        path = "emulator/v1/projects/${projectId()}/databases/(default)/documents",
        failureMessage = "the Firestore emulator refused to delete its documents",
        asOwner = true,
    )
  }

  /**
   * Creates a raw document while skipping the security rules.
   *
   * Seeds documents the rules reject, such as a profile without its `uid` field, to check that the
   * app still handles them.
   *
   * @param collection collection path relative to the database root, such as `Users`
   * @param documentId identifier of the document to create
   * @param fieldsJson the document's `fields` object in the Firestore REST format
   * @author krfpn
   */
  fun createDocumentBypassingRules(collection: String, documentId: String, fieldsJson: String) {
    // POST with an explicit id fails if the document already exists, so seeding never overwrites.
    sendEmulatorRequest(
        port = FIRESTORE_PORT,
        method = "POST",
        path =
            "v1/projects/${projectId()}/databases/(default)/documents/$collection" +
                "?documentId=$documentId",
        failureMessage = "the Firestore emulator refused to create $collection/$documentId",
        asOwner = true,
        body = """{"fields":$fieldsJson}""",
    )
  }

  /**
   * Deletes every account from the Authentication emulator.
   *
   * The SDK exposes no call for this, so the emulator's own REST endpoint is used. It is the
   * counterpart of [clearFirestore]: without it, accounts created by an earlier run stay visible to
   * the next one.
   *
   * This deletes every account in the emulator, not only the ones a test created: it is a side
   * effect shared by every test in the same process, so a test that needs an account has to create
   * it after this has run. Timeouts are set because it is called from both the set-up and the
   * tear-down of a class, where a wedged emulator would hang the run instead of failing it.
   *
   * @author timo-by
   */
  fun clearAccounts() {
    sendEmulatorRequest(
        port = AUTH_PORT,
        method = "DELETE",
        path = "emulator/v1/projects/${projectId()}/accounts",
        failureMessage = "the Authentication emulator refused to delete its accounts",
    )
  }

  /** Returns the Firebase project ID that the emulators' REST endpoints need. */
  private fun projectId(): String =
      checkNotNull(FirebaseApp.getInstance().options.projectId) {
        "the Firebase project ID is required to reach the emulators"
      }

  /**
   * Sends a request to one of the emulators' REST endpoints and fails if it is refused.
   *
   * @param port port of the emulator to reach
   * @param method HTTP method to use
   * @param path endpoint path after the emulator's host and port
   * @param failureMessage start of the error message if the emulator refuses the request
   * @param asOwner whether to send the admin token that makes Firestore skip the security rules
   * @param body optional JSON body
   * @throws IllegalStateException if the emulator answers with a non-2xx status
   */
  private fun sendEmulatorRequest(
      port: Int,
      method: String,
      path: String,
      failureMessage: String,
      asOwner: Boolean = false,
      body: String? = null,
  ) {
    val request =
        Request.Builder()
            .url("http://$HOST:$port/$path")
            .method(method, body?.toRequestBody(JSON_MEDIA_TYPE))
            .apply { if (asOwner) header("Authorization", OWNER_TOKEN) }
            .build()
    // use {} closes the response so its connection goes back to the pool.
    httpClient.newCall(request).execute().use { response ->
      check(response.isSuccessful) { "$failureMessage: ${response.code}" }
    }
  }
}
