package net.theluckycoder.familyphotos.core.data.model.sharing

/**
 * [present] is false for removals (null `data`) and for trashed photos. There is no uploader: the
 * server stores a photo uploaded into a folder under the folder owner's user id.
 */
internal class PhotoEvent(val photoId: Long, val present: Boolean, val folderId: Long? = null)

internal class FolderSyncDelta(
    val id: Long,
    val ownerId: String?,
    /** `photos != null` in the response: a full list rather than an event delta. */
    val isFullList: Boolean,
    val events: List<PhotoEvent>,
)

internal object SharedFolderChangeDetector {

    /**
     * @param knownFolderIds the cursors we sent.
     * @param photoIdsAlreadyInFolder filters out metadata-only updates, and the user's own uploads
     * from this device (the upload inserts the returned photo before the sync sees its event).
     */
    fun detect(
        currentUserId: String,
        isFirstSync: Boolean,
        knownFolderIds: Set<Long>,
        photoIdsAlreadyInFolder: Map<Long, Set<Long>>,
        folders: List<FolderSyncDelta>,
    ): List<SharedFolderChange> {
        if (isFirstSync) return emptyList()

        return folders.mapNotNull { folder ->
            // Own and public folders carry no photo data; own folders are handled by [detectInOwnFolders]
            val ownerId = folder.ownerId
            if (ownerId == null || ownerId == currentUserId) return@mapNotNull null

            if (folder.id !in knownFolderIds) {
                return@mapNotNull SharedFolderChange(folder.id, ownerId, isNewlyShared = true)
            }

            // A full list for a known folder means our cursor was stale, not that photos were added
            if (folder.isFullList) return@mapNotNull null

            val count = countNew(folder.events, photoIdsAlreadyInFolder[folder.id].orEmpty())
            if (count == 0) null else SharedFolderChange(folder.id, ownerId, false, listOf(PhotosAddedBy(null, count)))
        }
    }

    /**
     * Heuristic for `/api/sync/partial` events: a photo entering an own folder that isn't in the DB
     * yet wasn't uploaded from this device. The user's uploads from elsewhere are reported too.
     */
    fun detectInOwnFolders(
        currentUserId: String,
        ownFolderIds: Set<Long>,
        photoIdsAlreadyKnown: Set<Long>,
        events: List<PhotoEvent>,
    ): List<SharedFolderChange> =
        events
            .associateBy { it.photoId }.values
            .filter { it.present && it.folderId in ownFolderIds && it.photoId !in photoIdsAlreadyKnown }
            .groupingBy { it.folderId!! }
            .eachCount()
            .map { (folderId, count) ->
                SharedFolderChange(folderId, currentUserId, false, listOf(PhotosAddedBy(null, count)))
            }

    private fun countNew(events: List<PhotoEvent>, alreadyKnown: Set<Long>): Int =
        // Only a photo's last event tells where it ended up: added and removed in one batch is gone
        events.associateBy { it.photoId }.values.count { it.present && it.photoId !in alreadyKnown }
}
