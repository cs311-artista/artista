// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.walking

import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [WalkingRouteRepositoryProvider].
 *
 * @author patrickmcdan
 */
class WalkingRouteRepositoryProviderTest {
  @Test
  fun walkingRouteRepository_calledTwice_returnsTheSameInstance() {
    assertSame(
        WalkingRouteRepositoryProvider.walkingRouteRepository,
        WalkingRouteRepositoryProvider.walkingRouteRepository,
    )
  }
}
