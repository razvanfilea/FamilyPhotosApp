package net.theluckycoder.familyphotos.utils

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fast ThumbHash decoder using Chebyshev cosine recurrence and separable 2D IDCT.
 * Based on https://github.com/VectorPrivacy/fast-thumbhash
 */
object ThumbHash {

    private const val MAX_SIZE = 32
    private const val MAX_COMPONENTS = 7

    private class Scratch {
        val lAc = FloatArray(28)
        val pAc = FloatArray(8)
        val qAc = FloatArray(8)
        val aAc = FloatArray(14)
        val fxTable = FloatArray(MAX_COMPONENTS * MAX_SIZE)
        val fyTable = FloatArray(MAX_COMPONENTS * MAX_SIZE)
        val lXc = FloatArray(MAX_COMPONENTS * MAX_SIZE)
        val pXc = FloatArray(3 * MAX_SIZE)
        val qXc = FloatArray(3 * MAX_SIZE)
        val aXc = FloatArray(5 * MAX_SIZE)
        val pixels = IntArray(MAX_SIZE * MAX_SIZE)
        val lRow = FloatArray(MAX_SIZE)
        val pRow = FloatArray(MAX_SIZE)
        val qRow = FloatArray(MAX_SIZE)
        val aRow = FloatArray(MAX_SIZE)
    }

    private val scratch = ThreadLocal.withInitial { Scratch() }

