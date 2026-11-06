package net.theluckycoder.familyphotos.notifications

import android.app.Activity
import android.app.Application
import android.os.Bundle
import net.theluckycoder.familyphotos.ui.MainActivity

/**
 * Whether the user is looking at the app's content, i.e. a [MainActivity] is started.
 * ShareActivity doesn't count: it shows no photos or shared-folder activity.
 */
object AppForegroundTracker {

    @Volatile
    var isInForeground = false
        private set

    // Only touched on the main thread; more than one MainActivity can exist (one per task)
    private var startedCount = 0

    fun register(application: Application, onBackground: () -> Unit) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity !is MainActivity) return
                startedCount++
                isInForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity !is MainActivity) return
                startedCount--
                if (startedCount == 0) {
                    isInForeground = false
                    // A recreation isn't leaving the app
                    if (!activity.isChangingConfigurations) onBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
