package net.theluckycoder.familyphotos.data.remote

import net.theluckycoder.familyphotos.data.model.CreateShareRequest
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.SharedNetworkFolder
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SharingService {

    @GET("/photos/sharing/with-me")
    suspend fun getSharesWithMe(): Response<List<SharedNetworkFolder>>

    @GET("/photos/sharing/{share_id}/photos")
    suspend fun getSharedFolderPhotos(@Path("share_id") shareId: Long): Response<List<NetworkPhoto>>

    @GET("/photos/sharing/")
    suspend fun getMyShares(): Response<List<SharedNetworkFolder>>

    @POST("/photos/sharing/")
    suspend fun createShare(@Body body: CreateShareRequest): Response<SharedNetworkFolder>

    @DELETE("/photos/sharing/{share_id}")
    suspend fun revokeShare(@Path("share_id") shareId: Long): Response<Void>
}