// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.walking

/**
 * Provides the shared walking route repository used by the app.
 *
 * @author patrickmcdan
 */
object WalkingRouteRepositoryProvider {
  private val _walkingRouteRepository: WalkingRouteRepository by lazy {
    WalkingRouteRepositoryRemote()
  }

  /** The shared repository for retrieving walking routes. */
  val walkingRouteRepository: WalkingRouteRepository
    get() = _walkingRouteRepository
}
