package net.theluckycoder.familyphotos.ui.composables

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.LocalAnimatedVisibilityScope
import net.theluckycoder.familyphotos.ui.screen.PhotoScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

