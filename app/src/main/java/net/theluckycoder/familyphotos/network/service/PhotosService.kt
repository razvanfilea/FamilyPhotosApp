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

    @GET("/photos")
    suspend fun getPhotosList(@Query("public") public: Boolean): Response<List<NetworkPhoto>>

    @Streaming
    @GET("photos/download/{id}")
    suspend fun downloadPhoto(
        @Path("id") id: Long
    ): ResponseBody?

    @GET("photos/exif/{id}")
    suspend fun getPhotoExif(
        @Path("id") id: Long
    ): Response<List<ExifField>>

    @Multipart
    @POST("/photos/upload")
    suspend fun uploadPhoto(
        @Query("timeCreated") timeCreated: String,
        @Query("folderName") folderName: String?,
        @Query("makePublic") makePublic: Boolean,
        @Part file: MultipartBody.Part,
    ): Response<NetworkPhoto>

    @DELETE("/photos/delete/{photoId}")
    suspend fun deletePhoto(
        @Path("photoId") photoId: Long,
    ): Response<Void>

    @POST("/photos/change_location/{photoId}")
    suspend fun changePhotoLocation(
        @Path("photoId") photoId: Long,
        @Query("targetUserName") newUserName: String?,
        @Query("targetFolderName") newFolderName: String?,
    ): Response<NetworkPhoto>
}
