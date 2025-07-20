package net.theluckycoder.familyphotos.domain

import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    suspend operator fun invoke(): Result {
        val localPhotosJob = CoroutineScope(Dispatchers.IO).async {
            foldersRepository.updatePhoneAlbums()
        }

        var response = try {
            serverRepository.downloadPartialPhotos()
        } catch (e: Exception) {
            ServerRepository.DownloadResponse.UNSUCCESSFUL
            localPhotosJob.await()
            return Result.Error(e)
        }

        if (response == ServerRepository.DownloadResponse.FULL_DOWNLOAD_NEEDED) {
            try {
                response = serverRepository.downloadAllPhotos()
            } catch (e: Exception) {
                Log.e("RefreshPhotosUseCase", "Failed to download photos", e)
                localPhotosJob.await()
                return Result.Error(e)
            }
        }

        if (response == ServerRepository.DownloadResponse.NOT_LOGGED_IN) {
            // Consider how to signal logout. Maybe return a specific result.
            // For now, let's assume the ViewModel handles actual logout triggering.
            localPhotosJob.await()
            return Result.NotLoggedIn
        }

        isOnlineState.value = response == ServerRepository.DownloadResponse.SUCCESSFUL

        localPhotosJob.await()

        return Result.Success // TODO Handle error
    }

    sealed class Result {
        object Success : Result()
        object NotLoggedIn : Result()
        data class Error(val throwable: Throwable) : Result()
    }
}