package net.theluckycoder.familyphotos.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class SharedUploadMetadataTest {

    private val now = 1_750_000_000L

    @Test
    fun `embedded time wins over every other candidate`() {
        assertEquals(
            1_600_000_000L,
            SharedUploadMetadata.selectCaptureTime(1_600_000_000L, 1_650_000_000_000L, 1_700_000_000_000L, now)
        )
    }

    @Test
    fun `falls back to date taken, then last modified, then now`() {
        assertEquals(
            1_650_000_000L,
            SharedUploadMetadata.selectCaptureTime(null, 1_650_000_000_123L, 1_700_000_000_000L, now)
        )
        assertEquals(
            1_700_000_000L,
            SharedUploadMetadata.selectCaptureTime(null, null, 1_700_000_000_999L, now)
        )
        assertEquals(now, SharedUploadMetadata.selectCaptureTime(null, null, null, now))
    }

    @Test
    fun `implausible candidates are skipped`() {
        // Empty MP4 headers date to 1904, which is negative
        assertEquals(
            1_700_000_000L,
            SharedUploadMetadata.selectCaptureTime(-2_082_844_800L, 0L, 1_700_000_000_000L, now)
        )
        // More than a day in the future
        assertEquals(now, SharedUploadMetadata.selectCaptureTime(now + 2 * 86_400, null, null, now))
    }

    @Test
    fun `exif date uses the offset when present`() {
        assertEquals(
            1_686_832_200L, // 2023-06-15T12:30:00Z
            SharedUploadMetadata.parseExifDateTime("2023:06:15 14:30:00", "+02:00", ZoneOffset.UTC)
        )
    }

    @Test
    fun `exif date without offset is local time`() {
        assertEquals(
            1_686_832_200L,
            SharedUploadMetadata.parseExifDateTime("2023:06:15 15:30:00", null, ZoneId.of("Europe/Bucharest"))
        )
        assertEquals(
            1_686_832_200L,
            SharedUploadMetadata.parseExifDateTime("2023:06:15 15:30:00", "garbage", ZoneId.of("Europe/Bucharest"))
        )
    }

    @Test
    fun `invalid exif dates are null`() {
        assertNull(SharedUploadMetadata.parseExifDateTime(null, null, ZoneOffset.UTC))
        assertNull(SharedUploadMetadata.parseExifDateTime("    ", null, ZoneOffset.UTC))
        assertNull(SharedUploadMetadata.parseExifDateTime("0000:00:00 00:00:00", null, ZoneOffset.UTC))
    }

    @Test
    fun `video dates parse as UTC`() {
        assertEquals(1_686_832_200L, SharedUploadMetadata.parseVideoDate("20230615T123000.000Z"))
        assertEquals(1_686_832_200L, SharedUploadMetadata.parseVideoDate("20230615T123000Z"))
        assertNull(SharedUploadMetadata.parseVideoDate("not a date"))
        assertNull(SharedUploadMetadata.parseVideoDate(null))
    }

    @Test
    fun `names keep or gain an extension`() {
        assertEquals("IMG_1.jpg", SharedUploadMetadata.ensureExtension("IMG_1.jpg", "jpg"))
        assertEquals("IMG_1.jpg", SharedUploadMetadata.ensureExtension("IMG_1", "jpg"))
        assertEquals("clip", SharedUploadMetadata.ensureExtension("clip", null))
    }

    @Test
    fun `only images and videos are supported`() {
        assertEquals(true, SharedUploadMetadata.isSupportedMimeType("image/jpeg"))
        assertEquals(true, SharedUploadMetadata.isSupportedMimeType("video/mp4"))
        assertEquals(false, SharedUploadMetadata.isSupportedMimeType("application/pdf"))
        assertEquals(false, SharedUploadMetadata.isSupportedMimeType(null))
    }

    @Test
    fun `only a single supported intent type is a fallback`() {
        assertEquals("image/jpeg", SharedUploadMetadata.concreteMimeType("image/jpeg"))
        assertEquals("video/mp4", SharedUploadMetadata.concreteMimeType("VIDEO/MP4"))
        assertNull(SharedUploadMetadata.concreteMimeType("image/*"))
        assertNull(SharedUploadMetadata.concreteMimeType("*/*"))
        assertNull(SharedUploadMetadata.concreteMimeType("application/pdf"))
        assertNull(SharedUploadMetadata.concreteMimeType(null))
    }
}
