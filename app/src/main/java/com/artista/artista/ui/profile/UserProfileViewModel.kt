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
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import java.text.Normalizer
import kotlinx.coroutines.Job
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
 * user in sync with the user repository.
 *
 * @param authRepository source of the signed-in user, whose profile is loaded and saved
 * @param userRepository storage of the user profiles
 * @author Timz3rr
 */
class UserProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
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
  // the next user, so only the load of the current user is kept running.
  private var loadJob: Job? = null

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
    // Stops the load of the previous user: if Alice signs out and Bob signs in while her slow load
    // is still running, its late result would otherwise replace Bob's name with Alice's.
    loadJob?.cancel()
    if (authUser == null) {
      // Nobody signed in from the start (e.g. before Google sign-in is wired): a username already
      // displayed belongs to no one who left, so it is kept.
      if (signedInUid != null) _uiState.update { it.copy(username = null) }
      signedInUid = null
      return
    }
    signedInUid = authUser.uid
    // Clears the previous user's name, so it is not displayed while the new profile loads.
    _uiState.update { it.copy(username = null) }
    loadJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              _uiState.update { it.copy(errorMessageResId = error.toMessageRes()) }
            },
        ) {
          findUser(authUser.uid)?.let(::setUser)
        }
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
   * The new name is displayed at once. If saving it fails, the previous name is displayed again and
   * an error message is exposed. When nobody is signed in, the name is only kept in memory.
   *
   * @param newName the username as typed by the user; ignored if [isValidUsername] rejects it.
   * @author Timz3rr
   */
  fun updateUsername(newName: String) {
    // The dialog already disables saving an invalid name; checking again keeps the state valid
    // whatever the caller.
    if (!isValidUsername(newName)) return
    val previousUsername = _uiState.value.username
    val username = normalizeUsername(newName)
    _uiState.update { it.copy(username = username) }

    // Nobody signed in (as in the UI tests and the preview): there is no one to save the name for,
    // so it is only kept in memory.
    val uid = signedInUid ?: return
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          // The name was displayed before being saved, so a failed save puts the previous one back
          // and the screen shows the error in a toast. A successful save needs nothing more.
          _uiState.update {
            it.copy(username = previousUsername, errorMessageResId = error.toMessageRes())
          }
        },
    ) {
      saveUsername(uid, username)
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
     * @return a factory that creates [UserProfileViewModel] instances
     * @author Timz3rr
     */
    fun provideFactory(
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserProfileViewModel::class.java)) {
              return UserProfileViewModel(authRepository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
