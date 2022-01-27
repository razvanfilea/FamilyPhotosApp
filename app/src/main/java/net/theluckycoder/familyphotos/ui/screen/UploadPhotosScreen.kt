package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Immutable
private class Choice(val painterRes: Int, val stringRes: Int)

private val choices = listOf(
    Choice(R.drawable.ic_person_filled, R.string.section_personal),
    Choice(R.drawable.ic_family_filled, R.string.section_family),
)

@Parcelize
data class UploadPhotosScreen(
    val photoIds: List<Long>,
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        var choiceIndex by remember { mutableStateOf(0) }
        var folderName by remember { mutableStateOf("") }

        val photosToUpload = remember { mutableStateOf(emptyList<Photo>()) }
        LaunchedEffect(photoIds) {
            photosToUpload.value = photoIds.mapNotNull { mainViewModel.getLocalPhoto(it) }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                        }
                    },
                    title = {
                        Text(stringResource(R.string.action_upload_photos))
                    },
                    actions = {
                        IconButton(onClick = {
                            mainViewModel.uploadPhotosAsync(
                                photoIds,
                                choiceIndex == choices.lastIndex,
                                folderName.trim().takeIf { it.isNotEmpty() })
                            navigator.pop()
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_action_done),
                                tint = Color.White,
                                contentDescription = null
                            )
                        }
                    },
                    elevation = 0.dp,
                    backgroundColor = Color.Transparent,
                )
            },
        ) {
            val foldersList by mainViewModel.networkFolders.collectAsState(emptyList())

            val filteredFoldersList = remember(foldersList, choiceIndex, folderName) {
                foldersList.asSequence()
                    .filter { it.isPublic == (choiceIndex == choices.lastIndex) }
                    .map { it.name }
                    .filter { it.startsWith(folderName, ignoreCase = true) }
                    .toList()
            }

            UploadDialogContent(
                photosToUpload = photosToUpload.value,
                choiceIndex = choiceIndex,
                onChoiceIndexChange = { choiceIndex = it },
                folderName = folderName,
                onFolderNameChange = { folderName = it },
                foldersList = filteredFoldersList
            )
        }
    }

    @Composable
    private fun UploadDialogContent(
        photosToUpload: List<Photo>,
        choiceIndex: Int,
        onChoiceIndexChange: (Int) -> Unit,
        folderName: String,
        onFolderNameChange: (String) -> Unit,
        foldersList: List<String>
    ) = Column {
        LazyRow(Modifier.fillMaxWidth()) {
            items(photosToUpload) { photo ->
                Box(Modifier.size(72.dp)) {
                    SimpleSquarePhotoItem(photo)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            choices.forEachIndexed { index, choice ->
                val backgroundColor = if (choiceIndex == index)
                    MaterialTheme.colors.primary
                else
                    Color.DarkGray

                IconToggleButton(
                    modifier = Modifier
                        .background(backgroundColor)
                        .weight(1f)
                        .padding(4.dp),
                    checked = choiceIndex == index,
                    onCheckedChange = { onChoiceIndexChange(index) }
                ) {
                    Icon(
                        modifier = Modifier.size(54.dp),
                        painter = painterResource(choice.painterRes),
                        tint = Color.Unspecified,
                        contentDescription = stringResource(choice.stringRes),
                    )
                }
            }
        }

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            value = folderName,
            onValueChange = onFolderNameChange,
            label = { Text(stringResource(R.string.folder_name)) },
            trailingIcon = {
                AnimatedVisibility(
                    folderName.isNotEmpty(),
                    enter = fadeIn() + expandIn(),
                    exit = fadeOut() + shrinkOut(),
                ) {
                    IconButton(onClick = { onFolderNameChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                    }
                }
            }
        )

        LazyColumn(Modifier.fillMaxSize()) {
            items(foldersList) {
                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderNameChange(it) }
                        .padding(12.dp)
                )
            }
        }
    }
}