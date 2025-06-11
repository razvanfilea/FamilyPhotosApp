package net.theluckycoder.familyphotos.utils

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@InstallIn(SingletonComponent::class)
@Module
class CoroutineScopeModule {

    @Provides
    fun provideDefaultCoroutineScope(): DefaultCoroutineScope = object : DefaultCoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    @Provides
    fun provideIOCoroutineScope(): IOCoroutineScope = object : IOCoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.IO
    }

    @Provides
    fun provideMainCoroutineScope(): MainCoroutineScope = object : MainCoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Main
    }
}
