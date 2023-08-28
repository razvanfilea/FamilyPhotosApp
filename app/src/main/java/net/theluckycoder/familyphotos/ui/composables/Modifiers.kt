package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.selectableClickable(
    inSelectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDeselect: () -> Unit
) = composed {
    this
        .semantics {
            if (!inSelectionMode) {
                onLongClick("Select") {
                    onSelect()
                    true
                }
            }
        }
        .then(if (inSelectionMode) {
            Modifier.toggleable(
                value = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // do not show a ripple
                onValueChange = {
                    if (it) {
                        onSelect()
                    } else {
                        onDeselect()
                    }
                }
            )
        } else {
            Modifier.combinedClickable(onClick = { onClick() }, onLongClick = onSelect)
        }
        )
}
