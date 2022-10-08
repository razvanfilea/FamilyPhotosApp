package net.theluckycoder.familyphotos.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import coil.ImageLoader
import dagger.Lazy
import okhttp3.OkHttpClient

val LocalImageLoader = compositionLocalOf<Lazy<ImageLoader>> { error("No ImageLoader found!") }

val LocalSnackbarHostState =
    compositionLocalOf<SnackbarHostState> { error("No SnackbarHostState found!") }

val LocalOkHttpClient = compositionLocalOf<Lazy<OkHttpClient>> {
    error("No OkHttpClient found!")
}
