package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.isPublic
import net.theluckycoder.familyphotos.ui.LocalNavBackStack

@Immutable
enum class UploadChoice(internal val stringRes: Int) {
    Personal(R.string.photo_type_personal),
    Public(R.string.photo_type_family)
}

@Composable
private fun UploadDialogContent(
    photosToShowcase: List<Photo>,
    selectedChoice: UploadChoice,
    onChoiceChange: (UploadChoice) -> Unit,
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    foldersList: List<NetworkFolder>
) = FoldersGridList(
    folders = foldersList,
    onFolderClick = { onFolderNameChange(it.name) },
    folderDetailsText = { folder ->
        pluralStringResource(R.plurals.items_photos, folder.count, folder.count)
    },
    folderNameFilter = folderName,
    onSearch = onFolderNameChange,
    extraHeader = {
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.5.dp)) {
            items(photosToShowcase) { photo ->
                CoilPhoto(
                    photo = photo,
                    modifier = Modifier.size(72.dp),
                    preview = true,
                    contentScale = ContentScale.Crop,
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            UploadChoice.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = UploadChoice.entries.size
                    ),
                    selected = selectedChoice == choice,
                    onClick = { onChoiceChange(choice) }
                ) {
                    Text(stringResource(choice.stringRes))
                }
            }
        }

    }
)

@Composable
fun UploadPhotosLayout(
    networkFolders: List<NetworkFolder>,
    actionName: String,
    photosToShowcase: List<Photo>,
    doneAction: (choice: UploadChoice, folderName: String) -> Unit,
) {
    val backStack = LocalNavBackStack.current

    var choice by remember { mutableStateOf(UploadChoice.Personal) }
    var folderName by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
            ) {
                TextButton(onClick = { backStack.removeLastOrNull() }) {
                    Text(stringResource(R.string.action_cancel))
                }

                Button(onClick = { doneAction(choice, folderName) }) {
                    Icon(
                        painterResource(R.drawable.ic_action_done),
                        contentDescription = actionName
                    )

                    Text(actionName)
                }
            }
        }
    ) { contentPadding ->
        val filteredFoldersList = remember(networkFolders, choice) {
            networkFolders.filter { it.isPublic == (choice == UploadChoice.Public) }
        }

        Box(Modifier.padding(bottom = contentPadding.calculateBottomPadding())) {
            UploadDialogContent(
                photosToShowcase = photosToShowcase,
                selectedChoice = choice,
                onChoiceChange = { choice = it },
                folderName = folderName,
                onFolderNameChange = { folderName = it },
                foldersList = filteredFoldersList
            )
        }
    }
}
