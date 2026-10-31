package net.theluckycoder.familyphotos.core.data.model.sharing

import kotlinx.serialization.Serializable

/**
 * Shared-folder activity not yet dismissed by the user. [added] is cumulative since the user last
 * opened the folder, so the notification can be updated in place; [dirty] marks entries that
 * changed since a notification was last posted for them.
 */
@Serializable
data class PendingFolderActivity(
    val folderId: Long,
    val ownerId: String?,
    val isNewlyShared: Boolean,
    val added: List<PhotosAddedBy> = emptyList(),
    val dirty: Boolean = true,
    val notified: Boolean = false,
) {
    val addedCount: Int get() = added.sumOf { it.count }
}

@Serializable
data class PendingSharedActivity(
    val syncedUserId: String? = null,
    val folders: List<PendingFolderActivity> = emptyList(),
) {

    fun merge(changes: List<SharedFolderChange>): PendingSharedActivity {
        val byId = folders.associateBy { it.folderId }.toMutableMap()
        for (change in changes) {
            val existing = byId[change.folderId]
            byId[change.folderId] = PendingFolderActivity(
                folderId = change.folderId,
                ownerId = change.ownerId,
                isNewlyShared = change.isNewlyShared || existing?.isNewlyShared == true,
                added = mergeAdded(existing?.added.orEmpty(), change.added),
                notified = existing?.notified == true,
            )
        }
        return copy(folders = byId.values.toList())
    }

    /**
     * Forgets changes the user didn't get notified about because they were looking at the app.
     * Entries with a visible notification keep their count, so a later addition updates it
     * instead of restarting from 1.
     */
    fun dropUnseen() = copy(
        folders = folders.mapNotNull { if (!it.dirty) it else if (it.notified) it.copy(dirty = false) else null }
    )

    private fun mergeAdded(a: List<PhotosAddedBy>, b: List<PhotosAddedBy>): List<PhotosAddedBy> =
        (a + b).groupBy { it.userId }.map { (userId, items) -> PhotosAddedBy(userId, items.sumOf { it.count }) }
}
