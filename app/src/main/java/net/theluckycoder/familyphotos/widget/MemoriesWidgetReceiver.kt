package net.theluckycoder.familyphotos.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.WorkManager
import net.theluckycoder.familyphotos.workers.cancelMemoriesWidgetWorker
import net.theluckycoder.familyphotos.workers.enqueueMemoriesWidgetWorker

/** One worker refreshes the widgets of all types. */
abstract class MemoriesWidgetReceiver : GlanceAppWidgetReceiver() {

    /** The first widget of this type was added. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkManager.getInstance(context).enqueueMemoriesWidgetWorker()
    }

    /** The last widget of this type was removed: the worker may still be needed by the other type. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!MemoriesWidget.hasAny(context)) {
            WorkManager.getInstance(context).cancelMemoriesWidgetWorker()
        }
    }
}

class PersonalMemoriesWidgetReceiver : MemoriesWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PersonalMemoriesWidget()
}

class FamilyMemoriesWidgetReceiver : MemoriesWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FamilyMemoriesWidget()
}
