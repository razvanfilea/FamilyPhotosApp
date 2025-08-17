package net.theluckycoder.familyphotos.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import kotlin.io.encoding.Base64

@Composable
fun rememberThumbHashPainter(thumbHash: String): Painter? = remember(thumbHash) {
    try {
        val hash = Base64.decode(thumbHash)
        val bitmap = ThumbHash.thumbHashToRGBA(hash).asImageBitmap()
        ScaledBitmapPainter(bitmap)
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

