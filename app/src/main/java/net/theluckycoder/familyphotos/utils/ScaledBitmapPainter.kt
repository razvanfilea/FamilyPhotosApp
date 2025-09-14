package net.theluckycoder.familyphotos.utils

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

fun loadThumbHashImage(thumbHash: String): Bitmap? =
    try {
        val hash = Base64.decode(thumbHash)
        ThumbHash.thumbHashToRGBA(hash)
    } catch (e: Exception) {
        Log.e("ThumbHash", "Failed to decode thumb hash", e)
        null
    }

suspend fun loadThumbHashPainter(thumbHash: String): Painter? =
    withContext(Dispatchers.Default) {
        try {
            val hash = Base64.decode(thumbHash)
            ensureActive()
            val bitmap = ThumbHash.thumbHashToRGBA(hash).asImageBitmap()
            ensureActive()
            ScaledBitmapPainter(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ThumbHash", "Failed to decode thumb hash", e)
            null
        }
    }

class ScaledBitmapPainter(private val imageBitmap: ImageBitmap) : Painter() {

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val srcWidth = imageBitmap.width.toFloat()
        val srcHeight = imageBitmap.height.toFloat()
        val dstWidth = size.width
        val dstHeight = size.height

        val scale: Float = maxOf(dstWidth / srcWidth, dstHeight / srcHeight)
        val dx = (dstWidth - srcWidth * scale) / 2f
        val dy = (dstHeight - srcHeight * scale) / 2f

        translate(left = dx, top = dy) {
            scale(scale = scale, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawImage(imageBitmap)
            }
        }
    }
}

