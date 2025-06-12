package net.theluckycoder.familyphotos.ui.dialog

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH
import androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER
import androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH
import androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH
import androidx.exifinterface.media.ExifInterface.TAG_MAKE
import androidx.exifinterface.media.ExifInterface.TAG_MODEL
import androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.ExifData
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.composables.photoDateText
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewModel
import java.text.DecimalFormat

fun interface NetworkPhotoInfoDialogCaller {
    fun show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNetworkPhotoInfoDialog(photo: NetworkPhoto?): NetworkPhotoInfoDialogCaller {
    var visible by remember { mutableStateOf(false) }

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
        .safeDrawingPadding()
        .padding(16.dp)
) {
    val viewModel: PhotoViewModel = viewModel()
    var exif by remember { mutableStateOf(ExifData()) }

    LaunchedEffect(Unit) {
        exif = viewModel.getExifData(photo) ?: ExifData()
    }

    Text(photo.photoDateText(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    Text("Details", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    Spacer(Modifier.height(4.dp))

    DetailItem(
        photo.name,
        "${exif[TAG_IMAGE_WIDTH]} x ${exif[TAG_IMAGE_LENGTH]}".takeIf { exif.isNotEmpty },
        R.drawable.ic_exif_image
    )

    if (exif.isNotEmpty) {
        DetailItem(
            "${exif[TAG_MAKE].orEmpty().trim('"')} ${exif[TAG_MODEL].orEmpty().trim('"')}",
            "f${exif[TAG_F_NUMBER]}・${exif[TAG_FOCAL_LENGTH]}mm・ISO${exif[TAG_PHOTOGRAPHIC_SENSITIVITY]}",
            R.drawable.ic_exif_camera
        )
    }

    DetailItem(
        photo.folder ?: "No folder",
        if (photo.fileSize != 0L) getStringSizeLengthFile(photo.fileSize) else null,
        R.drawable.ic_folder_outlined
    )
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

