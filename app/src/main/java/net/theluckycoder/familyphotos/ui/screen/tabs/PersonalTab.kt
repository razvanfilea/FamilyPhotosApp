package net.theluckycoder.familyphotos.ui.screen.tabs

import android.os.Parcelable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.TIME_ZONE
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
import net.theluckycoder.familyphotos.ui.screen.MemoriesList
import net.theluckycoder.familyphotos.ui.screen.PhotosList
import net.theluckycoder.familyphotos.ui.screen.SettingsScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object PersonalTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            0.toUShort(),
            stringResource(R.string.section_personal),
            painterResource(R.drawable.ic_person_outline)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_person_filled)

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content() {
        Navigator(PersonalScreen()) {
            PhotosSlideTransition(navigator = it)
        }
    }

    @Parcelize
    private class PersonalScreen(
        var initialPhotoId: Long = 0
    ) : Screen, Parcelable {

        @Composable
        override fun Content() {
            val mainViewModel: MainViewModel = viewModel()

            SideEffect {
                mainViewModel.showBottomAppBar.value = true
            }

            val isOnline by mainViewModel.isOnline.collectAsState()
            val displayName by mainViewModel.displayNameFlow.collectAsState(null)
            val memoriesList = remember { mutableStateListOf<Pair<Int, List<NetworkPhoto>>>() }

            LaunchedEffect(Unit) {
                memoriesList.addAll(mainViewModel.getPersonalMemories())
            }

            PhotosList(
                headerContent = {
                    Column {
                        Header(isOnline, displayName)

                        Spacer(Modifier.size(8.dp))

                        if (memoriesList.isNotEmpty()) {
                            MemoriesList(
                                memoriesList = memoriesList
                            )
                        }
                    }
                },
                photosPagingList = mainViewModel.personalPhotosPager,
                mainViewModel = mainViewModel,
                initialPhotoId = initialPhotoId,
                onSaveInitialPhotoId = { it?.let { initialPhotoId = it } }
            )
        }

        @Composable
        private fun Header(isOnline: Boolean, displayName: String?) {
            val navigator = LocalNavigator.currentOrThrow

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerStatusIcon(online = isOnline)

                if (!displayName.isNullOrEmpty()) {
                    val str = remember {
                        val localDateTime = Clock.System.now().toLocalDateTime(TIME_ZONE)
                        val hour = localDateTime.hour
                        when {
                            hour in 6..10 -> R.string.message_morning
                            hour < 6 || hour > 22 -> R.string.message_night
                            else -> R.string.message_normal
                        }
                    }

                    val resources = LocalContext.current.resources
                    val message = resources.getString(str, displayName)

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 16.dp, horizontal = 48.dp),
                        text = message,
                        textAlign = TextAlign.Center,
                        fontSize = 24.sp
                    )
                }

                IconButton(
                    onClick = { navigator.push(SettingsScreen()) }
                ) {
                    Icon(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(8.dp),
                        painter = painterResource(R.drawable.ic_settings_outline),
                        contentDescription = null
                    )
                }
            }
        }

        @Composable
        private fun ServerStatusIcon(modifier: Modifier = Modifier, online: Boolean) {
            val res = if (online)
                R.drawable.ic_cloud_done_outline
            else
                R.drawable.ic_cloud_off_outline

            Icon(
                modifier = modifier
                    .size(48.dp)
                    .padding(8.dp),
                painter = painterResource(res), contentDescription = null
            )
        }
    }
}
