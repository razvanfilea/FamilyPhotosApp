package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.getPreviewUri
import net.theluckycoder.familyphotos.data.model.db.getUri
import net.theluckycoder.familyphotos.data.model.db.thumbHash
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.utils.ScaledBitmapPainter
import net.theluckycoder.familyphotos.utils.ThumbHashCache
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val LOADING_PAINTER = ColorPainter(Color.DarkGray)

@Composable
fun CoilPhoto(
    photo: Photo,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val isImageLoaded = remember { mutableStateOf(false) }
    var targetSizePx by remember { mutableIntStateOf(0) }

    // Check synchronous cache first to avoid coroutine overhead for cached items
    var thumbHashPainter by remember(photo.thumbHash) {
        mutableStateOf(ThumbHashCache.get(photo.thumbHash)?.let { ScaledBitmapPainter(it) })
    }
    // Only launch coroutine for cache misses
    if (thumbHashPainter == null && photo.thumbHash != null) {
        LaunchedEffect(photo.thumbHash) {
            ThumbHashCache.getOrCompute(photo.thumbHash)?.let {
                thumbHashPainter = ScaledBitmapPainter(it)
            }
        }
    }

    Box(modifier = modifier.onSizeChanged { size ->
        targetSizePx = maxOf(size.width, size.height)
    }) {
        if (!isImageLoaded.value) {
            Image(
                painter = thumbHashPainter ?: LOADING_PAINTER,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Only start loading once we know the target size to avoid loading full-size images
        if (targetSizePx > 0) {
            val context = LocalContext.current
            // Round to 64px buckets for better memory cache hits across similar-sized cells
            val bucketSize = ((targetSizePx + 63) / 64) * 64
            val model = remember(preview, bucketSize, photo.id) {
                ImageRequest.Builder(context)
                    .data(if (!preview) photo.getUri() else photo.getPreviewUri())
                    .size(Size(bucketSize, bucketSize))
                    .crossfade(false) // Disable crossfade animation for grid items
                    .build()
            }

            AsyncImage(
                model = model,
                imageLoader = LocalImageLoader.current.get(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                filterQuality = if (preview) FilterQuality.None else FilterQuality.Low,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        isImageLoaded.value = true
                    }
                }
            )
        }
    }
}

@Composable
fun IconButtonText(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) = Column(
    modifier = modifier
        .clickable(
            onClick = onClick,
            role = Role.Button,
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(bounded = false, radius = 24.dp)
        )
        .defaultMinSize(48.dp, 48.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    content()
    Text(
        modifier = Modifier.padding(top = 2.dp),
        text = text,
        fontSize = 12.sp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBackTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    navIconOnClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = navIconOnClick) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
            }
        },
        title = {
            Column {
                if (title != null)
                    Text(text = title)
                if (subtitle != null)
                    Text(text = subtitle, style = MaterialTheme.typography.titleMedium)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = actions
    )
}

@Composable
fun SelectablePhoto(
    modifier: Modifier = Modifier,
    inSelectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit = {},
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) = Box(
    modifier = modifier.selectableClickable(
        inSelectionMode = inSelectionMode,
        selected = selected,
        onClick = onClick,
        onSelect = onSelect,
        onDeselect = onDeselect
    )
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(inSelectionMode, selected) {
        progress.animateTo(if (inSelectionMode && selected) 1f else 0f)
    }

    Box(
        modifier = Modifier
            .padding((progress.value * 8f).dp)
            .clip(RoundedCornerShape(percent = (progress.value * 30f).toInt()))
    ) {
        content()
    }

    if (inSelectionMode) {
        Icon(
            painter = painterResource(
                if (selected) R.drawable.radio_button_checked
                else R.drawable.radio_button_unchecked
            ),
            tint =
                if (selected) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.85f),
            contentDescription = null,
            modifier = Modifier
                .padding(4.dp)
                .then(
                    if (selected) Modifier.background(
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    ) else Modifier
                )
        )
    }
}

private val PHOTO_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM uuuu・HH:mm")

@OptIn(ExperimentalTime::class)
@Composable
fun Photo.photoDateText(): String = remember(this) {
    val instant = Instant.fromEpochSeconds(this.timeCreated)
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    PHOTO_DATE_FORMATTER.format(date.toJavaLocalDateTime())
}

@Composable
fun VerticallyAnimatedInt(
    targetState: Int,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable AnimatedVisibilityScope.(targetState: Int) -> Unit
) = AnimatedContent(
    targetState = targetState,
    transitionSpec = {
        if (targetState > initialState) {
            // If the target number is larger, it slides up and fades in
            // while the initial (smaller) number slides up and fades out.
            slideInVertically { height -> height } + fadeIn() togetherWith
                    slideOutVertically { height -> -height } + fadeOut()
        } else {
            // If the target number is smaller, it slides down and fades in
            // while the initial number slides down and fades out.
            slideInVertically { height -> -height } + fadeIn() togetherWith
                    slideOutVertically { height -> height } + fadeOut()
        }.using(
            // Disable clipping since the faded slide-in/out should
            // be displayed out of bounds.
            SizeTransform(clip = false)
        )
    },
    contentAlignment = contentAlignment,
    content = content,
    label = "int_animation"
)
