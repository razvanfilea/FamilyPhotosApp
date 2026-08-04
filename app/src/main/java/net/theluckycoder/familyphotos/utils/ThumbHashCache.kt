package net.theluckycoder.familyphotos.utils

import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

object ThumbHashCache {
    private val cache = object : LruCache<String, ImageBitmap>(1000) {} // ~4MB memory

    fun get(thumbHash: String?): ImageBitmap? {
        if (thumbHash == null) return null

        return cache.get(thumbHash)
    }

    /**
     * Decoding takes around 200-1000 micro seconds
     */
    fun getOrDecodeSync(thumbHash: String?): ImageBitmap? {
        if (thumbHash == null) return null

        cache.get(thumbHash)?.let { return it }

        return try {
            val bytes = Base64.decode(thumbHash)
            val bitmap = ThumbHash.thumbHashToRGBA(bytes)
            val imageBitmap = bitmap.asImageBitmap()
            cache.put(thumbHash, imageBitmap)
            imageBitmap
        } catch (e: Exception) {
            Log.e("ThumbHash", "Failed to decode thumb hash", e)
            null
        }
    }

    suspend fun getOrCompute(thumbHash: String?): ImageBitmap? {
        if (thumbHash == null) return null

        cache.get(thumbHash)?.let { return it }

        return withContext(Dispatchers.Default) {
            try {
                val bytes = Base64.decode(thumbHash)
                if (!isActive) return@withContext null

                val bitmap = ThumbHash.thumbHashToRGBA(bytes)
                val imageBitmap = bitmap.asImageBitmap()

                cache.put(thumbHash, imageBitmap)
                imageBitmap
            } catch (e: Exception) {
                Log.e("ThumbHash", "Failed to decode thumb hash", e)
                null
            }
        }
    }
}

