package com.pulse.music.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.pulse.music.data.Song
import com.pulse.music.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object ArtworkColorExtractor {
    suspend fun dominantColor(context: Context, song: Song, artworkUrl: String?): Color? =
        withContext(Dispatchers.IO) {
            val bitmap = artworkUrl
                ?.takeIf { it.isNotBlank() }
                ?.let(::bitmapFromUrl)
                ?: bitmapFromSong(context, song)

            bitmap?.let { source ->
                val palette = Palette.from(source)
                    .maximumColorCount(16)
                    .generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch
                val color = swatch?.let { Color(tintReadyColor(it.rgb)) }
                source.recycle()
                color
            }
        }

    private fun bitmapFromUrl(url: String): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Pulse-Android-Artwork")
            .get()
            .build()

        return try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                decodeSampledByteArray(bytes)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun bitmapFromSong(context: Context, song: Song): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(song.albumArtUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            }
            resolver.openInputStream(song.albumArtUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledByteArray(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > 420 || height / sample > 420) {
            sample *= 2
        }
        return sample
    }

    private fun tintReadyColor(rgb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.28f)
        hsl[2] = hsl[2].coerceIn(0.32f, 0.62f)
        return ColorUtils.HSLToColor(hsl)
    }
}
