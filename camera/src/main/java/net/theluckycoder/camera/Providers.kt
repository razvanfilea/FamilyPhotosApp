package net.theluckycoder.camera

import androidx.compose.runtime.compositionLocalOf
import coil3.ImageLoader

internal val LocalImageLoader = compositionLocalOf<ImageLoader> { error("No ImageLoader found!") }

/**
 * Device rotation in degrees (0, 90, 180, 270) for rotating UI elements.
 * 0 = portrait, 90 = landscape left, 180 = upside down, 270 = landscape right
 */
internal val LocalDeviceRotation = compositionLocalOf { 0 }
