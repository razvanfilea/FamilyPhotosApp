package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import net.theluckycoder.familyphotos.core.data.model.SharedFolderAccess
import net.theluckycoder.familyphotos.core.data.model.network.CreateShareRequest
import net.theluckycoder.familyphotos.core.data.model.network.SharedNetworkFolderDto
import net.theluckycoder.familyphotos.core.data.model.network.UpdateShareRequest
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.remote.SharingService
import net.theluckycoder.familyphotos.core.data.remote.UserService
import dagger.Reusable
import javax.inject.Inject

@Reusable
class SharingRepository @Inject internal constructor(
    private val sharingService: Lazy<SharingService>,
    private val userService: Lazy<UserService>,
) {

    private var cachedMembers: List<UserDto> = emptyList()

    suspend fun getFolderShares(folderId: Long): SharedFolderAccess {
        val availableMembers = getAvailableMembers()
            ?: return SharedFolderAccess.EMPTY

        val response = sharingService.get().getFolderShares(folderId)
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get folder shares: ${response.errorBody()?.string()}")
            return SharedFolderAccess.EMPTY.copy(folderId = folderId)
        }
        val folderShares =
            response.body() ?: return SharedFolderAccess.EMPTY.copy(folderId = folderId)

        return SharedFolderAccess(
            folderId = folderId,
            sharedWith = folderShares.mapNotNull {
                val userId = it.granteeId ?: return@mapNotNull null
                val name = availableMembers.find { member -> member.userId == userId }?.displayName
                    ?: userId
                SharedFolderAccess.Member(
                    shareId = it.id,
                    userId = userId,
                    userDisplayName = name,
                    canUpload = it.canUpload,
                    canDelete = it.canDelete,
                    expiresAt = it.expiresAt,
                )
            },
            links = folderShares.mapNotNull {
                SharedFolderAccess.Link(
                    sharedId = it.id,
                    token = it.token ?: return@mapNotNull null,
                    canUpload = it.canUpload,
                    canDelete = it.canDelete,
                    expiresAt = it.expiresAt
                )
            },
            availableMembers = availableMembers,
        )
    }

    suspend fun createShare(
        folderId: Long,
        granteeId: String,
        canUpload: Boolean = false,
        canDelete: Boolean = false,
        expiresAt: Long? = null,
    ): SharedNetworkFolderDto? {
        val response = sharingService.get().createShare(
            CreateShareRequest(
                folderId = folderId,
                granteeId = granteeId,
                canUpload = canUpload,
                canDelete = canDelete,
                expiresAt = expiresAt,
            )
        )
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to create share: ${response.errorBody()?.string()}")
            return null
        }
        return response.body()
    }

    suspend fun updateShare(
        shareId: Long,
        canUpload: Boolean,
        canDelete: Boolean,
        expiresAt: Long? = null, // TODO Maybe add in the future
    ): SharedNetworkFolderDto? {
        val response = sharingService.get().updateShare(
            shareId,
            UpdateShareRequest(
                canUpload = canUpload,
                canDelete = canDelete,
            )
        )
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to update share: ${response.errorBody()?.string()}")
            return null
        }
        return response.body()
    }

    suspend fun revokeShare(shareId: Long): Boolean {
        val response = sharingService.get().revokeShare(shareId)
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to revoke share: ${response.errorBody()?.string()}")
        }
        return response.isSuccessful
    }

    private suspend fun getAvailableMembers(): List<UserDto>? {
        cachedMembers.ifEmpty { null }?.let { return it }

        val response = userService.get().getMembersList()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get members: ${response.errorBody()?.string()}")
            return null
        }
        val members = response.body() ?: emptyList()
        cachedMembers = members
        return members
    }

    private suspend fun getMyShares(): List<SharedNetworkFolderDto>? {
        val response = sharingService.get().getMyShares()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get my shares: ${response.errorBody()?.string()}")
            return null
        }
        return response.body()
    }

    companion object {
        private const val TAG = "SharingRepository"
    }
}