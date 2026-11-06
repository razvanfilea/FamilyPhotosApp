package net.theluckycoder.familyphotos.core.data.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbHashBytesTest {

    @Test
    fun `equality is by content`() {
        val a = ThumbHashBytes(byteArrayOf(1, 2, 3))
        val b = ThumbHashBytes(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, ThumbHashBytes(byteArrayOf(1, 2, 4)))
        // What Paging diffing relies on
        assertEquals(NetworkPhoto(1, null, "a", 0, thumbHash = a), NetworkPhoto(1, null, "a", 0, thumbHash = b))
    }

    @Test
    fun `decodes base64 from the server`() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ThumbHashBytes.fromBase64("AQID")!!.bytes)
        assertNull(ThumbHashBytes.fromBase64(null))
        assertNull(ThumbHashBytes.fromBase64(""))
        assertNull(ThumbHashBytes.fromBase64("not base64!"))
    }
}
