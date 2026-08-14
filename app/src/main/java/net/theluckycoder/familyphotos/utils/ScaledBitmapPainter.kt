package net.theluckycoder.familyphotos.utils

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter

class ScaledBitmapPainter(
    private val imageBitmapState: State<ImageBitmap?>,
    private val placeholderColor: Color = Color.DarkGray
) : Painter() {

    val aspectRatio: Float
        get() = imageBitmapState.value?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f

    override val intrinsicSize: Size
        get() = imageBitmapState.value?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Unspecified

    override fun DrawScope.onDraw() {
        val imageBitmap = imageBitmapState.value
        if (imageBitmap != null) {
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
        } else {
            drawRect(placeholderColor)
        }
    }
}
