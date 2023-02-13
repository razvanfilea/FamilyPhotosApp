package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.ExifField
import net.theluckycoder.familyphotos.model.NetworkPhoto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface PhotosService {

    @GET("/ping")
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
    @GET("photos/{userId}/exif/{id}")
    suspend fun getPhotoExif(
        @Path("userId") userId: Long,
        @Path("id") id: Long
    ): Response<List<ExifField>>

    @Multipart
    @POST("/photos/{userId}/upload")
    suspend fun uploadPhoto(
        @Path("userId") userId: Long,
        @Query("timeCreated") timeCreated: String,
        @Query("fileSize") fileSize: String,
        @Query("folderName") folderName: String?,
        @Part file: MultipartBody.Part,
    ): Response<NetworkPhoto>

    @POST("/photos/{userId}/update_caption/{photoId}")
    suspend fun updateCaption(
        @Path("userId") userId: Long,
        @Path("photoId") photoId: Long,
        @Query("timeCreated") newCaption: String?
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
        @Query("timeCreated") timeCreated: String,
        @Query("fileSize") fileSize: String,
        @Query("folderName") folderName: String?,
        @Part file: MultipartBody.Part,
    ): Response<NetworkPhoto>

    @DELETE("/public_photos/delete/{photoId}")
    suspend fun deletePublicPhoto(
        @Path("photoId") photoId: Long,
    ): Response<Void>
}
