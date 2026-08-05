package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
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
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.getPreviewUri
import net.theluckycoder.familyphotos.core.data.model.getUri
import net.theluckycoder.familyphotos.core.data.model.thumbHash
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.utils.ScaledBitmapPainter
import net.theluckycoder.familyphotos.utils.ThumbHashCache
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val PLACEHOLDER_COLOR = Color.DarkGray
private val SELECTION_SCRIM_COLOR = Color.Black.copy(alpha = 0.4f)

@Composable
fun CoilPhoto(
    photo: Photo,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    requestedPhotoSize: Size? = null,
) {
    val isImageLoaded = remember { mutableStateOf(false) }
    val thumbHashPainter = if (!isImageLoaded.value) thumbHashPainter(photo.thumbHash) else null

    val context = LocalContext.current
    val model = remember(preview, photo.id, requestedPhotoSize) {
        ImageRequest.Builder(context)
            .data(if (!preview) photo.getUri() else photo.getPreviewUri())
            .crossfade(false)
            .apply { requestedPhotoSize?.let { size(it) } }
            .build()
    }

    val placeholderModifier = remember(thumbHashPainter) {
        Modifier.drawBehind {
            if (isImageLoaded.value) return@drawBehind
            if (thumbHashPainter == null) {
                drawRect(PLACEHOLDER_COLOR)
                return@drawBehind
            }

            clipRect {
                with(thumbHashPainter) { draw(size) }
            }
        }
    }

    AsyncImage(
        model = model,
        imageLoader = LocalImageLoader.current.get(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = placeholderModifier
            .then(modifier),
        filterQuality = if (preview) FilterQuality.None else FilterQuality.Low,
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) {
                isImageLoaded.value = true
            }
        }
    )
}

@Composable
fun thumbHashPainter(thumbHash: String?): ScaledBitmapPainter? {
    return remember(thumbHash) {
        ThumbHashCache.getOrDecodeSync(thumbHash)?.let { ScaledBitmapPainter(it) }
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
    Box(
        modifier = Modifier.drawWithContent {
            drawContent()
            if (selected) drawRect(SELECTION_SCRIM_COLOR)
        }
    ) {
        content()
    }

    val iconAlpha by animateFloatAsState(
        targetValue = if (inSelectionMode) 1f else 0f,
        label = "selectionIconAlpha"
    )
    if (iconAlpha > 0f) {
        val iconScale by animateFloatAsState(
            targetValue = if (selected) 1f else 0.85f,
            label = "selectionIconScale"
        )

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
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleX = iconScale
                    scaleY = iconScale
                }
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
