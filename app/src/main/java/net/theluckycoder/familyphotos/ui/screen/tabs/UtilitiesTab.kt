package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.camera.CameraActivity
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.PhotoStatistics
import net.theluckycoder.familyphotos.ui.DuplicatesNav
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LargeFilesNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.SettingsNav
import net.theluckycoder.familyphotos.ui.TrashNav
import net.theluckycoder.familyphotos.ui.viewmodel.UtilitiesViewModel
import java.text.DecimalFormat

@Composable
fun UtilitiesTab(
    viewModel: UtilitiesViewModel = viewModel(),
) = Column(
    modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
) {
    val backStack = LocalNavBackStack.current
    val statistics by viewModel.photoStatistics.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxCurrentLineSpan)}) {
            PhotoStatisticsCard(statistics)
        }

        item {
            val ctx = LocalContext.current
            UtilityButton(
                iconId = R.drawable.ic_exif_camera,
                text = stringResource(R.string.camera_name),
                iconTint = Color(0xFF26A69A), // Teal
                onClick = {
                    val intent = Intent(ctx, CameraActivity::class.java)
                    ctx.startActivity(intent)
                },
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_settings_outline,
                text = stringResource(R.string.title_settings),
                onClick = { backStack.add(SettingsNav) },
                iconTint = Color(0xFF78909C), // Blue Grey
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_star_outline,
                text = stringResource(R.string.title_favorites),
                onClick = { backStack.add(FolderNav(FolderNav.Source.Favorites)) },
                iconTint = Color(0xFFFFB300), // Amber
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_duplicates_outlined,
                text = stringResource(R.string.title_duplicates),
                onClick = { backStack.add(DuplicatesNav) },
                iconTint = Color(0xFFFF7043), // Deep Orange
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_large_photos,
                text = stringResource(R.string.title_large_files),
                onClick = { backStack.add(LargeFilesNav) },
                iconTint = Color(0xFF42A5F5), // Blue
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_action_delete,
                text = stringResource(R.string.title_trash),
                onClick = { backStack.add(TrashNav) },
                iconTint = Color(0xFFEF5350), // Red
            )
        }
    }
}

@Composable
private fun PhotoStatisticsCard(
    statistics: PhotoStatistics,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.statistics_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    iconId = R.drawable.ic_family_outline,
                    label = stringResource(R.string.photo_type_family),
                    count = statistics.familyCount,
                    size = formatFileSize(statistics.familySize)
                )
                StatisticItem(
                    iconId = R.drawable.ic_person_outline,
                    label = stringResource(R.string.photo_type_personal),
                    count = statistics.personalCount,
                    size = formatFileSize(statistics.personalSize)
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    iconId = R.drawable.ic_exif_image,
                    label = stringResource(R.string.photo_type_images),
                    count = statistics.imageCount,
                    size = formatFileSize(statistics.imageSize)
                )
                StatisticItem(
                    iconId = R.drawable.ic_video_play,
                    label = stringResource(R.string.photo_type_videos),
                    count = statistics.videoCount,
                    size = formatFileSize(statistics.videoSize)
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.statistics_total),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.items_photos,
                        statistics.totalCount,
                        statistics.totalCount
                    ) + " • " + formatFileSize(statistics.totalSize),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    @DrawableRes iconId: Int,
    label: String,
    count: Int,
    size: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = pluralStringResource(R.plurals.items_photos, count, count),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = size,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UtilityButton(
    @DrawableRes iconId: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = LocalContentColor.current,
) {
    OutlinedButton(
        modifier = modifier.height(52.dp),
        onClick = onClick,
    ) {
        Icon(
            painterResource(iconId),
            contentDescription = null,
            tint = iconTint
        )

        Spacer(Modifier.width(12.dp))

        Text(text)

        Spacer(Modifier.weight(1f))
    }
}

private const val SIZE_KB = 1024.0f
private const val SIZE_MB = SIZE_KB * SIZE_KB
private const val SIZE_GB = SIZE_MB * SIZE_KB
private const val SIZE_TB = SIZE_GB * SIZE_KB

private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("0.00")
    return when {
        size < SIZE_MB -> df.format(size / SIZE_KB) + " KB"
        size < SIZE_GB -> df.format(size / SIZE_MB) + " MB"
        size < SIZE_TB -> df.format(size / SIZE_GB) + " GB"
        else -> df.format(size / SIZE_TB) + " TB"
    }
}
