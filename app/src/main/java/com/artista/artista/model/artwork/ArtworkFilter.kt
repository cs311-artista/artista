// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import java.time.ZoneOffset

/**
 * Describes optional criteria for selecting artworks. Empty sets and null bounds mean no filter.
 *
 * Artist, museum and location values match exactly; locations compare against
 * [com.artista.artista.model.map.Location.name], so artworks without a location name never match an
 * active location criterion. Year bounds are inclusive, and artworks with no determinable
 * conception year are excluded while either bound is set.
 *
 * @property artists selected artist names
 * @property museums selected museum names
 * @property locations selected location names
 * @property fromYear inclusive lower conception-year bound
 * @property toYear inclusive upper conception-year bound
 * @author patrickmcdan
 */
data class ArtworkFilter(
    val artists: Set<String> = emptySet(),
    val museums: Set<String> = emptySet(),
    val locations: Set<String> = emptySet(),
    val fromYear: Int? = null,
    val toYear: Int? = null,
) {
  /** Returns whether at least one criterion is active. */
  fun isActive(): Boolean =
      artists.isNotEmpty() ||
          museums.isNotEmpty() ||
          locations.isNotEmpty() ||
          fromYear != null ||
          toYear != null
}

/**
 * Applies all active criteria while preserving the source order.
 *
 * @param criteria criteria to apply
 * @return artworks matching every active criterion
 * @author patrickmcdan
 */
fun List<Artwork>.applyFilter(criteria: ArtworkFilter): List<Artwork> = filter {
  (criteria.artists.isEmpty() || it.artist?.name in criteria.artists) &&
      (criteria.museums.isEmpty() || it.museum in criteria.museums) &&
      (criteria.locations.isEmpty() || it.location?.name in criteria.locations) &&
      matchesYear(it, criteria.fromYear, criteria.toYear)
}

/**
 * Returns distinct sorted artist options from the supplied artworks.
 *
 * @param artworks source artworks
 * @return sorted artist names
 * @author patrickmcdan
 */
fun artistOptions(artworks: List<Artwork>): List<String> =
    artworks.mapNotNull { it.artist?.name }.distinct().sorted()

/**
 * Returns distinct sorted museum options from the supplied artworks.
 *
 * @param artworks source artworks
 * @return sorted museum names
 * @author patrickmcdan
 */
fun museumOptions(artworks: List<Artwork>): List<String> =
    artworks.mapNotNull { it.museum }.distinct().sorted()

/**
 * Returns distinct sorted non-null location-name options from the supplied artworks.
 *
 * @param artworks source artworks
 * @return sorted location names
 * @author patrickmcdan
 */
fun locationOptions(artworks: List<Artwork>): List<String> =
    artworks.mapNotNull { it.location?.name }.distinct().sorted()

/**
 * Suggests unselected options, prioritizing prefix matches before other contains matches.
 *
 * @param query user-entered suggestion query
 * @param options available exact options
 * @param selected options already selected
 * @return matching options in prefix and alphabetical order
 * @author patrickmcdan
 */
fun suggestions(
    query: String,
    options: List<String>,
    selected: Set<String>,
): List<String> {
  val normalizedQuery = query.trim()
  return options
      .asSequence()
      .filterNot { it in selected }
      .filter { normalizedQuery.isBlank() || it.contains(normalizedQuery, ignoreCase = true) }
      .sortedWith(
          compareBy<String> { !it.startsWith(normalizedQuery, ignoreCase = true) }
              .thenBy { it.lowercase() }
      )
      .toList()
}

private fun matchesYear(artwork: Artwork, fromYear: Int?, toYear: Int?): Boolean {
  if (fromYear == null && toYear == null) return true
  val date = artwork.conceptionDate ?: return false
  // Wikidata dates are UTC. java.time returns a signed year, so BCE dates compare correctly.
  val year = date.toInstant().atZone(ZoneOffset.UTC).year
  return (fromYear == null || year >= fromYear) && (toYear == null || year <= toYear)
}
