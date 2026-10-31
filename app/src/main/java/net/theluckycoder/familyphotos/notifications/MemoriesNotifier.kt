package net.theluckycoder.familyphotos.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.getPreviewUri
import net.theluckycoder.familyphotos.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoriesNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageLoader: Lazy<ImageLoader>,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    val canPost: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked through canPost, which lint can't follow
    suspend fun post(yearsAgo: Int, photoCount: Int, cover: Photo) {
        if (!canPost) return

        val resources = context.resources
        val title = resources.getQuantityString(
            R.plurals.notification_memories_title, yearsAgo, yearsAgo
        )
        val text = resources.getQuantityString(
            R.plurals.notification_memories_text, photoCount, photoCount
        )

        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MEMORY_YEARS_AGO, yearsAgo)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val picture = loadPreview(cover)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_large_photos)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .apply {
                if (picture != null) {
                    setLargeIcon(picture)
                    setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(picture)
                            .bigLargeIcon(null as Bitmap?)
                    )
                }
            }
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_memories),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun cancel() = notificationManager.cancel(NOTIFICATION_ID)

    private suspend fun loadPreview(photo: Photo): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(photo.getPreviewUri())
            // The notification is drawn by the system, which can't use a hardware bitmap
            .allowHardware(false)
            .build()
        (imageLoader.get().execute(request) as? SuccessResult)?.image?.toBitmap()
    } catch (e: Exception) {
        Log.w("MemoriesNotifier", "Failed to load the memory preview", e)
        null
    }

    private companion object {
        const val CHANNEL_ID = "memories"
        const val NOTIFICATION_ID = 7001
        const val REQUEST_CODE = 7001
    }
}
