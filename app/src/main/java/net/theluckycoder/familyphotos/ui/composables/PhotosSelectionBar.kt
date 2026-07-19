package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R

@Composable
fun PhotosSelectionBar(
    selectedPhotoIds: SnapshotStateSet<Long>,
    modifier: Modifier = Modifier,
    actionsContent: @Composable RowScope.() -> Unit
) = AnimatedVisibility(
    modifier = modifier.clip(CircleShape),
    visible = selectedPhotoIds.isNotEmpty(),
    enter = expandHorizontally(expandFrom = Alignment.CenterHorizontally) + fadeIn(),
    exit = shrinkHorizontally(shrinkTowards = Alignment.CenterHorizontally) + fadeOut()
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 6.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                onClick = { selectedPhotoIds.clear() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.action_cancel),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(Modifier.width(8.dp))

                VerticallyAnimatedInt(
                    targetState = selectedPhotoIds.size,
                    contentAlignment = Alignment.Center
                ) { count ->
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actionsContent()
            }
        }
    }
}
