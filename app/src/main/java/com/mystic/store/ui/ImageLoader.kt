package com.mystic.store.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.mystic.store.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tiny icon loader: downloads a remote image in the background, keeps an
 * in-memory cache, and falls back to the placeholder on failure. Large images
 * are sampled down so big screenshots don't blow up memory.
 */
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String?, imageView: ImageView, scope: CoroutineScope) {
        val placeholder = imageView.context.getDrawable(R.drawable.ic_app_placeholder)
        imageView.setImageDrawable(placeholder)
        if (url.isNullOrBlank()) return

        val src = normalize(url)
        cache.get(src)?.let {
            imageView.setImageBitmap(it)
            return
        }
        scope.launch {
            fetchBitmap(src)?.let { bitmap ->
                cache.put(src, bitmap)
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    /** Downloads + decodes a remote image, sampled to at most [targetWidth] px. */
    suspend fun fetchBitmap(url: String, targetWidth: Int? = null): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = downloadBytes(url) ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sample = 1
                val target = targetWidth ?: 1024
                while (bounds.outWidth / (sample * 2) > target) sample *= 2

                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Turns github.com blob/raw URLs into raw.githubusercontent.com URLs so common
     * paste-in-the-README links work. Other URLs pass through unchanged.
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        val blob = blobOrRawRegex.find(trimmed)
        if (blob != null) {
            return "https://raw.githubusercontent.com/" +
                blob.groupValues[1] + "/" + blob.groupValues[2] + "/" + blob.groupValues[3]
        }
        return trimmed
    }

    private fun downloadBytes(url: String): ByteArray? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.connect()
                if (connection.responseCode !in 200..299) return null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}

private val blobOrRawRegex = Regex(
    "^(?:https?://)?github\\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$"
)