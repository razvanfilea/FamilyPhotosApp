package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.NetworkPhoto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface PhotosService {

    @GET("/")
    suspend fun ping(): Response<Void>

    @GET("/photos/{userId}")
    suspend fun getPhotosList(
        @Path("userId") userId: Long
    ): Response<List<NetworkPhoto>>

    @Streaming
    @GET("photos/{userId}/download/{id}")
    suspend fun downloadPhoto(
        @Path("userId") userId: Long,
        @Path("id") id: Long
    ): ResponseBody?

    @Multipart
    @POST("/photos/{userId}/upload")
    suspend fun uploadPhoto(
        @Path("userId") userId: Long,
        @Query("timeCreated") timeCreated: Long,
        @Query("folderName") folderName: String?,
        @Part file: MultipartBody.Part,
    ): Response<NetworkPhoto>

    @DELETE("/photos/{userId}/delete/{photoId}")
    suspend fun deletePhoto(
        @Path("userId") userId: Long,
        @Path("photoId") photoId: Long,
    ): Response<Void>

    @GET("/public_photos/")
    suspend fun getPublicPhotosList(): Response<List<NetworkPhoto>>

    @POST("/photos/{userId}/change_location/{photoId}")
    suspend fun changePhotoLocation(
        @Path("userId") userId: Long,
        @Path("photoId") photoId: Long,
        @Query("targetUserId") newUserId: Long?,
        @Query("targetFolderName") newFolderName: String?,
    ): Response<NetworkPhoto>

    @Multipart
    @POST("/public_photos/upload")
    suspend fun uploadPublicPhoto(
        @Query("timeCreated") timeCreated: Long,
        @Query("folderName") folderName: String?,
        @Part file: MultipartBody.Part,
    ): Response<NetworkPhoto>

    @POST("/public_photos/delete/{photoId}")
    suspend fun deletePublicPhoto(
        @Path("photoId") photoId: Long,
    ): Response<Void>
}
