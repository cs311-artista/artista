// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.user.USER_COLLECTION_PATH
import com.artista.artista.resources.C
import com.artista.artista.utils.FirebaseEmulator
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Verifies the Firestore security rules against the local emulator.
 *
 * The tests call the Firestore SDK directly instead of the repositories, so they check the rules
 * themselves rather than the way the app uses them. The other user's documents are seeded through
 * [FirebaseEmulator.createDocumentBypassingRules]; that user needs no account because the rules
 * only compare its id with the signed-in uid.
 *
 * @author krfpn
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {

  /** Prevents emulator operations from leaving a test blocked indefinitely. */
  @get:Rule val testTimeout: Timeout = Timeout.seconds(30)

  private val firestore = FirebaseEmulator.firestore
  private val auth = FirebaseEmulator.auth

  /** Uid of the account signed in for the current test. */
  private lateinit var ownerId: String

  /** Starts every test from an empty database, signed in to a fresh account. */
  @Before
  fun setUp() = runBlocking {
    auth.signOut()
    FirebaseEmulator.clearFirestore()
    FirebaseEmulator.clearAccounts()
    ownerId = checkNotNull(auth.signInAnonymously().await().user).uid
  }

  /** Leaves neither a session, data nor an account behind for the next test. */
  @After
  fun tearDown() {
    auth.signOut()
    FirebaseEmulator.clearFirestore()
    FirebaseEmulator.clearAccounts()
  }

  /** Checks that a user can create their own profile. */
  @Test
  fun ownerCanCreateTheirProfile() = runTest {
    profile(ownerId).set(profileData(ownerId)).await()

    assertTrue(profile(ownerId).get().await().exists())
  }

  /** Checks that a user can read their own profile. */
  @Test
  fun ownerCanReadTheirProfile() = runTest {
    profile(ownerId).set(profileData(ownerId)).await()

    assertEquals(ownerId, profile(ownerId).get().await().getString("uid"))
  }

  /** Checks that a user can replace their own profile while keeping its uid. */
  @Test
  fun ownerCanUpdateTheirProfile() = runTest {
    profile(ownerId).set(profileData(ownerId)).await()

    profile(ownerId).set(profileData(ownerId, userName = "Renamed")).await()

    assertEquals("Renamed", profile(ownerId).get().await().getString("userName"))
  }

  /** Checks that a user can delete their own profile. */
  @Test
  fun ownerCanDeleteTheirProfile() = runTest {
    profile(ownerId).set(profileData(ownerId)).await()

    profile(ownerId).delete().await()

    assertFalse(profile(ownerId).get().await().exists())
  }

  /** Checks that a user cannot create their profile with a uid field naming someone else. */
  @Test
  fun ownerCannotCreateTheirProfileWithAnotherUid() = runTest {
    assertPermissionDenied { profile(ownerId).set(profileData(OTHER_USER_ID)).await() }
  }

  /** Checks that a user cannot change the uid field of their existing profile. */
  @Test
  fun ownerCannotChangeTheUidOfTheirProfile() = runTest {
    profile(ownerId).set(profileData(ownerId)).await()

    assertPermissionDenied { profile(ownerId).set(profileData(OTHER_USER_ID)).await() }
  }

  /** Checks that a signed-in user cannot read another user's profile. */
  @Test
  fun signedInUserCannotReadAnotherProfile() = runTest {
    seedOtherUserProfile()

    assertPermissionDenied { profile(OTHER_USER_ID).get().await() }
  }

  /** Checks that a signed-in user cannot write another user's profile, even with its own uid. */
  @Test
  fun signedInUserCannotWriteAnotherProfile() = runTest {
    assertPermissionDenied { profile(OTHER_USER_ID).set(profileData(OTHER_USER_ID)).await() }
  }

  /** Checks that a signed-in user cannot delete another user's profile. */
  @Test
  fun signedInUserCannotDeleteAnotherProfile() = runTest {
    seedOtherUserProfile()

    assertPermissionDenied { profile(OTHER_USER_ID).delete().await() }
  }

  /** Checks that a signed-in user cannot list every profile of the collection. */
  @Test
  fun signedInUserCannotListAllProfiles() = runTest {
    seedOtherUserProfile()

    assertPermissionDenied { firestore.collection(USER_COLLECTION_PATH).get().await() }
  }

  /** Checks that a signed-out user cannot read a profile. */
  @Test
  fun signedOutUserCannotReadAProfile() = runTest {
    seedOtherUserProfile()
    auth.signOut()

    assertPermissionDenied { profile(OTHER_USER_ID).get().await() }
  }

  /** Checks that a signed-out user cannot write a profile. */
  @Test
  fun signedOutUserCannotWriteAProfile() = runTest {
    auth.signOut()

    assertPermissionDenied { profile(OTHER_USER_ID).set(profileData(OTHER_USER_ID)).await() }
  }

  /** Checks that a user can write, read and delete their own saved artworks. */
  @Test
  fun ownerCanWriteAndReadTheirSavedArtworks() = runTest {
    savedArtwork(ownerId).set(mapOf("uid" to ARTWORK_ID)).await()
    assertTrue(savedArtwork(ownerId).get().await().exists())

    savedArtwork(ownerId).delete().await()

    assertFalse(savedArtwork(ownerId).get().await().exists())
  }

  /** Checks that a signed-in user cannot read another user's saved artworks. */
  @Test
  fun signedInUserCannotReadAnotherUsersSavedArtworks() = runTest {
    seedOtherUserSavedArtwork()

    assertPermissionDenied { savedArtwork(OTHER_USER_ID).get().await() }
  }

  /** Checks that a signed-in user cannot write another user's saved artworks. */
  @Test
  fun signedInUserCannotWriteAnotherUsersSavedArtworks() = runTest {
    assertPermissionDenied { savedArtwork(OTHER_USER_ID).set(mapOf("uid" to ARTWORK_ID)).await() }
  }

  /** Checks that a collection without its own rule is denied, so no catch-all rule remains. */
  @Test
  fun collectionsWithoutARuleAreDenied() = runTest {
    assertPermissionDenied {
      firestore.collection(UNRULED_COLLECTION).document("doc").set(mapOf("x" to 1)).await()
    }
  }

  /**
   * Returns the reference of a user's profile.
   *
   * @param uid id of the profile's owner
   * @return the reference to `Users/{uid}`
   */
  private fun profile(uid: String): DocumentReference =
      firestore.collection(USER_COLLECTION_PATH).document(uid)

  /**
   * Returns the reference of one saved artwork of a user.
   *
   * @param uid id of the artworks' owner
   * @return the reference to `Users/{uid}/saved_artworks/{ARTWORK_ID}`
   */
  private fun savedArtwork(uid: String): DocumentReference =
      profile(uid).collection(C.Constants.SAVED_ARTWORKS_COLLECTION_PATH).document(ARTWORK_ID)

  /**
   * Builds the fields of a valid profile.
   *
   * @param uid value of the stored uid field
   * @param userName value of the stored username
   * @return the profile fields, shaped like the ones `UserRepositoryFirestore` writes
   */
  private fun profileData(uid: String, userName: String = "Test User"): Map<String, Any> =
      mapOf(
          "uid" to uid,
          "userName" to userName,
          "preference" to mapOf("artists" to listOf("Monet")),
      )

  /** Stores the other user's profile, which the signed-in user may not write themselves. */
  private fun seedOtherUserProfile() {
    FirebaseEmulator.createDocumentBypassingRules(
        USER_COLLECTION_PATH,
        OTHER_USER_ID,
        """{"uid":{"stringValue":"$OTHER_USER_ID"}}""",
    )
  }

  /** Stores one saved artwork of the other user, which the signed-in user may not write. */
  private fun seedOtherUserSavedArtwork() {
    FirebaseEmulator.createDocumentBypassingRules(
        "$USER_COLLECTION_PATH/$OTHER_USER_ID/${C.Constants.SAVED_ARTWORKS_COLLECTION_PATH}",
        ARTWORK_ID,
        """{"uid":{"stringValue":"$ARTWORK_ID"}}""",
    )
  }

  /**
   * Asserts that a Firestore operation is refused by the security rules.
   *
   * @param operation the read or write expected to be denied
   */
  private suspend fun assertPermissionDenied(operation: suspend () -> Unit) {
    // Catch only Firestore errors, so any other failure still surfaces with its own stack trace.
    val error =
        try {
          operation()
          null
        } catch (e: FirebaseFirestoreException) {
          e
        }
    assertNotNull("expected the rules to deny the operation, but it succeeded", error)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
  }

  private companion object {
    /** Id of a user who is never signed in; only its documents exist. */
    const val OTHER_USER_ID = "other-user"

    /** Id of the saved artwork used in the subcollection tests. */
    const val ARTWORK_ID = "artwork-1"

    /** A collection that has no rule in `firestore.rules`. */
    const val UNRULED_COLLECTION = "Other"
  }
}
