package net.theluckycoder.familyphotos.data.repository

import android.util.Log
import dagger.Lazy
import net.theluckycoder.familyphotos.data.model.network.CreateShareRequest
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.SharedNetworkFolder
import net.theluckycoder.familyphotos.data.remote.SharingService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingRepository @Inject constructor(
    private val sharingService: Lazy<SharingService>,
) {

    suspend fun getSharesWithMe(): List<SharedNetworkFolder>? {
        val response = sharingService.get().getSharesWithMe()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get shares with me: ${response.errorBody()?.string()}")
            return null
        }
        return response.body()
    }

    suspend fun getSharedFolderPhotos(shareId: Long): List<NetworkPhoto>? {
        val response = sharingService.get().getSharedFolderPhotos(shareId)
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get shared folder photos: ${response.errorBody()?.string()}")
            return null
        }
        return response.body()
    }

    suspend fun getMyShares(): List<SharedNetworkFolder>? {
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
    ): SharedNetworkFolder? {
        val response = sharingService.get().createShare(
            CreateShareRequest(
                folder_id = folderId,
                grantee_id = granteeId,
                can_upload = canUpload,
                can_delete = canDelete,
                expires_at = expiresAt,
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