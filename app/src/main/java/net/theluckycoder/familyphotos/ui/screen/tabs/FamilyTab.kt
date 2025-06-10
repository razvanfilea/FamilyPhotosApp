package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.tab.TabOptions
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.composables.MemoriesList
import net.theluckycoder.familyphotos.ui.composables.PhotoListWithViewer
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object FamilyTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            1.toUShort(),
            stringResource(R.string.section_family),
            painterResource(R.drawable.ic_family_outlined)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_family_filled)

    private val gridState = LazyGridState()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()

        val photos = mainViewModel.publicPhotosPager.collectAsLazyPagingItems()
        PhotoListWithViewer(
            gridState,
            photos,
            headerContent = { Spacer(Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) },
            memoriesContent = {
                MemoriesList(source = mainViewModel::getPublicMemories)
            },
            mainViewModel = mainViewModel,
        )
    }
}