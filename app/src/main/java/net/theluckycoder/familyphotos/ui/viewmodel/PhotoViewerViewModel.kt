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
import net.theluckycoder.familyphotos.data.model.ExifData
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
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

    fun updateFavorite(photo: NetworkPhoto, add: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            serverRepository.updateFavorite(photo, add)
        }
    }

    fun getEquivalentLocalUriFlow(photo: NetworkPhoto): Flow<Uri?> =
        flow { photosRepository.getLocalPhotoFromNetwork(photo.id)?.uri }

    suspend fun getEquivalentLocalUri(photo: NetworkPhoto): Uri? =
         photosRepository.getLocalPhotoFromNetwork(photo.id)?.uri

    suspend fun getExifData(photo: NetworkPhoto): ExifData? = withContext(Dispatchers.IO) {
        serverRepository.getExifData(photo)
    }
}