package net.theluckycoder.familyphotos.repository

import dagger.Lazy
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.network.service.PhotosService
import javax.inject.Inject

@ViewModelScoped
class PhotosListRepository @Inject constructor(
    private val photosService: Lazy<PhotosService>,
    private val networkPhotosDao: NetworkPhotosDao,
) {

    suspend fun pingServer(): Boolean = photosService.get().ping().isSuccessful

    fun getHomePhotosPaged(userId: Long) = networkPhotosDao.getUserPhotosPaged(userId)

    fun getPublicPhotosPaged(userId: Long) = networkPhotosDao.getPublicPhotosPaged(userId)

    suspend fun downloadAllPhotos(userId: Long) = coroutineScope {
        val service = photosService.get()
        val userPhotosAsync = async { service.getPhotosList(userId) }
        val publicPhotos = service.getPublicPhotosList()

        val userPhotos = userPhotosAsync.await()
        if (userPhotos.isSuccessful && publicPhotos.isSuccessful) {
            val photos = (userPhotos.body() ?: emptyList()) + (publicPhotos.body() ?: emptyList())

            if (photos.isNotEmpty()) {
                networkPhotosDao.replaceAll(photos)
                return@coroutineScope true
            }
        }

        false

        /*photos.forEach {
            val calender = it.timeCreated.let {
                val c = Calendar.getInstance()
                c.timeInMillis = it
                c
            }

            Log.v(
                "NetworkPhoto",
                "${it.name}, ${
                    DateFormat.format(
                        "MMMM",
                        calender
                    )
                } ${calender.get(Calendar.YEAR)}, ${it.timeCreated}"
            )
        }*/
    }
}
