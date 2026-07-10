package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import net.theluckycoder.familyphotos.ui.UiMessageType

@Composable
fun TypedSnackbar(snackbarData: SnackbarData) {
    val visuals = snackbarData.visuals
    val type = (visuals as? TypedSnackbarVisuals)?.type ?: UiMessageType.Info

    val containerColor = when (type) {
        UiMessageType.Success -> MaterialTheme.colorScheme.primaryContainer
        UiMessageType.Error -> MaterialTheme.colorScheme.errorContainer
        UiMessageType.Info -> MaterialTheme.colorScheme.inverseSurface
    }

    val contentColor = when (type) {
        UiMessageType.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        UiMessageType.Error -> MaterialTheme.colorScheme.onErrorContainer
        UiMessageType.Info -> MaterialTheme.colorScheme.inverseOnSurface
    }

    Snackbar(
        snackbarData = snackbarData,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}
