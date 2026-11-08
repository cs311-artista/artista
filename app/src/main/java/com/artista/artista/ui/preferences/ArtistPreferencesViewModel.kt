package com.artista.artista.ui.preferences

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserId
import com.artista.artista.model.user.UserPreference
import com.artista.artista.model.user.UserRepository
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * UI state of the artist preferences screen.
 *
 * Kept separate from [com.artista.artista.model.user.UserPreference]: the suggested artists are not
 * user data, and the selection only becomes a preference once the user saves it.
 *
 * @property popularArtists the artists suggested to the user, in display order.
 * @property selectedArtists identifiers of the artists the user has selected as favorites.
 * @property searchQuery the text typed in the search field, exactly as the user entered it.
 * @property isSaving whether the selection is being saved, during which saving again is disabled.
 * @property errorMessageResId string resource of the message shown when saving the selection fails,
 *   or null when there is nothing to show.
 * @author Timz3rr, patrickmcdan, krfpn
 */
data class ArtistPreferencesUiState(
    val popularArtists: List<Artist> = emptyList(),
    val selectedArtists: Set<ArtistId> = emptySet(),
    val searchQuery: String = "",
    val isSaving: Boolean = false,
    @param:StringRes val errorMessageResId: Int? = null,
) {
  /**
   * The popular artists whose name matches [searchQuery], in display order.
   *
   * Derived rather than stored, so it stays correct when the artists finish loading after the user
   * has started typing.
   */
  val displayedArtists: List<Artist>
    get() {
      // Trimmed so stray spaces do not hide every artist; an empty query matches all of them.
      val query = searchQuery.trim()
      return popularArtists.filter { it.name.contains(query, ignoreCase = true) }
    }
}

/**
 * Holds the popular artists and the selection made by the user on the artist preferences screen,
 * and saves that selection in the preferences of the signed-in user.
 *
 * @param artworkRepository source of the popular artists
 * @param authRepository source of the signed-in user, whose preferences are saved
 * @param userRepository storage of the user profiles
 * @author Timz3rr, patrickmcdan, krfpn
 */
class ArtistPreferencesViewModel(
    private val artworkRepository: WikiDataRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ArtistPreferencesUiState())

  /**
   * The current state of the artist preferences screen.
   *
   * @author Timz3rr
   */
  val uiState: StateFlow<ArtistPreferencesUiState> = _uiState.asStateFlow()

  /** Identifier of the signed-in user, or null when nobody is signed in. */
  private var signedInUid: UserId? = null

  init {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = {
          // Already logged by launchCatching. The grid stays empty until a retry exists.
        },
    ) {
      _uiState.update { it.copy(popularArtists = artworkRepository.getPopularArtists()) }
    }
    // A plain launch is enough: collecting the auth state only listens to sign-ins and sign-outs
    // and does not fail (see AuthRepository.authState).
    viewModelScope.launch { authRepository.authState.collect { signedInUid = it?.uid } }
  }

  /**
   * Selects the given artist, or deselects it if it is already selected.
   *
   * @param artistId the identifier of the artist to toggle.
   * @author Timz3rr, patrickmcdan
   */
  fun toggleArtist(artistId: ArtistId) {
    _uiState.update { state ->
      val selectedArtists =
          if (artistId in state.selectedArtists) state.selectedArtists - artistId
          else state.selectedArtists + artistId
      state.copy(selectedArtists = selectedArtists)
    }
  }

  /**
   * Updates the search query, which filters the artists displayed by the screen.
   *
   * @param query the text currently typed in the search field.
   * @author krfpn
   */
  fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  /**
   * Saves the selected artists in the preferences of the signed-in user.
   *
   * Only the favorite artists of the stored profile are replaced; its other fields are kept. If
   * saving fails, [onSaved] is not called and an error message is exposed instead. When nobody is
   * signed in, nothing is saved and [onSaved] is called at once. A call made while a save is still
   * running is ignored.
   *
   * @param onSaved called once the selection is saved, e.g. to leave the screen
   * @author krfpn
   */
  fun saveSelectedArtists(onSaved: () -> Unit) {
    // A double tap would otherwise start two writes and navigate back twice.
    if (_uiState.value.isSaving) return
    // Nobody signed in (the sign-in screen is still a placeholder, and in the UI tests and the
    // preview): there is no one to save the selection for, so the screen is just left.
    val uid =
        signedInUid
            ?: run {
              onSaved()
              return
            }
    // Taken now, so a toggle made while the write runs does not change what is saved.
    val artists = _uiState.value.selectedArtists.toList()
    _uiState.update { it.copy(isSaving = true) }
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          // The user stays on the screen with their selection, so they can try again.
          _uiState.update { it.copy(isSaving = false, errorMessageResId = error.toMessageRes()) }
        },
    ) {
      saveArtists(uid, artists)
      _uiState.update { it.copy(isSaving = false) }
      // Reached only when the write succeeded, since a failure jumps to onError.
      onSaved()
    }
  }

  /**
   * Clears the error message after the screen has shown it.
   *
   * @author krfpn
   */
  fun clearErrorMessage() {
    _uiState.update { it.copy(errorMessageResId = null) }
  }

  /**
   * Saves the favorite artists of a user, creating their profile if they have none yet.
   *
   * @param uid identifier of the user
   * @param artists the favorite artists to save, in the order they were selected
   */
  private suspend fun saveArtists(uid: UserId, artists: List<ArtistId>) {
    // The stored profile is read right before writing so that the fields saved by other screens,
    // such as the username, are written back unchanged instead of being reset.
    val storedUser = findUser(uid)
    if (storedUser == null) {
      // TODO: no profile is created at sign-in yet, so a user who never saved a username has none.
      //  Once sign-in creates it, decide whether to keep this fallback (it still covers a failed
      //  creation and accounts created before that) here and in UserProfileViewModel.
      userRepository.createUser(User(uid = uid, preference = UserPreference(artists = artists)))
    } else {
      userRepository.updateUser(
          uid,
          storedUser.copy(preference = storedUser.preference.copy(artists = artists)),
      )
    }
  }

  /**
   * Reads the profile of a user.
   *
   * @param uid identifier of the user
   * @return the stored profile, or null when the user has none yet
   */
  private suspend fun findUser(uid: UserId): User? =
      try {
        userRepository.getUser(uid)
      } catch (exception: AppException.NotFound) {
        // Caught here because launchCatching would report it as a failure, while it is expected.
        null
      }

  /** Holds the log tag and the factory that creates [ArtistPreferencesViewModel]. */
  companion object {
    /** Log tag identifying messages emitted by [ArtistPreferencesViewModel]. */
    private const val TAG = "ArtistPreferencesViewModel"

    /**
     * Creates a factory for an artist-preferences ViewModel with its repository dependencies.
     *
     * @param wikiDataRepository repository used to load popular artists
     * @param authRepository source of the signed-in user
     * @param userRepository storage of the user profiles
     * @return factory that creates [ArtistPreferencesViewModel] instances
     * @author patrickmcdan, krfpn
     */
    fun provideFactory(
        wikiDataRepository: WikiDataRepository,
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtistPreferencesViewModel::class.java)) {
              return ArtistPreferencesViewModel(wikiDataRepository, authRepository, userRepository)
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
