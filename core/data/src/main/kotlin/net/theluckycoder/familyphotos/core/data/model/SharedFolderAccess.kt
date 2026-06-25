package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable
import net.theluckycoder.familyphotos.core.data.model.network.UserDto

@Immutable
data class SharedFolderAccess(
    val folderId: Long,
    val sharedWith: List<Member>,
    val links: List<Link>,
    val availableMembers: List<UserDto>,
) {

    @Immutable
    data class Member(
        val shareId: Long,
        val userId: String,
        val userDisplayName: String,
        val canUpload: Boolean,
        val canDelete: Boolean,
        val expiresAt: Long?
    )

    @Immutable
    data class Link(
        val sharedId: Long,
        val token: String,
        val canUpload: Boolean,
        val canDelete: Boolean,
        val expiresAt: Long?
    )

    companion object {
        val EMPTY = SharedFolderAccess(
            folderId = 0,
            sharedWith = emptyList(),
            links = emptyList(),
            availableMembers = emptyList(),
        )
    }
}