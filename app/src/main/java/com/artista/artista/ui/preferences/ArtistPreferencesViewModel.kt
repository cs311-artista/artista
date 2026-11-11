package com.artista.artista.ui.preferences

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserId
import com.artista.artista.model.user.UserPreference
import com.artista.artista.model.user.UserRepository
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
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
 * @property savedArtists identifiers of the artists in the last saved selection, compared with
 *   [selectedArtists] to know whether there is anything to save.
 * @property isSaving whether the selection is being saved, during which saving again is disabled.
 * @property isLoadingSavedArtists whether the stored artists of the signed-in user are loading,
 *   during which the artists cannot be selected, so a tap is not replaced by the loaded selection.
 * @property messageResId string resource of the message shown once a save succeeds or fails, or
 *   when loading the saved artists fails; null when there is nothing to show.
 * @author Timz3rr, patrickmcdan, krfpn
 */
data class ArtistPreferencesUiState(
    val popularArtists: List<Artist> = emptyList(),
    val selectedArtists: Set<ArtistId> = emptySet(),
    val searchQuery: String = "",
    val savedArtists: Set<ArtistId> = emptySet(),
    val isSaving: Boolean = false,
    val isLoadingSavedArtists: Boolean = false,
    @param:StringRes val messageResId: Int? = null,
) {
  /**
   * Whether the Save button can be used: the selection differs from the last saved one and no save
   * is running.
   *
   * Comparing with the saved selection, rather than tracking toggles, makes the button disabled
   * again when the user undoes their changes.
   */
  val canSave: Boolean
    get() = selectedArtists != savedArtists && !isSaving

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
 * pre-selects the artists the signed-in user saved earlier, and saves the selection in their
 * preferences.
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

  // A slow load for a user who has since signed out could finish last and select their artists
  // for the next user, so only the load of the current user is kept running.
  private var loadJob: Job? = null

  init {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = {
          // TODO (#260): Add an error field to the state and show a message with a retry action.
          // Already logged by launchCatching. The grid stays empty until a retry exists.
        },
    ) {
      _uiState.update { it.copy(popularArtists = artworkRepository.getPopularArtists()) }
    }
    // A plain launch is enough: collecting the auth state only listens to sign-ins and sign-outs
    // and does not fail (see AuthRepository.authState). The load of the saved artists, which can
    // fail, is protected by launchCatching in onAuthStateChanged.
    viewModelScope.launch { authRepository.authState.collect(::onAuthStateChanged) }
  }

  /**
   * Pre-selects the saved artists of the user who just signed in, or clears the selection of the
   * user who left.
   *
   * @param authUser the signed-in user, or null when nobody is signed in
   */
  private fun onAuthStateChanged(authUser: AuthUser?) {
    // Stops the load of the previous user, whose late result would otherwise replace the
    // selection of the new one.
    loadJob?.cancel()
    if (authUser == null) {
      // Nobody signed in from the start: an in-memory selection belongs to no one who left, so it
      // is kept. After a sign-out, the previous user's choices are cleared.
      if (signedInUid != null) clearSelection()
      signedInUid = null
      // The canceled load cannot reset the flag itself, so the screen would stay locked.
      _uiState.update { it.copy(isLoadingSavedArtists = false) }
      return
    }
    signedInUid = authUser.uid
    // Clears the previous user's selection, so it is not shown while the new one loads.
    clearSelection()
    _uiState.update { it.copy(isLoadingSavedArtists = true) }
    loadJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              // The cards are enabled again with an empty selection, so the screen stays usable.
              // The message warns the user that saving now could replace artists they saved
              // before.
              _uiState.update {
                it.copy(isLoadingSavedArtists = false, messageResId = error.toMessageRes())
              }
            },
        ) {
          // No profile yet means no saved artists, so the selection stays empty. toSet() keeps the
          // saved order, and artists that are not popular stay selected, so the next save does not
          // drop them.
          val artists = findUser(authUser.uid)?.preference?.artists.orEmpty().toSet()
          _uiState.update {
            it.copy(
                selectedArtists = artists,
                savedArtists = artists,
                isLoadingSavedArtists = false,
            )
          }
        }
  }

  /** Empties both the current and the saved selection. */
  private fun clearSelection() {
    _uiState.update { it.copy(selectedArtists = emptySet(), savedArtists = emptySet()) }
  }

  /**
   * Selects the given artist, or deselects it if it is already selected.
   *
   * Ignored while the saved artists load.
   *
   * @param artistId the identifier of the artist to toggle.
   * @author Timz3rr, patrickmcdan, krfpn
   */
  fun toggleArtist(artistId: ArtistId) {
    // The cards are disabled while loading; checking here keeps the state valid whatever the
    // caller.
    if (_uiState.value.isLoadingSavedArtists) return
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
   * Only the favorite artists of the stored profile are replaced; its other fields are kept. On
   * success, the selection becomes the saved one and a confirmation message is exposed; on failure,
   * an error message is exposed instead and the selection stays unsaved, so the user can retry.
   * When nobody is signed in, the selection only becomes the saved one in memory. The call is
   * ignored when [ArtistPreferencesUiState.canSave] is false.
   *
   * @author krfpn
   */
  fun saveSelectedArtists() {
    // Also covers a double tap: the first tap sets isSaving, so the second one is ignored.
    if (!_uiState.value.canSave) return
    // Taken now, so a toggle made while the write runs does not change what is saved.
    val artists = _uiState.value.selectedArtists
    // Nobody signed in (the sign-in screen is still a placeholder, and in the UI tests and the
    // preview): there is no one to save the selection for, so it is only kept in memory, as the
    // profile screen does for the username.
    val uid =
        signedInUid
            ?: run {
              _uiState.update { it.copy(savedArtists = artists) }
              return
            }
    _uiState.update { it.copy(isSaving = true) }
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          // savedArtists is unchanged, so the button is enabled again for a retry.
          _uiState.update { it.copy(isSaving = false, messageResId = error.toMessageRes()) }
        },
    ) {
      saveArtists(uid, artists.toList())
      // Reached only when the writing succeeded, since a failure jumps to onError. If the user
      // toggled during the save, the selection now differs from savedArtists, so the button is
      // enabled again for the next save.
      _uiState.update {
        it.copy(
            savedArtists = artists,
            isSaving = false,
            messageResId = R.string.artist_preferences_saved,
        )
      }
    }
  }

  /**
   * Clears the message after the screen has shown it.
   *
   * @author krfpn
   */
  fun clearMessage() {
    _uiState.update { it.copy(messageResId = null) }
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
      // TODO(#253): no profile is created at sign-in yet, so a user who never saved a username has
      //  none. Once sign-in creates it, decide whether to keep this fallback (it still covers a
      //  failed creation and accounts created before that) here and in UserProfileViewModel.
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
