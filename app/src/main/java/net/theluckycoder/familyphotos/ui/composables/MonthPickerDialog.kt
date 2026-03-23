package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.utils.buildDateString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerBottomSheet(
    monthSummaries: List<MonthSummary>,
    onMonthSelected: (MonthSummary) -> Unit,
    onDismiss: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
    ) {
        Text(
            text = stringResource(R.string.action_select_month),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(monthSummaries, key = { it.timeCreated }) { summary ->
                MonthSummaryItem(
                    summary = summary,
                    onClick = {
                        onMonthSelected(summary)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun MonthSummaryItem(
    summary: MonthSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthLabel = remember(summary.timeCreated) {
        buildDateString(summary.timeCreated)
    }

    val coverPhoto = remember(summary.coverPhotoId) {
        NetworkPhoto(
            id = summary.coverPhotoId,
            userId = null,
            name = "",
            timeCreated = summary.timeCreated,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoilPhoto(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            photo = coverPhoto,
            preview = true,
            contentScale = ContentScale.Crop,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = pluralStringResource(
                    R.plurals.items_photos,
                    summary.photoCount,
                    summary.photoCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}