package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.UiMessage
import net.theluckycoder.familyphotos.ui.UiMessageType

class TypedSnackbarVisuals(
    override val message: String,
    val type: UiMessageType,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
fun CollectUiMessages(snackbarManager: SnackbarManager) {
    val resources = LocalResources.current
    val snackbarHostState = LocalSnackbarHostState.current
    val currentResources = rememberUpdatedState(resources)

    LaunchedEffect(snackbarManager) {
        snackbarManager.messages.collect { message ->
            val res = currentResources.value
            val text = when (message) {
                is UiMessage.Text -> message.value
                is UiMessage.Resource -> {
                    if (message.args.isEmpty()) {
                        res.getString(message.resId)
                    } else {
                        res.getString(message.resId, *message.args.toTypedArray())
                    }
                }
                is UiMessage.PluralResource -> {
                    if (message.args.isEmpty()) {
                        res.getQuantityString(message.resId, message.quantity, message.quantity)
                    } else {
                        res.getQuantityString(message.resId, message.quantity, *message.args.toTypedArray())
                    }
                }
            }
            snackbarHostState.showSnackbar(
                TypedSnackbarVisuals(message = text, type = message.type)
            )
        }
    }
}
