package com.artista.artista.ui.preferences

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artist.getArtistsFromList
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserId
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
 * @property savedArtists the artists the signed-in user saved as favorites, in the order they were
 *   saved. An artist being removed stays here until the removal succeeds, so a failed removal puts
 *   its card back at the same place.
 * @property deselectedArtists identifiers of the saved artists the user has untoggled, ready for
 *   removal. Their cards stay shown, unchecked, until the user taps Save.
 * @property removingArtists identifiers of the artists who is being removed. Their cards are hidden
 *   at once; while the write runs, no card can be toggled and Save is disabled.
 * @property searchQuery the text typed in the search field, exactly as the user entered it.
 * @property isLoading whether the saved artists of the signed-in user are loading.
 * @property hasLoadFailed whether the last load of the saved artists failed. Unlike [messageResId],
 *   it stays set after the error has been shown, so the screen can keep offering a retry.
 * @property messageResId string resource of the message shown when loading the saved artists or
 *   removing one of them fails; null when there is nothing to show.
 * @author Timz3rr, patrickmcdan, krfpn
 */
data class ArtistPreferencesUiState(
    val savedArtists: List<Artist> = emptyList(),
    val deselectedArtists: Set<ArtistId> = emptySet(),
    val removingArtists: Set<ArtistId> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val hasLoadFailed: Boolean = false,
    @param:StringRes val messageResId: Int? = null,
) {
  /** Whether the search field can be used: not while the saved artists load or failed to load. */
  val canSearch: Boolean
    get() = !isLoading && !hasLoadFailed

  /** Whether a save is being written, during which the user cannot change anything. */
  val isSaving: Boolean
    get() = removingArtists.isNotEmpty()

  /**
   * Whether the Save button can be used: the loading of the user's saved artists was successful,
   * the user has untoggled at least one artist, and no save is running.
   *
   * The empty state has no card to untoggle, so the button is disabled there too.
   */
  val canSave: Boolean
    get() = deselectedArtists.isNotEmpty() && !isLoading && !hasLoadFailed && !isSaving

  /**
   * Whether the user has no saved artist left to show, so the screen shows its empty state.
   *
   * Artists being removed do not count, so the empty state appears as soon as the last removal is
   * saved; it is ignored while loading or after a failed load, which have their own states.
   */
  val hasNoSavedArtists: Boolean
    get() = !isLoading && !hasLoadFailed && savedArtists.all { it.uid in removingArtists }

  /**
   * The saved artists that are not being removed and whose name matches [searchQuery], in saved
   * order.
   *
   * Derived rather than stored, so it stays correct when the artists finish loading after the user
   * has started typing, and when a failed removal brings an artist back.
   */
  val displayedArtists: List<Artist>
    get() {
      // Trimmed so stray spaces do not hide every artist; an empty query matches all of them.
      val query = searchQuery.trim()
      return savedArtists.filter {
        it.uid !in removingArtists && it.name.contains(query, ignoreCase = true)
      }
    }
}

/**
 * Shows the artists the signed-in user saved as favorites, lets the user untoggle some of them, and
 * removes the untoggled ones from their preferences when the user saves.
 *
 * @param wikiDataRepository source of the details (name, picture) of the saved artists, which the
 *   user profile only stores by identifier
 * @param authRepository source of the signed-in user, whose preferences are shown and edited
 * @param userRepository storage of the user profiles
 * @author Timz3rr, patrickmcdan, krfpn
 */
