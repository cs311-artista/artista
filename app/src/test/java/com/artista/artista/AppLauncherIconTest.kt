// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista

import android.content.Context
import android.content.res.XmlResourceParser
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * Unit tests for the wiring of the launcher icon resources.
 *
 * The look of the logo and its safe zone are left to human review.
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
class AppLauncherIconTest {

  private companion object {
    const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    const val ANDROID_TEMPLATE_GREEN = "3ddc84"
    const val RGB_LENGTH = 6
  }

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** Checks that the manifest uses the app mipmaps for its icon and its round icon. */
  @Test
  fun manifest_declaresLauncherMipmapsAsIcons() {
    val application =
        context.assets.openXmlResourceParser("AndroidManifest.xml").readTags("application") {
          it.androidResourceId("icon") to it.androidResourceId("roundIcon")
        }

    assertEquals(listOf(R.mipmap.ic_launcher to R.mipmap.ic_launcher_round), application)
  }

  /** Checks that the launcher icon loads as an adaptive icon with its three layers. */
  @Test
  fun launcherIcon_isAdaptiveWithAllLayers() {
    assertIsAdaptiveWithAllLayers(R.mipmap.ic_launcher)
  }

  /** Checks that the round launcher icon loads as an adaptive icon with its three layers. */
  @Test
  fun roundLauncherIcon_isAdaptiveWithAllLayers() {
    assertIsAdaptiveWithAllLayers(R.mipmap.ic_launcher_round)
  }

  /** Checks that the launcher icon uses the dedicated drawable as its monochrome layer. */
  @Test
  fun launcherIcon_usesDedicatedMonochromeLayer() {
    assertUsesDedicatedMonochromeLayer(R.mipmap.ic_launcher)
  }

  /** Checks that the round launcher icon uses the dedicated drawable as its monochrome layer. */
  @Test
  fun roundLauncherIcon_usesDedicatedMonochromeLayer() {
    assertUsesDedicatedMonochromeLayer(R.mipmap.ic_launcher_round)
  }

  /** Checks that the background no longer contains the green of the Android template. */
  @Test
  fun launcherBackground_doesNotUseAndroidTemplateGreen() {
    val rgbFills = pathFillColors(R.drawable.ic_launcher_background).map { it?.rgbPart() }

    // Without this guard, a parser that finds no path would make the check below pass.
    assertTrue("The background has no path", rgbFills.isNotEmpty())
    assertFalse("Found the template green in $rgbFills", ANDROID_TEMPLATE_GREEN in rgbFills)
  }

  /** Checks that every path of the monochrome layer is filled with one single colour. */
  @Test
  fun monochromeLayer_usesExactlyOneFillColor() {
    val fills = pathFillColors(R.drawable.ic_launcher_monochrome)

    assertTrue("The monochrome layer has no path", fills.isNotEmpty())
    // Launchers tint this layer from its alpha, so a second colour or an unfilled path would not
    // show up as intended in a themed icon.
    assertEquals("Expected a single fill colour in $fills", 1, fills.toSet().size)
    assertNotNull("The paths of the monochrome layer have no fill colour", fills.first())
  }

  private fun assertIsAdaptiveWithAllLayers(mipmap: Int) {
    val icon = context.getDrawable(mipmap)

    assertTrue("Expected an adaptive icon but got $icon", icon is AdaptiveIconDrawable)
    val adaptiveIcon = icon as AdaptiveIconDrawable
    assertNotNull("Missing foreground layer", adaptiveIcon.foreground)
    assertNotNull("Missing background layer", adaptiveIcon.background)
    assertNotNull("Missing monochrome layer", adaptiveIcon.monochrome)
  }

  private fun assertUsesDedicatedMonochromeLayer(mipmap: Int) {
    val foreground = layerDrawableIds(mipmap, "foreground").single()
    val monochrome = layerDrawableIds(mipmap, "monochrome").single()

    assertEquals(R.drawable.ic_launcher_monochrome, monochrome)
    assertNotEquals(foreground, monochrome)
  }

  private fun layerDrawableIds(mipmap: Int, layer: String): List<Int> =
      context.resources.getXml(mipmap).readTags(layer) { it.androidResourceId("drawable") }

  private fun pathFillColors(vector: Int): List<String?> =
      context.resources.getXml(vector).readTags("path") {
        it.getAttributeValue(ANDROID_NAMESPACE, "fillColor")
      }

  private fun XmlResourceParser.androidResourceId(attribute: String): Int =
      getAttributeResourceValue(ANDROID_NAMESPACE, attribute, 0)

  // Compiled colours can be normalised (#3DDC84 becomes #ff3ddc84), so only the RGB part compares.
  private fun String.rgbPart(): String = takeLast(RGB_LENGTH).lowercase()

  private fun <T> XmlResourceParser.readTags(
      tag: String,
      read: (XmlResourceParser) -> T,
  ): List<T> = use {
    val values = mutableListOf<T>()
    while (next() != XmlPullParser.END_DOCUMENT) {
      if (eventType == XmlPullParser.START_TAG && name == tag) values += read(this)
    }
    values
  }
}
