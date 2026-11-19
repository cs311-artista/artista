// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
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

/**
 * Default time a test waits for an image to load, in milliseconds.
 *
 * Kept apart from [IMAGE_TIMEOUT_MS], which stays next to the artwork samples that use it, because
 * this file is shared by both test suites and cannot depend on a constant of the unit test sources.
 */
private const val DEFAULT_IMAGE_TIMEOUT_MS = 5_000L

/**
 * Replaces Coil's process-wide image loader with one answering from [engine], so tests never reach
 * the network. Callers must call [SingletonImageLoader.reset] after each test.
 *
 * It lives in the shared test sources, next to the other fakes, because the artwork and the profile
 * screen both display a downloaded image and Coil's singleton can only be replaced once: a copy per
 * suite would be two things to keep in sync on every Coil upgrade.
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
 * @param timeoutMs how long to wait for the image before failing the test
 * @author Timz3rr
 */
fun ComposeTestRule.waitUntilFallbackDisappears(
    fallbackTag: String,
    timeoutMs: Long = DEFAULT_IMAGE_TIMEOUT_MS,
) {
  waitUntil(timeoutMs) {
    onAllNodesWithTag(fallbackTag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
  }
}