class ArtistPreferencesViewModel(
    private val wikiDataRepository: WikiDataRepository,
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

  // A slow load for a user who has since signed out could finish last and show their artists to
  // the next user, so only the load of the current user is kept running.
  private var loadJob: Job? = null

  init {
    // A plain launch is enough: collecting the auth state only listens to sign-ins and sign-outs
    // and does not fail (see AuthRepository.authState). The load of the saved artists, which can
    // fail, is protected by launchCatching in loadSavedArtists.
    viewModelScope.launch { authRepository.authState.collect(::onAuthStateChanged) }
  }

  /**
   * Reloads the saved artists of the signed-in user after a failed load.
   *
   * @author krfpn
   */
  fun retryLoad() {
    val uid = signedInUid ?: return
    if (_uiState.value.hasLoadFailed) loadSavedArtists(uid)
  }

  /**
   * Shows the saved artists of the user who just signed in, or clears those of the user who left.
   *
   * @param authUser the signed-in user, or null when nobody is signed in
   */
  private fun onAuthStateChanged(authUser: AuthUser?) {
    // Stops the load of the previous user, whose late result would otherwise replace the artists
    // of the new one.
    loadJob?.cancel()
    signedInUid = authUser?.uid
    // The previous user's artists, pending removals, failure, canceled load belong to them, so none
    // is kept.
    _uiState.update {
      it.copy(
          savedArtists = emptyList(),
          deselectedArtists = emptySet(),
          removingArtists = emptySet(),
          isLoading = false,
          hasLoadFailed = false,
      )
    }
    authUser?.let { loadSavedArtists(it.uid) }
  }

  /**
   * Loads the saved artists of a user.
   *
   * On failure, [ArtistPreferencesUiState.hasLoadFailed] is set and an error message is exposed.
   *
   * @param uid identifier of the user whose artists are loaded
   */
  private fun loadSavedArtists(uid: UserId) {
    // Clears the failure of the previous attempt, so the screen shows the loading state.
    _uiState.update { it.copy(isLoading = true, hasLoadFailed = false) }
    loadJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              _uiState.update {
                it.copy(
                    isLoading = false,
                    hasLoadFailed = true,
                    messageResId = error.toMessageRes(),
                )
              }
            },
        ) {
          val ids = findUser(uid)?.preference?.artists.orEmpty().distinct()
          val artists = wikiDataRepository.getArtistsFromList(ids)
          _uiState.update { it.copy(savedArtists = artists, isLoading = false) }
        }
  }

  /**
   * Untoggles a saved artist, or toggles it back if it is already untoggled.
   *
   * Nothing is written until [saveChanges]. Ignored while the saved artists load, or after they
   * failed to load, while a save runs, and for an artist that is not shown.
   *
   * @param artistId identifier of the artist to toggle
   * @author Timz3rr, patrickmcdan, krfpn
   */
  fun toggleArtist(artistId: ArtistId) {
    val state = _uiState.value
    if (state.isLoading || state.hasLoadFailed || state.isSaving) return
    if (state.savedArtists.none { it.uid == artistId }) {
      // The screen only shows cards of saved artists, so this means a caller is out of sync.
      Log.w(TAG, "Ignored a toggle of artist $artistId, which is not among the saved artists.")
      return
    }
    _uiState.update {
      val deselected =
          if (artistId in it.deselectedArtists) it.deselectedArtists - artistId
          else it.deselectedArtists + artistId
      it.copy(deselectedArtists = deselected)
    }
  }

  /**
   * Removes the untoggled artists from the favorites of the signed-in user.
   *
   * Their cards are hidden at once, before the write ends, and nothing can be changed until it
   * ends. If the write fails, the cards come back where they were, checked again as they were
   * before the user untoggled them, and an error message is exposed. The call is ignored when
   * [ArtistPreferencesUiState.canSave] is false, which also makes a double tap write once.
   *
   * @author krfpn
   */
  fun saveChanges() {
    val state = _uiState.value
    if (!state.canSave) return
    val uid =
        signedInUid
            ?: run {
              // Signing out clears the untoggled artists, so canSave should already be false here.
              Log.w(TAG, "Ignored a save while nobody is signed in.")
              return
            }
    val artistIds = state.deselectedArtists

    _uiState.update { it.copy(deselectedArtists = emptySet(), removingArtists = artistIds) }
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          // A user who signed out meanwhile no longer sees these artists, so nothing comes back.
          if (signedInUid != uid) return@launchCatching
          // In case of error, the cards come back checked (as initially) and the save button stays
          // disabled.
          _uiState.update {
            it.copy(removingArtists = emptySet(), messageResId = error.toMessageRes())
          }
        },
    ) {
      removeStoredArtists(uid, artistIds)
      if (signedInUid != uid) return@launchCatching
      _uiState.update { s ->
        s.copy(
            savedArtists = s.savedArtists.filterNot { it.uid in artistIds },
            removingArtists = emptySet(),
        )
      }
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
   * Clears the message after the screen has shown it.
   *
   * @author krfpn
   */
  fun clearMessage() {
    _uiState.update { it.copy(messageResId = null) }
  }

  /**
   * Removes artists from the favorites stored in a user's profile, keeping its other fields.
   *
   * @param uid identifier of the user
   * @param artistIds identifiers of the artists to remove
   */
  private suspend fun removeStoredArtists(uid: UserId, artistIds: Set<ArtistId>) {
    // A missing profile has no artist to remove, so we return directly.
    val storedUser = findUser(uid) ?: return
    val artists = storedUser.preference.artists
    val remainingArtists = artists.filterNot { it in artistIds }
    userRepository.updateUser(
        uid,
        storedUser.copy(preference = storedUser.preference.copy(artists = remainingArtists)),
    )
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
      } catch (_: AppException.NotFound) {
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
     * @param wikiDataRepository repository used to load the details of the saved artists
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
