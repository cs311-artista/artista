// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Location
import com.artista.artista.model.user.USER_COLLECTION_PATH
import com.artista.artista.resources.C
import com.artista.artista.utils.FirebaseEmulator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/** Verifies the Firestore repository against the local Firebase emulators. */
@RunWith(AndroidJUnit4::class)
class SavedArtworksRepositoryFirestoreTest {

  @get:Rule val testTimeout: Timeout = Timeout.seconds(30)

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: SavedArtworksRepositoryFirestore
  private lateinit var userId: String
  private lateinit var userEmail: String

  @Before
  fun setUp() = runBlocking {
    auth = FirebaseEmulator.auth
    firestore = FirebaseEmulator.firestore
    auth.signOut()
    userEmail = "saved-artworks-${UUID.randomUUID()}@example.com"
    userId =
        checkNotNull(auth.createUserWithEmailAndPassword(userEmail, TEST_PASSWORD).await().user).uid
    repository = SavedArtworksRepositoryFirestore(firestore, auth)
  }

  @After
  fun tearDown() = runBlocking {
    // The rules only let a user clear their own artworks, and a test may end signed out or as
    // another user, so the data is wiped through the emulator's admin endpoint.
    FirebaseEmulator.clearFirestore()
    auth.signOut()
  }

  @Test
  fun saveArtworkThenReadAndRemovePersistsAllArtworkFields() = runBlocking {
    val artwork = completeArtwork()

    repository.saveArtwork(artwork)

    assertTrue(repository.isArtworkSaved(artwork.uid))
    assertEquals(listOf(artwork), repository.getSavedArtworks())
    assertEquals(
        listOf(artwork.uid),
        savedArtworksCollection(userId).get().await().documents.map { it.id },
    )

    repository.removeArtworkFromSaved(artwork.uid)

    assertFalse(repository.isArtworkSaved(artwork.uid))
    assertTrue(repository.getSavedArtworks().isEmpty())
  }

  @Test
  fun getSavedArtworksPreservesNullableFields() = runBlocking {
    val artwork =
        Artwork(
            uid = "artwork-nullable",
            name = null,
            artist = null,
            description = null,
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        )
    repository.saveArtwork(artwork)

    assertEquals(listOf(artwork), repository.getSavedArtworks())
  }

  @Test
  fun isArtworkSavedReturnsFalseForAnUnknownUid() = runBlocking {
    assertFalse(repository.isArtworkSaved("not-saved"))
  }

  @Test
  fun removeArtworkFromSavedThrowsNotFoundForAnUnknownUid() = runBlocking {
    assertThrowsSuspend<AppException.NotFound> { repository.removeArtworkFromSaved("not-saved") }
  }

  @Test
  fun operationsRejectBlankArtworkIds() = runBlocking {
    assertThrowsSuspend<AppException.InvalidInput> { repository.isArtworkSaved(" ") }
    assertThrowsSuspend<AppException.InvalidInput> {
      repository.saveArtwork(completeArtwork().copy(uid = ""))
    }
    assertThrowsSuspend<AppException.InvalidInput> { repository.removeArtworkFromSaved("\n") }
  }

  @Test
  fun allOperationsRequireAnAuthenticatedUser() = runBlocking {
    auth.signOut()

    assertThrowsSuspend<AppException.Unauthorized> { repository.isArtworkSaved("artwork-1") }
    assertThrowsSuspend<AppException.Unauthorized> { repository.saveArtwork(completeArtwork()) }
    assertThrowsSuspend<AppException.Unauthorized> {
      repository.removeArtworkFromSaved("artwork-1")
    }
    assertThrowsSuspend<AppException.Unauthorized> { repository.getSavedArtworks() }
  }

  @Test
  fun savedArtworksAreIsolatedByAuthenticatedUser() = runBlocking {
    repository.saveArtwork(completeArtwork())

    val secondUserEmail = "saved-artworks-${UUID.randomUUID()}@example.com"
    val secondUserId =
        checkNotNull(
                auth.createUserWithEmailAndPassword(secondUserEmail, TEST_PASSWORD).await().user
            )
            .uid
    val secondUserRepository = SavedArtworksRepositoryFirestore(firestore, auth)
    assertFalse(secondUserRepository.isArtworkSaved("artwork-1"))

    auth.signInWithEmailAndPassword(userEmail, TEST_PASSWORD).await()
    assertTrue(repository.isArtworkSaved("artwork-1"))
  }

