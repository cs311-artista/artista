// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserId
import com.artista.artista.model.user.UserPreference
import com.artista.artista.model.user.UserRepository
import com.artista.artista.model.user.UsernameCache
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import java.text.Normalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Maximum number of characters of a username, counted after trimming. */
const val MAX_USERNAME_LENGTH = 20

/**
 * Cleans up a username before it is validated or saved.
 *
 * @param name the username as typed by the user.
 * @return the trimmed username in Unicode NFC form.
 * @author Timz3rr
 */
fun normalizeUsername(name: String): String =
    // NFC turns "e" + combining acute accent into a single "é", so accents typed or pasted in
    // decomposed form count as letters and as one character for the length limit.
    Normalizer.normalize(name.trim(), Normalizer.Form.NFC)

/**
 * Checks whether a username entered by the user can be saved.
 *
 * The name is checked once normalized by [normalizeUsername], the form in which it is saved. It
 * must be non-empty, at most [MAX_USERNAME_LENGTH] characters long, and contain only letters
 * (accents included), digits and spaces.
 *
 * @param name the username as typed by the user.
 * @return true if the username can be saved, false otherwise.
 * @author Timz3rr
 */
fun isValidUsername(name: String): Boolean {
  val normalized = normalizeUsername(name)
  // A plain space is checked instead of isWhitespace() so that tabs and line breaks are rejected.
  return normalized.isNotEmpty() &&
      normalized.length <= MAX_USERNAME_LENGTH &&
      normalized.all { it.isLetterOrDigit() || it == ' ' }
}

/**
 * UI state of the user profile screen.
 *
 * @property username the display name of the user, or null when the user has not chosen one.
 * @property errorMessageResId string resource of the message shown when loading or saving the
 *   profile fails, or null when there is nothing to show.
 * @author Timz3rr
 */
data class UserProfileUiState(
    val username: String? = null,
    @param:StringRes val errorMessageResId: Int? = null,
)

/**
 * Holds the state displayed by the user profile screen, and keeps the username of the signed-in
 * user in sync with the user repository, which stays the source of truth, and with a local copy
 * that lets the name be displayed offline.
 *
 * @param authRepository source of the signed-in user, whose profile is loaded and saved
 * @param userRepository storage of the user profiles
 * @param usernameCache local copy of the username, so that it can be displayed offline
 * @author Timz3rr
 */
class UserProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val usernameCache: UsernameCache,
) : ViewModel() {

  private val _uiState = MutableStateFlow(UserProfileUiState())

  /**
   * The current state of the user profile screen.
   *
   * @author Timz3rr
   */
  val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

  /** Identifier of the signed-in user, or null when nobody is signed in. */
  private var signedInUid: UserId? = null

  // A slow load for a user who has since signed out could finish last and display their name for
  // the next user, and a save replaced by a newer rename or a sign-out must not report or cache
  // anything, so only the latest load or save of the current user is kept running.
  private var profileJob: Job? = null

  // A rename replaced while still running was never saved, so a failed rename goes back to this
  // name and not to the one displayed just before it.
  private var lastSavedUsername: String? = null

  init {
    // A plain launch is enough: collecting the auth state only listens to sign-ins and sign-outs
    // and does not fail. The profile load, which can fail, is protected by launchCatching in
    // onAuthStateChanged.
    viewModelScope.launch { authRepository.authState.collect(::onAuthStateChanged) }
  }

  /**
   * Loads the profile of the user who just signed in, or clears the one of the user who left.
   *
   * @param authUser the signed-in user, or null when nobody is signed in
   */
  private fun onAuthStateChanged(authUser: AuthUser?) {
    // Stops the load or save of the previous user: if Alice signs out and Bob signs in while her
    // slow load is still running, its late result would otherwise replace Bob's name with Alice's.
    profileJob?.cancel()
    lastSavedUsername = null
    if (authUser == null) {
      // Nobody signed in from the start (e.g. before Google sign-in is wired): a username already
      // displayed belongs to no one who left, so it is kept.
      signedInUid?.let(::forgetUser)
      signedInUid = null
      return
    }
    signedInUid = authUser.uid
    // Clears the previous user's name, so it is not displayed while the new profile loads.
    _uiState.update { it.copy(username = null) }
    launchProfileJob(
        onError = { error ->
          // Being offline is expected when a cached name is displayed: the user has what they need
          // on screen, so a toast at every start without connection would only be noise.
          val offlineWithName = error is AppException.Network && _uiState.value.username != null
          if (!offlineWithName) {
            _uiState.update { it.copy(errorMessageResId = error.toMessageRes()) }
          }
        },
    ) {
      loadProfile(authUser.uid)
    }
  }

  /**
   * Removes what is kept about a user who signed out.
   *
   * @param uid identifier of the user who left
   */
  private fun forgetUser(uid: UserId) {
    _uiState.update { it.copy(username = null) }
    // Cleared so that no name stays on a shared phone and a rename cancelled by the sign-out cannot
    // leave a stale value. Not part of profileJob, which the next sign-in would cancel.
    viewModelScope.launch { usernameCache.clearUsername(uid) }
  }

  /**
   * Displays the cached name of a user, then replaces it with the stored profile if there is one.
   *
   * @param uid identifier of the user
   */
  private suspend fun loadProfile(uid: UserId) {
    val cachedUsername = usernameCache.getUsername(uid)
    lastSavedUsername = cachedUsername
    _uiState.update { it.copy(username = cachedUsername) }
    // No profile leaves the cache alone: a rename made offline is cached before its profile reaches
    // Firestore, so a missing profile does not mean the cached name is wrong.
    val user = findUser(uid) ?: return
    setUser(user)
    val storedUsername = _uiState.value.username
    lastSavedUsername = storedUsername
    writeCache(uid, storedUsername)
  }

  /**
   * Starts the given work as the new [profileJob].
   *
   * @param onError reports a failure of [block] that happened while the job was still running
   * @param undo reverts, before [onError] runs, what [block] already wrote
   * @param block the work to run
   */
  private fun launchProfileJob(
      onError: (AppException) -> Unit,
      undo: suspend () -> Unit = {},
      block: suspend () -> Unit,
  ) {
    profileJob =
        viewModelScope.launchCatching(tag = TAG, onError = onError) {
          try {
            block()
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            // launchCatching only keeps a failure arriving after cancellation from onError. It runs
            // too late to protect undo, which would overwrite what the next user or rename cached.
            ensureActive()
            undo()
            throw exception
          }
        }
  }

  /**
   * Makes the cache hold the given name.
   *
   * @param uid identifier of the user
   * @param username the name to cache, or null to remove the entry of the user
   */
  private suspend fun writeCache(uid: UserId, username: String?) {
    if (username == null) usernameCache.clearUsername(uid)
    else usernameCache.saveUsername(uid, username)
  }

  /**
   * Displays the given user on the profile screen.
   *
   * @param user the user whose profile is displayed.
   * @author Timz3rr
   */
  fun setUser(user: User) {
    // A blank name carries no information, if username null then USERNAME is displayed, else the
    // name is.
    _uiState.update { state -> state.copy(username = user.userName?.takeIf { it.isNotBlank() }) }
    // copy() keeps the other state fields instead of resetting them to their defaults.
  }

  /**
   * Replaces the displayed username with the one entered by the user, and saves it for the
   * signed-in user.
   *
   * The new name is displayed and cached at once, and replaces a save still running. If saving it
   * fails, the last name really saved is displayed and cached again and an error message is
   * exposed. When nobody is signed in, the name is only kept in memory.
   *
   * @param newName the username as typed by the user; ignored if [isValidUsername] rejects it.
   * @author Timz3rr
   */
  fun updateUsername(newName: String) {
    // The dialog already disables saving an invalid name; checking again keeps the state valid
    // whatever the caller.
    if (!isValidUsername(newName)) return
    // A load still running would overwrite the new name with the stored one when it finishes, and
    // a save still running is replaced by this one.
    profileJob?.cancel()
    val username = normalizeUsername(newName)
    _uiState.update { it.copy(username = username) }

    // Nobody signed in (as in the UI tests and the preview): there is no one to save the name for,
    // so it is only kept in memory.
    val uid = signedInUid ?: return
    launchProfileJob(
        onError = { error ->
          // The name was displayed before being saved, so a failed save puts the last saved one
          // back and the screen shows the error in a toast. A successful save needs nothing more.
          _uiState.update {
            it.copy(username = lastSavedUsername, errorMessageResId = error.toMessageRes())
          }
        },
        undo = { writeCache(uid, lastSavedUsername) },
    ) {
      // Written before Firestore because offline set().await() does not complete until the
      // connection returns, so a write placed after it would be lost on a cold start.
      usernameCache.saveUsername(uid, username)
      saveUsername(uid, username)
      lastSavedUsername = username
    }
  }

  /**
   * Clears the error message after the screen has shown it.
   *
   * @author Timz3rr
   */
  fun clearErrorMessage() {
    _uiState.update { it.copy(errorMessageResId = null) }
  }

  /**
   * Saves the username of a user, creating their profile if they have none yet.
   *
   * Two cases:
   * - the user has no stored profile yet, as after their first sign-in: a profile is created with
   *   this username and empty preferences;
   * - the user already has one: only its username is replaced, its other fields are kept.
   *
   * @param uid identifier of the user
   * @param username the username to save, already cleaned up by [normalizeUsername] (trimmed and in
   *   NFC form), so the stored name is exactly the one displayed
   */
  private suspend fun saveUsername(uid: UserId, username: String) {
    // The stored profile is read first so that the fields saved by other screens, such as the
    // preferences, are written back unchanged instead of being reset.
    val storedUser = findUser(uid)
    if (storedUser == null) {
      // Kept as a fallback for accounts created before initial profile creation was added at
      // sign-in or in case initial profile creation at sign-in failed.
      userRepository.createUser(User(uid = uid, userName = username, preference = UserPreference()))
    } else {
      userRepository.updateUser(uid, storedUser.copy(userName = username))
    }
  }

  /**
   * Reads the profile of a user.
   *
   * @param uid identifier of the user
   * @return the stored profile, or null when the user has none yet, as on their first sign-in
   */
  private suspend fun findUser(uid: UserId): User? =
      try {
        userRepository.getUser(uid)
      } catch (exception: AppException.NotFound) {
        // Caught here because launchCatching would report it as a failure, while it is expected.
        null
      }

  /** Holds the log tag and the factory that creates [UserProfileViewModel]. */
  companion object {
    /** Log tag identifying messages emitted by [UserProfileViewModel]. */
    private const val TAG = "UserProfileViewModel"

    /**
     * Creates a factory for a profile ViewModel with its repository dependencies.
     *
     * @param authRepository source of the signed-in user
     * @param userRepository storage of the user profiles
     * @param usernameCache local copy of the username, so that it can be displayed offline
     * @return a factory that creates [UserProfileViewModel] instances
     * @author Timz3rr
     */
    fun provideFactory(
        authRepository: AuthRepository,
        userRepository: UserRepository,
        usernameCache: UsernameCache,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserProfileViewModel::class.java)) {
              return UserProfileViewModel(authRepository, userRepository, usernameCache) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
