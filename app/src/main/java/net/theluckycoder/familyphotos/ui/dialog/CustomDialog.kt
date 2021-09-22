package net.theluckycoder.familyphotos.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension

@ExperimentalComposeUiApi
@Composable
fun CustomDialog(
    onDismissRequest: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) = Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false)
) {
    Surface(Modifier.padding(16.dp), shape = RoundedCornerShape(8.dp)) {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            constraintSet = ConstraintSet {
                val column = createRefFor("main")
                val button = createRefFor("buttons")

                constrain(column) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(button.top)

                    height = Dimension.fillToConstraints
                }

                constrain(button) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                }
            }
        ) {
            Box(Modifier.layoutId("main")) {
                content()
            }

            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .layoutId("buttons")
            ) {
                buttons()
            }
        }
    }
}
