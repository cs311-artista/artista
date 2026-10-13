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
    UserRepositoryProvider.resetFakeRepository()
  }

  @After
  fun tearDown() {
    UserRepositoryProvider.resetFakeRepository()
  }

  /** Verifies that fake mode selects the in-memory repository implementation. */
  @Test
  fun useFakeRepository_selectsLocalRepository() {
    assertTrue(UserRepositoryProvider.repository is UserRepositoryLocal)
  }

  /** Verifies that custom users are populated into the reset fake repository. */
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

  /** Verifies that resetting the fake repository removes users from the previous instance. */
  @Test
  fun resetFakeRepository_clearsPreviouslyPopulatedUsers() = runTest {
    val user = user("provider-local-only")
    UserRepositoryProvider.populateFakeRepository(listOf(user))

    UserRepositoryProvider.resetFakeRepository()

    try {
      UserRepositoryProvider.repository.getUser(user.uid)
      throw AssertionError("The reset repository should not contain previous users")
    } catch (_: NoSuchElementException) {
      // Expected: reset creates a fresh in-memory repository.
    }
  }

  private fun user(uid: UserId) =
      User(uid = uid, userName = "Test user", preference = UserPreference())
}
