package net.theluckycoder.familyphotos.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.SharedActivityStore
import net.theluckycoder.familyphotos.core.data.model.sharing.PendingFolderActivity
import net.theluckycoder.familyphotos.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedFolderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sharedActivityStore: SharedActivityStore,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    val canPost: Boolean
        get() = context.canPostNotifications()

    @SuppressLint("MissingPermission") // checked through canPost, which lint can't follow
    fun post(
        activity: PendingFolderActivity,
        folderName: String,
        memberNames: Map<String, String>
    ) {
        if (!canPost) return

        val text = buildText(activity, folderName, memberNames)

        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FOLDER_ID, activity.folderId)
            .putExtra(MainActivity.EXTRA_OPEN_FOLDER_NAME, folderName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            activity.folderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(text)
            .setTicker(text)
            .setSmallIcon(R.drawable.ic_folder_outlined)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(TAG, activity.folderId.hashCode(), notification)
    }

    suspend fun dismiss(folderId: Long) {
        cancel(folderId)
        sharedActivityStore.dismiss(folderId)
    }

    fun cancel(folderId: Long) = notificationManager.cancel(TAG, folderId.hashCode())

    fun cancelAll() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag == TAG }.forEach { manager.cancel(it.tag, it.id) }
    }

    private fun buildText(
        activity: PendingFolderActivity,
        folderName: String,
        memberNames: Map<String, String>,
    ): String {
        val resources = context.resources

        if (activity.isNewlyShared) {
            val owner = activity.ownerId?.let { memberNames[it] }
            return if (owner != null) {
                context.getString(R.string.notification_shared_folder_new, owner, folderName)
            } else {
                context.getString(R.string.notification_shared_folder_new_unknown, folderName)
            }
        }

        // The server doesn't record who uploaded a photo
        val count = activity.addedCount
        return resources.getQuantityString(
            R.plurals.notification_shared_folder_added_unknown, count, count, folderName
        )
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_shared_folders),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "shared_folders"
        const val TAG = "shared_folder"
    }
}
