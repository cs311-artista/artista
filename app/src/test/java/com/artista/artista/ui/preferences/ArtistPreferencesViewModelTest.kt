package com.artista.artista.ui.preferences

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference
import com.artista.artista.utils.TEST_PICTURE_URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
/**
 * Tests the state exposed by [ArtistPreferencesViewModel].
 *
 * @author Timz3rr, patrickmcdan, krfpn
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistPreferencesViewModelTest {

  private val artworkRepository = ControllableWikiDataRepository()
  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private lateinit var viewModel: ArtistPreferencesViewModel

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Before
  fun setUp() {
    viewModel = ArtistPreferencesViewModel(artworkRepository, authRepository, userRepository)
  }

  /** Verifies that nothing is shown or loaded while nobody is signed in. */
  @Test
  fun signedOut_showsNoArtistAndReadsNothing() = runTest {
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.savedArtists.isEmpty())
    assertFalse(state.isLoading)
    assertTrue(state.hasNoSavedArtists)
    assertEquals(0, userRepository.readCount)
  }

  /** Verifies that the saved artists are shown with their details, in the order they were saved. */
  @Test
  fun signIn_savedArtists_showsThemInSavedOrder() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, PICASSO.uid, MONET.uid)))

    signIn()

    // Compared by id and name: the mock artists also carry picture links, which this ViewModel
    // only passes through.
    assertEquals(
        listOf(KLIMT, PICASSO, MONET).map { it.uid to it.name },
        viewModel.uiState.value.savedArtists.map { it.uid to it.name },
    )
    assertFalse(viewModel.uiState.value.hasNoSavedArtists)
  }

  /** Verifies that a successful load neither reports a failure nor shows a message. */
  @Test
  fun signIn_loadSucceeds_reportsNoFailure() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))

    signIn()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.hasLoadFailed)
    assertNull(state.messageResId)
  }

  /** Verifies that a user without a profile sees the empty state, not a failure. */
  @Test
  fun signIn_noProfile_isEmpty() = runTest {
    signIn()

    val state = viewModel.uiState.value
    assertTrue(state.savedArtists.isEmpty())
    assertFalse(state.hasLoadFailed)
    assertTrue(state.hasNoSavedArtists)
  }

  /** Verifies that a profile without favorite artists shows the empty state. */
  @Test
  fun signIn_profileWithoutArtists_isEmpty() = runTest {
    userRepository.store(userWithArtists(emptyList()))

    signIn()

    assertTrue(viewModel.uiState.value.hasNoSavedArtists)
    assertEquals(0, artworkRepository.callCount)
  }

  /**
   * Verifies that an identifier stored twice is shown once, since the grid needs a unique key per
   * card.
   */
  @Test
  fun signIn_duplicatedArtist_showsItOnce() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid, KLIMT.uid)))

    signIn()

    assertEquals(listOf(KLIMT.uid, MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
  }

  /** Verifies that an artist the catalog does not know is hidden, while the others still load. */
  @Test
  fun signIn_unknownArtist_isHidden() = runTest {
    userRepository.store(userWithArtists(listOf(UNKNOWN_ARTIST_ID, MONET.uid)))

    signIn()

    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  /**
   * Verifies that an identifier the catalog rejects, such as one saved while the screen used mock
   * data, is hidden without failing the load.
   */
  @Test
  fun signIn_invalidArtistId_isHidden() = runTest {
    artworkRepository.failures[KLIMT.uid] = AppException.InvalidInput("Not a QID")
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))

    signIn()

    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  /**
   * Verifies that an artist the catalog reports as not found, which the contract of getArtist
   * allows instead of returning null, is hidden without failing the load.
   */
  @Test
  fun signIn_artistReportedNotFound_isHidden() = runTest {
    artworkRepository.failures[KLIMT.uid] = AppException.NotFound("No such artist")
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))

    signIn()

    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  /** Verifies that a user whose saved artists all cannot be resolved sees the empty state. */
  @Test
  fun signIn_onlyUnknownArtists_hasNoSavedArtists() = runTest {
    userRepository.store(userWithArtists(listOf(UNKNOWN_ARTIST_ID)))

    signIn()

    assertTrue(viewModel.uiState.value.hasNoSavedArtists)
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  /** Verifies that a network failure while fetching an artist fails the whole load. */
  @Test
  fun signIn_artistLookupFails_reportsFailureWithMessage() = runTest {
    artworkRepository.failures[MONET.uid] = AppException.Network()
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))

    signIn()

    val state = viewModel.uiState.value
    assertTrue(state.hasLoadFailed)
    assertFalse(state.isLoading)
    assertTrue(state.savedArtists.isEmpty())
    assertEquals(R.string.error_network, state.messageResId)
    // The error state is shown instead of the empty state.
    assertFalse(state.hasNoSavedArtists)
  }

  /** Verifies that failing to read the profile is reported with the matching message. */
  @Test
  fun signIn_profileReadFails_reportsFailureWithMessage() = runTest {
    userRepository.readFailure = AppException.Network()

    signIn()

    val state = viewModel.uiState.value
    assertTrue(state.hasLoadFailed)
    assertFalse(state.isLoading)
    assertEquals(R.string.error_network, state.messageResId)
  }

  /** Verifies that a failure that is not an AppException shows the generic message. */
  @Test
  fun signIn_unexpectedFailure_showsUnknownMessage() = runTest {
    userRepository.readFailure = IllegalStateException("Bug")

    signIn()

    assertTrue(viewModel.uiState.value.hasLoadFailed)
    assertEquals(R.string.error_unknown, viewModel.uiState.value.messageResId)
  }

  /** Verifies that the loading flag is set while the saved artists load, and reset after. */
  @Test
  fun signIn_whileLoading_isLoading() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.readDelays[UID] = 1_000
    authRepository.setAuthState(FakeAuthUser(UID))
    // runCurrent starts the load without letting time pass, so it is still waiting on the read.
    runCurrent()

    val state = viewModel.uiState.value
    assertTrue(state.isLoading)
    // The loading state is shown instead of the empty state.
    assertFalse(state.hasNoSavedArtists)
    assertFalse(state.canSearch)

    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** Verifies that the details of the saved artists are all requested at once. */
  @Test
  fun signIn_fetchesArtistsConcurrently() = runTest {
    artworkRepository.pending = CompletableDeferred()
    userRepository.store(userWithArtists(listOf(KLIMT.uid, PICASSO.uid, MONET.uid)))

    signIn()

    // Every lookup has started although none has answered yet.
    assertEquals(3, artworkRepository.callCount)
    assertTrue(viewModel.uiState.value.isLoading)

    artworkRepository.pending?.complete(Unit)
    advanceUntilIdle()

    assertEquals(3, viewModel.uiState.value.savedArtists.size)
  }

  /**
   * Verifies that clearing the toast message does not hide the error state, so Try again stays
   * available after the toast is gone.
   */
  @Test
  fun clearMessage_afterLoadFails_keepsFailure() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()
    // Otherwise the test would pass even if the failure never set a message to clear.
    assertNotNull(viewModel.uiState.value.messageResId)

    viewModel.clearMessage()

    assertNull(viewModel.uiState.value.messageResId)
    assertTrue(viewModel.uiState.value.hasLoadFailed)
  }

  // ---- Retrying ----

  /** Verifies that retrying after a failure loads the artists and clears the failure. */
  @Test
  fun retryLoad_afterFailure_loadsArtistsAndClearsFailure() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.readFailure = AppException.Network()
    signIn()

    userRepository.readFailure = null
    viewModel.retryLoad()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(KLIMT.uid), state.savedArtists.map { it.uid })
    assertFalse(state.hasLoadFailed)
    assertFalse(state.isLoading)
  }

  /** Verifies that a retry hides the error state at once and shows the loading state instead. */
  @Test
  fun retryLoad_afterFailure_showsLoadingWhileRetrying() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()

    viewModel.retryLoad()

    // Checked before the retry runs: the state changes as soon as the user taps.
    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  /** Verifies that a retry that fails again reports the failure again. */
  @Test
  fun retryLoad_failsAgain_reportsFailureAgain() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()
    viewModel.clearMessage()

    viewModel.retryLoad()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.hasLoadFailed)
    assertEquals(R.string.error_network, viewModel.uiState.value.messageResId)
    assertEquals(2, userRepository.readCount)
  }

  /** Verifies that a second tap on Try again, while the retry runs, starts no second load. */
  @Test
  fun retryLoad_doubleTap_loadsOnce() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()

    viewModel.retryLoad()
    viewModel.retryLoad()
    advanceUntilIdle()

    assertEquals(2, userRepository.readCount)
  }

  /** Verifies that retrying when the last load succeeded starts no new load. */
  @Test
  fun retryLoad_withoutFailure_isIgnored() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()

    viewModel.retryLoad()
    advanceUntilIdle()

    assertEquals(1, userRepository.readCount)
  }

  /** Verifies that retrying while nobody is signed in reads nothing. */
  @Test
  fun retryLoad_signedOut_isIgnored() = runTest {
    advanceUntilIdle()

    viewModel.retryLoad()
    advanceUntilIdle()

    assertEquals(0, userRepository.readCount)
  }

  // ---- Sign-in and sign-out ----

  /** Verifies that another user signing in replaces the artists of the previous one. */
  @Test
  fun signIn_otherUser_replacesPreviousUsersArtists() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.store(userWithArtists(listOf(MONET.uid), uid = OTHER_UID))
    signIn()

    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    advanceUntilIdle()

    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
  }

  /**
   * Verifies that a slow load of the previous user, ending after another user signed in, does not
   * replace the new user's artists.
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

    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
  }

  /** Verifies that signing out clears the artists of the user who left. */
  @Test
  fun signOut_clearsArtists() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()

    authRepository.setAuthState(null)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.savedArtists.isEmpty())
    assertTrue(viewModel.uiState.value.hasNoSavedArtists)
  }

  /** Verifies that signing out during a load resets the flag, so the screen is not stuck. */
  @Test
  fun signOut_whileLoading_stopsLoading() = runTest {
    userRepository.readDelays[UID] = 1_000
    authRepository.setAuthState(FakeAuthUser(UID))
    runCurrent()

    authRepository.setAuthState(null)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** Verifies that the failure of the user who left is not shown after they sign out. */
  @Test
  fun signOut_afterLoadFails_clearsFailure() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()

    authRepository.setAuthState(null)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  /** Verifies that the failure of the previous user is not shown to the next one. */
  @Test
  fun signIn_otherUser_afterLoadFails_clearsFailure() = runTest {
    userRepository.readFailure = AppException.Network()
    signIn()

    userRepository.readFailure = null
    userRepository.store(userWithArtists(listOf(MONET.uid), uid = OTHER_UID))
    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.hasLoadFailed)
    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
  }

  // ---- Untoggling artists ----

  /** Verifies that tapping a card untoggles it and enables Save, without writing anything. */
  @Test
  fun toggleArtist_untogglesArtistAndEnablesSave() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    signIn()

    viewModel.toggleArtist(KLIMT.uid)

    val state = viewModel.uiState.value
    assertEquals(setOf(KLIMT.uid), state.deselectedArtists)
    assertTrue(state.canSave)
    // The untoggled card stays shown until the user saves.
    assertEquals(listOf(KLIMT.uid, MONET.uid), state.displayedArtists.map { it.uid })
    advanceUntilIdle()
    assertEquals(0, userRepository.writeCount)
  }

  /** Verifies that tapping an untoggled card toggles it back and disables Save again. */
  @Test
  fun toggleArtist_twice_togglesBackAndDisablesSave() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    signIn()

    viewModel.toggleArtist(KLIMT.uid)
    viewModel.toggleArtist(KLIMT.uid)

    assertTrue(viewModel.uiState.value.deselectedArtists.isEmpty())
    assertFalse(viewModel.uiState.value.canSave)
  }

  /** Verifies that toggling an artist that is not shown is ignored, so Save stays disabled. */
  @Test
  fun toggleArtist_artistNotShown_isIgnored() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()

    viewModel.toggleArtist(MONET.uid)

    assertTrue(viewModel.uiState.value.deselectedArtists.isEmpty())
    assertFalse(viewModel.uiState.value.canSave)
  }

  /** Verifies that a tap made while the saved artists load is ignored. */
  @Test
  fun toggleArtist_whileLoading_isIgnored() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    // The first load ends, then a second user's slow load is running when the user taps a card
    // of the first one.
    signIn()
    userRepository.store(userWithArtists(listOf(KLIMT.uid), uid = OTHER_UID))
    userRepository.readDelays[OTHER_UID] = 1_000
    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    runCurrent()

    viewModel.toggleArtist(KLIMT.uid)

    assertTrue(viewModel.uiState.value.deselectedArtists.isEmpty())
  }

  /** Verifies that no card can be toggled while a save runs. */
  @Test
  fun toggleArtist_whileSaving_isIgnored() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    signIn()
    userRepository.writeDelays[UID] = 1_000
    viewModel.toggleArtist(KLIMT.uid)
    viewModel.saveChanges()
    runCurrent()

    viewModel.toggleArtist(MONET.uid)

    assertTrue(viewModel.uiState.value.deselectedArtists.isEmpty())
    advanceUntilIdle()
    assertEquals(listOf(MONET.uid), userRepository.storedUser(UID)?.preference?.artists)
  }

  /** Verifies that the untoggled artists of the user who left are not kept for the next one. */
  @Test
  fun signIn_otherUser_clearsUntoggledArtists() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    userRepository.store(userWithArtists(listOf(KLIMT.uid), uid = OTHER_UID))
    signIn()
    viewModel.toggleArtist(KLIMT.uid)

    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.deselectedArtists.isEmpty())
    assertFalse(viewModel.uiState.value.canSave)
  }

  // ---- Saving the removals ----

  /**
   * Verifies that saving removes the untoggled artist from the screen and from the stored profile,
   * keeping the other artists in order and the other fields of the profile.
   */
  @Test
  fun saveChanges_removesFromScreenAndProfile() = runTest {
    val storedPreference =
        UserPreference(
            artists = listOf(KLIMT.uid, PICASSO.uid, MONET.uid),
            type = listOf("painting"),
            timePeriod = listOf("19th"),
        )
    val storedUser =
        User(
            uid = UID,
            userName = "Artista Fan",
            preference = storedPreference,
            photoUrl = TEST_PICTURE_URL,
        )
    userRepository.store(storedUser)
    signIn()

    untoggleAndSave(PICASSO.uid)
    advanceUntilIdle()

    assertEquals(
        storedUser.copy(preference = storedPreference.copy(artists = listOf(KLIMT.uid, MONET.uid))),
        userRepository.storedUser(UID),
    )
    val state = viewModel.uiState.value
    assertEquals(listOf(KLIMT.uid, MONET.uid), state.savedArtists.map { it.uid })
    assertTrue(state.removingArtists.isEmpty())
    assertTrue(state.deselectedArtists.isEmpty())
    assertFalse(state.canSave)
    assertNull(state.messageResId)
  }

  /** Verifies that several untoggled artists are removed together, in a single write. */
  @Test
  fun saveChanges_severalArtists_removesThemInOneWrite() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, PICASSO.uid, MONET.uid)))
    signIn()

    untoggleAndSave(KLIMT.uid, MONET.uid)
    advanceUntilIdle()

    assertEquals(1, userRepository.writeCount)
    assertEquals(listOf(PICASSO.uid), userRepository.storedUser(UID)?.preference?.artists)
    assertEquals(listOf(PICASSO.uid), viewModel.uiState.value.savedArtists.map { it.uid })
  }

  /**
   * Verifies that the cards are hidden at once, before writing has finished, and that Save is
   * disabled while it runs.
   */
  @Test
  fun saveChanges_hidesCardsAndDisablesSaveBeforeWriteEnds() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    untoggleAndSave(KLIMT.uid)

    val state = viewModel.uiState.value
    assertEquals(setOf(KLIMT.uid), state.removingArtists)
    assertTrue(state.isSaving)
    assertFalse(state.canSave)
    assertEquals(listOf(MONET.uid), state.displayedArtists.map { it.uid })
    // Kept until writing succeeds, so a failure can put the card back in place.
    assertEquals(listOf(KLIMT.uid, MONET.uid), state.savedArtists.map { it.uid })
  }

  /** Verifies that a double tap on Save writes once. */
  @Test
  fun saveChanges_doubleTap_writesOnce() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    untoggleAndSave(KLIMT.uid)
    runCurrent()
    viewModel.saveChanges()
    advanceUntilIdle()

    assertEquals(1, userRepository.writeCount)
  }

  /**
   * Verifies that a failed write brings the cards back at their place, checked as before they were
   * untoggled, with Save disabled, an error message and the profile unchanged.
   */
  @Test
  fun saveChanges_writeFails_bringsCardsBackCheckedWithMessage() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, PICASSO.uid, MONET.uid)))
    signIn()
    userRepository.writeFailure = AppException.Network()

    untoggleAndSave(PICASSO.uid)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(KLIMT.uid, PICASSO.uid, MONET.uid), state.displayedArtists.map { it.uid })
    assertTrue(state.deselectedArtists.isEmpty())
    assertTrue(state.removingArtists.isEmpty())
    assertFalse(state.canSave)
    assertEquals(R.string.error_network, state.messageResId)
    assertEquals(
        listOf(KLIMT.uid, PICASSO.uid, MONET.uid),
        userRepository.storedUser(UID)?.preference?.artists,
    )
  }

  /**
   * Verifies that failing to read the profile before writing brings the card back and writes
   * nothing, so a profile that could not be read is never overwritten.
   */
  @Test
  fun saveChanges_readFails_bringsCardBackAndDoesNotWrite() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()
    userRepository.readFailure = AppException.Network()

    untoggleAndSave(KLIMT.uid)
    advanceUntilIdle()

    assertEquals(listOf(KLIMT.uid), viewModel.uiState.value.displayedArtists.map { it.uid })
    assertEquals(R.string.error_network, viewModel.uiState.value.messageResId)
    assertEquals(0, userRepository.writeCount)
  }

  /** Verifies that every stored copy of a duplicated artist is removed. */
  @Test
  fun saveChanges_duplicatedInProfile_removesEveryCopy() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid, KLIMT.uid)))
    signIn()

    untoggleAndSave(KLIMT.uid)
    advanceUntilIdle()

    assertEquals(listOf(MONET.uid), userRepository.storedUser(UID)?.preference?.artists)
  }

  /** Verifies that saving the removal of every artist shows the empty state. */
  @Test
  fun saveChanges_allArtists_isEmpty() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    untoggleAndSave(KLIMT.uid)

    // Shown as soon as the save button is tapped, not only once the write ends.
    assertTrue(viewModel.uiState.value.hasNoSavedArtists)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.hasNoSavedArtists)
    assertEquals(emptyList<ArtistId>(), userRepository.storedUser(UID)?.preference?.artists)
  }

  /** Verifies that saving without any untoggled artist writes nothing. */
  @Test
  fun saveChanges_withoutUntoggledArtist_isIgnored() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()

    viewModel.saveChanges()
    advanceUntilIdle()

    assertEquals(0, userRepository.writeCount)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /**
   * Verifies that when the artist is no longer in the stored profile, for example removed from
   * another device, the card is removed and the profile keeps the other artists.
   */
  @Test
  fun saveChanges_alreadyRemovedFromProfile_removesCard() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    signIn()
    userRepository.store(userWithArtists(listOf(MONET.uid)))

    untoggleAndSave(KLIMT.uid)
    advanceUntilIdle()

    assertEquals(listOf(MONET.uid), userRepository.storedUser(UID)?.preference?.artists)
    assertEquals(listOf(MONET.uid), viewModel.uiState.value.savedArtists.map { it.uid })
  }

  /** Verifies that when the profile was deleted meanwhile, the card is removed without writing. */
  @Test
  fun saveChanges_profileDeleted_removesCardWithoutWriting() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()
    userRepository.deleteUser(UID)

    untoggleAndSave(KLIMT.uid)
    advanceUntilIdle()

    assertEquals(0, userRepository.writeCount)
    assertTrue(viewModel.uiState.value.savedArtists.isEmpty())
    assertNull(viewModel.uiState.value.messageResId)
  }

  /**
   * Verifies that a save ending after another user signed in still updates the profile it was made
   * for, and leaves the new user's artists untouched.
   */
  @Test
  fun saveChanges_otherUserSignsInDuringWrite_keepsNewUsersArtists() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid, MONET.uid)))
    userRepository.store(userWithArtists(listOf(KLIMT.uid), uid = OTHER_UID))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    untoggleAndSave(KLIMT.uid)
    runCurrent()
    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    advanceUntilIdle()

    assertEquals(listOf(MONET.uid), userRepository.storedUser(UID)?.preference?.artists)
    assertEquals(listOf(KLIMT.uid), viewModel.uiState.value.savedArtists.map { it.uid })
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** Verifies that a save failing after the user signed out shows no message to anyone. */
  @Test
  fun saveChanges_failsAfterSignOut_showsNoMessage() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()
    userRepository.writeDelays[UID] = 1_000
    userRepository.writeFailure = AppException.Network()

    untoggleAndSave(KLIMT.uid)
    runCurrent()
    authRepository.setAuthState(null)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.messageResId)
    assertTrue(viewModel.uiState.value.savedArtists.isEmpty())
  }

  // ---- Search ----

  /** Verifies that the query is stored exactly as typed, spaces included. */
  @Test
  fun onSearchQueryChange_storesTextAsTyped() {
    viewModel.onSearchQueryChange("  Mon ")

    assertEquals("  Mon ", viewModel.uiState.value.searchQuery)
  }

  /** Verifies that a blank query shows every saved artist. */
  @Test
  fun displayedArtists_blankQuery_returnsAllArtists() {
    assertEquals(ALL_ARTISTS, displayedArtistsFor("   "))
  }

  /** Verifies that a partial name keeps the matching artist. */
  @Test
  fun displayedArtists_partialQuery_keepsMatchingArtist() {
    assertEquals(listOf(PICASSO), displayedArtistsFor("Pica"))
  }

  /** Verifies that the search ignores case. */
  @Test
  fun displayedArtists_ignoresCase() {
    assertEquals(listOf(MONET), displayedArtistsFor("cLaUdE"))
  }

  /** Verifies that spaces around the query are ignored. */
  @Test
  fun displayedArtists_ignoresSurroundingSpaces() {
    assertEquals(listOf(KLIMT), displayedArtistsFor("  Klimt  "))
  }

  /** Verifies that several matches keep the saved order. */
  @Test
  fun displayedArtists_severalMatches_keepSavedOrder() {
    // "Pablo Picasso" and "Pierre-Auguste Renoir" both contain "pi".
    assertEquals(listOf(PICASSO, RENOIR), displayedArtistsFor("pi"))
  }

  /** Verifies that a query matching no name shows no artist. */
  @Test
  fun displayedArtists_noMatch_returnsNoArtist() {
    assertTrue(displayedArtistsFor("xyz").isEmpty())
  }

  /** Verifies that an untoggled artist stays shown until the save button is hit. */
  @Test
  fun displayedArtists_keepsUntoggledArtists() {
    val state =
        ArtistPreferencesUiState(savedArtists = ALL_ARTISTS, deselectedArtists = setOf(MONET.uid))

    assertEquals(ALL_ARTISTS, state.displayedArtists)
  }

  /** Verifies that an artist being removed is hidden, even when it matches the query. */
  @Test
  fun displayedArtists_excludesArtistsBeingRemoved() {
    val state =
        ArtistPreferencesUiState(savedArtists = ALL_ARTISTS, removingArtists = setOf(MONET.uid))

    assertEquals(ALL_ARTISTS - MONET, state.displayedArtists)
  }

  // ---- Derived flags ----

  /** Verifies that the empty state is not shown while some artist is still displayed. */
  @Test
  fun hasNoSavedArtists_withArtistNotBeingRemoved_isFalse() {
    val state =
        ArtistPreferencesUiState(
            savedArtists = listOf(KLIMT, MONET),
            removingArtists = setOf(KLIMT.uid),
        )

    assertFalse(state.hasNoSavedArtists)
  }

  /** Verifies that untoggled artists still count, since their cards are shown until saved. */
  @Test
  fun hasNoSavedArtists_withOnlyUntoggledArtists_isFalse() {
    val state =
        ArtistPreferencesUiState(savedArtists = listOf(KLIMT), deselectedArtists = setOf(KLIMT.uid))

    assertFalse(state.hasNoSavedArtists)
  }

  /** Verifies that the empty state ignores the search: no match is not the same as no artist. */
  @Test
  fun hasNoSavedArtists_searchWithoutMatch_isFalse() {
    val state = ArtistPreferencesUiState(savedArtists = listOf(KLIMT), searchQuery = "xyz")

    assertFalse(state.hasNoSavedArtists)
  }

  /** Verifies that the empty state is not shown after a failed load, which has its own state. */
  @Test
  fun hasNoSavedArtists_afterLoadFails_isFalse() {
    assertFalse(ArtistPreferencesUiState(hasLoadFailed = true).hasNoSavedArtists)
  }

  /** Verifies that the search can be used once the artists are loaded. */
  @Test
  fun canSearch_afterLoad_isTrue() {
    assertTrue(ArtistPreferencesUiState(savedArtists = listOf(KLIMT)).canSearch)
  }

  /** Verifies that the search cannot be used after a failed load. */
  @Test
  fun canSearch_afterLoadFails_isFalse() {
    assertFalse(ArtistPreferencesUiState(hasLoadFailed = true).canSearch)
  }

  /** Verifies that the search cannot be used while the artists load. */
  @Test
  fun canSearch_whileLoading_isFalse() {
    assertFalse(ArtistPreferencesUiState(isLoading = true).canSearch)
  }

  /**
   * Verifies that Save is disabled while no artist is untoggled, for example in the empty state.
   */
  @Test
  fun canSave_withoutUntoggledArtist_isFalse() {
    assertFalse(ArtistPreferencesUiState(savedArtists = listOf(KLIMT)).canSave)
    assertFalse(ArtistPreferencesUiState().canSave)
  }

  /** Verifies that Save is enabled once an artist is untoggled. */
  @Test
  fun canSave_withUntoggledArtist_isTrue() {
    val state =
        ArtistPreferencesUiState(savedArtists = listOf(KLIMT), deselectedArtists = setOf(KLIMT.uid))

    assertTrue(state.canSave)
  }

  /** Verifies that Save is disabled while a save runs. */
  @Test
  fun canSave_whileSaving_isFalse() {
    val state =
        ArtistPreferencesUiState(
            savedArtists = listOf(KLIMT, MONET),
            deselectedArtists = setOf(MONET.uid),
            removingArtists = setOf(KLIMT.uid),
        )

    assertTrue(state.isSaving)
    assertFalse(state.canSave)
  }

  /** Verifies that Save is disabled while the artists load. */
  @Test
  fun canSave_whileLoading_isFalse() {
    assertFalse(
        ArtistPreferencesUiState(isLoading = true, deselectedArtists = setOf(KLIMT.uid)).canSave
    )
  }

  /** Verifies that Save is disabled after a failed load, where the error state is shown. */
  @Test
  fun canSave_afterLoadFails_isFalse() {
    assertFalse(
        ArtistPreferencesUiState(hasLoadFailed = true, deselectedArtists = setOf(KLIMT.uid)).canSave
    )
  }

  /** Verifies that clearing the message resets it. */
  @Test
  fun clearMessage_resetsMessage() = runTest {
    userRepository.store(userWithArtists(listOf(KLIMT.uid)))
    signIn()
    userRepository.writeFailure = AppException.Network()
    untoggleAndSave(KLIMT.uid)
    advanceUntilIdle()
    assertNotNull(viewModel.uiState.value.messageResId)

    viewModel.clearMessage()

    assertNull(viewModel.uiState.value.messageResId)
  }

  // ---- Factory ----

  // Only meant for coverage: the factory is boilerplate with no behavior of its own.
  /** Verifies that the factory creates the ViewModel it is meant for. */
  @Test
  fun provideFactory_createsArtistPreferencesViewModel() {
    val factory =
        ArtistPreferencesViewModel.provideFactory(artworkRepository, authRepository, userRepository)

    assertNotNull(factory.create(ArtistPreferencesViewModel::class.java))
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
   * Untoggles the given artists and taps Save, without letting the write run.
   *
   * @param artistIds identifiers of the artists to remove
   */
  private fun untoggleAndSave(vararg artistIds: ArtistId) {
    artistIds.forEach(viewModel::toggleArtist)
    viewModel.saveChanges()
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
      ArtistPreferencesUiState(savedArtists = ALL_ARTISTS, searchQuery = query).displayedArtists

  /**
   * Catalog whose artist lookups the test controls: they can be held until [pending] completes, and
   * a lookup fails with the exception set for its artist in [failures]. Otherwise, the artist comes
   * from the mock artists of [ArtworkRepositoryLocal], and every other method is forwarded to it.
   */
  private class ControllableWikiDataRepository(
      private val catalog: ArtworkRepositoryLocal = ArtworkRepositoryLocal()
  ) : WikiDataRepository by catalog {
    /** Exception thrown by the lookup of an artist, per artist. */
    val failures = mutableMapOf<ArtistId, Exception>()

    /** When set, every lookup waits for it to complete, which simulates a slow network. */
    var pending: CompletableDeferred<Unit>? = null

    /** Number of lookups started, to check that they run at the same time. */
    var callCount = 0
      private set

    override suspend fun getArtist(qid: ArtistId): Artist? {
      callCount++
      pending?.await()
      failures[qid]?.let { throw it }
      return catalog.getArtist(qid)
    }
  }

  private companion object {
    const val UID = "uid-1"
    const val OTHER_UID = "uid-2"
    // Not among the mock artists, so the catalog has no details for it.
    const val UNKNOWN_ARTIST_ID = "unknown-artist"
    val PICASSO = Artist("mock-artist-001", "Pablo Picasso")
    val VAN_GOGH = Artist("mock-artist-002", "Vincent van Gogh")
    val RENOIR = Artist("mock-artist-003", "Pierre-Auguste Renoir")
    val MONET = Artist("mock-artist-004", "Claude Monet")
    val KLIMT = Artist("mock-artist-005", "Gustav Klimt")
    val MATISSE = Artist("mock-artist-006", "Henri Matisse")
    val ALL_ARTISTS = listOf(PICASSO, VAN_GOGH, RENOIR, MONET, KLIMT, MATISSE)
  }
}
