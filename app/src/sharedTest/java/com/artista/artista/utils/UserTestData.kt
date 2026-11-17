package com.artista.artista.utils

import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

/**
 * URL of the profile picture a test account reports.
 *
 * Shared by the unit and instrumented test suites, so that every test that signs a user in with a
 * picture names the same one.
 *
 * @author timo-by
 */
const val TEST_PICTURE_URL = "https://lh3.googleusercontent.com/a/artista-test-user"

/**
 * URL of a profile picture that differs from [TEST_PICTURE_URL].
 *
 * Used by the tests that check a stored picture being replaced, which need two distinct values.
 *
 * @author timo-by
 */
const val TEST_OTHER_PICTURE_URL = "https://lh3.googleusercontent.com/a/artista-other-user"

/**
 * Builds a test user that differs only by its name. Shared by the unit and instrumented test suites
 * so that both always build the same user when [User] changes.
 *
 * @param userName the name of the user, or null for a user without a name.
 * @return a user with a fixed uid and default preferences.
 * @author Timz3rr
 */
fun userNamed(userName: String?): User =
    User(uid = "uid-1", userName = userName, preference = UserPreference())
