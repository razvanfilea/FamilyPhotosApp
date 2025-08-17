package net.theluckycoder.familyphotos.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * This code is heavily based on https://github.com/evanw/thumbhash/blob/main/java/com/madebyevan/thumbhash/ThumbHash.java
 *
 * Credit to Evan Wallace
 */
object ThumbHash {
    /**
     * Decodes a ThumbHash to an RGBA image. RGB is not be premultiplied by A.
     *
     * @param hash The bytes of the ThumbHash.
     * @return The width, height, and pixels of the rendered placeholder image.
     */
    fun thumbHashToRGBA(hash: ByteArray): Bitmap {
        // Read the constants
        val header24 =
            (hash[0].toInt() and 255) or ((hash[1].toInt() and 255) shl 8) or ((hash[2].toInt() and 255) shl 16)
        val header16 = (hash[3].toInt() and 255) or ((hash[4].toInt() and 255) shl 8)
        val l_dc = (header24 and 63).toFloat() / 63.0f
        val p_dc = ((header24 shr 6) and 63).toFloat() / 31.5f - 1.0f
        val q_dc = ((header24 shr 12) and 63).toFloat() / 31.5f - 1.0f
        val l_scale = ((header24 shr 18) and 31).toFloat() / 31.0f
        val hasAlpha = (header24 shr 23) != 0
        val p_scale = ((header16 shr 3) and 63).toFloat() / 63.0f
        val q_scale = ((header16 shr 9) and 63).toFloat() / 63.0f
        val isLandscape = (header16 shr 15) != 0
        val lx = max(3, if (isLandscape) if (hasAlpha) 5 else 7 else header16 and 7)
        val ly = max(3, if (isLandscape) header16 and 7 else if (hasAlpha) 5 else 7)
        val a_dc = if (hasAlpha) (hash[5].toInt() and 15).toFloat() / 15.0f else 1.0f
        val a_scale = ((hash[5].toInt() shr 4) and 15).toFloat() / 15.0f

        // Read the varying factors (boost saturation by 1.25x to compensate for quantization)
        val ac_start = if (hasAlpha) 6 else 5
        var ac_index = 0
        val l_channel = Channel(lx, ly)
        val p_channel = Channel(3, 3)
        val q_channel = Channel(3, 3)
        var a_channel: Channel? = null
        ac_index = l_channel.decode(hash, ac_start, ac_index, l_scale)
        ac_index = p_channel.decode(hash, ac_start, ac_index, p_scale * 1.25f)
        ac_index = q_channel.decode(hash, ac_start, ac_index, q_scale * 1.25f)
        if (hasAlpha) {
            a_channel = Channel(5, 5)
            a_channel.decode(hash, ac_start, ac_index, a_scale)
        }
        val l_ac = l_channel.ac
        val p_ac = p_channel.ac
        val q_ac = q_channel.ac
        val a_ac = a_channel?.ac

        // Decode using the DCT into RGB
        val ratio = thumbHashToApproximateAspectRatio(hash)
        val w = (if (ratio > 1.0f) 32.0f else 32.0f * ratio).roundToInt()
        val h = (if (ratio > 1.0f) 32.0f / ratio else 32.0f).roundToInt()
        val rgba = ByteArray(w * h * 4)
        val cx_stop = max(lx, if (hasAlpha) 5 else 3)
        val cy_stop = max(ly, if (hasAlpha) 5 else 3)
        val fx = FloatArray(cx_stop)
        val fy = FloatArray(cy_stop)
        var y = 0
        var i = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                var l = l_dc
                var p = p_dc
                var q = q_dc
                var a = a_dc

                // Precompute the coefficients
                for (cx in 0..<cx_stop) fx[cx] = cos(Math.PI / w * (x + 0.5f) * cx).toFloat()
                for (cy in 0..<cy_stop) fy[cy] = cos(Math.PI / h * (y + 0.5f) * cy).toFloat()

                // Decode L
                run {
                    var cy = 0
                    var j = 0
                    while (cy < ly) {
                        val fy2 = fy[cy] * 2.0f
                        var cx = if (cy > 0) 0 else 1
                        while (cx * ly < lx * (ly - cy)) {
                            l += l_ac[j] * fx[cx] * fy2
                            cx++
                            j++
                        }
                        cy++
                    }
                }

                // Decode P and Q
                var cy = 0
                var j = 0
                while (cy < 3) {
                    val fy2 = fy[cy] * 2.0f
                    var cx = if (cy > 0) 0 else 1
                    while (cx < 3 - cy) {
                        val f = fx[cx] * fy2
                        p += p_ac[j] * f
                        q += q_ac[j] * f
                        cx++
                        j++
                    }
                    cy++
                }

                // Decode A
                if (hasAlpha && a_ac != null) {
                    var cy = 0
                    var j = 0
                    while (cy < 5) {
                        val fy2 = fy[cy] * 2.0f
                        var cx = if (cy > 0) 0 else 1
                        while (cx < 5 - cy) {
                            a += a_ac[j] * fx[cx] * fy2
                            cx++
                            j++
                        }
                        cy++
                    }
                }

                // Convert to RGB
                val b = l - 2.0f / 3.0f * p
                val r = (3.0f * l - b + q) / 2.0f
                val g = r - q
                rgba[i] = max(0, (255.0f * min(1f, r)).roundToInt()).toByte()
                rgba[i + 1] = max(0, (255.0f * min(1f, g)).roundToInt()).toByte()
                rgba[i + 2] = max(0, (255.0f * min(1f, b)).roundToInt()).toByte()
                rgba[i + 3] = max(0, (255.0f * min(1f, a)).roundToInt()).toByte()
                x++
                i += 4
            }
            y++
        }

        val bitmap = createBitmap(w, h)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
        return bitmap
    }

    /**
     * Extracts the approximate aspect ratio of the original image.
     *
     * @param hash The bytes of the ThumbHash.
     * @return The approximate aspect ratio (i.e. width / height).
     */
    fun thumbHashToApproximateAspectRatio(hash: ByteArray): Float {
        val header = hash[3]
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) if (hasAlpha) 5 else 7 else header.toInt() and 7
        val ly = if (isLandscape) header.toInt() and 7 else if (hasAlpha) 5 else 7
        return lx.toFloat() / ly.toFloat()
    }

    private class Channel(nx: Int, ny: Int) {
        var ac: FloatArray

        init {
            var n = 0
            for (cy in 0..<ny) {
                var cx = if (cy > 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    n++
                    cx++
                }
            }
            ac = FloatArray(n)
        }

        fun decode(hash: ByteArray, start: Int, index: Int, scale: Float): Int {
            var index = index
            for (i in ac.indices) {
                val data = hash[start + (index shr 1)].toInt() shr ((index and 1) shl 2)
                ac[i] = ((data and 15).toFloat() / 7.5f - 1.0f) * scale
                index++
            }
            return index
        }

    }
}