package net.theluckycoder.familyphotos.db

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.theluckycoder.familyphotos.db.dao.LocalPhotosDao
import net.theluckycoder.familyphotos.db.dao.NetworkPhotosDao

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {

    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): PhotosDatabase =
        PhotosDatabase.getDatabase(context)

    @Provides
    fun provideLocalPhotosDao(photosDatabase: PhotosDatabase): LocalPhotosDao =
        photosDatabase.localPhotosDao()

    @Provides
    fun provideNetworkPhotosDao(photosDatabase: PhotosDatabase): NetworkPhotosDao =
        photosDatabase.networkPhotosDao()
}
