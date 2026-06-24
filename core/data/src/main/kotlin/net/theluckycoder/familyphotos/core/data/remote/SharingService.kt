package net.theluckycoder.familyphotos.core.data.remote

import net.theluckycoder.familyphotos.core.data.model.network.CreateShareRequest
import net.theluckycoder.familyphotos.core.data.model.network.SharedNetworkFolderDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface SharingService {

    @GET("/photos/sharing/")
    suspend fun getMyShares(): Response<List<SharedNetworkFolderDto>>

    @POST("/photos/sharing/")
    suspend fun createShare(@Body body: CreateShareRequest): Response<SharedNetworkFolderDto>

    @DELETE("/photos/sharing/{share_id}")
    suspend fun revokeShare(@Path("share_id") shareId: Long): Response<Void>
}