package net.theluckycoder.camera

import android.view.ViewGroup
import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraController: CameraController,
) {
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                previewView.controller = cameraController
                previewView
            },
        )
    }
}

