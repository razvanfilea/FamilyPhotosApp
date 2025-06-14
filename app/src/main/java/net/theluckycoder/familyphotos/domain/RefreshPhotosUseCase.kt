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
        val pingResponse = try {
            serverRepository.pingServer()
        } catch (_: Exception) {
            ServerRepository.PingResponse.UNSUCCESSFUL
        }

        if (pingResponse == ServerRepository.PingResponse.NOT_LOGGED_IN) {
            // Consider how to signal logout. Maybe return a specific result.
            // For now, let's assume the ViewModel handles actual logout triggering.
            return Result.NotLoggedIn
        }
        isOnlineState.value = pingResponse == ServerRepository.PingResponse.SUCCESSFUL

        val localPhotosJob = CoroutineScope(Dispatchers.IO).async {
            foldersRepository.updatePhoneAlbums()
        }

        if (pingResponse == ServerRepository.PingResponse.SUCCESSFUL) {
            try {
                serverRepository.downloadAllPhotos()
            } catch (e: Exception) {
                Log.e("RefreshPhotosUseCase", "Failed to download photos", e)
                return Result.Error(e)
            }
        }

        localPhotosJob.await()

        return Result.Success
    }

    sealed class Result {
        object Success : Result()
        object NotLoggedIn : Result()
        data class Error(val throwable: Throwable) : Result()
    }
}