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
    // Klimt is pre-selected from the stored profile; replacing him checks that the stored list is
    // replaced, not appended to.
    viewModel.toggleArtist(KLIMT.uid)
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(
        User(
            uid = UID,
            userName = "Artista Fan",
            preference = storedPreference.copy(artists = listOf(PICASSO.uid)),
        ),
        userRepository.storedUser(UID),
    )
    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.savedArtists)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** Verifies that saving creates the profile of a user who has none yet. */
  @Test
  fun saveSelectedArtists_noProfile_createsProfileWithArtists() = runTest {
    signIn()
    viewModel.toggleArtist(MONET.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(
        User(uid = UID, userName = null, preference = UserPreference(artists = listOf(MONET.uid))),
        userRepository.storedUser(UID),
    )
    assertEquals(setOf(MONET.uid), viewModel.uiState.value.savedArtists)
  }

  /** Verifies that a failed write exposes an error and leaves the selection unsaved. */
  @Test
  fun saveSelectedArtists_writeFails_exposesErrorAndStays() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.writeFailure = AppException.Network()
    signIn()
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(R.string.error_network, state.messageResId)
    assertFalse(state.isSaving)
    // The saved selection is still the loaded one, so the new one stays unsaved.
    assertEquals(setOf(KLIMT.uid), state.savedArtists)
    // The selection is kept so the user can try again.
    assertEquals(setOf(KLIMT.uid, PICASSO.uid), state.selectedArtists)
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
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(R.string.error_network, viewModel.uiState.value.messageResId)
    assertEquals(0, userRepository.writeCount)
    assertTrue(viewModel.uiState.value.savedArtists.isEmpty())
  }

  /**
   * Verifies that nothing is written when nobody is signed in, and that the selection is still kept
   * as the saved one in memory.
   */
  @Test
  fun saveSelectedArtists_signedOut_savesInMemoryWithoutWriting() = runTest {
    // No signIn(): the auth repository starts signed out.
    advanceUntilIdle()
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(0, userRepository.writeCount)
    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.savedArtists)
  }

  /** Verifies that a second save requested while the first one runs is ignored. */
  @Test
  fun saveSelectedArtists_whileSaving_ignoresSecondCall() = runTest {
    // The fake can only slow down reads; the read comes right before the write, so it keeps the
    // save running just as a slow write would.
    userRepository.readDelays[UID] = 1_000
    signIn()
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    // runCurrent starts the save without letting time pass, so it is still waiting on the read.
    runCurrent()
    assertTrue(viewModel.uiState.value.isSaving)
    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(1, userRepository.writeCount)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /**
   * Verifies that the artists saved are the ones selected when Save was tapped, even if the user
   * changes the selection while the save runs.
   */
  @Test
  fun saveSelectedArtists_toggleWhileSaving_savesSelectionAtTapTime() = runTest {
    // Slows the read before the write, so the save is still running when the user toggles.
    userRepository.readDelays[UID] = 1_000
    signIn()
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    // runCurrent starts the save without letting time pass, so it is still waiting on the read.
    runCurrent()
    viewModel.toggleArtist(MONET.uid)
    advanceUntilIdle()

    assertEquals(listOf(PICASSO.uid), userRepository.storedUser(UID)?.preference?.artists)
    // The toggle is not lost: it stays on screen, ready for the next save.
    assertEquals(setOf(PICASSO.uid, MONET.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that Save is disabled when the screen opens, since nothing has changed yet. */
  @Test
  fun canSave_initially_isFalse() {
    assertFalse(viewModel.uiState.value.canSave)
  }

  /** Verifies that selecting an artist enables Save. */
  @Test
  fun canSave_afterToggle_isTrue() {
    viewModel.toggleArtist(PICASSO.uid)

    assertTrue(viewModel.uiState.value.canSave)
  }

  /** Verifies that undoing a change disables Save again, since the selection is the saved one. */
  @Test
  fun canSave_afterUndoingToggle_isFalse() {
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(PICASSO.uid)

    assertFalse(viewModel.uiState.value.canSave)
  }

  /** Verifies that a successful save exposes the confirmation message and disables Save. */
  @Test
  fun saveSelectedArtists_success_exposesSavedMessage() = runTest {
    signIn()
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(R.string.artist_preferences_saved, viewModel.uiState.value.messageResId)
    assertFalse(viewModel.uiState.value.canSave)
  }

  /** Verifies that saving without any change writes nothing. */
  @Test
  fun saveSelectedArtists_withoutChange_isIgnored() = runTest {
    signIn()

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(0, userRepository.writeCount)
    assertNull(viewModel.uiState.value.messageResId)
  }

  /**
   * Verifies that a toggle made during a save leaves Save enabled once the save ends, since the
   * selection then differs from the one just saved.
   */
  @Test
  fun saveSelectedArtists_toggleWhileSaving_canSaveAgain() = runTest {
    // Slows the read before the write, so the save is still running when the user toggles.
    userRepository.readDelays[UID] = 1_000
    signIn()
    viewModel.toggleArtist(PICASSO.uid)

    viewModel.saveSelectedArtists()
    // runCurrent starts the save without letting time pass, so it is still waiting on the read.
    runCurrent()
    viewModel.toggleArtist(MONET.uid)
    advanceUntilIdle()

    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.savedArtists)
    assertTrue(viewModel.uiState.value.canSave)
  }

  /** Verifies that the artists are stored in the order the user selected them. */
  @Test
  fun saveSelectedArtists_keepsSelectionOrder() = runTest {
    signIn()
    viewModel.toggleArtist(MONET.uid)
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(KLIMT.uid)

    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(
        listOf(MONET.uid, PICASSO.uid, KLIMT.uid),
        userRepository.storedUser(UID)?.preference?.artists,
    )
  }

  /** Verifies that the message is removed once the screen has shown it. */
  @Test
  fun clearMessage_resetsMessage() = runTest {
    userRepository.writeFailure = AppException.Network()
    signIn()
    // Saving needs a change to save, otherwise the call is ignored.
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.saveSelectedArtists()
    advanceUntilIdle()
    assertEquals(R.string.error_network, viewModel.uiState.value.messageResId)

    viewModel.clearMessage()

    assertNull(viewModel.uiState.value.messageResId)
  }

  /** Verifies that the stored artists are pre-selected and count as saved, so Save is disabled. */
  @Test
  fun signIn_savedArtists_preselectsThem() = runTest {
    userRepository.store(userWithArtists(listOf(MONET.uid, KLIMT.uid)))

    signIn()

    val state = viewModel.uiState.value
    assertEquals(setOf(MONET.uid, KLIMT.uid), state.selectedArtists)
    assertEquals(setOf(MONET.uid, KLIMT.uid), state.savedArtists)
    assertFalse(state.canSave)
  }

  /** Verifies that a user without a profile starts with an empty selection and no error. */
  @Test
  fun signIn_noProfile_startsWithEmptySelection() = runTest {
    signIn()

    val state = viewModel.uiState.value
    assertTrue(state.selectedArtists.isEmpty())
    assertTrue(state.savedArtists.isEmpty())
    assertNull(state.messageResId)
  }

  /** Verifies that a profile without saved artists gives an empty selection. */
  @Test
  fun signIn_profileWithoutArtists_startsWithEmptySelection() = runTest {
    userRepository.store(userWithArtists(emptyList()))

    signIn()

    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
    assertNull(viewModel.uiState.value.messageResId)
  }

  /**
   * Verifies that a failed load exposes an error, leaves the selection empty, and that the user can
   * still select artists and save them once the backend answers again.
   */
  @Test
  fun signIn_loadFails_keepsScreenUsableAndExposesError() = runTest {
    userRepository.readFailure = AppException.Network()

    signIn()

    assertEquals(R.string.error_network, viewModel.uiState.value.messageResId)
    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())

    // The backend is reachable again, as when the user retries after the error.
    userRepository.readFailure = null
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(listOf(PICASSO.uid), userRepository.storedUser(UID)?.preference?.artists)
  }

  /** Verifies that a saved artist missing from the popular artists is still selected. */
  @Test
  fun signIn_nonPopularSavedArtist_staysSelected() = runTest {
    userRepository.store(userWithArtists(listOf(NON_POPULAR_ARTIST_ID)))

    signIn()

    assertEquals(setOf(NON_POPULAR_ARTIST_ID), viewModel.uiState.value.selectedArtists)
  }

  /**
   * Verifies that saving after reopening the screen keeps the earlier choices the user did not
   * change, in their order, including those that are not popular artists.
   */
  @Test
  fun save_afterReopening_keepsUnchangedEarlierChoices() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, NON_POPULAR_ARTIST_ID)))
    signIn()

    viewModel.toggleArtist(PICASSO.uid)
    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(
        listOf(KLIMT.uid, NON_POPULAR_ARTIST_ID, PICASSO.uid),
        userRepository.storedUser(UID)?.preference?.artists,
    )
  }

  /** Verifies that another user signing in replaces the previous user's selection with theirs. */
  @Test
  fun signIn_otherUser_replacesPreviousUsersSelection() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.store(userWithArtists(listOf(MONET.uid), uid = OTHER_UID))
    signIn()

    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    advanceUntilIdle()

    assertEquals(setOf(MONET.uid), viewModel.uiState.value.selectedArtists)
    assertEquals(setOf(MONET.uid), viewModel.uiState.value.savedArtists)
  }

  /**
   * Verifies that a slow load of the previous user, ending after another user signed in, does not
   * replace the new user's selection.
   */
  @Test
  fun signIn_slowLoadOfPreviousUser_doesNotOverwriteNewUser() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.store(userWithArtists(listOf(MONET.uid), uid = OTHER_UID))
    userRepository.readDelays[UID] = 1_000
    authRepository.setAuthState(FakeAuthUser(UID))
    // runCurrent starts the first load without letting time pass, so it is still running.
    runCurrent()

    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    advanceUntilIdle()

    assertEquals(setOf(MONET.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that signing out clears the selection of the user who left. */
  @Test
  fun signOut_clearsSelection() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()

    authRepository.setAuthState(null)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
    assertTrue(viewModel.uiState.value.savedArtists.isEmpty())
  }

  /**
   * Verifies that saving while the stored artists are still loading keeps the saved selection,
   * instead of letting the late load replace it.
   */
  @Test
  fun save_whileLoading_cancelsLoad() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    // Slows both the load and the read made by the save, so the load is still running at the tap.
    userRepository.readDelays[UID] = 1_000
    authRepository.setAuthState(FakeAuthUser(UID))
    // runCurrent starts the load without letting time pass, so it is still waiting on the read.
    runCurrent()

    viewModel.toggleArtist(PICASSO.uid)
    viewModel.saveSelectedArtists()
    advanceUntilIdle()

    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.selectedArtists)
    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.savedArtists)
    assertEquals(listOf(PICASSO.uid), userRepository.storedUser(UID)?.preference?.artists)
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
   * Builds the profile of a user with the given favorite artists.
   *
   * @param artists the favorite artists of the user
   * @param uid the identifier of the user, the signed-in [UID] by default
   * @return the user profile
   */
  private fun userWithArtists(artists: List<String>, uid: String = UID): User =
      User(uid = uid, userName = "Artista Fan", preference = UserPreference(artists = artists))

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
    const val OTHER_UID = "uid-2"
    // Saved from a search, so it is not among the popular artists of the screen.
    const val NON_POPULAR_ARTIST_ID = "non-popular-artist"
    val PICASSO = Artist("mock-artist-001", "Pablo Picasso")
    val VAN_GOGH = Artist("mock-artist-002", "Vincent van Gogh")
    val RENOIR = Artist("mock-artist-003", "Pierre-Auguste Renoir")
    val MONET = Artist("mock-artist-004", "Claude Monet")
    val KLIMT = Artist("mock-artist-005", "Gustav Klimt")
    val MATISSE = Artist("mock-artist-006", "Henri Matisse")
    val ALL_ARTISTS = listOf(PICASSO, VAN_GOGH, RENOIR, MONET, KLIMT, MATISSE)
  }
}
