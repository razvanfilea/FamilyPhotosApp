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
    fun newFolderIsReportedAsNewlyShared() {
        val result = detect(listOf(delta(id = 2L, isFullList = true)), known = setOf(1L))

        assertEquals(listOf(SharedFolderChange(2L, alice, isNewlyShared = true)), result)
    }

    @Test
    fun eventsWithAdditionsAreCountedWithoutAnUploader() {
        val entered = listOf(added(10), added(11), added(12))

        val result = detect(listOf(delta(entered = entered)))

        assertEquals(1, result.size)
        assertEquals(false, result[0].isNewlyShared)
        // The server stores every upload into a folder under the owner, so nobody can be named
        assertEquals(listOf(PhotosAddedBy(null, 3)), result[0].added)
    }

    @Test
    fun removalsOnlyProduceNothing() {
        // Removals have no data, so the sync layer reports no entered photos
        assertTrue(detect(listOf(delta(entered = emptyList()))).isEmpty())
    }

    @Test
    fun fullListForKnownFolderIsAStaleCursorNotNews() {
        val entered = listOf(added(10))

        assertTrue(detect(listOf(delta(isFullList = true, entered = entered))).isEmpty())
    }

    @Test
    fun firstSyncNeverNotifies() {
        val result = detect(
            listOf(delta(id = 2L, isFullList = true), delta(entered = listOf(added(10)))),
            known = emptySet(),
            isFirstSync = true,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun ownUploadsFromThisDeviceAreExcluded() {
        // The upload inserts the returned photo before the sync sees its event
        val entered = listOf(added(10), added(11), added(12))

        val result = detect(listOf(delta(entered = entered)), alreadyInFolder = mapOf(1L to setOf(10L, 11L)))

        assertEquals(1, result.single().addedCount)
    }

    @Test
    fun othersUploadsIntoOwnFolderAreDetected() {
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
    fun ownFolderDetectionSkipsKnownPhotosOtherFoldersAndRemovals() {
        val events = listOf(
            added(10, folderId = 5L), // uploaded from this device, already in the DB
            added(11, folderId = null), // personal photo outside any folder
            added(12, folderId = 7L), // not an own folder
            added(13, folderId = 5L), removed(13), // added then removed
        )

        assertTrue(SharedFolderChangeDetector.detectInOwnFolders(me, setOf(5L), setOf(10L), events).isEmpty())
    }

    @Test
    fun ownAndPublicFoldersInTheResponseAreIgnored() {
        val result = detect(
            listOf(
                delta(id = 5L, ownerId = me, isFullList = true),
                delta(id = 6L, ownerId = null, isFullList = true),
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun metadataUpdatesOfKnownPhotosAreNotAdditions() {
        val entered = listOf(added(10), added(11))

        val result = detect(listOf(delta(entered = entered)), alreadyInFolder = mapOf(1L to setOf(10L)))

        assertEquals(1, result.single().addedCount)
    }

    @Test
    fun repeatedEventsForOnePhotoCountOnce() {
        val entered = listOf(added(10), added(10))

        assertEquals(1, detect(listOf(delta(entered = entered))).single().addedCount)
    }

    @Test
    fun photoAddedThenRemovedInOneBatchIsNotCounted() {
        val events = listOf(added(10), removed(10), added(11))

        assertEquals(1, detect(listOf(delta(entered = events))).single().addedCount)
        assertTrue(detect(listOf(delta(entered = listOf(added(10), removed(10))))).isEmpty())
    }

    @Test
    fun photoRemovedThenRestoredCounts() {
        val events = listOf(removed(10), added(10))

        assertEquals(1, detect(listOf(delta(entered = events))).single().addedCount)
    }

    @Test
    fun dropUnseenKeepsCountsOfVisibleNotifications() {
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
    fun pendingActivityAccumulatesUntilDismissed() {
        val first = SharedFolderChange(1L, alice, false, listOf(PhotosAddedBy(alice, 2)))
        val second = SharedFolderChange(1L, alice, false, listOf(PhotosAddedBy(alice, 3)))

        val merged = PendingSharedActivity().merge(listOf(first)).merge(listOf(second))

        assertEquals(5, merged.folders.single().addedCount)
        assertTrue(merged.folders.single().dirty)
    }
}
