package net.theluckycoder.familyphotos.ui

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import coil.ImageLoader
import dagger.Lazy
import net.theluckycoder.familyphotos.utils.PlayerController

val LocalImageLoader = compositionLocalOf<Lazy<ImageLoader>> { error("No ImageLoader found!") }

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> { error("No SnackbarHostState found!") }

val LocalPlayerController = compositionLocalOf<PlayerController> {
    error("PlayerController is not provided for this scope.")
}
