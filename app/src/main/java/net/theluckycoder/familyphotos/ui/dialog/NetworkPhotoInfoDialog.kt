package net.theluckycoder.familyphotos.ui.dialog

import android.os.Parcelable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.composables.photoDateText

@Parcelize
data class NetworkPhotoInfoDialog(
    val photo: NetworkPhoto
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text("Name: ${photo.name}", fontSize = 18.sp)

            Spacer(Modifier.height(8.dp))

            Text("Folder: ${photo.folder}", fontSize = 18.sp)

            Spacer(Modifier.height(8.dp))

            Text("Time: ${photo.photoDateText()}", fontSize = 18.sp)
        }
    }
}