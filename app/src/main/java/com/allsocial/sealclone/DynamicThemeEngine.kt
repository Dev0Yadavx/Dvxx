package com.allsocial.sealclone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic Material 3 Color Theme Engine.
 * Extracts dominant vibrant colors from media thumbnails and generates
 * harmonious Material 3 color schemes for background, surfaces, and accent UI.
 */
object DynamicThemeEngine {

    // Default primary fallback color
    private val DEFAULT_PRIMARY = Color(0xFFFFCC00) // Vibrant Gold / M3 Amber

    suspend fun extractThemeFromThumbnail(
        context: Context,
        thumbnailUrl: String
    ): Color? = withContext(Dispatchers.IO) {
        if (thumbnailUrl.isBlank()) return@withContext null
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .allowHardware(false)
                .size(64, 64) // Low-res downsample for fast extraction
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    return@withContext extractDominantColor(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun extractDominantColor(bitmap: Bitmap): Color {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        // Score based on saturation and brightness to find vibrant accents
        var bestColor = 0
        var highestScore = -1f

        val hsv = FloatArray(3)
        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xff
            if (a < 128) continue

            val r = (pixel ushr 16) and 0xff
            val g = (pixel ushr 8) and 0xff
            val b = pixel and 0xff

            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]

            // We prioritize vibrant colors over washed out dark/white pixels
            if (value in 0.25f..0.95f && saturation > 0.35f) {
                val score = saturation * 2f + value
                if (score > highestScore) {
                    highestScore = score
                    bestColor = pixel
                }
            }

            totalR += r
            totalG += g
            totalB += b
            count++
        }

        if (highestScore > 0f) {
            return Color(bestColor)
        }

        if (count > 0) {
            val avgR = (totalR / count).toInt()
            val avgG = (totalG / count).toInt()
            val avgB = (totalB / count).toInt()
            return Color(android.graphics.Color.rgb(avgR, avgG, avgB))
        }

        return DEFAULT_PRIMARY
    }

    /**
     * Builds complete Material 3 ColorScheme from an extracted primary seed color.
     */
    fun buildDynamicColorScheme(seedColor: Color, isDark: Boolean): ColorScheme {
        val argb = seedColor.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)

        val hue = hsv[0]

        if (isDark) {
            val primaryDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.70f, 0.95f)))
            val onPrimaryDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.90f, 0.20f)))
            val primaryContainerDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.75f, 0.38f)))
            val onPrimaryContainerDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.25f, 0.98f)))

            val surfaceDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.12f, 0.13f)))
            val surfaceVariantDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.18f, 0.18f)))
            val backgroundDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.10f, 0.09f)))

            return darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                surface = surfaceDark,
                onSurface = Color(0xFFE6E1E5),
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = Color(0xFFCAC4D0),
                background = backgroundDark,
                onBackground = Color(0xFFE6E1E5),
                outline = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.30f, 0.45f))),
                outlineVariant = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.25f, 0.28f)))
            )
        } else {
            val primaryLight = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.65f)))
            val onPrimaryLight = Color.White
            val primaryContainerLight = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.30f, 0.95f)))
            val onPrimaryContainerLight = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.95f, 0.25f)))

            val surfaceLight = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.05f, 0.98f)))
            val surfaceVariantLight = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.10f, 0.92f)))
            val backgroundLight = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.04f, 0.99f)))

            return lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                surface = surfaceLight,
                onSurface = Color(0xFF1C1B1F),
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = Color(0xFF49454F),
                background = backgroundLight,
                onBackground = Color(0xFF1C1B1F),
                outline = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.25f, 0.60f))),
                outlineVariant = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.15f, 0.85f)))
            )
        }
    }
}
