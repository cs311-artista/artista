package com.artista.artista.model.user

/**
 * Stores the user preferences used to tailor artwork recommendations.
 *
 * @property artists the artists the user wants to see more often, if any.
 * @property type the artwork categories the user is interested in, if any.
 * @property timePeriod the time periods the user prefers, if any.
 * @author 5kyPhy
 */
data class UserPreference(
    val artists: List<String>? = null,
    val type: List<String>? = null,
    val timePeriod: List<String>? = null,
)
