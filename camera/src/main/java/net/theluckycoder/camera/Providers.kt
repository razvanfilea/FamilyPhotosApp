package net.theluckycoder.camera

import androidx.compose.runtime.compositionLocalOf
import coil3.ImageLoader

internal val LocalImageLoader = compositionLocalOf<ImageLoader> { error("No ImageLoader found!") }
