// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.artista.artista.model.artwork.WIKIMEDIA_USER_AGENT
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Sets the `User-Agent` header of every request, replacing any value already present.
 *
 * @param userAgent value sent in the `User-Agent` header
 * @author Timz3rr
 */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {

  /**
   * Sends the request with [userAgent] as its only `User-Agent` header.
   *
   * @param chain the OkHttp chain carrying the request
   * @return the response to the modified request
   * @author Timz3rr
   */
  override fun intercept(chain: Interceptor.Chain): Response =
      chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}

/**
 * Creates the http client used to download artwork images.
 *
 * Wikimedia Commons answers 403 to clients without an identifying user agent, which OkHttp's
 * default one is not, so every image request carries [WIKIMEDIA_USER_AGENT].
 *
 * @return an http client that identifies the app to Wikimedia
 * @author Timz3rr
 */
fun createArtworkHttpClient(): OkHttpClient =
    OkHttpClient.Builder().addInterceptor(UserAgentInterceptor(WIKIMEDIA_USER_AGENT)).build()

/**
 * Creates the image loader the app uses to display artwork images.
 *
 * @param context context used by the loader for its caches
 * @param httpClient client used to download images; inject one in tests to intercept requests
 * @return an image loader that downloads through [httpClient]
 * @author Timz3rr
 */
fun createArtworkImageLoader(
    context: PlatformContext,
    httpClient: OkHttpClient = createArtworkHttpClient(),
): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
        // Fades images in instead of popping them over the fallback icon.
        .crossfade(true)
        .build()
