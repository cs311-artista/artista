package com.artista.artista.ui.profile

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.model.user.FakeUserRepository
import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserId
import com.artista.artista.model.user.UserPreference
import com.artista.artista.model.user.UsernameCache
import com.artista.artista.model.user.UsernameCacheInMemory
import com.artista.artista.utils.userNamed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [UserProfileViewModel], the saving of the username for the signed-in
 * user, and the username validation rule.
 *
 * @author Timz3rr
 */
class UserProfileViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private val usernameCache = RecordingUsernameCache()
  private lateinit var viewModel: UserProfileViewModel

  private companion object {
    /** Identifier of the users built by [userNamed]. */
    const val UID = "uid-1"
    const val OTHER_UID = "uid-2"
  }

  @Before
  fun setUp() {
    viewModel = UserProfileViewModel(authRepository, userRepository, usernameCache)
  }

  /** Checks that the username stored for the signed-in user is displayed once loaded. */
  @Test
  fun signIn_displaysStoredUsername() {
    userRepository.store(userNamed("Alice"))

    signIn()

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /**
   * Checks that a first sign-in, with no profile stored yet, shows the placeholder and no error.
   */
  @Test
  fun signIn_withoutStoredUser_displaysNoUsernameAndNoError() {
    signIn()

    assertNull(viewModel.uiState.value.username)
    assertNull(viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that a failed load reports the message of its failure. */
  @Test
  fun signIn_whenLoadFails_showsErrorMessage() {
    userRepository.readFailure = AppException.Network()

    signIn()

    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that signing in as another user replaces the username with theirs. */
  @Test
  fun signIn_asAnotherUser_displaysTheirUsername() {
    userRepository.store(userNamed("Alice"))
    userRepository.store(User(uid = OTHER_UID, userName = "Bob", preference = UserPreference()))

    signIn()
    signIn(OTHER_UID)

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /**
   * Checks that a slow load of a user who signed out does not replace the username of the next user
   * when it finishes last.
   */
  @Test
  fun signIn_afterSlowLoadOfPreviousUser_keepsNewUsername() {
    userRepository.store(userNamed("Alice"))
    userRepository.store(User(uid = OTHER_UID, userName = "Bob", preference = UserPreference()))
    userRepository.readDelays[UID] = 1_000
    val scheduler = mainDispatcherRule.testDispatcher.scheduler

    // runCurrent starts each load without letting time pass, so Alice's load is still waiting
    // when she signs out and Bob signs in.
    authRepository.setAuthState(FakeAuthUser(UID))
    scheduler.runCurrent()
    authRepository.setAuthState(null)
    scheduler.runCurrent()
    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    runPendingCoroutines()

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /**
   * Checks that renaming while the profile is still loading keeps the new name, instead of the
   * stored one the load brings back when it finishes.
   */
  @Test
  fun updateUsername_whileProfileLoads_keepsNewUsername() {
    userRepository.store(userNamed("Alice"))
    userRepository.readDelays[UID] = 1_000

    // runCurrent starts the load without letting time pass, so it is still waiting when the user
    // renames.
    authRepository.setAuthState(FakeAuthUser(UID))
    mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Bob", viewModel.uiState.value.username)
    assertEquals("Bob", userRepository.storedUser(UID)?.userName)
  }

  /** Checks that signing out clears the username of the user who left. */
  @Test
  fun signOut_clearsUsername() {
    userRepository.store(userNamed("Alice"))
    signIn()

    authRepository.setAuthState(null)
    runPendingCoroutines()

    assertNull(viewModel.uiState.value.username)
  }

  /**
   * Checks that being signed out from the start does not clear a username already displayed, since
   * no user left.
   */
  @Test
  fun signedOutFromStart_keepsDisplayedUsername() {
    viewModel.setUser(userNamed("Alice"))

    runPendingCoroutines()

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Checks that a new username is saved for the signed-in user. */
  @Test
  fun updateUsername_whenSignedIn_savesUsername() {
    userRepository.store(userNamed("Alice"))
    signIn()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Bob", userRepository.storedUser(UID)?.userName)
  }

  /** Checks that saving the username keeps the other stored fields, such as the preferences. */
  @Test
  fun updateUsername_keepsStoredPreferences() {
    val preference = UserPreference(artists = listOf("VanGogh"))
    userRepository.store(userNamed("Alice").copy(preference = preference))
    signIn()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals(preference, userRepository.storedUser(UID)?.preference)
  }

  /** Checks that the first username of a user with no stored profile creates that profile. */
  @Test
  fun updateUsername_withoutStoredUser_createsUser() {
    signIn()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals(
        User(uid = UID, userName = "Bob", preference = UserPreference()),
        userRepository.storedUser(UID),
    )
  }

  /** Checks that without a signed-in user the username is displayed but not saved. */
  @Test
  fun updateUsername_whenSignedOut_displaysButDoesNotSave() {
    runPendingCoroutines()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Bob", viewModel.uiState.value.username)
    assertEquals(0, userRepository.writeCount)
  }

  /** Checks that a failed save puts the previous username back and reports the failure. */
  @Test
  fun updateUsername_whenSaveFails_restoresPreviousUsernameAndShowsError() {
    userRepository.store(userNamed("Alice"))
    signIn()
    userRepository.writeFailure = AppException.Network()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Alice", viewModel.uiState.value.username)
    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that an invalid username is not saved. */
  @Test
  fun updateUsername_withInvalidName_doesNotSave() {
    userRepository.store(userNamed("Alice"))
    signIn()

    viewModel.updateUsername("Alice!")
    runPendingCoroutines()

    assertEquals(0, userRepository.writeCount)
  }

  /** Checks that the error message is removed once the screen has shown it. */
  @Test
  fun clearErrorMessage_removesErrorMessage() {
    userRepository.readFailure = AppException.Network()
    signIn()

    viewModel.clearErrorMessage()

    assertNull(viewModel.uiState.value.errorMessageResId)
  }

  /**
   * Checks that the cached name is displayed while the profile is still loading, so that a slow or
   * missing connection does not leave the placeholder on screen.
   */
  @Test
  fun signIn_displaysCachedUsernameBeforeSlowLoadFinishes() {
    usernameCache.seed(UID, "Cached")
    userRepository.store(userNamed("Alice"))
    userRepository.readDelays[UID] = 1_000

    authRepository.setAuthState(FakeAuthUser(UID))
    mainDispatcherRule.testDispatcher.scheduler.runCurrent()

    assertEquals("Cached", viewModel.uiState.value.username)
  }

  /** Checks that the Firestore name replaces the cached one on screen and in the cache. */
  @Test
  fun signIn_withStoredProfile_replacesCachedUsernameAndRefreshesCache() {
    usernameCache.seed(UID, "Cached")
    userRepository.store(userNamed("Alice"))

    signIn()

    assertEquals("Alice", viewModel.uiState.value.username)
    assertEquals("Alice", usernameCache.stored(UID))
  }

  /** Checks that a profile with no name removes the outdated name from the screen and the cache. */
  @Test
  fun signIn_withProfileWithoutName_clearsCacheEntry() {
    usernameCache.seed(UID, "Cached")
    userRepository.store(userNamed(null))

    signIn()

    assertNull(viewModel.uiState.value.username)
    assertNull(usernameCache.stored(UID))
  }

  /** Checks that a blank remote name counts as no name, as it does on the screen. */
  @Test
  fun signIn_withProfileWithBlankName_clearsCacheEntry() {
    usernameCache.seed(UID, "Cached")
    userRepository.store(userNamed("   "))

    signIn()

    assertNull(viewModel.uiState.value.username)
    assertNull(usernameCache.stored(UID))
  }

  /**
   * Checks that being offline with a cached name shows that name and no error: the user has what
   * they need on screen, so a toast at every start without connection would only be noise.
   */
  @Test
  fun signIn_whenNetworkFailsWithCachedUsername_keepsItWithoutError() {
    usernameCache.seed(UID, "Cached")
    userRepository.readFailure = AppException.Network()

    signIn()

    assertEquals("Cached", viewModel.uiState.value.username)
    assertNull(viewModel.uiState.value.errorMessageResId)
    assertEquals("Cached", usernameCache.stored(UID))
  }

  /** Checks that being offline with nothing to display still reports the failure. */
  @Test
  fun signIn_whenNetworkFailsWithoutCachedUsername_showsError() {
    userRepository.readFailure = AppException.Network()

    signIn()

    assertNull(viewModel.uiState.value.username)
    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /**
   * Checks that a failure other than the network is reported even with a cached name, because being
   * offline is the only failure the cache is meant to hide.
   */
  @Test
  fun signIn_whenLoadFailsForAnotherReasonWithCachedUsername_showsError() {
    usernameCache.seed(UID, "Cached")
    userRepository.readFailure = AppException.Unauthorized()

    signIn()

    assertEquals("Cached", viewModel.uiState.value.username)
    assertEquals(R.string.error_unauthorized, viewModel.uiState.value.errorMessageResId)
  }

  /**
   * Checks that a user with no profile keeps their cached name: a rename made offline is cached
   * before its profile reaches Firestore, so a missing profile does not mean the name is wrong.
   */
  @Test
  fun signIn_withoutStoredProfile_leavesCacheAlone() {
    usernameCache.seed(UID, "Cached")

    signIn()

    assertEquals("Cached", viewModel.uiState.value.username)
    assertNull(viewModel.uiState.value.errorMessageResId)
    assertEquals(0, usernameCache.modificationCount)
  }

  /**
   * Checks that the cache is written while the Firestore write is still pending. Offline,
   * Firestore's write does not complete until the connection returns, so a cache write placed after
   * it would never happen and the new name would be lost on a cold start.
   */
  @Test
  fun updateUsername_whileFirestoreWriteIsPending_writesCache() {
    userRepository.store(userNamed("Alice"))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    viewModel.updateUsername("Bob")
    mainDispatcherRule.testDispatcher.scheduler.runCurrent()

    assertEquals("Alice", userRepository.storedUser(UID)?.userName)
    assertEquals("Bob", usernameCache.stored(UID))
  }

  /** Checks that a failed save puts the previous name back on screen and in the cache. */
  @Test
  fun updateUsername_whenSaveFails_rollsBackScreenAndCache() {
    userRepository.store(userNamed("Alice"))
    signIn()
    userRepository.writeFailure = AppException.Network()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Alice", viewModel.uiState.value.username)
    assertEquals("Alice", usernameCache.stored(UID))
    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /** Checks that a failed first name removes the entry written before the save was attempted. */
  @Test
  fun updateUsername_whenSaveFailsWithNoEarlierName_clearsCacheEntry() {
    signIn()
    userRepository.writeDelays[UID] = 1_000
    userRepository.writeFailure = AppException.Network()
    val scheduler = mainDispatcherRule.testDispatcher.scheduler

    viewModel.updateUsername("Bob")
    scheduler.runCurrent()
    assertEquals("Bob", usernameCache.stored(UID))
    runPendingCoroutines()

    assertNull(viewModel.uiState.value.username)
    assertNull(usernameCache.stored(UID))
  }

  /**
   * Checks that a failed rename goes back to a name saved by an earlier rename, not to the one
   * loaded at sign-in.
   */
  @Test
  fun updateUsername_whenSaveFailsAfterSuccessfulRename_rollsBackToThatRename() {
    userRepository.store(userNamed("Alice"))
    signIn()
    viewModel.updateUsername("Bob")
    runPendingCoroutines()
    userRepository.writeFailure = AppException.Network()

    viewModel.updateUsername("Carol")
    runPendingCoroutines()

    assertEquals("Bob", viewModel.uiState.value.username)
    assertEquals("Bob", usernameCache.stored(UID))
  }

  /**
   * Checks that a cached name shown after an offline start counts as the earlier name, so that a
   * failed rename does not wipe the only copy the device has.
   */
  @Test
  fun updateUsername_whenSaveFailsAfterOfflineStart_rollsBackToCachedUsername() {
    usernameCache.seed(UID, "Cached")
    userRepository.readFailure = AppException.Network()
    signIn()
    userRepository.writeFailure = AppException.Network()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals("Cached", viewModel.uiState.value.username)
    assertEquals("Cached", usernameCache.stored(UID))
  }

  /** Checks that a rename with nobody signed in stays in memory and leaves the cache untouched. */
  @Test
  fun updateUsername_whenSignedOut_doesNotTouchCache() {
    usernameCache.seed(UID, "Cached")
    runPendingCoroutines()

    viewModel.updateUsername("Bob")
    runPendingCoroutines()

    assertEquals(0, usernameCache.modificationCount)
    assertEquals("Cached", usernameCache.stored(UID))
  }

  /** Checks that a rename replacing a save still running is the only one written. */
  @Test
  fun updateUsername_twiceQuickly_writesOnlySecondName() {
    userRepository.store(userNamed("Alice"))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    // runCurrent lets the first save reach its write without letting time pass, so it is still
    // running when the second rename replaces it.
    viewModel.updateUsername("Bob")
    mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    viewModel.updateUsername("Carol")
    runPendingCoroutines()

    assertEquals(1, userRepository.writeCount)
    assertEquals("Carol", userRepository.storedUser(UID)?.userName)
    assertEquals("Carol", viewModel.uiState.value.username)
    assertEquals("Carol", usernameCache.stored(UID))
  }

  /**
   * Checks that a failed rename which replaced a running one goes back to the last name actually
   * saved. The name of the cancelled rename never reached Firestore, so restoring it would display
   * and cache a name the backend does not have.
   */
  @Test
  fun updateUsername_whenReplacingRenameFails_restoresLastSavedUsername() {
    userRepository.store(userNamed("Alice"))
    signIn()
    userRepository.writeDelays[UID] = 1_000

    viewModel.updateUsername("Bob")
    mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    userRepository.writeFailure = AppException.Network()
    viewModel.updateUsername("Carol")
    runPendingCoroutines()

    assertEquals("Alice", viewModel.uiState.value.username)
    assertEquals("Alice", usernameCache.stored(UID))
    assertEquals(R.string.error_network, viewModel.uiState.value.errorMessageResId)
  }

  /**
   * Checks that a save failing after its user signed out changes nothing for the next user: the
   * write ignores cancellation here because a backend can still report a failure once it is too
   * late to matter.
   */
  @Test
  fun signOut_duringSave_doesNotChangeNextUserState() {
    userRepository.store(userNamed("Alice"))
    userRepository.store(User(uid = OTHER_UID, userName = "Carol", preference = UserPreference()))
    signIn()
    userRepository.writeDelays[UID] = 1_000
    userRepository.writesIgnoreCancellation = true
    userRepository.writeFailure = AppException.Network()
    val scheduler = mainDispatcherRule.testDispatcher.scheduler

    viewModel.updateUsername("Bob")
    scheduler.runCurrent()
    authRepository.setAuthState(null)
    scheduler.runCurrent()
    authRepository.setAuthState(FakeAuthUser(OTHER_UID))
    runPendingCoroutines()

    assertEquals("Carol", viewModel.uiState.value.username)
    assertNull(viewModel.uiState.value.errorMessageResId)
    assertEquals("Carol", usernameCache.stored(OTHER_UID))
    assertNull(usernameCache.stored(UID))
  }

  /**
   * Checks that signing out removes the entry of the user who left, and only theirs, so that their
   * name does not remain on a shared device.
   */
  @Test
  fun signOut_clearsCacheEntryOfLeavingUser() {
    usernameCache.seed(UID, "Alice")
    usernameCache.seed(OTHER_UID, "Bob")
    userRepository.store(userNamed("Alice"))
    signIn()

    authRepository.setAuthState(null)
    runPendingCoroutines()

    assertNull(usernameCache.stored(UID))
    assertEquals("Bob", usernameCache.stored(OTHER_UID))
  }

  /** Checks that being signed out from the start clears nothing, since no user left. */
  @Test
  fun signedOutFromStart_doesNotClearCache() {
    usernameCache.seed(UID, "Alice")

    runPendingCoroutines()

    assertEquals("Alice", usernameCache.stored(UID))
    assertEquals(0, usernameCache.modificationCount)
  }

  /** Verifies that no username is displayed before a user is provided. */
  @Test
  fun initialState_hasNoUsername() {
    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that the username of the provided user is exposed. */
  @Test
  fun setUser_exposesUsername() {
    viewModel.setUser(userNamed("Alice"))

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Verifies that a user without a username exposes no username. */
  @Test
  fun setUser_withoutUsername_exposesNoUsername() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.setUser(userNamed(null))

    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that a blank username is treated as missing so the placeholder is shown instead. */
  @Test
  fun setUser_withBlankUsername_exposesNoUsername() {
    viewModel.setUser(userNamed("   "))

    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that providing another user replaces the previously displayed username. */
  @Test
  fun setUser_replacesPreviousUsername() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.setUser(userNamed("Bob"))

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /** Verifies that a valid username replaces the placeholder. */
  @Test
  fun updateUsername_exposesNewUsername() {
    viewModel.updateUsername("Alice")

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Verifies that a username entered by the user replaces the one of the provided user. */
  @Test
  fun updateUsername_replacesUsernameSetByUser() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.updateUsername("Bob")

    assertEquals("Bob", viewModel.uiState.value.username)
  }

  /** Verifies that surrounding whitespace typed by mistake is not stored in the username. */
  @Test
  fun updateUsername_trimsWhitespace() {
    viewModel.updateUsername("  John Doe  ")

    assertEquals("John Doe", viewModel.uiState.value.username)
  }

  /** Verifies that an invalid username leaves the current username unchanged. */
  @Test
  fun updateUsername_withInvalidName_keepsPreviousUsername() {
    viewModel.setUser(userNamed("Alice"))
    viewModel.updateUsername("Alice!")

    assertEquals("Alice", viewModel.uiState.value.username)
  }

  /** Verifies that an invalid username does not replace the placeholder. */
  @Test
  fun updateUsername_withInvalidName_keepsPlaceholder() {
    viewModel.updateUsername("   ")

    assertNull(viewModel.uiState.value.username)
  }

  /** Verifies that the username is stored in composed form, whatever form it was typed in. */
  @Test
  fun updateUsername_storesComposedForm() {
    viewModel.updateUsername("Timothe\u0301e")

    assertEquals("Timothée", viewModel.uiState.value.username)
  }

  /** Verifies that letters, digits and spaces are accepted. */
  @Test
  fun isValidUsername_acceptsLettersDigitsAndSpaces() {
    assertTrue(isValidUsername("John Doe 42"))
  }

  /** Verifies that accented letters are accepted, so users can write their real name. */
  @Test
  fun isValidUsername_acceptsAccentedLetters() {
    assertTrue(isValidUsername("Timothée"))
  }

  /** Verifies that accents typed in decomposed form (letter + combining mark) are accepted. */
  @Test
  fun isValidUsername_acceptsDecomposedAccents() {
    assertTrue(isValidUsername("Timothe\u0301e"))
  }

  /** Verifies that a combining accent does not count as an extra character for the length limit. */
  @Test
  fun isValidUsername_countsDecomposedAccentAsOneCharacter() {
    assertTrue(isValidUsername("e\u0301".repeat(MAX_USERNAME_LENGTH)))
  }

  /** Verifies that surrounding whitespace is ignored, since it is trimmed before saving. */
  @Test
  fun isValidUsername_ignoresSurroundingWhitespace() {
    assertTrue(isValidUsername("  Alice  "))
  }

  /** Verifies that an empty or blank username is rejected. */
  @Test
  fun isValidUsername_rejectsBlankName() {
    assertFalse(isValidUsername(""))
    assertFalse(isValidUsername("   "))
  }

  /** Verifies that a username of exactly the maximum length is accepted. */
  @Test
  fun isValidUsername_acceptsMaxLength() {
    assertTrue(isValidUsername("a".repeat(MAX_USERNAME_LENGTH)))
  }

  /** Verifies that a username longer than the maximum length is rejected. */
  @Test
  fun isValidUsername_rejectsTooLongName() {
    assertFalse(isValidUsername("a".repeat(MAX_USERNAME_LENGTH + 1)))
  }

  /** Verifies that the length is measured after trimming, as the trimmed name is what is saved. */
  @Test
  fun isValidUsername_measuresLengthAfterTrimming() {
    assertTrue(isValidUsername("  " + "a".repeat(MAX_USERNAME_LENGTH) + "  "))
  }

  /** Verifies that symbols and punctuation are rejected. */
  @Test
  fun isValidUsername_rejectsSpecialCharacters() {
    assertFalse(isValidUsername("Tim_42"))
    assertFalse(isValidUsername("tim@epfl"))
    assertFalse(isValidUsername("Alice!"))
  }

  /** Verifies that tabs and line breaks are rejected: only plain spaces may separate words. */
  @Test
  fun isValidUsername_rejectsTabsAndLineBreaks() {
    assertFalse(isValidUsername("John\tDoe"))
    assertFalse(isValidUsername("John\nDoe"))
  }

  /**
   * Signs in as the user with the given identifier and lets the ViewModel load their profile.
   *
   * @param uid identifier of the user to sign in as
   */
  private fun signIn(uid: UserId = UID) {
    authRepository.setAuthState(FakeAuthUser(uid))
    runPendingCoroutines()
  }

  /** Runs the coroutines the ViewModel launched, such as loading or saving the profile. */
  private fun runPendingCoroutines() {
    mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
  }

  /** [UsernameCacheInMemory] that also counts the changes made through [UsernameCache]. */
  private class RecordingUsernameCache : UsernameCache {
    private val cache = UsernameCacheInMemory()

    /** Number of calls to [saveUsername] and [clearUsername]. */
    var modificationCount: Int = 0
      private set

    /**
     * Stores a name without counting as a change, to set up the state a test starts from.
     *
     * @param userId identifier of the user
     * @param username the name to store
     */
    fun seed(userId: UserId, username: String) = runBlocking {
      cache.saveUsername(userId, username)
    }

    /**
     * Reads the name currently cached for a user.
     *
     * @param userId identifier of the user
     * @return the cached name, or null if there is none
     */
    fun stored(userId: UserId): String? = runBlocking { cache.getUsername(userId) }

    override suspend fun getUsername(userId: UserId): String? = cache.getUsername(userId)

    override suspend fun saveUsername(userId: UserId, username: String) {
      modificationCount++
      cache.saveUsername(userId, username)
    }

    override suspend fun clearUsername(userId: UserId) {
      modificationCount++
      cache.clearUsername(userId)
    }
  }
}
