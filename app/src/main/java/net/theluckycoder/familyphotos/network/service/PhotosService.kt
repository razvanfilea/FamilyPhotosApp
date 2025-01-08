package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.BasicNetworkPhoto
import net.theluckycoder.familyphotos.model.ExifField
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

    @GET("/profile")
    suspend fun ping(): Response<Void>

    @GET("/photos")
    suspend fun getPhotosList(): Response<List<BasicNetworkPhoto>>

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
        @Query("makePublic") makePublic: Boolean,
        @Query("targetFolderName") newFolderName: String?,
    ): Response<NetworkPhoto>

    @GET("/photos/favorite")
    suspend fun getFavorites(): Response<List<Long>>

    @POST("/photos/favorite/{photoId}")
    suspend fun addFavorite(@Path("photoId") photoId: Long): Response<Void>

    @DELETE("/photos/favorite/{photoId}")
    suspend fun removeFavorite(@Path("photoId") photoId: Long): Response<Void>
}
