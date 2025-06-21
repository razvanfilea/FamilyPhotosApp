package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.Photo

@Composable
fun FolderFilterTextField(folderNameFilter: String, onFilterChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        value = folderNameFilter,
        onValueChange = onFilterChange,
        label = { Text(stringResource(R.string.folder_name)) },
        singleLine = true,
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
        },
        trailingIcon = {
            AnimatedVisibility(
                folderNameFilter.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = { onFilterChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }
        }
    )
}

@Composable
fun SortButton(sortAscending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Icon(
            painterResource(R.drawable.ic_sort_ascending),
            contentDescription = null
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(if (sortAscending) R.string.ascending else R.string.descending),
            fontSize = 14.sp
        )
    }
}

@Composable
fun FolderPreviewItem(
    modifier: Modifier = Modifier,
    photo: Photo,
    name: String,
    photosCount: Int,
    onClick: () -> Unit,
    content: @Composable (BoxScope.() -> Unit)? = null
) = Column(
    modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp)
) {
    Box(
        Modifier
            .photoSharedBounds(photo.id)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        CoilPhoto(
            photo = photo,
            modifier = Modifier.fillMaxSize(),
            preview = true,
            contentScale = ContentScale.Crop,
        )

        if (content != null)
            content()
    }

    Text(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        text = name,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        text = "$photosCount items",
        fontSize = 14.sp,
        fontWeight = FontWeight.Light
    )
}
