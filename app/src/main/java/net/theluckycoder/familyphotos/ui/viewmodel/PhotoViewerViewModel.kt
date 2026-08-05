package net.theluckycoder.familyphotos.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.core.data.model.ExifData
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.ServerRepository
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    application: Application,
    okHttpClient: OkHttpClient,
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
        application,
        OkHttpDataSource.Factory(okHttpClient),
    )

    fun getLocalPhotoFlow(photoId: Long): Flow<LocalPhoto?> =
        photosRepository.getLocalPhotoFlow(photoId)

    fun getNetworkPhotoFlow(photoId: Long): Flow<NetworkPhoto?> =
        photosRepository.getNetworkPhotoFlow(photoId)

    fun isNetworkPhotoFavorite(photoId: Long): Flow<Boolean> =
        photosRepository.isNetworkPhotoFavorite(photoId)

    fun updateFavorite(photoId: Long, add: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = photosRepository.getNetworkPhoto(photoId) ?: return@launch
            serverRepository.updateFavorite(photo, add)
        }
    }

    suspend fun getNetworkPhoto(photoId: Long): NetworkPhoto? =
        photosRepository.getNetworkPhoto(photoId)

    fun getEquivalentLocalUriFlow(photoId: Long): Flow<Uri?> =
        flow { emit(photosRepository.getLocalPhotoFromNetwork(photoId)?.uri) }

    suspend fun getEquivalentLocalUri(photoId: Long): Uri? =
        photosRepository.getLocalPhotoFromNetwork(photoId)?.uri

    suspend fun getExifData(photo: NetworkPhoto): ExifData? = withContext(Dispatchers.IO) {
        serverRepository.getExifData(photo)
    }

    suspend fun getFolderName(folderId: Long): String? = withContext(Dispatchers.IO) {
        serverRepository.getFolderName(folderId)
    }
}