// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.artista.artista.model.artwork.WIKIMEDIA_USER_AGENT
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the image loader that downloads artwork images from Wikimedia Commons.
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
class ArtworkImageLoaderTest {

  private companion object {
    const val IMAGE_URL =
        "https://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg?width=800"
  }

  /** Checks that the interceptor adds the given user agent to a request that has none. */
  @Test
  fun userAgentInterceptor_setsUserAgentHeader() {
    val capturing = CapturingInterceptor()
    val client =
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor("Test/1.0"))
            .addInterceptor(capturing)
            .build()

    client.newCall(Request.Builder().url(IMAGE_URL).build()).execute().close()

    assertEquals("Test/1.0", capturing.lastRequest?.header("User-Agent"))
  }

  /** Checks that the interceptor replaces a user agent that was already set on the request. */
  @Test
  fun userAgentInterceptor_replacesExistingUserAgent() {
    val capturing = CapturingInterceptor()
    val client =
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor("Test/1.0"))
            .addInterceptor(capturing)
            .build()

    client
        .newCall(Request.Builder().url(IMAGE_URL).header("User-Agent", "okhttp/4.12.0").build())
        .execute()
        .close()

    assertEquals(listOf("Test/1.0"), capturing.lastRequest?.headers("User-Agent"))
  }

  /** Checks that the http client used for images sends the Artista user agent to Wikimedia. */
  @Test
  fun createArtworkHttpClient_sendsWikimediaUserAgent() {
    val capturing = CapturingInterceptor()
    val client = createArtworkHttpClient().newBuilder().addInterceptor(capturing).build()

    client.newCall(Request.Builder().url(IMAGE_URL).build()).execute().close()

    assertEquals(WIKIMEDIA_USER_AGENT, capturing.lastRequest?.header("User-Agent"))
  }

  /**
   * Checks that the image loader downloads images through the given http client, so the user agent
   * set on that client is the one Wikimedia receives.
   */
  @Test
  fun createArtworkImageLoader_downloadsThroughGivenHttpClient() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val capturing = CapturingInterceptor()
    val client = createArtworkHttpClient().newBuilder().addInterceptor(capturing).build()
    val imageLoader = createArtworkImageLoader(context, client)

    // Caches are disabled because a response cached by an earlier run would skip the download
    // this test checks.
    val request =
        ImageRequest.Builder(context)
            .data(IMAGE_URL)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    runBlocking { imageLoader.execute(request) }

    val sent = requireNotNull(capturing.lastRequest) { "The image loader sent no request" }
    assertEquals(IMAGE_URL, sent.url.toString())
    assertEquals(WIKIMEDIA_USER_AGENT, sent.header("User-Agent"))
  }

  /** Records the last request it sees and answers it without reaching the network. */
  private class CapturingInterceptor : Interceptor {
    var lastRequest: Request? = null

    override fun intercept(chain: Interceptor.Chain): Response {
      lastRequest = chain.request()
      return Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body("".toResponseBody())
          .build()
    }
  }
}
