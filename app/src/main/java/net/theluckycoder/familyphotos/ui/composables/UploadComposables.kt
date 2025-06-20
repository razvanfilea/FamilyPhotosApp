package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.NetworkFolder
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.data.model.isPublic
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.utils.normalize

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
    foldersList: List<String>
) = Column {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.5.dp)) {
        items(photosToShowcase) { photo ->
            Box(Modifier.size(72.dp)) {
                CoilPhoto(
                    photo = photo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    preview = true,
                    contentScale = ContentScale.Crop,
                )
            }
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

    FolderFilterTextField(folderName, onFolderNameChange)

    LazyColumn(Modifier.fillMaxSize()) {
        items(foldersList) {
            Column {
                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderNameChange(it) }
                        .padding(12.dp)
                )

                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPhotosLayout(
    networkFolders: List<NetworkFolder>,
    title: String,
    photosToShowcase: List<Photo>,
    doneAction: (choice: UploadChoice, folderName: String) -> Unit,
) {
    val backStack = LocalNavBackStack.current

    var choice by remember { mutableStateOf(UploadChoice.Personal) }
    var folderName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                    }
                },
                title = { Text(title) },
                actions = {
                    IconButton(onClick = {
                        doneAction(choice, folderName)
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_action_done),
                            tint = Color.White,
                            contentDescription = null
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        val filteredFoldersList = remember(networkFolders, choice, folderName) {
            val normalizedName = folderName.normalize()
            networkFolders.asSequence()
                .filter { it.isPublic() == (choice == UploadChoice.Public) }
                .map { it.name }
                .filter { it.normalize().contains(normalizedName, ignoreCase = true) }
                .toList()
        }

        Box(Modifier.padding(contentPadding)) {
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
