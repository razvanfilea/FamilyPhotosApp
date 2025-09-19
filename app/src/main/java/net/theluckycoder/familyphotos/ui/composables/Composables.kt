package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.ConstraintsSizeResolver
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.getPreviewUri
import net.theluckycoder.familyphotos.data.model.db.getUri
import net.theluckycoder.familyphotos.data.model.db.thumbHash
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.utils.loadThumbHashPainter
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
fun CoilPhoto(
    photo: Photo,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    sizeResolver: ConstraintsSizeResolver = rememberConstraintsSizeResolver()
) {
    val scope = rememberCoroutineScope()
    var thumbHashPainter by remember { mutableStateOf<Painter?>(null) }
    LaunchedEffect(photo.thumbHash) {
        photo.thumbHash?.let { hash ->
            scope.launch(Dispatchers.Main.immediate) {
                thumbHashPainter = loadThumbHashPainter(hash)
            }
        }
    }

    val ctx = LocalContext.current
    val fullImagePainter = rememberAsyncImagePainter(
        imageLoader = LocalImageLoader.current.get(),
        model = remember(photo) {
            ImageRequest.Builder(ctx)
                .data(if (!preview) photo.getUri() else photo.getPreviewUri())
                .size(sizeResolver)
                .build()
        },
        contentScale = contentScale,
    )

    val state by fullImagePainter.state.collectAsState()
    val painter = remember(state, thumbHashPainter) {
        when (state) {
            is AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> {
                thumbHashPainter ?: ColorPainter(Color.DarkGray)
            }

            is AsyncImagePainter.State.Success -> {
                scope.cancel()
                thumbHashPainter = null // Free the bitmap from memory
                fullImagePainter
            }

            is AsyncImagePainter.State.Error -> {
                thumbHashPainter ?: ColorPainter(Color(0xB6D63535))
            }
        }
    }

    Image(
        modifier = modifier
            .then(sizeResolver),
        painter = painter,
        contentDescription = null,
        contentScale = contentScale,
    )
}

@Composable
fun IconButtonText(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    Column(
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
    val transition = updateTransition(selected, label = "selected")
    val padding by transition.animateDp(label = "padding") { selected ->
        if (selected) 8.dp else 0.dp
    }
    val roundedCornerShape by transition.animateInt(label = "corner") { selected ->
        if (selected) 30 else 0
    }

    Box(
        modifier = Modifier
            .padding(padding)
            .clip(RoundedCornerShape(percent = roundedCornerShape))
    ) {
        content()
    }

    if (inSelectionMode) {
        val iconPadding = Modifier.padding(4.dp)

        val iconRes =
            if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked
        val tint =
            if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
        val iconModifier = if (selected) {
            iconPadding.background(
                MaterialTheme.colorScheme.surface,
                CircleShape
            )
        } else {
            iconPadding
        }

        Icon(
            painter = painterResource(iconRes),
            tint = tint,
            contentDescription = null,
            modifier = iconModifier
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
