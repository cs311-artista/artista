// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import java.io.IOException

const val IMAGE_URL =
    "https://commons.wikimedia.org/wiki/Special:FilePath/Mona%20Lisa.jpg?width=800"
const val BROKEN_IMAGE_URL =
    "https://commons.wikimedia.org/wiki/Special:FilePath/Missing.jpg?width=800"
const val IMAGE_TIMEOUT_MS = 5_000L

/**
 * Replaces Coil's process-wide image loader with one answering from [engine], so tests never reach
 * the network. Callers must call [SingletonImageLoader.reset] after each test.
 *
 * @param engine fake engine deciding the result of each image request
 * @author Timz3rr
 */
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
fun installFakeImageLoader(engine: FakeImageLoaderEngine) {
  val context = ApplicationProvider.getApplicationContext<Context>()
  SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
}

/**
 * Builds a fake engine for which the download of [imageUrl] always fails.
 *
 * @param imageUrl link whose download must fail
 * @param onRequested called when the image is requested, so a test can wait for the attempt
 * @return the fake engine, to give to [installFakeImageLoader]
 * @author Timz3rr
 */
@OptIn(ExperimentalCoilApi::class)
fun failingImageEngine(imageUrl: String, onRequested: () -> Unit): FakeImageLoaderEngine =
    FakeImageLoaderEngine.Builder()
        .intercept({ it == imageUrl }) { chain ->
          onRequested()
          ErrorResult(image = null, request = chain.request, throwable = IOException("404"))
        }
        .build()

/**
 * Waits until the fallback icon is gone from the composition, meaning the image has loaded.
 *
 * @param fallbackTag test tag of the fallback icon
 * @author Timz3rr
 */
fun ComposeTestRule.waitUntilFallbackDisappears(fallbackTag: String) {
  waitUntil(IMAGE_TIMEOUT_MS) {
    onAllNodesWithTag(fallbackTag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
  }
}
