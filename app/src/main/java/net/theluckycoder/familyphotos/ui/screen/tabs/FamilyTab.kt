package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.screen.MemoriesList
import net.theluckycoder.familyphotos.ui.screen.PhotosList
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

    @Composable
    override fun Content() = Navigator(FamilyScreen)

    private object FamilyScreen : Screen {

        @Composable
        override fun Content() {
            val mainViewModel: MainViewModel = viewModel()

            SideEffect {
                mainViewModel.showBottomAppBar.value = true
            }

            val memoriesList = remember { mutableStateListOf<Pair<Int, List<NetworkPhoto>>>() }

            LaunchedEffect(Unit) {
                memoriesList.addAll(mainViewModel.getPublicMemories())
            }

            PhotosList(
                headerContent = {
                    if (memoriesList.isNotEmpty()) {
                        MemoriesList(
                            memoriesList = memoriesList
                        )
                    }
                },
                photosPagingList = mainViewModel.publicPhotosPaging,
                mainViewModel = mainViewModel
            )
        }
    }
}