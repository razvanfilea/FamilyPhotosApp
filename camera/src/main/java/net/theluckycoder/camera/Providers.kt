package net.theluckycoder.camera

import androidx.compose.runtime.compositionLocalOf
import coil.ImageLoader

internal val LocalImageLoader = compositionLocalOf<ImageLoader> { error("No ImageLoader found!") }
