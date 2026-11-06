package net.theluckycoder.familyphotos.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.di.IOCoroutineScope
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.SharedUploadFile
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.core.data.repository.LoginRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.core.data.repository.SharedUploadsRepository
import net.theluckycoder.familyphotos.domain.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.UiMessageType
import net.theluckycoder.familyphotos.workers.enqueueBackupAndUploadWorker
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Backs [net.theluckycoder.familyphotos.ui.ShareActivity]: copies the shared items, then
 * queues them or deletes the copies. Cleared when the activity finishes, which is when the
 * share is cancelled.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val sharedUploadsRepository: SharedUploadsRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val workManager: WorkManager,
    private val ioScope: IOCoroutineScope,
    val loginRepository: LoginRepository,
) : ViewModel() {

    // Not the singleton: MainActivity collects that one too and would repeat these messages
    val snackbarManager = SnackbarManager()

    /** null until the login state is known */
    val isLoggedIn: StateFlow<Boolean?> = loginRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Copy progress: null when idle, 0..1 otherwise */
    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress = _importProgress.asStateFlow()

    /** The copies, kept in the saved state so the screen survives process death */
    private var files: List<SharedUploadFile>?
        get() = savedStateHandle.get<String>(KEY_FILES)?.let { Json.decodeFromString(it) }
        set(value) {
            savedStateHandle[KEY_FILES] = value?.let { Json.encodeToString(it) }
        }

    private val _previews = MutableStateFlow<List<Photo>>(emptyList())
    val previews = _previews.asStateFlow()

    /** Set once there is nothing left to do here: the activity should finish */
    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()

    private var queued = false

    /**
     * Every finished copy, including ones [files] never got: a cancellation right as the
     * import returns drops its result
     */
    private val copiedFiles = ConcurrentLinkedQueue<SharedUploadFile>()

    init {
        val restoredFiles = files
        if (restoredFiles != null) {
            loadPreviews(restoredFiles)
        } else if (savedStateHandle.get<Boolean>(KEY_IMPORT_STARTED) == true) {
            // The process died mid-copy, with the share's URI grant
            finishWithMessage(R.string.error_shared_items_failed)
        }

        viewModelScope.launch {
            // A restored screen can be older than the sweep's cutoff; any instance
            // (this one's or MainViewModel's) must keep these copies
            if (restoredFiles != null) sharedUploadsRepository.touch(restoredFiles.map { it.fileName })
            photoUploadRepository.deleteOrphanedSharedFiles()
        }

        viewModelScope.launch {
            // Logging in here leaves the database empty: fetch the folders to upload into
            if (isLoggedIn.first { it != null } == false) {
                isLoggedIn.first { it == true }
                val result = withContext(Dispatchers.IO) {
                    refreshPhotosUseCase(refreshDeviceAlbums = false)
                }
                if (result is RefreshPhotosUseCase.Result.Error) {
                    Log.e(TAG, "Failed to refresh after login", result.throwable)
                }
            }
        }
    }

    /** Must run while the share's URI grant is valid, i.e. while the activity is alive */
    fun importSharedItems(uris: List<Uri>, intentMimeType: String?) {
        if (savedStateHandle.get<Boolean>(KEY_IMPORT_STARTED) == true) return
        savedStateHandle[KEY_IMPORT_STARTED] = true

        if (uris.isEmpty()) {
            finishWithMessage(R.string.error_shared_items_failed)
            return
        }

        viewModelScope.launch {
            _importProgress.value = 0f
            val imported = try {
                sharedUploadsRepository.import(
                    uris = uris,
                    intentMimeType = intentMimeType,
                    onCopied = copiedFiles::add,
                ) { copiedBytes, totalBytes ->
                    if (totalBytes > 0) {
                        _importProgress.value = (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                    }
                }
            } finally {
                _importProgress.value = null
            }

            if (imported.isEmpty()) {
                finishWithMessage(R.string.error_shared_items_failed)
                return@launch
            }
            if (imported.size < uris.size) {
                snackbarManager.showMessage(R.string.error_shared_items_partial, UiMessageType.Error)
            }
            files = imported
            loadPreviews(imported)
        }
    }

    private fun loadPreviews(files: List<SharedUploadFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val previews = files.mapIndexedNotNull { index, file ->
                val copy = sharedUploadsRepository.fileOf(file)
                if (!copy.exists()) return@mapIndexedNotNull null
                // Never stored: only used to preview the copy through CoilPhoto
                LocalPhoto(
                    id = -(index + 1L),
                    name = file.displayName,
                    timeCreated = file.timeCreated,
                    folder = null,
                    uri = Uri.fromFile(copy),
                    mimeType = file.mimeType,
                )
            }
            // Every copy can be gone, e.g. removed on logout or by the orphan sweep
            if (previews.isEmpty()) finishWithMessage(R.string.error_shared_items_failed)
            else _previews.value = previews
        }
    }

    /** The caller finishes the activity right away; queuing outlives it */
    fun upload(uploadChoice: UploadChoice) {
        // A double tap on Upload would insert the same shared files twice
        if (queued) return
        val sharedFiles = files
        if (sharedFiles.isNullOrEmpty()) {
            // Still copying, or nothing could be copied: the activity closes anyway
            ioScope.launch { showToast(R.string.error_shared_items_failed) }
            return
        }
        queued = true

        ioScope.launch {
            // Copies can vanish while the screen is open (logout, clear queue, orphan sweep)
            val existing = sharedFiles.filter { sharedUploadsRepository.fileOf(it).exists() }
            if (existing.isEmpty()) {
                showToast(R.string.error_shared_items_failed)
                return@launch
            }
            photoUploadRepository.enqueueSharedUploads(existing, uploadChoice)
            workManager.enqueueBackupAndUploadWorker(skipFolderScan = true)
            showToast(
                if (existing.size < sharedFiles.size) R.string.status_upload_queued_partial
                else R.string.status_upload_queued
            )
        }
    }

    private fun finishWithMessage(@StringRes message: Int) {
        if (_finished.value) return
        _finished.value = true
        ioScope.launch { showToast(message) }
    }

    // A toast, not a snackbar: the activity is finishing, the user is back in the sending app
    private suspend fun showToast(@StringRes message: Int) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        // Only when the activity finishes (Upload, Cancel, back), not on configuration changes
        if (queued) return
        val names = (files.orEmpty() + copiedFiles).map { it.fileName }.toSet()
        if (names.isNotEmpty()) ioScope.launch { sharedUploadsRepository.delete(names) }
    }

    private companion object {
        const val TAG = "ShareViewModel"
        const val KEY_FILES = "shared_files"
        const val KEY_IMPORT_STARTED = "import_started"
    }
}
