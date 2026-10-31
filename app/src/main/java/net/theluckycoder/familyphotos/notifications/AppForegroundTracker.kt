package net.theluckycoder.familyphotos.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

object AppForegroundTracker {

    @Volatile
    var isInForeground = false
        private set

    fun register(onBackground: () -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isInForeground = true
            }

            override fun onStop(owner: LifecycleOwner) {
                isInForeground = false
                onBackground()
            }
        })
    }
}
