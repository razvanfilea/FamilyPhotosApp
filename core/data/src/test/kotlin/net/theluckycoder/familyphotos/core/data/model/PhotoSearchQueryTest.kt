package net.theluckycoder.familyphotos.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSearchQueryTest {

    @Test
    fun `a year and a month name become a date filter`() {
        assertEquals(PhotoSearchQuery(year = 2024, month = 10), PhotoSearchQuery.parse("octombrie 2024"))
        assertEquals(PhotoSearchQuery(year = 2024, month = 10), PhotoSearchQuery.parse("2024 October"))
        assertEquals(PhotoSearchQuery(year = 2023), PhotoSearchQuery.parse("  2023 "))
        assertEquals(PhotoSearchQuery(month = 5), PhotoSearchQuery.parse("Mai"))
    }

    @Test
    fun `month names match without case or diacritics, and abbreviated`() {
        assertEquals(2, PhotoSearchQuery.parse("FEBRUARIE").month)
        assertEquals(8, PhotoSearchQuery.parse("aug").month)
        assertEquals(9, PhotoSearchQuery.parse("sept.").month)
    }

    @Test
    fun `other words stay as text`() {
        assertEquals(
            PhotoSearchQuery(text = "Iubirea mea", year = 2025),
            PhotoSearchQuery.parse("Iubirea 2025 mea"),
        )
        assertEquals(PhotoSearchQuery(text = "PXL_2024"), PhotoSearchQuery.parse("PXL_2024"))
        // Not a plausible year
        assertEquals(PhotoSearchQuery(text = "1234"), PhotoSearchQuery.parse("1234"))
        // Only the first year counts
        assertEquals(PhotoSearchQuery(text = "2021", year = 2020), PhotoSearchQuery.parse("2020 2021"))
    }

    @Test
    fun `an empty search with no filters is empty`() {
        assertTrue(PhotoSearchQuery.parse("   ").isEmpty)
        assertEquals(false, PhotoSearchQuery.parse("", videosOnly = true).isEmpty)
    }

    @Test
    fun `like wildcards in the text are escaped`() {
        assertEquals("100\\%\\_a\\\\b", PhotoSearchQuery(text = "100%_a\\b").likeText)
    }
}
