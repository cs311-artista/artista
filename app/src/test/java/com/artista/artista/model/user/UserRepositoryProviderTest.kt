package com.artista.artista.model.user

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests repository selection and fake-data population provided by [UserRepositoryProvider].
 *
 * @author Copilot
 */
class UserRepositoryProviderTest {

  @Before
  fun setUp() {
    UserRepositoryProvider.useFakeRepository()
  }

  @After
  fun tearDown() {
    UserRepositoryProvider.useFakeRepository()
  }

  /** Verifies that fake mode selects the in-memory repository implementation. */
  @Test
  fun useFakeRepository_selectsLocalRepository() {
    assertTrue(UserRepositoryProvider.repository is UserRepositoryLocal)
  }

  /** Verifies that custom users are populated into the selected fake repository. */
  @Test
  fun populateFakeRepository_withCustomUsers_storesAllUsers() = runTest {
    val users = listOf(user("provider-custom-1"), user("provider-custom-2"))

    UserRepositoryProvider.populateFakeRepository(users)

    users.forEach { expected ->
      assertEquals(expected, UserRepositoryProvider.repository.getUser(expected.uid))
    }
  }

  /** Verifies that the default mock users are all available after population. */
  @Test
  fun populateFakeRepository_withoutArguments_storesDefaultUsers() = runTest {
    UserRepositoryProvider.populateFakeRepository()

    mockUsers.forEach { expected ->
      assertEquals(expected, UserRepositoryProvider.repository.getUser(expected.uid))
    }
  }

  /**
   * Verifies that population targets the provider's protected local repository rather than the
   * currently selected repository.
   */
  @Test
  fun populateFakeRepository_populatesLocalRepositoryOnly() = runTest {
    val selectedRepository = UserRepositoryLocal()
    val user = user("provider-local-only")
    UserRepositoryProvider.repository = selectedRepository

    UserRepositoryProvider.populateFakeRepository(listOf(user))
    UserRepositoryProvider.useFakeRepository()

    assertEquals(user, UserRepositoryProvider.repository.getUser(user.uid))
    try {
      selectedRepository.getUser(user.uid)
      throw AssertionError("The selected repository should not have been populated")
    } catch (_: NoSuchElementException) {
      // Expected: population is deliberately isolated from the selected repository.
    }
  }

  private fun user(uid: UserId) =
      User(uid = uid, userName = "Test user", preference = UserPreference())
}
