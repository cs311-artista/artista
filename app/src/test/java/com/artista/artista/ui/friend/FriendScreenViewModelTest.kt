// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.friend

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the state exposed by [FriendScreenViewModel].
 *
 * @author krfpn
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendScreenViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val userRepository = FakeUserRepository()
  private val catalog = ArtworkRepositoryLocal()

  /** When set, reading the friend's saved artworks throws it. */
  private var savedArtworksFailure: Exception? = null

  /** When set, reading the friend's saved artworks waits for it, to simulate a slow network. */
  private var savedArtworksGate: CompletableDeferred<Unit>? = null

  private val savedArtworksRepository =
      object : SavedArtworksRepository by catalog {
        override suspend fun getSavedArtworksOf(userId: String): List<Artwork> {
          savedArtworksGate?.await()
          savedArtworksFailure?.let { throw it }
          return catalog.getSavedArtworksOf(userId)
        }
      }

  /** Verifies that the friend's name, picture, artists and saved artworks are shown. */
  @Test
  fun load_showsTheFriendsProfileArtistsAndSavedArtworks() = runTest {
    // The unknown identifier is skipped, so one bad artist does not hide the others.
    storeFriend(listOf(PICASSO_ID, "Q404", MONET_ID), photoUrl = PHOTO_URL)
    catalog.putSavedArtworksOfUser(FRIEND_UID, listOf(ARTWORK))

    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("5kyPhy", state.userName)
    assertEquals(PHOTO_URL, state.photoUrl)
    assertEquals(listOf(PICASSO_ID, MONET_ID), state.artists.map { it.uid })
    assertEquals(listOf(ARTWORK), state.savedArtworks)
    assertFalse(state.isLoading)
    assertNull(state.errorMessageResId)
  }

  /** Verifies that a friend with no artists, no saved artworks and a blank picture is no error. */
  @Test
  fun load_friendWithNothingSaved_showsEmptySectionsWithoutError() = runTest {
    storeFriend(emptyList(), photoUrl = " ")

    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull(state.photoUrl)
    assertTrue(state.artists.isEmpty())
    assertTrue(state.savedArtworks.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.errorMessageResId)
  }

  /** Verifies that the name is shown as soon as the profile is read, while the rest loads. */
  @Test
  fun load_showsTheNameBeforeTheSectionsFinishLoading() = runTest {
    storeFriend(emptyList())
    savedArtworksGate = CompletableDeferred()

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals("5kyPhy", viewModel.uiState.value.userName)
    assertTrue(viewModel.uiState.value.isLoading)

    savedArtworksGate?.complete(Unit)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** Verifies that an unknown friend shows the error state with the matching message. */
  @Test
  fun load_unknownFriend_showsError() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(R.string.error_not_found, state.errorMessageResId)
  }

  /** Verifies that failing to read the saved artworks fails the whole screen. */
  @Test
  fun load_savedArtworksFail_showsError() = runTest {
    storeFriend(listOf(PICASSO_ID))
    savedArtworksFailure = AppException.Network()

    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(R.string.error_network, state.errorMessageResId)
  }

  /** Verifies that retrying after a failure clears the error and shows the friend's data. */
  @Test
  fun retry_afterFailure_showsTheFriendsData() = runTest {
    storeFriend(listOf(PICASSO_ID))
    savedArtworksFailure = AppException.Network()
    val viewModel = createViewModel()
    advanceUntilIdle()

    savedArtworksFailure = null
    viewModel.retry()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull(state.errorMessageResId)
    assertFalse(state.isLoading)
    assertEquals(listOf(PICASSO_ID), state.artists.map { it.uid })
  }

  /**
   * Stores the friend's profile.
   *
   * @param artistIds identifiers of the friend's favourite artists
   * @param photoUrl link of the friend's profile picture
   */
  private fun storeFriend(artistIds: List<String>, photoUrl: String? = null) {
    userRepository.store(
        User(
            uid = FRIEND_UID,
            userName = "5kyPhy",
            preference = UserPreference(artists = artistIds),
            photoUrl = photoUrl,
        )
    )
  }

  /** Creates the ViewModel showing [FRIEND_UID]; it starts loading at once. */
  private fun createViewModel() =
      FriendScreenViewModel(FRIEND_UID, userRepository, savedArtworksRepository, catalog)

  private companion object {
    const val FRIEND_UID = "friend-uid"
    // Identifiers of mock artists known by ArtworkRepositoryLocal.
    const val PICASSO_ID = "mock-artist-001"
    const val MONET_ID = "mock-artist-004"
    const val PHOTO_URL = "https://example.com/friend.png"
    val ARTWORK =
        Artwork(
            uid = "artwork-1",
            name = "Le cri",
            artist = null,
            description = null,
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        )
  }
}
