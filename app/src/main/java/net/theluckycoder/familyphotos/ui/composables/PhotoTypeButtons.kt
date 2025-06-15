package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun PhotoTypeSegmentedButtons(
    selectedPhotoType: PhotoType,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    SingleChoiceSegmentedButtonRow(
        modifier
    ) {
        PhotoType.entries.forEach { type ->
            val selected = selectedPhotoType == type
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = type.index,
                    count = PhotoType.entries.size
                ),
                onClick = {
                    scope.launch {
                        mainViewModel.settingsStore.setFolderFilterType(
                            type
                        )
                    }
                },
                selected = selected,
                icon = {
                    val res = if (selected) {
                        when (type) {
                            PhotoType.All -> R.drawable.ic_photo_filled
                            PhotoType.Personal -> R.drawable.ic_person_filled
                            PhotoType.Family -> R.drawable.ic_family_filled
                        }
                    } else {
                        when (type) {
                            PhotoType.All -> R.drawable.ic_photo_outline
                            PhotoType.Personal -> R.drawable.ic_person_outline
                            PhotoType.Family -> R.drawable.ic_family_outline
                        }
                    }
                    Icon(painterResource(res), contentDescription = null)
                }
            ) {
                val res = when (type) {
                    PhotoType.All -> R.string.photo_type_all
                    PhotoType.Personal -> R.string.photo_type_personal
                    PhotoType.Family -> R.string.photo_type_family
                }
                Text(stringResource(res))
            }
        }
    }
}

