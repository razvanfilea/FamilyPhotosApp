package net.theluckycoder.familyphotos.ui.dialog

import android.content.Intent
import android.text.format.Formatter
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME
import androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH
import androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF
import androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH
import androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH
import androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL
import androidx.exifinterface.media.ExifInterface.TAG_MAKE
import androidx.exifinterface.media.ExifInterface.TAG_MODEL
import androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.ExifData
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.isPublic
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.photoDateText
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel
import java.text.DecimalFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPhotoInfoDialog(
    photo: NetworkPhoto,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        NetworkPhotoInfoDialogContent(photo)
    }
}

@Composable
private fun DetailItem(title: String, summary: String?, @DrawableRes icon: Int) =
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = if (summary != null) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

@Composable
fun NetworkPhotoInfoDialogContent(photo: NetworkPhoto) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
) {
    val viewModel: PhotoViewerViewModel = viewModel()
    var exif by remember { mutableStateOf(ExifData()) }
    var folderName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.id) {
        exif = viewModel.getExifData(photo) ?: ExifData()
        Log.d(
            "NetworkPhotoInfoDialog",
            "Loaded EXIF tags for photo ${photo.id}: ${exif.keys}"
        )
        folderName = photo.folderId?.let { viewModel.getFolderName(it) }
    }

    Text(
        text = photo.name,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = photo.photoDateText(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(16.dp))

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Spacer(Modifier.height(8.dp))

    LocationInfoCard(exif)

    Text(
        text = stringResource(R.string.photo_detail_title_details),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(vertical = 8.dp)
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 1. Format / Extension & Resolution
            val (typeText, typeIcon) = getMediaTypeDetails(photo)
            val resolutionText = getResolutionText(exif)

            DetailItem(
                title = typeText,
                summary = resolutionText,
                icon = typeIcon
            )

            // 2. Camera EXIF Settings (if available)
            val cameraDetails = getCameraDetails(exif)
            if (cameraDetails != null) {
                DetailItem(
                    title = cameraDetails.first,
                    summary = cameraDetails.second.ifEmpty { null },
                    icon = R.drawable.ic_exif_camera
                )
            }

            // 3. Folder & File Size
            val context = LocalContext.current
            val sizeText =
                if (photo.fileSize != 0L) Formatter.formatShortFileSize(context, photo.fileSize) else null
            DetailItem(
                title = folderName ?: stringResource(R.string.photo_detail_no_folder),
                summary = sizeText,
                icon = R.drawable.ic_folder_outlined
            )

            // 4. Visibility Status (Public/Private)
            val visibilityTitle = stringResource(R.string.photo_detail_visibility)
            val (visibilitySummary, visibilityIcon) = if (photo.isPublic) {
                Pair(
                    stringResource(R.string.photo_detail_visibility_public),
                    R.drawable.ic_visibility
                )
            } else {
                Pair(
                    stringResource(R.string.photo_detail_visibility_private),
                    R.drawable.ic_lock_outline
                )
            }

            DetailItem(
                title = visibilityTitle,
                summary = visibilitySummary,
                icon = visibilityIcon
            )
        }
    }
}

@Composable
private fun LocationInfoCard(exifData: ExifData) {
    val (latitude, longitude) = remember(exifData) {
        val latRef = if (exifData[TAG_GPS_LATITUDE_REF] == "S") -1.0 else 1.0
        val longRef = if (exifData[TAG_GPS_LONGITUDE_REF] == "W") -1.0 else 1.0
        Pair(
            exifData[TAG_GPS_LATITUDE]?.let(::parseGpsStringToDouble)?.let { it * latRef },
            exifData[TAG_GPS_LONGITUDE]?.let(::parseGpsStringToDouble)?.let { it * longRef },
        )
    }
    if (longitude == null || latitude == null) {
        return
    }

    val ctx = LocalContext.current

    Text(
        text = stringResource(R.string.photo_detail_title_location),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Card(
        onClick = {
            val gmmIntentUri = "geo:$latitude,$longitude".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            ctx.startActivity(mapIntent)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = "%.4f, %.4f".format(latitude, longitude),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.photo_detail_open_maps),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_visibility),
                    contentDescription = null
                )
            }
        )
    }
}

