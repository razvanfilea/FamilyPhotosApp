package net.theluckycoder.familyphotos.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun BottomSheetNavigatorBackHandler(
    navigator: BottomSheetNavigator,
    sheetState: ModalBottomSheetState,
    hideOnBackPress: Boolean
) {
    if (sheetState.isVisible) {
        BackHandler {
            if (navigator.pop().not() && hideOnBackPress) {
                navigator.hide()
            }
        }
    }
}
