package com.artista.artista.model.user

/**
 * Represents a registered user in the app and the preferences attached to their account.
 *
 * @property uid the unique identifier of the user.
 * @property userName the display name chosen by the user, if any.
 * @property preference the saved preferences used to personalize recommendations.
 * @author 5kyPhy
 */
data class User(
    val uid: String,
    val userName: String? = null,
    val preference: UserPreference,
)
