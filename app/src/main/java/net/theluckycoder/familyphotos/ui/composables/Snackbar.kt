package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private fun CollectUiMessages(snackbarManager: SnackbarManager) {
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
                        res.getQuantityString(
                            message.resId,
                            message.quantity,
                            *message.args.toTypedArray()
                        )
                    }
                }
            }
            snackbarHostState.showSnackbar(
                TypedSnackbarVisuals(message = text, type = message.type)
            )
        }
    }
}

@Composable
fun BoxScope.TypedSnackbar(snackbarManager: SnackbarManager) {
    CollectUiMessages(snackbarManager)

    val snackbarHostState = LocalSnackbarHostState.current
    val dismissBoxState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissBoxState.currentValue) {
        if (dismissBoxState.currentValue != SwipeToDismissBoxValue.Settled) {
            snackbarHostState.currentSnackbarData?.dismiss()
            dismissBoxState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissBoxState,
        backgroundContent = {},
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
    ) {
        SnackbarHost(hostState = snackbarHostState) { snackbarData ->
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
                dismissActionContentColor = contentColor,
            )
        }
    }
}