fun parseGpsStringToDouble(gpsString: String): Double? {
    val parts = gpsString.split(",")
    if (parts.size != 3) return null

    val fractions = parts.map { part ->
        val nums = part.split("/")
        if (nums.size != 2) return null
        val numerator = nums[0].toDouble()
        val denominator = nums[1].toDouble()
        if (denominator == 0.0) return null
        numerator / denominator
    }

    val (degrees, minutes, seconds) = fractions
    return degrees + (minutes / 60) + (seconds / 3600)
}

fun formatExposureTime(exposureTimeStr: String?): String? {
    if (exposureTimeStr.isNullOrBlank()) return null
    if (exposureTimeStr.contains("/")) return exposureTimeStr + "s"
    val value = exposureTimeStr.toDoubleOrNull() ?: return null
    return if (value >= 1.0) {
        "${value.toInt()}s"
    } else {
        val denom = (1.0 / value).roundToInt()
        "1/${denom}s"
    }
}

private fun getMediaTypeDetails(photo: NetworkPhoto): Pair<String, Int> {
    val extension = photo.name.substringAfterLast('.', "").uppercase()
    val typeText = if (photo.isVideo) {
        if (extension.isNotEmpty()) "Video ($extension)" else "Video"
    } else {
        if (extension.isNotEmpty()) "Image ($extension)" else "Image"
    }
    val typeIcon = if (photo.isVideo) R.drawable.ic_play_circle_filled else R.drawable.ic_exif_image
    return Pair(typeText, typeIcon)
}

private fun getResolutionText(exif: ExifData): String? {
    val width = exif.findValue(
        TAG_IMAGE_WIDTH,
        "Width",
        "width",
        "VideoWidth",
        "Video Width",
        "Image Width",
        "ImageWidth"
    )
    val height = exif.findValue(
        TAG_IMAGE_LENGTH,
        "Height",
        "height",
        "VideoHeight",
        "Video Height",
        "Image Height",
        "ImageHeight",
        "ImageLength"
    )
    return if (width != null && height != null) {
        val w = width.toIntOrNull()
        val h = height.toIntOrNull()
        if (w != null && h != null) {
            val mp = (w * h) / 1_000_000.0
            "$w x $h (%.1f MP)".format(mp)
        } else {
            "$width x $height"
        }
    } else {
        null
    }
}

private fun getCameraDetails(exif: ExifData): Pair<String, String>? {
    if (!exif.isNotEmpty) return null

    val cameraName =
        exif[TAG_MODEL].orEmpty().trim('"').trim()
    val lensName = exif[TAG_LENS_MODEL]?.trim('"')?.trim()

    val cameraHeadline = if (cameraName.isNotEmpty() && !lensName.isNullOrBlank()) {
        "$cameraName ($lensName)"
    } else cameraName.ifEmpty {
        lensName.orEmpty()
    }

    val cameraInfoParts = mutableListOf<String>()
    val fNumber = exif[TAG_F_NUMBER]
    if (!fNumber.isNullOrBlank()) cameraInfoParts.add("f/$fNumber")
    val exposureTime = formatExposureTime(exif[TAG_EXPOSURE_TIME])
    if (!exposureTime.isNullOrBlank()) cameraInfoParts.add(exposureTime)
    val focalLength = exif[TAG_FOCAL_LENGTH]
    if (!focalLength.isNullOrBlank()) {
        val fl = focalLength.toDoubleOrNull()?.toInt() ?: focalLength
        cameraInfoParts.add("${fl}mm")
    }
    val iso = exif[TAG_PHOTOGRAPHIC_SENSITIVITY]
    if (!iso.isNullOrBlank()) cameraInfoParts.add("ISO $iso")

    val cameraSettingsSummary = cameraInfoParts.joinToString(" ・ ")

    if (cameraHeadline.isEmpty() && cameraSettingsSummary.isEmpty()) return null

    return Pair(cameraHeadline.ifEmpty { "Camera" }, cameraSettingsSummary)
}

fun ExifData.findValue(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key]
        if (!value.isNullOrBlank()) {
            return value
        }
    }
    val lowercaseKeys = keys.map { it.lowercase() }.toSet()
    for (actualKey in this.keys) {
        if (actualKey.lowercase() in lowercaseKeys) {
            val value = this[actualKey]
            if (!value.isNullOrBlank()) {
                return value
            }
        }
    }
    return null
}


