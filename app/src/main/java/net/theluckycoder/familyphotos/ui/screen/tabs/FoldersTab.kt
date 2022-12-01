package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.PhotoFolder
import net.theluckycoder.familyphotos.ui.screen.FolderFilterTextField

abstract class FoldersTab<T : PhotoFolder> : Screen {

    @Composable
    fun rememberFiltering(
        folders: List<T>,
        sortAscending: Boolean,
    ): List<T> {
        var folderNameFilter by remember { mutableStateOf("") }
        FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

        return remember(folders, folderNameFilter, sortAscending) {
            val searchFilter = folderNameFilter.lowercase()
            val filtered = folders.filter { it.name.lowercase().contains(searchFilter) }
            if (sortAscending) filtered else filtered.sortedByDescending { it.name }
        }
    }

    @Composable
    fun SortButton(sortAscending: Boolean, onClick: () -> Unit) {
        Button(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 8.dp),
            onClick = onClick
        ) {
            Text(
                stringResource(if (sortAscending) R.string.ascending else R.string.descending),
                fontSize = 16.sp
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(
                    if (sortAscending) R.drawable.ic_sort_ascending else R.drawable.ic_sort_descending
                ),
                contentDescription = null
            )
        }
    }
}