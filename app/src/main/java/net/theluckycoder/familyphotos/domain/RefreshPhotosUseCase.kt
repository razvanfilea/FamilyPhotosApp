package net.theluckycoder.familyphotos.domain

import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import net.theluckycoder.familyphotos.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import javax.inject.Inject

@ViewModelScoped
class RefreshPhotosUseCase @Inject constructor(
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
) {
    val isOnlineState = MutableStateFlow(true)

    suspend operator fun invoke(): Result = coroutineScope {
        val localPhotosJob = async(Dispatchers.IO) {
            foldersRepository.updatePhoneAlbums()
        }

        var response = try {
            serverRepository.downloadPartialPhotos()
        } catch (e: Exception) {
            Log.e("RefreshPhotosUseCase", "Failed to download partial photos: ${e.message}", e)
            ServerRepository.DownloadResponse.UNSUCCESSFUL
        }

        if (response == ServerRepository.DownloadResponse.NOT_LOGGED_IN) {
            localPhotosJob.await()
            return@coroutineScope Result.NotLoggedIn
        }

        if (response == ServerRepository.DownloadResponse.FULL_DOWNLOAD_NEEDED || response == ServerRepository.DownloadResponse.UNSUCCESSFUL) {
            try {
                response = serverRepository.downloadAllPhotos()
            } catch (e: Exception) {
                Log.e("RefreshPhotosUseCase", "Failed to download photos", e)
                localPhotosJob.await()
                return@coroutineScope Result.Error(e)
            }
        }

        isOnlineState.value = response == ServerRepository.DownloadResponse.SUCCESSFUL

        localPhotosJob.await()

        Result.Success
    }

    sealed class Result {
        object Success : Result()
        object NotLoggedIn : Result()
        data class Error(val throwable: Throwable? = null) : Result()
    }
}