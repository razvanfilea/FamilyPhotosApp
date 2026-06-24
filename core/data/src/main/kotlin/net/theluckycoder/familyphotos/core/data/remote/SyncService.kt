package net.theluckycoder.familyphotos.core.data.remote

import net.theluckycoder.familyphotos.core.data.model.network.FolderSyncDto
import net.theluckycoder.familyphotos.core.data.model.network.FullPhotoListDto
import net.theluckycoder.familyphotos.core.data.model.network.PartialPhotoListDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

internal interface SyncService {
    @GET("/photos/sync/full")
    suspend fun getFullPhotosList(): Response<FullPhotoListDto>

    @GET("/photos/sync/partial")
    suspend fun getEventLogsList(@Query("last_synced_event_id") lastSyncedEventLogId: Long): Response<PartialPhotoListDto>

    @POST("/photos/sync/folders")
    suspend fun syncFolders(@Body cursors: Map<Long, Long>): Response<List<FolderSyncDto>>

}