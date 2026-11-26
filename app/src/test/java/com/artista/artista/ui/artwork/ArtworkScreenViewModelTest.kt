// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.AddressRepository
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.FakeAddressRepository
import com.artista.artista.model.map.Location
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ArtworkScreenViewModel].
 *
 * @author hixeum, Timz3rr
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkScreenViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val savedRepository = ArtworkRepositoryProvider.mockRepository
  private val wikiDataRepository = ArtworkRepositoryProvider.mockRepository
  private val addressRepository = FakeAddressRepository()
  private lateinit var viewModel: ArtworkScreenViewModel

  private val sampleArtwork =
      Artwork(
          uid = "monaId",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = "Portrait of Lisa Gherardini",
          conceptionDate = null,
          museum = "Louvre",
          location = null,
          dimension = null,
      )
  private val locatedArtwork =
      sampleArtwork.copy(
          uid = "thinkerId",
          name = "The Thinker",
          location = Location(longitude = 2.3158, latitude = 48.8553, name = "Musée Rodin"),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    savedRepository.clear()
    wikiDataRepository.clear()
    ArtworkRepositoryProvider.populateAPI(listOf(sampleArtwork))
    viewModel =
        ArtworkScreenViewModel(
            sampleArtwork.uid,
            wikiDataRepository,
            savedRepository,
            addressRepository,
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun setArtwork_updatesArtworkAndIsSavedFromRepository() = runTest {
    savedRepository.saveArtwork(sampleArtwork)

    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertTrue(state.isSaved)
  }

  @Test
  fun setArtwork_exposesLoadingUntilArtworkIsFetched() = runTest {
    val artworkResult = CompletableDeferred<Artwork>()
    val delayedRepository =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork = artworkResult.await()
        }
    viewModel =
        ArtworkScreenViewModel(
            sampleArtwork.uid,
            delayedRepository,
            savedRepository,
            addressRepository,
        )

    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(null, viewModel.uiState.value.artwork)

    artworkResult.complete(sampleArtwork)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(sampleArtwork, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  @Test
  fun setArtwork_whenLoadingFails_clearsLoadingAndExposesLoadFailure() = runTest {
    val artworkResult = CompletableDeferred<Artwork>()
    val delayedRepository =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork = artworkResult.await()
        }
    viewModel =
        ArtworkScreenViewModel(
            sampleArtwork.uid,
            delayedRepository,
            savedRepository,
            addressRepository,
        )

    assertTrue(viewModel.uiState.value.isLoading)
    artworkResult.completeExceptionally(AppException.Network())
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.hasLoadFailed)
    assertEquals(R.string.error_network, viewModel.uiState.value.loadErrorMessageResId)
    assertEquals(null, viewModel.uiState.value.artwork)
  }

  @Test
  fun retryArtwork_restartsLoadingAndDisplaysTheArtworkAfterRetry() = runTest {
    val firstResult = CompletableDeferred<Artwork>()
    val secondResult = CompletableDeferred<Artwork>()
    var requestCount = 0
    val delayedRepository =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork {
            requestCount++
            return if (requestCount == 1) firstResult.await() else secondResult.await()
          }
        }
    viewModel =
        ArtworkScreenViewModel(
            sampleArtwork.uid,
            delayedRepository,
            savedRepository,
            addressRepository,
        )
    firstResult.completeExceptionally(AppException.Network())
    advanceUntilIdle()

    viewModel.retryArtwork()
    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.hasLoadFailed)

    secondResult.complete(sampleArtwork)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(sampleArtwork, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  @Test
  fun setArtwork_whenNotSavedInRepository_setsIsSavedToFalse() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertFalse(state.isSaved)
  }

  @Test
  fun toggleSaveArtwork_whenArtworkNotSaved_savesArtworkInRepositoryAndUpdatesState() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()
    viewModel.toggleSaveArtwork()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isSaved)
    assertTrue(savedRepository.isArtworkSaved(sampleArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenArtworkAlreadySaved_removesArtworkFromRepositoryAndUpdatesState() =
      runTest {
        savedRepository.saveArtwork(sampleArtwork)
        viewModel.setArtwork(sampleArtwork.uid)
        advanceUntilIdle()

        viewModel.toggleSaveArtwork()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(savedRepository.isArtworkSaved(sampleArtwork.uid))
      }

  @Test
  fun setArtwork_whenArtworkHasNoName_tracksSavedStateByUid() = runTest {
    val untitledArtwork = sampleArtwork.copy(name = null)
    savedRepository.saveArtwork(untitledArtwork)

    viewModel.setArtwork(untitledArtwork.uid)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isSaved)

    viewModel.toggleSaveArtwork()
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isSaved)
    assertFalse(savedRepository.isArtworkSaved(untitledArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenCurrentArtworkIsNull_doesNothing() {
    viewModel.toggleSaveArtwork()

    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun setArtwork_whenRepositoryThrowsAppException_showsMappedError() = runTest {
    val invalidArtwork = sampleArtwork.copy(uid = "")

    viewModel.setArtwork(invalidArtwork.uid)
    advanceUntilIdle()

    assertEquals(null, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.isSaved)
    assertEquals(R.string.error_invalid_input, viewModel.uiState.value.errorMessageResId)
  }

  @Test
  fun toggleSaveArtwork_whileSaveIsPending_ignoresFurtherToggles() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()

    viewModel.toggleSaveArtwork()
    assertTrue(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)

    viewModel.toggleSaveArtwork()

    assertTrue(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)
    assertTrue(savedRepository.isArtworkSaved(sampleArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenRepositoryThrowsAppException_revertsStateAndShowsMappedError() =
      runTest {
        val failingSavedRepository =
            object : SavedArtworksRepository {
              override suspend fun isArtworkSaved(uid: ArtworkId): Boolean = false

              override suspend fun saveArtwork(artwork: Artwork) {
                throw AppException.InvalidInput("Simulated invalid artwork")
              }

              override suspend fun removeArtworkFromSaved(uid: ArtworkId) = Unit

              override suspend fun getSavedArtworks(): List<Artwork> = emptyList()

              override suspend fun getSavedArtwork(uid: ArtworkId): Artwork? = null

              override suspend fun getCachedSavedArtwork(uid: ArtworkId): Artwork? = null

              override suspend fun getCachedSavedArtworks(): List<Artwork> = emptyList()
            }
        viewModel =
            ArtworkScreenViewModel(
                sampleArtwork.uid,
                wikiDataRepository,
                failingSavedRepository,
                addressRepository,
            )

        advanceUntilIdle()
        viewModel.clearErrorMessage()

        viewModel.toggleSaveArtwork()
        assertTrue(viewModel.uiState.value.isSaveInProgress)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaveInProgress)
        assertEquals(R.string.error_invalid_input, viewModel.uiState.value.errorMessageResId)
      }

  /** Checks that the address of a located artwork is looked up from its coordinates and exposed. */
  @Test
  fun setArtwork_withLocation_exposesFoundAddress() = runTest {
    viewModel = located(addressRepository)
    advanceUntilIdle()

    assertEquals(
        ArtworkAddressState.Found(FakeAddressRepository.DEFAULT_ADDRESS),
        viewModel.uiState.value.addressState,
    )
    assertEquals(
        listOf(Coordinate(latitude = 48.8553, longitude = 2.3158)),
        addressRepository.requestedCoordinates,
    )
  }

  /** Checks that the artwork is displayed while its address is still being looked up. */
  @Test
  fun setArtwork_withLocation_exposesArtworkBeforeAddressIsFound() = runTest {
    val addressResult = CompletableDeferred<String>()
    viewModel =
        located(
            object : AddressRepository {
              override suspend fun getAddress(coordinate: Coordinate): String =
                  addressResult.await()
            }
        )
    advanceUntilIdle()

    assertEquals(locatedArtwork, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(ArtworkAddressState.Loading, viewModel.uiState.value.addressState)

    addressResult.complete("Rue de Varenne, Paris")
    advanceUntilIdle()

    assertEquals(
        ArtworkAddressState.Found("Rue de Varenne, Paris"),
        viewModel.uiState.value.addressState,
    )
  }

  /** Checks that a failed address lookup only marks the address unavailable, not the artwork. */
  @Test
  fun setArtwork_whenAddressLookupFails_exposesUnavailableAndKeepsArtwork() = runTest {
    viewModel = located(FakeAddressRepository(error = AppException.Network()))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(ArtworkAddressState.Unavailable, state.addressState)
    assertEquals(locatedArtwork, state.artwork)
    assertFalse(state.hasLoadFailed)
    assertEquals(null, state.errorMessageResId)
  }

  /** Checks that an artwork without coordinates triggers no lookup and exposes no location. */
  @Test
  fun setArtwork_withoutLocation_exposesNoLocationWithoutLookup() = runTest {
    advanceUntilIdle()

    assertEquals(ArtworkAddressState.NoLocation, viewModel.uiState.value.addressState)
    assertTrue(addressRepository.requestedCoordinates.isEmpty())
  }

  /** Checks that a late answer from a replaced lookup cannot overwrite the newer address. */
  @Test
  fun retryArtwork_ignoresAddressOfReplacedLookup() = runTest {
    val firstAddress = CompletableDeferred<String>()
    val secondAddress = CompletableDeferred<String>()
    var lookupCount = 0
    viewModel =
        located(
            object : AddressRepository {
              override suspend fun getAddress(coordinate: Coordinate): String {
                lookupCount++
                return if (lookupCount == 1) firstAddress.await() else secondAddress.await()
              }
            }
        )
    advanceUntilIdle()

    viewModel.retryArtwork()
    advanceUntilIdle()
    secondAddress.complete("New address")
    firstAddress.complete("Old address")
    advanceUntilIdle()

    assertEquals(2, lookupCount)
    assertEquals(
        ArtworkAddressState.Found("New address"),
        viewModel.uiState.value.addressState,
    )
  }

  /** Checks that a cached saved artwork is displayed without waiting for the network. */
  @Test
  fun setArtwork_whenSavedArtworkIsCached_showsItWithoutWaitingForNetwork() = runTest {
    val neverAnswers = CompletableDeferred<Artwork?>()
    viewModel = cacheFirst(cached = sampleArtwork, fresh = neverAnswers)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertFalse(state.isLoading)
    assertTrue(state.isSaved)
    assertFalse(state.hasLoadFailed)
    neverAnswers.cancel()
  }

  /** Checks that a cached saved artwork is replaced by its up-to-date data once it arrives. */
  @Test
  fun setArtwork_whenCachedArtworkIsRefreshed_showsUpToDateData() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    val updatedArtwork = sampleArtwork.copy(description = "Updated description")
    viewModel = cacheFirst(cached = sampleArtwork, fresh = fresh)
    advanceUntilIdle()

    fresh.complete(updatedArtwork)
    advanceUntilIdle()

    assertEquals(updatedArtwork, viewModel.uiState.value.artwork)
    assertTrue(viewModel.uiState.value.isSaved)
  }

  /** Checks that a refresh moving the artwork looks up the address of its new coordinates. */
  @Test
  fun setArtwork_whenRefreshChangesLocation_looksUpNewAddress() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    viewModel = cacheFirst(cached = sampleArtwork, fresh = fresh)
    advanceUntilIdle()
    assertEquals(ArtworkAddressState.NoLocation, viewModel.uiState.value.addressState)

    fresh.complete(sampleArtwork.copy(location = locatedArtwork.location))
    advanceUntilIdle()

    assertEquals(
        ArtworkAddressState.Found(FakeAddressRepository.DEFAULT_ADDRESS),
        viewModel.uiState.value.addressState,
    )
  }

  /** Checks that a failed refresh, e.g. offline, keeps the cached artwork on screen. */
  @Test
  fun setArtwork_whenRefreshFails_keepsCachedArtwork() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    viewModel = cacheFirst(cached = sampleArtwork, fresh = fresh)
    advanceUntilIdle()

    fresh.completeExceptionally(AppException.Network())
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertTrue(state.isSaved)
    assertFalse(state.hasLoadFailed)
    assertEquals(null, state.errorMessageResId)
  }

  /**
   * Checks that a refresh failing with an unexpected exception, not an AppException, also keeps the
   * cached artwork on screen.
   */
  @Test
  fun setArtwork_whenRefreshFailsUnexpectedly_keepsCachedArtwork() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    viewModel = cacheFirst(cached = sampleArtwork, fresh = fresh)
    advanceUntilIdle()

    fresh.completeExceptionally(IllegalStateException("Simulated unexpected failure"))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertTrue(state.isSaved)
    assertFalse(state.hasLoadFailed)
    assertEquals(null, state.errorMessageResId)
  }

  /**
   * Checks that a refresh finding the cached artwork no longer saved (e.g. unsaved on another
   * device) turns the bookmark off and keeps the artwork on screen.
   */
  @Test
  fun setArtwork_whenRefreshFindsArtworkUnsaved_turnsBookmarkOff() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    viewModel = cacheFirst(cached = sampleArtwork, fresh = fresh)
    advanceUntilIdle()

    fresh.complete(null)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertFalse(state.isSaved)
    assertFalse(state.hasLoadFailed)
  }

  /**
   * Checks that a refresh finding the artwork unsaved leaves the bookmark alone while the user is
   * saving it again, since the user's own change wins.
   */
  @Test
  fun setArtwork_whenRefreshFindsArtworkUnsavedDuringSave_leavesBookmarkOn() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    val save = CompletableDeferred<Unit>()
    viewModel = cacheFirst(cached = sampleArtwork, fresh = fresh, pendingSave = save)
    advanceUntilIdle()
    viewModel.toggleSaveArtwork() // Unsave, answers right away
    advanceUntilIdle()
    viewModel.toggleSaveArtwork() // Save again, still in progress
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isSaveInProgress)

    fresh.complete(null)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isSaved)
    save.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)
  }

  /** Checks that a saved artwork missing from the cache is loaded with a regular read. */
  @Test
  fun setArtwork_whenSavedArtworkIsNotCached_loadsItFromRepository() = runTest {
    viewModel = cacheFirst(cached = null, fresh = CompletableDeferred(sampleArtwork))
    advanceUntilIdle()

    assertEquals(sampleArtwork, viewModel.uiState.value.artwork)
    assertTrue(viewModel.uiState.value.isSaved)
  }

  /** Checks that a saved artwork neither cached nor reachable offline shows the load error. */
  @Test
  fun setArtwork_whenNotCachedAndOffline_showsLoadError() = runTest {
    val fresh = CompletableDeferred<Artwork?>()
    fresh.completeExceptionally(AppException.Network())
    viewModel = cacheFirst(cached = null, fresh = fresh)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(null, state.artwork)
    assertTrue(state.hasLoadFailed)
    assertEquals(R.string.error_network, state.loadErrorMessageResId)
  }

  /** Checks that an artwork that is not saved is still loaded from Wikidata. */
  @Test
  fun setArtwork_whenArtworkIsNotSaved_loadsItFromWikiData() = runTest {
    val wikiDataArtwork = sampleArtwork.copy(description = "From Wikidata")
    val countingWikiData =
        object : WikiDataRepository by wikiDataRepository {
          var requests = 0

          override suspend fun getArtwork(uid: ArtworkId): Artwork {
            requests++
            return wikiDataArtwork
          }
        }
    viewModel =
        ArtworkScreenViewModel(
            sampleArtwork.uid,
            countingWikiData,
            // A bare CompletableDeferred(null) would pick the parent-job overload and never
            // complete, so the null answer is completed explicitly.
            CacheFirstSavedRepository(
                cached = null,
                fresh = CompletableDeferred<Artwork?>().apply { complete(null) },
            ),
            addressRepository,
        )
    advanceUntilIdle()

    assertEquals(1, countingWikiData.requests)
    assertEquals(wikiDataArtwork, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.isSaved)
  }

  /** Checks that an artwork neither saved nor found on Wikidata shows the not-found load error. */
  @Test
  fun setArtwork_whenWikiDataReturnsNull_showsNotFoundError() = runTest {
    val emptyWikiData =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(qid: ArtworkId): Artwork? = null
        }
    viewModel =
        ArtworkScreenViewModel(
            sampleArtwork.uid,
            emptyWikiData,
            CacheFirstSavedRepository(
                cached = null,
                fresh = CompletableDeferred<Artwork?>().apply { complete(null) },
            ),
            addressRepository,
        )
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(null, state.artwork)
    assertFalse(state.isLoading)
    assertTrue(state.hasLoadFailed)
    assertEquals(R.string.error_not_found, state.loadErrorMessageResId)
  }

  private fun cacheFirst(
      cached: Artwork?,
      fresh: CompletableDeferred<Artwork?>,
      pendingSave: CompletableDeferred<Unit>? = null,
  ): ArtworkScreenViewModel =
      ArtworkScreenViewModel(
          sampleArtwork.uid,
          wikiDataRepository,
          CacheFirstSavedRepository(cached, fresh, pendingSave),
          addressRepository,
      )

  /**
   * Saved-artworks repository whose cached copy is fixed and whose up-to-date read answers only
   * when the test completes [fresh], to simulate a slow or offline network. When [pendingSave] is
   * set, saving suspends until the test completes it; unsaving always succeeds right away.
   */
  private class CacheFirstSavedRepository(
      private val cached: Artwork?,
      private val fresh: CompletableDeferred<Artwork?>,
      private val pendingSave: CompletableDeferred<Unit>? = null,
  ) : SavedArtworksRepository by ArtworkRepositoryLocal() {
    override suspend fun getCachedSavedArtwork(uid: ArtworkId): Artwork? = cached

    override suspend fun getSavedArtwork(uid: ArtworkId): Artwork? = fresh.await()

    override suspend fun saveArtwork(artwork: Artwork) {
      pendingSave?.await()
    }

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) = Unit
  }

  private fun located(addressRepository: AddressRepository): ArtworkScreenViewModel {
    ArtworkRepositoryProvider.populateAPI(listOf(locatedArtwork))
    return ArtworkScreenViewModel(
        locatedArtwork.uid,
        wikiDataRepository,
        savedRepository,
        addressRepository,
    )
  }
}
