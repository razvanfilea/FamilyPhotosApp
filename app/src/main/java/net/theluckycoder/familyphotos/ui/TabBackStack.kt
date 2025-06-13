package net.theluckycoder.familyphotos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class TabBackStack(startKey: TopLevelRouteNav) {

    // Maintain a stack for each top level route
    private var topLevelStacks: LinkedHashMap<TopLevelRouteNav, SnapshotStateList<NavKey>> =
        linkedMapOf(
            startKey to mutableStateListOf(startKey)
        )

    // Expose the current top level route for consumers
    var topLevelKey by mutableStateOf(startKey)
        private set

    // Expose the back stack so it can be rendered by the NavDisplay
    val backStack: NavBackStack = mutableStateListOf(startKey)

    fun addTopLevel(key: TopLevelRouteNav) {
        val topLevelStack = topLevelStacks.getOrPut(key) { mutableStateListOf(key) }

        topLevelKey = key

        backStack.clear()
        backStack.addAll(topLevelStack)
    }

    fun add(key: NavKey) {
        topLevelStacks[topLevelKey]?.add(key)
    }

    fun removeLast() = backStack.removeLastOrNull()
}