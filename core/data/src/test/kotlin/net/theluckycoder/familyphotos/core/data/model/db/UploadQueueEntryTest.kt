package net.theluckycoder.familyphotos.core.data.model.db

import net.theluckycoder.familyphotos.core.data.model.SharedUploadFile
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadQueueEntryTest {

    private val sharedFile = SharedUploadFile("abc.jpg", "IMG_1.jpg", 1_700_000_000L, "image/jpeg")

    private val choices = listOf(
        UploadChoice.NoFolder(isPublic = false),
        UploadChoice.NoFolder(isPublic = true),
        UploadChoice.NewFolder("Trip", isPublic = true),
        UploadChoice.Folder(42L),
    )

    @Test
    fun `local photo entries round-trip the upload choice`() {
        for (choice in choices) {
            val entry = UploadQueueEntry.forLocalPhoto(7L, choice, isManualUpload = false)
            assertEquals(7L, entry.localPhotoId)
            assertNull(entry.sharedFileName)
            assertEquals(false, entry.isManualUpload)
            assertEquals(choice, entry.toUploadChoice())
        }
    }

    @Test
    fun `shared entries reference the copy and are manual`() {
        for (choice in choices) {
            val entry = UploadQueueEntry.forSharedFile(sharedFile, choice)
            assertNull(entry.localPhotoId)
            assertEquals("abc.jpg", entry.sharedFileName)
            assertTrue(entry.isManualUpload)
            assertEquals(choice, entry.toUploadChoice())
        }
    }
}
