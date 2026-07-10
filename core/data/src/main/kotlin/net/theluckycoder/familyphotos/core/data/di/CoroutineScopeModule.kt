package net.theluckycoder.familyphotos.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class CoroutineScopeModule {

    @Singleton
    @Provides
    fun provideDefaultCoroutineScope(): DefaultCoroutineScope = object : DefaultCoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    @Singleton
    @Provides
    fun provideIOCoroutineScope(): IOCoroutineScope = object : IOCoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.IO
    }

    @Singleton
    @Provides
    fun provideMainCoroutineScope(): MainCoroutineScope = object : MainCoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Main
    }
}