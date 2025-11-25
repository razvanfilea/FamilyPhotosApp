package net.theluckycoder.familyphotos.utils

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter

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

