// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Sets the `User-Agent` header of every request, replacing any value already present.
 *
 * The `User-Agent` header tells the server which client sends the request, such as a browser or an
 * app. OkHttp sends a generic `okhttp/<version>` by default, which says nothing about the app.
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
 * The loader does the work behind every `AsyncImage`: it checks its memory and disk caches, and
 * otherwise downloads the image through [httpClient], so the request carries the Artista user
 * agent. The image is then decoded, cached and faded in. Caching and decoding keep Coil's defaults;
 * only the network client and the fade are configured here.
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
        // The network fetcher downloads images with our client. Coil calls the lambda only on the
        // first download, so building the loader does not slow down app start.
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
        // Fades images in instead of popping them over the fallback icon.
        .crossfade(true)
        .build()
