package net.theluckycoder.familyphotos.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Immutable
private class Choice(val painterRes: Int, val stringRes: Int)

private val choices = listOf(
    Choice(R.drawable.ic_person_filled, R.string.section_personal),
    Choice(R.drawable.ic_family_filled, R.string.section_family),
)

@Composable
fun UploadDialogContent(
    modifier: Modifier,
    title: String,
    choiceIndex: Int,
    onChoiceIndexChange: (Int) -> Unit,
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    foldersList: List<String>
) = Column(modifier) {



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
        label = { Text(text = "Folder Name") }
    )

    LazyColumn {
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

// TODO UploadPhotosScreen
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MoveDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (uploadToPublic: Boolean, uploadFolder: String?) -> Unit,
    mainViewModel: MainViewModel = viewModel(),
) {
    var choiceIndex by remember { mutableStateOf(0) }
    var folderName by remember { mutableStateOf("") }

    CustomDialog(
        onDismissRequest = onDismissRequest,
        buttons = {
            UploadDialogButtons(onDismissRequest, onConfirm, choiceIndex, folderName)
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
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.action_move_photos),
            choiceIndex = choiceIndex,
            onChoiceIndexChange = { choiceIndex = it },
            folderName = folderName,
            onFolderNameChange = { folderName = it },
            foldersList = filteredFoldersList
        )
    }
}

@Composable
private fun RowScope.UploadDialogButtons(
    onDismissRequest: () -> Unit,
    onConfirm: (uploadToPublic: Boolean, uploadFolder: String?) -> Unit,
    choiceIndex: Int,
    folderName: String
) {
    TextButton(
        modifier = Modifier
            .weight(1f)
            .padding(end = 4.dp),
        onClick = onDismissRequest
    ) {
        Text(text = stringResource(android.R.string.cancel))
    }

    Button(
        modifier = Modifier
            .weight(1f)
            .padding(start = 4.dp),
        onClick = {
            onConfirm(
                choiceIndex == choices.lastIndex,
                folderName.trim().takeIf { it.isNotEmpty() }
            )
        }
    ) {
        Text(text = stringResource(android.R.string.ok))
    }
}
