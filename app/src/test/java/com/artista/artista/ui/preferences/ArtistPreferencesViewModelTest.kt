package com.artista.artista.ui.preferences

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [ArtistPreferencesViewModel].
 *
 * @author Timz3rr, patrickmcdan, krfpn
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistPreferencesViewModelTest {

  private val artworkRepository = ArtworkRepositoryLocal()
  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private lateinit var viewModel: ArtistPreferencesViewModel

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Before
  fun setUp() {
    viewModel = ArtistPreferencesViewModel(artworkRepository, authRepository, userRepository)
  }

  /** Verifies that the popular artists of the mockup are exposed, in their display order. */
  @Test
  fun initialState_exposesPopularArtists() = runTest {
    advanceUntilIdle()

    assertEquals(
        listOf(PICASSO, VAN_GOGH, RENOIR, MONET, KLIMT, MATISSE),
        viewModel.uiState.value.popularArtists,
    )
  }

  /** Verifies that no artist is selected before the user interacts with the screen. */
  @Test
  fun initialState_hasNoSelectedArtist() {
    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
  }

  /** Verifies that toggling an unselected artist selects it. */
  @Test
  fun toggleArtist_selectsUnselectedArtist() {
    viewModel.toggleArtist(PICASSO.uid)

    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that toggling an artist twice brings it back to its initial unselected state. */
  @Test
  fun toggleArtist_twice_leavesArtistUnselected() {
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(PICASSO.uid)

    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
  }

  /** Verifies that toggling an artist leaves the selection of the other artists unchanged. */
  @Test
  fun toggleArtist_keepsOtherSelections() {
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(MONET.uid)

    assertEquals(setOf(PICASSO.uid, MONET.uid), viewModel.uiState.value.selectedArtists)

    viewModel.toggleArtist(PICASSO.uid)

    assertEquals(setOf(MONET.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that artists sharing a name are selected independently, since selection uses ids. */
  @Test
  fun toggleArtist_artistsWithSameName_areSelectedIndependently() = runTest {
    val first = Artist("test-same-1", "Same Name")
    val second = Artist("test-same-2", "Same Name")
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getPopularArtists(): List<Artist> = listOf(first, second)
        }
    val viewModel = ArtistPreferencesViewModel(repository, authRepository, userRepository)
    advanceUntilIdle()
    assertEquals(listOf(first, second), viewModel.uiState.value.popularArtists)

    viewModel.toggleArtist(first.uid)

    assertEquals(setOf(first.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that a failing popular artists load does not crash and leaves the list empty. */
  @Test
  fun init_whenLoadingPopularArtistsFails_exposesNoArtists() = runTest {
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getPopularArtists(): List<Artist> = error("Popular artists failure")
        }
    val viewModel = ArtistPreferencesViewModel(repository, authRepository, userRepository)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.popularArtists.isEmpty())
  }

  /** Verifies that the query is kept as typed, so the field never rewrites the user's text. */
  @Test
  fun onSearchQueryChange_storesTextAsTyped() {
    viewModel.onSearchQueryChange("  Mon ")

    assertEquals("  Mon ", viewModel.uiState.value.searchQuery)
  }

  /** Verifies that searching does not change the artists the user has selected. */
  @Test
  fun onSearchQueryChange_keepsSelection() {
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.onSearchQueryChange("monet")

    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that a query typed while the artists are loading applies once they arrive. */
  @Test
  fun onSearchQueryChange_beforeArtistsLoad_filtersOnceLoaded() = runTest {
    // Empty until the test fills it, which simulates a slow network.
    val artists = CompletableDeferred<List<Artist>>()

    // Fake repository whose popular artists only arrive once the test provides them.
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getPopularArtists(): List<Artist> = artists.await()
        }

    // The user opens the screen: loading starts, then stays stuck waiting for the artists.
    val slowLoadingViewModel =
        ArtistPreferencesViewModel(repository, authRepository, userRepository)
    advanceUntilIdle()

    // The user types while the artists are still loading.
    slowLoadingViewModel.onSearchQueryChange("mon")

    // The network answers, and the ViewModel receives the artists.
    artists.complete(ALL_ARTISTS)
    advanceUntilIdle()

    // The earlier query must apply to the artists that arrived after it.
    assertEquals(listOf(MONET), slowLoadingViewModel.uiState.value.displayedArtists)
  }

  /** Verifies that an empty or blank query does not hide any artist. */
  @Test
  fun displayedArtists_blankQuery_returnsAllArtists() {
    assertEquals(ALL_ARTISTS, displayedArtistsFor(""))
    assertEquals(ALL_ARTISTS, displayedArtistsFor("   "))
  }

  /** Verifies that a query matching part of a name keeps only that artist. */
  @Test
  fun displayedArtists_partialQuery_keepsMatchingArtist() {
    assertEquals(listOf(MONET), displayedArtistsFor("mon"))
  }

  /** Verifies that the case of the query does not affect the match. */
  @Test
  fun displayedArtists_ignoresCase() {
    assertEquals(listOf(MONET), displayedArtistsFor("MONET"))
  }

  /** Verifies that spaces around the query do not prevent a match. */
  @Test
  fun displayedArtists_ignoresSurroundingSpaces() {
    assertEquals(listOf(MONET), displayedArtistsFor("  monet "))
  }

  /** Verifies that several matching artists all stay, in their display order. */
  @Test
  fun displayedArtists_severalMatches_keepDisplayOrder() {
    assertEquals(listOf(PICASSO, RENOIR), displayedArtistsFor("pi"))
  }

  /** Verifies that a query matching no name returns no artist. */
  @Test
  fun displayedArtists_noMatch_returnsNoArtist() {
    assertTrue(displayedArtistsFor("xyz").isEmpty())
  }

  /** Verifies that saving replaces the stored artists and keeps the other fields of the profile. */
  @Test
  fun saveSelectedArtists_existingProfile_replacesArtistsOnly() = runTest {
    val storedPreference =
        UserPreference(
            artists = listOf(KLIMT.uid),
            type = listOf("painting"),
            timePeriod = listOf("19th"),
        )
    userRepository.store(User(uid = UID, userName = "Artista Fan", preference = storedPreference))
    signIn()
    var savedCount = 0
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists { savedCount++ }
    advanceUntilIdle()

    assertEquals(
        User(
            uid = UID,
            userName = "Artista Fan",
            preference = storedPreference.copy(artists = listOf(PICASSO.uid)),
        ),
        userRepository.storedUser(UID),
    )
    assertEquals(1, savedCount)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** Verifies that saving creates the profile of a user who has none yet. */
  @Test
  fun saveSelectedArtists_noProfile_createsProfileWithArtists() = runTest {
    signIn()
    var savedCount = 0
    viewModel.toggleArtist(MONET.uid)

    viewModel.saveSelectedArtists { savedCount++ }
    advanceUntilIdle()

    assertEquals(
        User(uid = UID, userName = null, preference = UserPreference(artists = listOf(MONET.uid))),
        userRepository.storedUser(UID),
    )
    assertEquals(1, savedCount)
  }

  /** Verifies that a failed write exposes an error and keeps the user on the screen. */
  @Test
  fun saveSelectedArtists_writeFails_exposesErrorAndStays() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.writeFailure = AppException.Network()
    signIn()
    var savedCount = 0
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists { savedCount++ }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(R.string.error_network, state.errorMessageResId)
    assertFalse(state.isSaving)
    assertEquals(0, savedCount)
    // The selection is kept so the user can try again.
    assertEquals(setOf(PICASSO.uid), state.selectedArtists)
    assertEquals(listOf(KLIMT.uid), userRepository.storedUser(UID)?.preference?.artists)
  }

  /**
   * Verifies that a failed read, other than a missing profile, exposes an error and writes nothing,
   * so a profile that could not be read is never overwritten.
   */
  @Test
  fun saveSelectedArtists_readFails_exposesErrorAndDoesNotWrite() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()
    var savedCount = 0
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists { savedCount++ }
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
    assertEquals(0, userRepository.writeCount)
    assertEquals(0, savedCount)
  }

  /** Verifies that nothing is saved when nobody is signed in, and that the screen is left. */
  @Test
  fun saveSelectedArtists_signedOut_callsOnSavedWithoutWriting() = runTest {
    // No signIn(): the auth repository starts signed out.
    advanceUntilIdle()
    var savedCount = 0
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists { savedCount++ }
    advanceUntilIdle()

    assertEquals(0, userRepository.writeCount)
    assertEquals(1, savedCount)
  }

  /** Verifies that a second save requested while the first one runs is ignored. */
  @Test
  fun saveSelectedArtists_whileSaving_ignoresSecondCall() = runTest {
    // The fake can only slow down reads; the read comes right before the write, so it keeps the
    // save running just as a slow write would.
    userRepository.readDelays[UID] = 1_000
    signIn()
    var savedCount = 0
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists { savedCount++ }
    // runCurrent starts the save without letting time pass, so it is still waiting on the read.
    runCurrent()
    assertTrue(viewModel.uiState.value.isSaving)
    viewModel.saveSelectedArtists { savedCount++ }
    advanceUntilIdle()

    assertEquals(1, userRepository.writeCount)
    assertEquals(1, savedCount)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** Verifies that the artists are stored in the order the user selected them. */
  @Test
  fun saveSelectedArtists_keepsSelectionOrder() = runTest {
    signIn()
    viewModel.toggleArtist(MONET.uid)
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(KLIMT.uid)

    viewModel.saveSelectedArtists {}
    advanceUntilIdle()

    assertEquals(
        listOf(MONET.uid, PICASSO.uid, KLIMT.uid),
        userRepository.storedUser(UID)?.preference?.artists,
    )
  }

  /** Verifies that the error message is removed once the screen has shown it. */
  @Test
  fun clearErrorMessage_resetsError() = runTest {
    userRepository.writeFailure = AppException.Network()
    signIn()
    viewModel.saveSelectedArtists {}
    advanceUntilIdle()
    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)

    viewModel.clearErrorMessage()

    assertNull(viewModel.uiState.value.errorMessageResId)
  }

  /**
   * Signs [UID] in and lets [viewModel] receive it before the test acts.
   *
   * An extension of [TestScope], the scope of runTest, so that it can call advanceUntilIdle.
   */
  private fun TestScope.signIn() {
    authRepository.setAuthState(FakeAuthUser(UID))
    advanceUntilIdle()
  }

  /**
   * Builds the profile of the signed-in user with the given favorite artists.
   *
   * @param artists the favorite artists of the user
   * @return the user profile
   */
  private fun userWithArtists(artists: List<String>): User =
      User(uid = UID, userName = "Artista Fan", preference = UserPreference(artists = artists))

  /**
   * Computes the artists displayed for a query, among all the mock artists.
   *
   * @param query the text typed in the search field.
   * @return the artists matching the query.
   */
  private fun displayedArtistsFor(query: String): List<Artist> =
      ArtistPreferencesUiState(popularArtists = ALL_ARTISTS, searchQuery = query).displayedArtists

  private companion object {
    const val UID = "uid-1"
    val PICASSO = Artist("mock-artist-001", "Pablo Picasso")
    val VAN_GOGH = Artist("mock-artist-002", "Vincent van Gogh")
    val RENOIR = Artist("mock-artist-003", "Pierre-Auguste Renoir")
    val MONET = Artist("mock-artist-004", "Claude Monet")
    val KLIMT = Artist("mock-artist-005", "Gustav Klimt")
    val MATISSE = Artist("mock-artist-006", "Henri Matisse")
    val ALL_ARTISTS = listOf(PICASSO, VAN_GOGH, RENOIR, MONET, KLIMT, MATISSE)
  }
}
