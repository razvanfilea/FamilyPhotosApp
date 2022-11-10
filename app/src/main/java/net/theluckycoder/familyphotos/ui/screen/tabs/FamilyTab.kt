package net.theluckycoder.familyphotos.ui.screen.tabs

import android.os.Parcelable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
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

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content() {
        Navigator(FamilyScreen()) {
            PhotosSlideTransition(navigator = it)
        }
    }

    @Parcelize
    private class FamilyScreen(
        var initialPhotoId: Long = 0L
    ) : Screen, Parcelable {

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
                memoriesContent = {
                    if (memoriesList.isNotEmpty()) {
                        MemoriesList(
                            memoriesList = memoriesList
                        )
                    }
                },
                photosPagingList = mainViewModel.publicPhotosPager,
                mainViewModel = mainViewModel,
                initialPhotoId = initialPhotoId,
                onSaveInitialPhotoId = { it?.let { initialPhotoId = it } },
            )
        }
    }
}