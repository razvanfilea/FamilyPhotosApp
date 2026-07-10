package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.PhotoType

@Composable
fun PhotoTypeChips(
    selectedPhotoType: PhotoType,
    onChangePhotoType: (PhotoType) -> Unit,
    modifier: Modifier = Modifier
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .then(modifier),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    PhotoType.entries.forEach { type ->
        val selected = selectedPhotoType == type
        val res = when (type) {
            PhotoType.All -> R.string.photo_type_all
            PhotoType.Personal -> R.string.photo_type_personal
            PhotoType.Family -> R.string.photo_type_family
            PhotoType.Shared -> R.string.photo_type_shared
        }

        FilterChip(
            modifier = Modifier.height(36.dp),
            selected = selected,
            onClick = { onChangePhotoType(type) },
            label = { Text(stringResource(res)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,

                labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                containerColor = Color.Transparent,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                selectedBorderColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                AnimatedVisibility(selected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_done),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            }
        )
    }
}