    fun thumbHashToRGBA(hash: ByteArray): Bitmap {
        val scratch = scratch.get()!!
        val header24 =
            (hash[0].toInt() and 255) or ((hash[1].toInt() and 255) shl 8) or ((hash[2].toInt() and 255) shl 16)
        val header16 = (hash[3].toInt() and 255) or ((hash[4].toInt() and 255) shl 8)
        val lDc = (header24 and 63).toFloat() / 63.0f
        val pDc = ((header24 shr 6) and 63).toFloat() / 31.5f - 1.0f
        val qDc = ((header24 shr 12) and 63).toFloat() / 31.5f - 1.0f
        val lScale = ((header24 shr 18) and 31).toFloat() / 31.0f
        val hasAlpha = (header24 shr 23) != 0
        val pScale = ((header16 shr 3) and 63).toFloat() / 63.0f
        val qScale = ((header16 shr 9) and 63).toFloat() / 63.0f
        val isLandscape = (header16 shr 15) != 0
        val lMax = if (hasAlpha) 5 else 7
        val lx = max(3, if (isLandscape) lMax else (header16 and 7))
        val ly = max(3, if (isLandscape) (header16 and 7) else lMax)
        val aDc: Float
        val aScale: Float
        if (hasAlpha) {
            aDc = (hash[5].toInt() and 15).toFloat() / 15.0f
            aScale = ((hash[5].toInt() shr 4) and 15).toFloat() / 15.0f
        } else {
            aDc = 1.0f
            aScale = 1.0f
        }

        // Read AC coefficients via nibble indexing
        val dataStart = if (hasAlpha) 6 else 5
        var nibIdx = 0

        val lAc = scratch.lAc
        var lAcN = 0
        val pAc = scratch.pAc
        var pAcN = 0
        val qAc = scratch.qAc
        var qAcN = 0
        val aAc = scratch.aAc
        var aAcN = 0

        fun readNibble(): Int {
            val byteIdx = nibIdx / 2
            val v = if (nibIdx % 2 == 0) hash[dataStart + byteIdx].toInt() and 0x0F
            else (hash[dataStart + byteIdx].toInt() shr 4) and 0x0F
            nibIdx++
            return v
        }

        // L channel
        for (cy in 0 until ly) {
            var cx = if (cy > 0) 0 else 1
            while (cx * ly < lx * (ly - cy)) {
                lAc[lAcN++] = (readNibble().toFloat() / 7.5f - 1.0f) * lScale
                cx++
            }
        }
        // P channel
        for (cy in 0 until 3) {
            var cx = if (cy > 0) 0 else 1
            while (cx < 3 - cy) {
                pAc[pAcN++] = (readNibble().toFloat() / 7.5f - 1.0f) * pScale * 1.25f
                cx++
            }
        }
        // Q channel
        for (cy in 0 until 3) {
            var cx = if (cy > 0) 0 else 1
            while (cx < 3 - cy) {
                qAc[qAcN++] = (readNibble().toFloat() / 7.5f - 1.0f) * qScale * 1.25f
                cx++
            }
        }
        // A channel
        if (hasAlpha) {
            for (cy in 0 until 5) {
                var cx = if (cy > 0) 0 else 1
                while (cx < 5 - cy) {
                    aAc[aAcN++] = (readNibble().toFloat() / 7.5f - 1.0f) * aScale
                    cx++
                }
            }
        }

        // Output dimensions
        val lxA = if (isLandscape) lMax else (hash[3].toInt() and 7)
        val lyA = if (isLandscape) (hash[3].toInt() and 7) else lMax
        val ratio = lxA.toFloat() / lyA.toFloat()
        val w: Int
        val h: Int
        if (ratio > 1.0f) {
            w = 32
            h = (32.0f / ratio).roundToInt()
        } else {
            w = (32.0f * ratio).roundToInt()
            h = 32
        }

        // Precompute cosine tables via Chebyshev recurrence
        val maxCx = max(lx, if (hasAlpha) 5 else 3)
        val maxCy = max(ly, if (hasAlpha) 5 else 3)
        val piW = kotlin.math.PI.toFloat() / w.toFloat()
        val piH = kotlin.math.PI.toFloat() / h.toFloat()

        val fxTable = scratch.fxTable
        for (cx in 0 until maxCx) {
            fillCosTable(fxTable, cx * w, piW * cx.toFloat(), w)
        }
        val fyTable = scratch.fyTable
        for (cy in 0 until maxCy) {
            fillCosTable(fyTable, cy * h, piH * cy.toFloat(), h)
        }

        // Phase 1: x-contributions per cy (separable IDCT)
        val lXc = scratch.lXc
        lXc.fill(0f, 0, ly * w)
        run {
            var j = 0
            for (cy in 0 until ly) {
                val base = cy * w
                var cx = if (cy > 0) 0 else 1
                while (cx * ly < lx * (ly - cy)) {
                    val coeff = lAc[j]
                    val cxBase = cx * w
                    for (x in 0 until w) {
                        lXc[base + x] += coeff * fxTable[cxBase + x]
                    }
                    j++
                    cx++
                }
            }
        }

        val pXc = scratch.pXc
        val qXc = scratch.qXc
        pXc.fill(0f, 0, 3 * w)
        qXc.fill(0f, 0, 3 * w)
        run {
            var j = 0
            for (cy in 0 until 3) {
                val base = cy * w
                var cx = if (cy > 0) 0 else 1
                while (cx < 3 - cy) {
                    val pc = pAc[j]
                    val qc = qAc[j]
                    val cxBase = cx * w
                    for (x in 0 until w) {
                        val fxv = fxTable[cxBase + x]
                        pXc[base + x] += pc * fxv
                        qXc[base + x] += qc * fxv
                    }
                    j++
                    cx++
                }
            }
        }

        val aXc = if (hasAlpha) scratch.aXc.also { it.fill(0f, 0, 5 * w) } else null
        if (hasAlpha && aXc != null) {
            var j = 0
            for (cy in 0 until 5) {
                val base = cy * w
                var cx = if (cy > 0) 0 else 1
                while (cx < 5 - cy) {
                    val coeff = aAc[j]
                    val cxBase = cx * w
                    for (x in 0 until w) {
                        aXc[base + x] += coeff * fxTable[cxBase + x]
                    }
                    j++
                    cx++
                }
            }
        }

        // Phase 2: row-by-row SAXPY accumulation
        val pixels = scratch.pixels
        val lRow = scratch.lRow
        val pRow = scratch.pRow
        val qRow = scratch.qRow
        val aRow = scratch.aRow

        for (y in 0 until h) {
            lRow.fill(lDc, 0, w)
            pRow.fill(pDc, 0, w)
            qRow.fill(qDc, 0, w)
            aRow.fill(aDc, 0, w)

            for (cy in 0 until ly) {
                val fy2 = fyTable[cy * h + y] * 2.0f
                val off = cy * w
                for (x in 0 until w) {
                    lRow[x] += lXc[off + x] * fy2
                }
            }

            for (cy in 0 until 3) {
                val fy2 = fyTable[cy * h + y] * 2.0f
                val off = cy * w
                for (x in 0 until w) {
                    pRow[x] += pXc[off + x] * fy2
                    qRow[x] += qXc[off + x] * fy2
                }
            }

            if (hasAlpha && aXc != null) {
                for (cy in 0 until 5) {
                    val fy2 = fyTable[cy * h + y] * 2.0f
                    val off = cy * w
                    for (x in 0 until w) {
                        aRow[x] += aXc[off + x] * fy2
                    }
                }
            }

            val rowOff = y * w
            for (x in 0 until w) {
                val l = lRow[x]
                val p = pRow[x]
                val q = qRow[x]
                val a = aRow[x]
                val b = l - 2.0f / 3.0f * p
                val r = (3.0f * l - b + q) / 2.0f
                val g = r - q

                val red = (r.coerceIn(0f, 1f) * 255.0f).toInt()
                val green = (g.coerceIn(0f, 1f) * 255.0f).toInt()
                val blue = (b.coerceIn(0f, 1f) * 255.0f).toInt()
                val alpha = (a.coerceIn(0f, 1f) * 255.0f).toInt()
                pixels[rowOff + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        val bitmap = createBitmap(w, h)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun thumbHashToApproximateAspectRatio(hash: ByteArray): Float {
        val header = hash[3]
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else header.toInt() and 7
        val ly = if (isLandscape) header.toInt() and 7 else (if (hasAlpha) 5 else 7)
        return lx.toFloat() / ly.toFloat()
    }

    /**
     * Fill [out] starting at [offset] with cos(freq * (i + 0.5)) for i in 0..<len
     * using Chebyshev recurrence: only 2 cos() calls + (len-2) multiply-subtracts.
     */
    private fun fillCosTable(out: FloatArray, offset: Int, freq: Float, len: Int) {
        if (len == 0) return
        val half = freq * 0.5f
        out[offset] = kotlin.math.cos(half)
        if (len == 1) return
        out[offset + 1] = kotlin.math.cos(freq + half)
        if (len == 2) return
        val twoCosFreq = 2.0f * kotlin.math.cos(freq)
        for (i in 2 until len) {
            out[offset + i] = twoCosFreq * out[offset + i - 1] - out[offset + i - 2]
        }
    }
}
