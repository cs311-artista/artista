package com.artista.artista.ui.preferences

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * UI state of the artist preferences screen.
 *
 * @property savedArtists the artists the signed-in user saved as favorites, in the order they were
 *   saved. An artist being removed stays here until the removal succeeds, so a failed removal puts
 *   its card back at the same place.
 * @property removingArtists identifiers of the artists whose removal is being done. Their cards are
 *   hidden at once, and tapping them again starts no second write.
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
    val removingArtists: Set<ArtistId> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val hasLoadFailed: Boolean = false,
    @param:StringRes val messageResId: Int? = null,
) {
  /** Whether the search field can be used: not while the saved artists load or failed to load. */
  val canSearch: Boolean
    get() = !isLoading && !hasLoadFailed

  /**
   * Whether the user has no saved artist left to show, so the screen shows its empty state.
   *
   * Artists being removed do not count, so the empty state appears as soon as the last card is
   * tapped; it is ignored while loading or after a failed load, which have their own states.
   */
  val isEmpty: Boolean
    get() = !isLoading && !hasLoadFailed && savedArtists.none { it.uid !in removingArtists }

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
 * Shows the artists the signed-in user saved as favorites, and removes one from their preferences
 * when the user taps it.
 *
 * @param artworkRepository source of the details (name, picture) of the saved artists, which the
 *   user profile only stores by identifier
 * @param authRepository source of the signed-in user, whose preferences are shown and edited
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

  // A slow load for a user who has since signed out could finish last and show their artists to
  // the next user, so only the load of the current user is kept running.
  private var loadJob: Job? = null

  // Each removal reads the stored profile and writes it back without one artist. Two removals
  // running side by side would both read the same list, and the last write would bring back the
  // artist the first one removed, so the writes run one after the other. This only orders the
  // writes of this ViewModel, not those made elsewhere (for example on another device signed-in
  // with the same account !).
  private val writeMutex = Mutex()

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
          val artists = resolveArtists(ids)
          _uiState.update { it.copy(savedArtists = artists, isLoading = false) }
        }
  }

  /**
   * Fetches the details of the given artists, all at once rather than one after the other.
   *
   * @param ids identifiers of the artists, in the order they are returned
   * @return one artist per identifier, in the same order
   */
  private suspend fun resolveArtists(ids: List<ArtistId>): List<Artist> = coroutineScope {
    ids.map { id -> async { resolveArtist(id) } }.awaitAll()
  }

  /**
   * Fetches the details of an artist, or builds a placeholder named after its identifier when the
   * artist cannot be found.
   *
   * Hiding such an artist would leave it in the user's preferences with no way to remove it, so a
   * card is still shown for it. Network failures are not caught: they fail the whole load, which
   * the user can retry.
   *
   * @param id identifier of the artist
   * @return the artist, or a placeholder carrying [id] as its name
   */
  private suspend fun resolveArtist(id: ArtistId): Artist {
    val artist =
        try {
          artworkRepository.getArtist(id)
        } catch (exception: AppException.NotFound) {
          null
        } catch (exception: AppException.InvalidInput) {
          // An identifier that is not a Wikidata QID, e.g. saved while the screen used mock data.
          null
        }
    return artist ?: Artist(uid = id, name = id)
  }

  /**
   * Removes an artist from the favorites of the signed-in user.
   *
   * The card is hidden at once. If the write fails, the card comes back where it was and an error
   * message is exposed, so the user can try again. When nobody is signed in, the artist is only
   * removed from memory. The call is ignored when the artist is not shown or its removal is already
   * running, so a double tap writes once.
   *
   * @param artistId identifier of the artist to remove
   * @author Timz3rr, patrickmcdan, krfpn
   */
  fun removeArtist(artistId: ArtistId) {
    val state = _uiState.value
    if (state.isLoading || state.hasLoadFailed) return
    if (artistId in state.removingArtists || state.savedArtists.none { it.uid == artistId }) return

    val uid =
        signedInUid
            ?: run {
              _uiState.update { s ->
                s.copy(savedArtists = s.savedArtists.filterNot { it.uid == artistId })
              }
              return
            }

    _uiState.update { it.copy(removingArtists = it.removingArtists + artistId) }
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          // A user who signed out meanwhile no longer sees this artist, so nothing comes back.
          if (signedInUid != uid) return@launchCatching
          _uiState.update {
            it.copy(
                removingArtists = it.removingArtists - artistId,
                messageResId = error.toMessageRes(),
            )
          }
        },
    ) {
      writeMutex.withLock { removeStoredArtist(uid, artistId) }
      if (signedInUid != uid) return@launchCatching
      _uiState.update { s ->
        s.copy(
            savedArtists = s.savedArtists.filterNot { it.uid == artistId },
            removingArtists = s.removingArtists - artistId,
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
   * Removes an artist from the favorites stored in a user's profile, keeping its other fields.
   *
   * @param uid identifier of the user
   * @param artistId identifier of the artist to remove
   */
  private suspend fun removeStoredArtist(uid: UserId, artistId: ArtistId) {
    // A missing profile has no artist to remove, so we return directly.
    val storedUser = findUser(uid) ?: return
    val artists = storedUser.preference.artists
    if (artistId !in artists) return
    val remainingArtists = artists.filterNot { it == artistId }
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
