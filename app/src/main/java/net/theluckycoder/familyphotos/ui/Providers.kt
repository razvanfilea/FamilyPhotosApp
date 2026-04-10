package net.theluckycoder.familyphotos.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import dagger.Lazy
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore

val LocalImageLoader = staticCompositionLocalOf<Lazy<ImageLoader>> { error("No ImageLoader found!") }

val LocalNavBackStack = compositionLocalOf<NavBackStack<NavKey>> { error("No NavBackStack found!") }

val LocalSnackbarHostState =
    staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState found!") }

val LocalSettingsDataStore = staticCompositionLocalOf<SettingsDataStore> {
    error("No SettingsDataStore found!")
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope> { error("No LocalSharedTransitionScope found!") }

