package net.theluckycoder.familyphotos.core.data.model.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFolderChangeDetectorTest {

    private val me = "me"
    private val alice = "alice"

    private fun detect(
        folders: List<FolderSyncDelta>,
        known: Set<Long> = setOf(1L),
        isFirstSync: Boolean = false,
        alreadyInFolder: Map<Long, Set<Long>> = emptyMap(),
    ) = SharedFolderChangeDetector.detect(me, isFirstSync, known, alreadyInFolder, folders)

    private fun added(id: Long, folderId: Long? = null) = PhotoEvent(id, present = true, folderId = folderId)
    private fun removed(id: Long) = PhotoEvent(id, present = false)

    private fun delta(
        id: Long = 1L,
        ownerId: String? = alice,
        isFullList: Boolean = false,
        entered: List<PhotoEvent> = emptyList(),
    ) = FolderSyncDelta(id, ownerId, isFullList, entered)

    @Test
    fun `new folder is reported as newly shared`() {
        val result = detect(listOf(delta(id = 2L, isFullList = true)), known = setOf(1L))

        assertEquals(listOf(SharedFolderChange(2L, alice, isNewlyShared = true)), result)
    }

    @Test
    fun `events with additions are counted without an uploader`() {
        val entered = listOf(added(10), added(11), added(12))

        val result = detect(listOf(delta(entered = entered)))

        assertEquals(1, result.size)
        assertEquals(false, result[0].isNewlyShared)
        // The server stores every upload into a folder under the owner, so nobody can be named
        assertEquals(listOf(PhotosAddedBy(null, 3)), result[0].added)
    }

    @Test
    fun `full list for a known folder is a stale cursor, not news`() {
        val entered = listOf(added(10))

        assertTrue(detect(listOf(delta(isFullList = true, entered = entered))).isEmpty())
    }

    @Test
    fun `first sync never notifies`() {
        val result = detect(
            listOf(delta(id = 2L, isFullList = true), delta(entered = listOf(added(10)))),
            known = emptySet(),
            isFirstSync = true,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `photos already in the folder are not additions`() {
        // Own uploads (inserted before the sync sees their event) and metadata updates of known photos
        val entered = listOf(added(10), added(11), added(12))

        val result = detect(listOf(delta(entered = entered)), alreadyInFolder = mapOf(1L to setOf(10L, 11L)))

        assertEquals(1, result.single().addedCount)
    }

    @Test
    fun `others' uploads into own folders are detected`() {
        val events = listOf(added(10, folderId = 5L), added(11, folderId = 5L), added(12, folderId = 6L))

        val result = SharedFolderChangeDetector.detectInOwnFolders(me, setOf(5L, 6L), emptySet(), events)

        assertEquals(
            setOf(
                SharedFolderChange(5L, me, false, listOf(PhotosAddedBy(null, 2))),
                SharedFolderChange(6L, me, false, listOf(PhotosAddedBy(null, 1))),
            ),
            result.toSet()
        )
    }

    @Test
    fun `own folder detection skips known photos, other folders and removals`() {
        val events = listOf(
            added(10, folderId = 5L), // uploaded from this device, already in the DB
            added(11, folderId = null), // personal photo outside any folder
            added(12, folderId = 7L), // not an own folder
            added(13, folderId = 5L), removed(13), // added then removed
        )

        assertTrue(SharedFolderChangeDetector.detectInOwnFolders(me, setOf(5L), setOf(10L), events).isEmpty())
    }

    @Test
    fun `own and public folders in the response are ignored`() {
        val result = detect(
            listOf(
                delta(id = 5L, ownerId = me, isFullList = true),
                delta(id = 6L, ownerId = null, isFullList = true),
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `repeated events for one photo count once`() {
        val entered = listOf(added(10), added(10))

        assertEquals(1, detect(listOf(delta(entered = entered))).single().addedCount)
    }

    @Test
    fun `photo added then removed in one batch is not counted`() {
        val events = listOf(added(10), removed(10), added(11))

        assertEquals(1, detect(listOf(delta(entered = events))).single().addedCount)
        assertTrue(detect(listOf(delta(entered = listOf(added(10), removed(10))))).isEmpty())
    }

    @Test
    fun `photo removed then restored counts`() {
        val events = listOf(removed(10), added(10))

        assertEquals(1, detect(listOf(delta(entered = events))).single().addedCount)
    }

    @Test
    fun `drop unseen keeps counts of visible notifications`() {
        val change = SharedFolderChange(1L, alice, false, listOf(PhotosAddedBy(alice, 3)))
        val posted = PendingSharedActivity().merge(listOf(change)).let {
            it.copy(folders = it.folders.map { f -> f.copy(dirty = false, notified = true) })
        }
        val withUnseen = posted.merge(listOf(change.copy(added = listOf(PhotosAddedBy(alice, 2)))))
            .merge(listOf(SharedFolderChange(2L, alice, isNewlyShared = true)))

        val dropped = withUnseen.dropUnseen()

        // Folder 1 keeps its cumulative count; folder 2 never had a notification
        assertEquals(listOf(1L), dropped.folders.map { it.folderId })
        assertEquals(5, dropped.folders.single().addedCount)
        assertEquals(false, dropped.folders.single().dirty)
    }

    @Test
    fun `pending activity accumulates until dismissed`() {
        val first = SharedFolderChange(1L, alice, false, listOf(PhotosAddedBy(alice, 2)))
        val second = SharedFolderChange(1L, alice, false, listOf(PhotosAddedBy(alice, 3)))

        val merged = PendingSharedActivity().merge(listOf(first)).merge(listOf(second))

        assertEquals(5, merged.folders.single().addedCount)
        assertTrue(merged.folders.single().dirty)
    }
}
