package com.artista.artista.utils

import com.artista.artista.model.user.User
import com.artista.artista.model.user.UserPreference

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
