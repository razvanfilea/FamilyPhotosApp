package net.theluckycoder.familyphotos.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import coil3.ImageLoader
import dagger.Lazy
import okhttp3.OkHttpClient

val LocalImageLoader = staticCompositionLocalOf<Lazy<ImageLoader>> { error("No ImageLoader found!") }

val LocalNavBackStack = compositionLocalOf<NavBackStack> { error("No NavBackStack found!") }

val LocalSnackbarHostState =
    staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState found!") }

val LocalOkHttpClient = staticCompositionLocalOf<Lazy<OkHttpClient>> {
    error("No OkHttpClient found!")
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> { error("No LocalSharedTransitionScope found!") }

