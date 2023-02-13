package net.theluckycoder.familyphotos.ui.dialog

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface.*
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.ExifData
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.composables.photoDateText
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Parcelize
data class NetworkPhotoInfoDialog(
    val photo: NetworkPhoto
) : Screen, Parcelable {

    @Composable
    private fun DetailItem(title: String, summary: String?, @DrawableRes icon: Int) {
        ListItem(
            headlineText = { Text(title) },
            supportingText = if (summary != null) {
                { Text(summary) }
            } else null,
            leadingContent = {
                Icon(painterResource(icon), contentDescription = null)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() = Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        val viewModel: MainViewModel = viewModel()
        var exif by remember { mutableStateOf(ExifData()) }

        LaunchedEffect(Unit) {
            exif = viewModel.getExifData(photo) ?: ExifData()
        }

        Text(photo.photoDateText(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

        var caption by remember { mutableStateOf(photo.caption.orEmpty()) }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Caption") },
        )

        Text("Details", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(8.dp))

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

        if (photo.folder != null)
            DetailItem(photo.folder, null, R.drawable.ic_folder_outlined)

        Spacer(Modifier.height(12.dp))
    }
}