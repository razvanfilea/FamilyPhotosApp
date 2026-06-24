package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.network.CreateShareRequest
import net.theluckycoder.familyphotos.core.data.model.network.SharedNetworkFolderDto
import net.theluckycoder.familyphotos.core.data.model.network.toEntity
import net.theluckycoder.familyphotos.core.data.remote.SharingService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingRepository @Inject internal constructor(
    private val sharingService: Lazy<SharingService>,
) {

    suspend fun getMyShares(): List<SharedNetworkFolderDto>? {
        val response = sharingService.get().getMyShares()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get my shares: ${response.errorBody()?.string()}")
            return null
        }
        return response.body()
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

    suspend fun revokeShare(shareId: Long): Boolean {
        val response = sharingService.get().revokeShare(shareId)
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to revoke share: ${response.errorBody()?.string()}")
        }
        return response.isSuccessful
    }

    companion object {
        private const val TAG = "SharingRepository"
    }
}