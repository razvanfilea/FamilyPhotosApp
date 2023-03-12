package net.theluckycoder.familyphotos.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.repository.FoldersRepository
import net.theluckycoder.familyphotos.repository.ServerRepository
import java.io.IOException
import java.net.ConnectException

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val foldersRepository: FoldersRepository,
    private val serverRepository: ServerRepository,
    private val userDataStore: UserDataStore,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        foldersRepository.updatePhoneAlbums()

        val photos = foldersRepository.localPhotosFromFolder("Camera").first()
            .take(20)
            .filter { it.networkPhotoId == 0L } // Photos not uploaded

        val userId = userDataStore.userIdFlow.first().toLong()

        val result = try {

            photos.forEach { localPhoto ->
                serverRepository.uploadFile(userId, localPhoto, null)
            }

            Result.success()
        } catch (e: ConnectException) {
            Result.retry()
        } catch (e: IOException) {
            e.printStackTrace()
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(BackupWorker::class.simpleName, e.stackTraceToString())
            Result.failure()
        }

        return result
    }
}
