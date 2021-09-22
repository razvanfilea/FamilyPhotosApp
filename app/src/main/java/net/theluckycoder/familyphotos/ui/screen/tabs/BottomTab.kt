package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import cafe.adriel.voyager.navigator.tab.Tab

interface BottomTab : Tab {

    val selectedIcon: Painter
        @Composable get
}
