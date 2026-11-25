// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/**
 * Matches nodes whose test tag starts with [prefix], for tags that end with an artwork uid the test
 * does not know.
 *
 * @param prefix start of the test tag to match
 * @return a matcher accepting nodes whose test tag starts with [prefix]
 * @author hixeum
 */
fun hasTestTagStartingWith(prefix: String): SemanticsMatcher =
    SemanticsMatcher("test tag starts with '$prefix'") { node ->
      node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
