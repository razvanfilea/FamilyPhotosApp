package net.theluckycoder.familyphotos.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PhotoDateTest {

    private val zones = listOf("UTC", "Europe/Bucharest", "America/New_York", "Asia/Kolkata", "Pacific/Auckland")
        .map(ZoneId::of)

    @Test
    fun `a picked date and time is shown again as picked`() {
        for (zone in zones) {
            val picks = listOf(
                Triple(LocalDate.of(2024, 2, 29), 23, 59),
                Triple(LocalDate.of(2020, 1, 1), 0, 0),
                Triple(LocalDate.of(1999, 12, 31), 12, 30),
                Triple(LocalDate.of(2025, 7, 15), 8, 5),
            )
            for ((date, hour, minute) in picks) {
                val seconds = PhotoDate.toEpochSeconds(date, hour, minute, zone)
                assertEquals(
                    "$date $hour:$minute in $zone",
                    LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)),
                    PhotoDate.toLocalDateTime(seconds, zone),
                )
            }
        }
    }

    @Test
    fun `the same wall clock time is a different instant per zone`() {
        val date = LocalDate.of(2024, 6, 8)
        val cases = listOf("UTC" to 0, "Europe/Bucharest" to 3, "America/New_York" to -4)
        for ((zone, offset) in cases) {
            val seconds = PhotoDate.toEpochSeconds(date, 14, 0, ZoneId.of(zone))
            val utc = PhotoDate.toEpochSeconds(date, 14, 0, ZoneId.of("UTC"))
            assertEquals(zone, utc - offset * 3600L, seconds)
        }
    }

    @Test
    fun `seconds are dropped`() {
        val zone = ZoneId.of("UTC")
        val withSeconds = PhotoDate.toEpochSeconds(LocalDate.of(2024, 1, 1), 10, 20, zone) + 45
        val shown = PhotoDate.toLocalDateTime(withSeconds, zone)
        assertEquals(10, shown.hour)
        assertEquals(20, shown.minute)
        assertEquals(45, shown.second)
        assertEquals(
            withSeconds - 45,
            PhotoDate.toEpochSeconds(shown.toLocalDate(), shown.hour, shown.minute, zone),
        )
    }

    @Test
    fun `only own and public photos that are not trashed can change date`() {
        fun photo(userId: String?, trashedOn: Long? = null) =
            NetworkPhoto(1, userId, "a.jpg", 0, trashedOn = trashedOn)

        val cases = listOf(
            photo("me") to true,
            photo(null) to true,
            photo("other") to false,
            photo("me", trashedOn = 5) to false,
            photo(null, trashedOn = 5) to false,
        )
        for ((photo, expected) in cases) {
            assertEquals("$photo", expected, photo.canChangeDate("me"))
        }
        assertTrue(photo0().canChangeDate(null))
        assertFalse(NetworkPhoto(1, "me", "a", 0).canChangeDate(null))
    }

    private fun photo0() = NetworkPhoto(1, null, "a", 0)
}
