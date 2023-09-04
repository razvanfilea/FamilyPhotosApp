package net.theluckycoder.familyphotos.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        foldersRepository.updatePhoneAlbums()

        val photos = foldersRepository.localPhotosFromFolder("Camera").first()
            .take(50)
            .filter { !it.isSavedToCloud } // Photos not uploaded

        val result = try {

            photos.forEach { localPhoto ->
                serverRepository.uploadFile(localPhoto, false, null)
            }

            Result.success()
        } catch (e: ConnectException) {
            Result.retry()
        } catch (e: IOException) {
            e.printStackTrace()
            Result.failure()
        } catch (e: CancellationException) {
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(BackupWorker::class.simpleName, e.stackTraceToString())
            Result.failure()
        }

        return result
    }
}
