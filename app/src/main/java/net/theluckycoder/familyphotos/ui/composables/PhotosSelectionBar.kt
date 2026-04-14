package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.theluckycoder.familyphotos.R

@Composable
fun PhotosSelectionBar(
    selectedPhotoIds: SnapshotStateSet<Long>,
    actionsContent: @Composable () -> Unit
) = AnimatedVisibility(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 48.dp)
        .height(64.dp),
    visible = selectedPhotoIds.isNotEmpty(),
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            modifier = Modifier.fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = Color.White,
            ),
            onClick = {
                selectedPhotoIds.clear()
            }
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null)

            Spacer(Modifier.width(16.dp))

            VerticallyAnimatedInt(
                targetState = selectedPhotoIds.size,
                contentAlignment = Alignment.Center
            ) { count ->
                Text(count.toString(), fontSize = 16.sp)
            }
        }

        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Row {
                actionsContent()
            }
        }
    }
}
