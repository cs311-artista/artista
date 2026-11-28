// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friend

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.RemoteArtistRepository
import com.artista.artista.model.artist.getArtistsFromList
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.user.UserId
import com.artista.artista.model.user.UserRepository
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State required to display a friend's profile.
 *
 * @property userName name of the friend, or null until their profile is read or when they have none
 * @property photoUrl link of the friend's profile picture, or null to display a placeholder
 * @property artists the friend's favourite artists, empty when they have none
 * @property savedArtworks the artworks the friend has saved, empty when they have none
 * @property isLoading whether the artists and saved artworks are being loaded
 * @property errorMessageResId string resource of the message shown when loading fails, or null
 * @author krfpn
 */
data class FriendScreenUiState(
    val userName: String? = null,
    val photoUrl: String? = null,
    val artists: List<Artist> = emptyList(),
    val savedArtworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = true,
    @param:StringRes val errorMessageResId: Int? = null,
)

/**
 * Loads the profile, favourite artists and saved artworks of a friend for the friend screen.
 *
 * @param friendUid identifier of the friend whose profile is shown
 * @param userRepository storage of the user profiles, holding the friend's name, picture and artist
 *   identifiers
 * @param savedArtworksRepository storage of the saved artworks, read for the friend
 * @param artistRepository source of the details (name, picture) of the friend's artists
 * @author krfpn
 */
class FriendScreenViewModel(
    private val friendUid: UserId,
    private val userRepository: UserRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
    private val artistRepository: RemoteArtistRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(FriendScreenUiState())

  /** The current state of the friend screen. */
  val uiState: StateFlow<FriendScreenUiState> = _uiState.asStateFlow()

  // A retry while a load is still running would otherwise let the older load finish last and
  // overwrite the newer result, so only the most recent load is kept running.
  private var loadJob: Job? = null

  init {
    load()
  }

  /**
   * Loads the friend's profile again, e.g. after a failed load.
   *
   * @author krfpn
   */
  fun retry() {
    load()
  }

  /**
   * Loads the friend's profile, then their artists and saved artworks at the same time.
   *
   * The name and picture are shown as soon as the profile is read, so the top bar is filled while
   * the rest loads. Any failure, including an unknown friend, shows the error state.
   */
  private fun load() {
    loadJob?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMessageResId = null) }
    loadJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              _uiState.update {
                it.copy(isLoading = false, errorMessageResId = error.toMessageRes())
              }
            },
        ) {
          val friend = userRepository.getUser(friendUid)
          _uiState.update {
            it.copy(
                userName = friend.userName,
                photoUrl = friend.photoUrl?.takeIf(String::isNotBlank),
            )
          }
          // Without this coroutineScope, the two async blocks would be direct children of the
          // coroutine that launchCatching created. If either one threw, e.g. an
          // AppException.Network, it would first cancel that coroutine, so the ensureActive() in
          // the catch of launchCatching would throw a CancellationException and onError would
          // never be called. coroutineScope instead rethrows the exception to its caller as an
          // ordinary exception, without cancelling the launched coroutine.
          coroutineScope {
            val artists = async {
              artistRepository.getArtistsFromList(friend.preference.artists.distinct())
            }
            val savedArtworks = async { savedArtworksRepository.getSavedArtworksOf(friendUid) }
            _uiState.update {
              it.copy(
                  artists = artists.await(),
                  savedArtworks = savedArtworks.await(),
                  isLoading = false,
              )
            }
          }
        }
  }

  /** Holds the log tag of [FriendScreenViewModel]. */
  companion object {
    /** Log tag identifying messages emitted by [FriendScreenViewModel]. */
    private const val TAG = "FriendScreenViewModel"
  }
}
