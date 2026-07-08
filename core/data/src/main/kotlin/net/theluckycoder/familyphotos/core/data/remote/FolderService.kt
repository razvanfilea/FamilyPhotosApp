package net.theluckycoder.familyphotos.core.data.remote

import net.theluckycoder.familyphotos.core.data.model.network.CreateFolderRequest
import net.theluckycoder.familyphotos.core.data.model.network.FolderSyncDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

internal interface FolderService {

    @POST("/api/folders")
    suspend fun createFolder(
        @Body newFolder: CreateFolderRequest,
    ): Response<FolderSyncDto>

    @PATCH("/api/folders/{id}")
    suspend fun updateFolder(
        @Path("id") folderId: Long,
        @Body newFolder: CreateFolderRequest,
    ): Response<FolderSyncDto>
}