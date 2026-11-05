package com.artista.artista.ui.authentication

import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import java.util.concurrent.Executor

// Co-authored-by: DeepSeek Flash
/**
 * In-memory [CredentialManager] for unit tests.
 *
 * Lets a test answer a get-credential call the way the platform would - with a credential or with
 * an exception - and inspect the requests it was asked, with no dependency on the system UI.
 *
 * Only the get-credential call is implemented, because it is the only one the authentication code
 * makes. The other operations fail loudly rather than silently succeeding, so a change that starts
 * using one of them is noticed instead of passing for the wrong reason.
 *
 * @param response the credential a successful call answers with, or `null` to report [error]
 * @param error the exception the call throws, or `null` to answer with [response]
 * @author timo-by
 */
class FakeCredentialManager(
    var response: GetCredentialResponse? = null,
    var error: GetCredentialException? = null,
) : CredentialManager {

  /**
   * The requests this manager was asked, in the order it was asked, so a test can inspect what the
   * caller built.
   *
   * @author timo-by
   */
  var requests: List<GetCredentialRequest> = emptyList()
    private set

  override fun getCredentialAsync(
      context: Context,
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
  ) {
    requests += request
    executor.execute {
      val failure = error
      val answered = response
      when {
        failure != null -> callback.onError(failure)
        answered != null -> callback.onResult(answered)
        else -> callback.onError(GetCredentialUnknownException(UNCONFIGURED))
      }
    }
  }

  override fun getCredentialAsync(
      context: Context,
      pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
  ): Unit = unsupported()

  override fun prepareGetCredentialAsync(
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
  ): Unit = unsupported()

  override fun createCredentialAsync(
      context: Context,
      request: CreateCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
  ): Unit = unsupported()

  override fun clearCredentialStateAsync(
      request: ClearCredentialStateRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<Void?, ClearCredentialException>,
  ): Unit = unsupported()

  override fun createSettingsPendingIntent(): PendingIntent = unsupported()

  /**
   * Reports that the caller asked for an operation this fake does not implement.
   *
   * @return never returns
   * @author timo-by
   */
  private fun unsupported(): Nothing =
      throw UnsupportedOperationException("FakeCredentialManager only implements getCredential")

  private companion object {
    const val UNCONFIGURED = "FakeCredentialManager was asked without a configured answer"
  }
}
