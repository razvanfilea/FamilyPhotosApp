package net.theluckycoder.familyphotos.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.data.local.db.LocalFolderBackupDao
import net.theluckycoder.familyphotos.data.local.db.UploadQueueDao
import net.theluckycoder.familyphotos.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.data.repository.FoldersRepository

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val foldersRepository: FoldersRepository,
    private val uploadQueueDao: UploadQueueDao,
    private val localFolderBackupDao: LocalFolderBackupDao,
    private val workManager: WorkManager,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        foldersRepository.updatePhoneAlbums()

        val folderNames = localFolderBackupDao.getAll().first()

        val entries = mutableListOf<UploadQueueEntry>()

        for (folderName in folderNames) {
            Log.i("BackupWorker", "Backing Up folder: $folderName)")
            val photos = foldersRepository.localPhotosFromFolder(folderName, 100)
                .filter { !it.isSavedToCloud }

            photos.mapTo(entries) { localPhoto ->
                UploadQueueEntry(
                    localPhotoId = localPhoto.id,
                    makePublic = false,
                    uploadFolder = folderName,
                )
            }
        }

        if (entries.isNotEmpty()) {
            uploadQueueDao.insertAll(entries)
            workManager.enqueueUploadWorker()
        }

        return Result.success()
    }
}
