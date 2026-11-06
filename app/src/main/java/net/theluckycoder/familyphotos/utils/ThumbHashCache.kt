package net.theluckycoder.familyphotos.utils

import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.core.data.model.ThumbHashBytes

object ThumbHashCache {
    private val cache = object : LruCache<ThumbHashBytes, ImageBitmap>(1000) {} // ~4MB memory

    fun get(thumbHash: ThumbHashBytes?): ImageBitmap? {
        if (thumbHash == null) return null

        return cache.get(thumbHash)
    }

    suspend fun getOrCompute(thumbHash: ThumbHashBytes?): ImageBitmap? {
        if (thumbHash == null) return null

        cache.get(thumbHash)?.let { return it }

        return withContext(Dispatchers.Default) {
            try {
                if (!isActive) return@withContext null

                val bitmap = ThumbHash.thumbHashToRGBA(thumbHash.bytes)
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
