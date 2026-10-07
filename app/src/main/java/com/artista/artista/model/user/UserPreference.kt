package com.artista.artista.model.user

/**
 * Stores the user preferences used to tailor artwork recommendations.
 *
 * @property artists the artists the user wants to see more often.
 * @property type the artwork categories the user is interested in.
 * @property timePeriod the time periods the user prefers.
 * @author 5kyPhy
 */
data class UserPreference(
    val artists: List<String> = emptyList(),
    val type: List<String> = emptyList(),
    val timePeriod: List<String> = emptyList(),
)