  @Test
  fun getSavedArtworksDeserializesFirestoreTimestamps() = runBlocking {
    seedDocument(
        "artwork-1",
        artworkData("artwork-1") + ("conceptionDate" to Timestamp(Date(1_234L))),
    )

    assertEquals(listOf(completeArtwork()), repository.getSavedArtworks())
  }

  @Test
  fun getSavedArtworksRejectsMissingBlankAndMismatchedUids() = runBlocking {
    val malformedDocuments =
        listOf(
            "missing" to (artworkData("missing") - "uid"),
            "blank" to artworkData(" "),
            "mismatch" to artworkData("another-id"),
        )

    malformedDocuments.forEach { (documentId, data) ->
      clearSavedArtworks()
      seedDocument(documentId, data)
      assertThrowsSuspend<AppException.InvalidData> { repository.getSavedArtworks() }
    }
  }

  @Test
  fun getSavedArtworksRejectsMalformedOptionalAndNestedFields() = runBlocking {
    val malformedDocuments =
        listOf(
            artworkData() + ("name" to 3),
            artworkData() + ("artist" to "unknown"),
            artworkData() + ("artist" to mapOf("name" to "Missing uid")),
            artworkData() + ("artist" to mapOf("uid" to "artist-1")),
            artworkData() + ("artist" to mapOf("uid" to " ", "name" to "Invalid uid")),
            artworkData() + ("location" to "unknown"),
            artworkData() + ("location" to mapOf("latitude" to 1.0, "name" to null)),
            artworkData() +
                ("location" to mapOf("longitude" to 1.0, "latitude" to "north", "name" to null)),
            artworkData() +
                ("location" to mapOf("longitude" to 1.0, "latitude" to 2.0, "name" to 4)),
            artworkData() + ("dimension" to "unknown"),
            artworkData() + ("dimension" to mapOf("height" to 2.0)),
            artworkData() + ("dimension" to mapOf("width" to 1.0, "height" to "tall")),
            artworkData() + ("conceptionDate" to "yesterday"),
            artworkData() + ("museum" to 4),
            artworkData() + ("description" to 4),
        )

    malformedDocuments.forEachIndexed { index, data ->
      val documentId = "malformed-$index"
      clearSavedArtworks()
      seedDocument(documentId, data + ("uid" to documentId))
      assertThrowsSuspend<AppException.InvalidData> { repository.getSavedArtworks() }
    }
  }

  private suspend fun seedDocument(documentId: String, data: Map<String, Any?>) {
    savedArtworksCollection(userId).document(documentId).set(data).await()
  }

  private suspend fun clearSavedArtworks() {
    savedArtworksCollection(userId).get().await().documents.forEach {
      it.reference.delete().await()
    }
  }

  private fun savedArtworksCollection(id: String) =
      firestore
          .collection(USER_COLLECTION_PATH)
          .document(id)
          .collection(C.Constants.SAVED_ARTWORKS_COLLECTION_PATH)

  private fun completeArtwork() =
      Artwork(
          uid = "artwork-1",
          name = "The Thinker",
          artist = Artist("artist-1", "Auguste Rodin"),
          description = "A bronze study",
          conceptionDate = Date(1_234L),
          museum = "Musée Rodin",
          location = Location(2.3158, 48.8554, "Paris"),
          dimension = Dimension(98.0, 189.0),
      )

  private fun artworkData(uid: String = "artwork-1"): Map<String, Any?> =
      mapOf(
          "uid" to uid,
          "name" to "The Thinker",
          "artist" to mapOf("uid" to "artist-1", "name" to "Auguste Rodin"),
          "description" to "A bronze study",
          "conceptionDate" to Date(1_234L),
          "museum" to "Musée Rodin",
          "location" to mapOf("longitude" to 2.3158, "latitude" to 48.8554, "name" to "Paris"),
          "dimension" to mapOf("width" to 98.0, "height" to 189.0),
      )

  private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
      crossinline block: suspend () -> Unit
  ) {
    try {
      block()
      assertTrue("Expected ${T::class.simpleName} to be thrown", false)
    } catch (exception: Throwable) {
      assertTrue(
          "Expected ${T::class.simpleName}, got ${exception::class.simpleName}",
          exception is T,
      )
    }
  }

  private companion object {
    const val TEST_PASSWORD = "test-password"
  }
}
