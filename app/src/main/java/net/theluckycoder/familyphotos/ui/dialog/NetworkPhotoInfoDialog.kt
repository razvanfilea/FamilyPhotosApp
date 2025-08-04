package net.theluckycoder.familyphotos.ui.dialog

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH
import androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE
import androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF
import androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH
import androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH
import androidx.exifinterface.media.ExifInterface.TAG_MAKE
import androidx.exifinterface.media.ExifInterface.TAG_MODEL
import androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.ExifData
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.ui.composables.photoDateText
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel
import java.text.DecimalFormat

fun interface NetworkPhotoInfoDialogCaller {
    fun show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNetworkPhotoInfoDialog(photo: NetworkPhoto?): NetworkPhotoInfoDialogCaller {
    var visible by remember(photo?.id) { mutableStateOf(false) }

    if (visible && photo != null) {
        ModalBottomSheet(onDismissRequest = { visible = false }) {
            NetworkPhotoInfoDialogContent(photo)
        }
    }

    return NetworkPhotoInfoDialogCaller {
        visible = true
    }
}

@Composable
private fun DetailItem(title: String, summary: String?, @DrawableRes icon: Int) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else null,
        leadingContent = {
            Icon(painterResource(icon), contentDescription = null)
        }
    )
}

@Composable
fun NetworkPhotoInfoDialogContent(photo: NetworkPhoto) = Column(
    Modifier
        .fillMaxWidth()
        .padding(16.dp)
) {
    val viewModel: PhotoViewerViewModel = viewModel()
    var exif by remember { mutableStateOf(ExifData()) }

    LaunchedEffect(Unit) {
        exif = viewModel.getExifData(photo) ?: ExifData()
    }

    Text(photo.photoDateText(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    Spacer(Modifier.height(12.dp))

    LocationInfo(exif)

    Text(
        stringResource(R.string.photo_detail_title_details),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(Modifier.height(4.dp))

    val width = exif[TAG_IMAGE_WIDTH]
    val height = exif[TAG_IMAGE_LENGTH]
    DetailItem(
        photo.name,
        if (width != null && height != null) "$width x $height" else null,
        R.drawable.ic_exif_image
    )

    if (exif.isNotEmpty) {
        DetailItem(
            "${exif[TAG_MAKE].orEmpty().trim('"')} ${exif[TAG_MODEL].orEmpty().trim('"')}",
            "f${exif[TAG_F_NUMBER].orEmpty()}・${exif[TAG_FOCAL_LENGTH]}mm・ISO${exif[TAG_PHOTOGRAPHIC_SENSITIVITY]}",
            R.drawable.ic_exif_camera
        )
    }

    DetailItem(
        photo.folder ?: stringResource(R.string.photo_detail_no_folder),
        if (photo.fileSize != 0L) getStringSizeLengthFile(photo.fileSize) else null,
        R.drawable.ic_folder_outlined
    )
}

@Composable
private fun LocationInfo(exifData: ExifData) {
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.photo_detail_title_location),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        val ctx = LocalContext.current
        TextButton(onClick = {
            val gmmIntentUri = "geo:$latitude,$longitude".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            ctx.startActivity(mapIntent)
        }) {
            Text(stringResource(R.string.photo_detail_open_maps))
        }
    }

    Text(
        "%.4f, %.4f".format(latitude, longitude),
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
    )
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


private const val sizeKb = 1024.0f
private const val sizeMb = sizeKb * sizeKb
private const val sizeGb = sizeMb * sizeKb
private const val sizeTerra = sizeGb * sizeKb

private fun getStringSizeLengthFile(size: Long): String {
    val df = DecimalFormat("0.00")

    return when {
        size < sizeMb -> df.format(size / sizeKb) + " KB"
        size < sizeGb -> df.format(size / sizeMb) + " MB"
        size < sizeTerra -> df.format(size / sizeGb) + " GB"
        else -> ""
    }
}

