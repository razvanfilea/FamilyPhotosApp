package net.theluckycoder.familyphotos.core.data.model.sharing

import kotlinx.serialization.Serializable

/** [userId] is always null: the server doesn't record who uploaded a photo. */
@Serializable
data class PhotosAddedBy(val userId: String?, val count: Int)

@Serializable
data class SharedFolderChange(
    val folderId: Long,
    val ownerId: String?,
    val isNewlyShared: Boolean,
    val added: List<PhotosAddedBy> = emptyList(),
) {
    val addedCount: Int get() = added.sumOf { it.count }
}
