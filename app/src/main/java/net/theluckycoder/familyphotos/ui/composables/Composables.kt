package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.data.model.getPreviewUri
import net.theluckycoder.familyphotos.data.model.getUri
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import java.time.format.DateTimeFormatter

@Composable
fun CoilPhoto(
    photo: Photo,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    AsyncImage(
        modifier = modifier,
        model = if (!preview) photo.getUri() else photo.getPreviewUri(),
        contentScale = contentScale,
        contentDescription = photo.name,
        imageLoader = LocalImageLoader.current.get(),
        placeholder = ColorPainter(Color.DarkGray),
        error = ColorPainter(Color(0xB6D63535))
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
fun SelectableItem(
    modifier: Modifier = Modifier,
    inSelectionMode: Boolean,
    selected: Boolean,
    content: @Composable BoxScope.() -> Unit
) {

    if (inSelectionMode) {
        Surface(
            modifier = modifier,
            tonalElevation = 15.dp
        ) {
            val transition = updateTransition(selected, label = "selected")
            val padding by transition.animateDp(label = "padding") { selected ->
                if (selected) 10.dp else 0.dp
            }
            val roundedCornerShape by transition.animateDp(label = "corner") { selected ->
                if (selected) 16.dp else 0.dp
            }

            Box(
                modifier = Modifier
                    .padding(padding)
                    .clip(RoundedCornerShape(roundedCornerShape))
            ) {
                content()

                if (selected) {
                    val bgColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    Icon(
                        painter = painterResource(R.drawable.radio_button_checked),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.radio_button_unchecked),
                        tint = Color.White.copy(alpha = 0.7f),
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun SelectablePhoto(
    modifier: Modifier = Modifier,
    inSelectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit = {},
    onSelect: () -> Unit = {},
    onDeselect: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    SelectableItem(
        modifier = modifier.selectableClickable(
            inSelectionMode = inSelectionMode,
            selected = selected,
            onClick = onClick,
            onSelect = onSelect,
            onDeselect = onDeselect
        ),
        inSelectionMode = inSelectionMode,
        selected = selected,
        content = content
    )
}

private val PHOTO_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM uuuu・HH:mm")

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
) {
    AnimatedContent(
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
}
