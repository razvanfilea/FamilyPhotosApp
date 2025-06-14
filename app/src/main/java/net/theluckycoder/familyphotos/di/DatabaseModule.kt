package net.theluckycoder.familyphotos.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.theluckycoder.familyphotos.data.local.db.LocalFolderBackupDao
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.local.db.PhotosDatabase

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {

    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): PhotosDatabase =
        PhotosDatabase.Companion.getDatabase(context)

    @Provides
    fun provideLocalPhotosDao(photosDatabase: PhotosDatabase): LocalPhotosDao =
        photosDatabase.localPhotosDao()

    @Provides
    fun provideNetworkPhotosDao(photosDatabase: PhotosDatabase): NetworkPhotosDao =
        photosDatabase.networkPhotosDao()

    @Provides
    fun provideLocalFolderBackupDao(photosDatabase: PhotosDatabase): LocalFolderBackupDao =
        photosDatabase.localFolderBackupDao()
}