package net.theluckycoder.familyphotos.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import dagger.Lazy
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore

val LocalImageLoader = staticCompositionLocalOf<Lazy<ImageLoader>> { error("No ImageLoader found!") }

val LocalNavBackStack = compositionLocalOf<NavBackStack<NavKey>> { error("No NavBackStack found!") }

val LocalSnackbarHostState =
    staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState found!") }

val LocalSettingsDataStore = staticCompositionLocalOf<SettingsDataStore> {
    error("No SettingsDataStore found!")
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope> { error("No LocalSharedTransitionScope found!") }

/**
 * Holds the id of the photo currently transitioning between the grid and the viewer.
 *
 * Only this ONE photo registers [Modifier.photoSharedBounds] on the grid side, so scrolling never
 * pays for shared-element machinery on the other ~40 cells. It lives above `NavDisplay` (rather than
 * inside `PhotosList`) so it survives while the grid is out of composition: on the close transition
 * the grid re-composes and reads this to supply the matching morph target. The viewer updates it on
 * swipe, so closing morphs back to whichever photo is currently shown, not the one first tapped.
 */
val LocalOpeningPhotoId = staticCompositionLocalOf<MutableState<Long?>> {
    error("No LocalOpeningPhotoId found!")
}

