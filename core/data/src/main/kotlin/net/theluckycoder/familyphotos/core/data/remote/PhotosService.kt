package net.theluckycoder.familyphotos.core.data.remote

import net.theluckycoder.familyphotos.core.data.model.network.ExifFieldDto
import net.theluckycoder.familyphotos.core.data.model.network.NetworkPhotoDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

internal interface PhotosService {

    @Streaming
    @GET("/photos/download/{id}")
    suspend fun downloadPhoto(
        @Path("id") id: Long
    ): ResponseBody?

    @GET("/photos/exif/{id}")
    suspend fun getPhotoExif(
        @Path("id") id: Long
    ): Response<List<ExifFieldDto>>

    @GET("/photos/duplicates")
    suspend fun getDuplicates(): Response<List<List<Long>>>

    @Multipart
    @POST("/photos/upload")
    suspend fun uploadPhoto(
        @Query("time_created") timeCreated: String,
        @Query("folder_name") folderName: String?,
        @Query("make_public") makePublic: Boolean,
        @Part file: MultipartBody.Part,
    ): Response<NetworkPhotoDto>

    @POST("/photos/trash")
    suspend fun trashPhotos(
        @Body photoIds: List<Long>,
    ): Response<List<NetworkPhotoDto>>

    @DELETE("/photos/trash/restore")
    suspend fun restorePhotos(
        @Body photoIds: List<Long>,
    ): Response<List<NetworkPhotoDto>>

    @DELETE("/photos/delete/{photo_id}")
    suspend fun deletePhoto(
        @Path("photo_id") photoId: Long,
    ): Response<Void>

    @POST("/photos/move/photos")
    suspend fun movePhotos(
        @Query("make_public") makePublic: Boolean,
        @Query("target_folder_name") newFolderName: String?,
        @Body photoId: List<Long>,
    ): Response<List<NetworkPhotoDto>>

    @POST("/photos/move/folder")
    suspend fun renameFolder(
        @Query("source_folder_id") sourceFolderId: Long,
        @Query("target_make_public") targetMakePublic: Boolean,
        @Query("target_folder_name") targetFolderName: String?,
    ): Response<List<NetworkPhotoDto>>

    @GET("/photos/favorite")
    suspend fun getFavorites(): Response<List<Long>>

    @POST("/photos/favorite/{photo_id}")
    suspend fun addFavorite(@Path("photo_id") photoId: Long): Response<Void>

    @DELETE("/photos/favorite/{photo_id}")
    suspend fun removeFavorite(@Path("photo_id") photoId: Long): Response<Void>
}
