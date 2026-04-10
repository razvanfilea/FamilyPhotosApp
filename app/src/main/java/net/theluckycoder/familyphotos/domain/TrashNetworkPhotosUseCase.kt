package net.theluckycoder.familyphotos.domain

import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import javax.inject.Inject

/**
 * Use case for trashing and restoring network photos.
 * Reusable by MainViewModel (trash) and TrashViewModel (restore).
 */
@ViewModelScoped
class TrashNetworkPhotosUseCase @Inject constructor(
    private val serverRepository: ServerRepository,
) {
    /**
     * Moves photos to trash.
     * @return List of results indicating success/failure for each photo
     */
    suspend fun trash(photoIds: LongArray): List<Boolean> = coroutineScope {
        photoIds.map { photoId ->
            async {
                serverRepository.trashNetworkPhoto(photoId, true)
            }
        }.awaitAll()
    }

    /**
     * Restores photos from trash.
     * @return List of results indicating success/failure for each photo
     */
    suspend fun restore(photoIds: LongArray): List<Boolean> = coroutineScope {
        photoIds.map { photoId ->
            async {
                serverRepository.trashNetworkPhoto(photoId, false)
            }
        }.awaitAll()
    }
}
