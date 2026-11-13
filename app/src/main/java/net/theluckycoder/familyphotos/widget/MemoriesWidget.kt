package net.theluckycoder.familyphotos.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.maxBitmapSize
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Memory
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.getPreviewUri
import net.theluckycoder.familyphotos.core.data.model.getScreenUri
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.core.data.model.mostMemorable
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.MainActivity
import net.theluckycoder.familyphotos.ui.composables.memoryDateText
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

/**
 * One photo of today's memories of [photoType], a different one every [REFRESH_HOURS] (see
 * [MemoriesWidgetWorker]). Tapping it opens the memory's story.
 *
 * One widget per type rather than following the timeline's filter: what's on the home screen
 * mustn't change with the last filter picked in the app.
 */
abstract class MemoriesWidget(
    private val photoType: PhotoType,
    @param:StringRes private val emptyText: Int,
) : GlanceAppWidget() {

    // Hides the date on short widgets
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val slide = loadSlide(context)

        provideContent {
            GlanceTheme {
                if (slide != null) MemoryContent(slide) else EmptyContent()
            }
        }
    }

    private suspend fun loadSlide(context: Context): Slide? {
        val entryPoint = EntryPointAccessors.fromApplication<MemoriesWidgetEntryPoint>(context)
        // Only those the timeline shows, so that tapping the widget can open the story
        val memories = entryPoint.photosRepository().getMemoriesNow(photoType).mostMemorable()

        val (memory, photo) = pickPhoto(memories, LocalDateTime.now()) ?: return null
        // Glance gives provideGlance a limited time: on a slow network, show the memory without its photo
        val bitmap = withTimeoutOrNull(BITMAP_TIMEOUT) { loadBitmap(context, entryPoint.imageLoader(), photo) }
        return Slide(memory.yearsAgo, memory.date, bitmap)
    }

    private suspend fun loadBitmap(context: Context, imageLoader: ImageLoader, photo: NetworkPhotoThumbnail): Bitmap? {
        // Previews are too small for a large widget, but the screen size doesn't exist for videos
        val uris = if (photo.isVideo) listOf(photo.getPreviewUri()) else listOf(photo.getScreenUri(), photo.getPreviewUri())
        for (uri in uris) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    // The widget is drawn by the launcher, which can't use a hardware bitmap
                    .allowHardware(false)
                    // RemoteViews limit the total size of their bitmaps: FILL makes the shorter side
                    // IMAGE_SIZE_PX, so cap the longer one too (panoramas) or the update is rejected
                    .size(IMAGE_SIZE_PX)
                    .scale(Scale.FILL)
                    .maxBitmapSize(Size(IMAGE_MAX_SIZE_PX, IMAGE_MAX_SIZE_PX))
                    .build()
                val result = imageLoader.execute(request) as? SuccessResult ?: continue
                return result.image.toBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MemoriesWidget", "Failed to load the memory photo", e)
            }
        }
        return null
    }

    private class Slide(
        val yearsAgo: Int,
        val date: LocalDate,
        val bitmap: Bitmap?,
    )

    companion object {
        const val REFRESH_HOURS = 2
        private const val IMAGE_SIZE_PX = 800
        private const val IMAGE_MAX_SIZE_PX = 1200
        private val BITMAP_TIMEOUT = 25.seconds

        private fun all(): List<MemoriesWidget> = listOf(PersonalMemoriesWidget(), FamilyMemoriesWidget())

        /** Whether any memories widget, of any type, is on the home screen. */
        fun hasAny(context: Context): Boolean {
            // Null on devices without app widgets (TV, Automotive)
            val manager = AppWidgetManager.getInstance(context) ?: return false
            return listOf(PersonalMemoriesWidgetReceiver::class.java, FamilyMemoriesWidgetReceiver::class.java)
                .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        }

        /** Redraws the memories widgets of all types. */
        suspend fun updateAllTypes(context: Context) {
            if (!hasAny(context)) return
            all().forEach { it.updateAll(context) }
        }

        /**
         * Covers first, so each day starts with the best photo of each memory, then the rest of the
         * highlights. The same for the whole [REFRESH_HOURS] slot, so that redraws (a resize, a
         * reboot) don't change it.
         */
        internal fun pickPhoto(memories: List<Memory>, now: LocalDateTime): Pair<Memory, NetworkPhotoThumbnail>? {
            val photos = memories.map { it to it.cover } +
                memories.flatMap { memory -> memory.highlights.filter { it != memory.cover }.map { memory to it } }
            if (photos.isEmpty()) return null
            return photos[now.hour / REFRESH_HOURS % photos.size]
        }
    }

    @Composable
    private fun MemoryContent(slide: Slide) {
        val context = LocalContext.current
        val openStory = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MEMORY_YEARS_AGO, slide.yearsAgo)
            .putExtra(MainActivity.EXTRA_OPEN_MEMORY_PHOTO_TYPE, photoType.index)

        Box(
            modifier = widgetModifier()
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(actionStartActivity(openStory)),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (slide.bitmap != null) {
                Image(
                    provider = ImageProvider(slide.bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_memory_scrim))
                    .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp)
            ) {
                Text(
                    context.resources.getQuantityString(R.plurals.years_ago, slide.yearsAgo, slide.yearsAgo),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                if (LocalSize.current.height >= 100.dp) {
                    Text(
                        memoryDateText(slide.date),
                        style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.8f)), fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyContent() {
        val context = LocalContext.current

        Column(
            modifier = widgetModifier()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_large_photos),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                context.getString(emptyText),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            if (LocalSize.current.height >= 140.dp) {
                Text(
                    context.getString(R.string.widget_memories_empty_summary),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }

    private fun widgetModifier(): GlanceModifier {
        val modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else modifier
    }
}

class PersonalMemoriesWidget : MemoriesWidget(PhotoType.Personal, R.string.widget_memories_personal_empty)

class FamilyMemoriesWidget : MemoriesWidget(PhotoType.Family, R.string.widget_memories_family_empty)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MemoriesWidgetEntryPoint {
    fun photosRepository(): PhotosRepository
    fun imageLoader(): ImageLoader
}
