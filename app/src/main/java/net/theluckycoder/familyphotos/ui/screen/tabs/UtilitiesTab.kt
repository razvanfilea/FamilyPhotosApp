package net.theluckycoder.familyphotos.ui.screen.tabs

import android.R.attr.onClick
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.DuplicatesNav
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.SettingsNav

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesTab() = Column(
    modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
) {
    val backStack = LocalNavBackStack.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            UtilityButton(
                iconId = R.drawable.ic_star_outline,
                text = stringResource(R.string.title_favorites),
                onClick = { backStack.add(FolderNav(FolderNav.Source.Favorites)) },
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_duplicates_outlined,
                text = stringResource(R.string.title_duplicates),
                onClick = { backStack.add(DuplicatesNav) },
            )
        }

        item {
            UtilityButton(
                iconId = R.drawable.ic_settings_outline,
                text = stringResource(R.string.title_settings),
                onClick = { backStack.add(SettingsNav) },
            )
        }

    }
}

@Composable
private fun UtilityButton(
    @DrawableRes iconId: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier.height(52.dp),
        onClick = onClick,
    ) {
        Icon(
            painterResource(iconId),
            contentDescription = null
        )

        Spacer(Modifier.width(12.dp))

        Text(text)
    }
}