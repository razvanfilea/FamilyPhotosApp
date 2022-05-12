package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IconButtonText(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = false, radius = 24.dp)
            )
            .defaultMinSize(48.dp, 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val contentAlpha = if (enabled) LocalContentAlpha.current else ContentAlpha.disabled
        CompositionLocalProvider(LocalContentAlpha provides contentAlpha) {
            content()
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = text,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun NavBackTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    navIconOnClick: () -> Unit,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = navIconOnClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
        },
        title = {
            Column {
                if (title != null)
                    Text(text = title)
                if (subtitle != null)
                    Text(text = subtitle, style = MaterialTheme.typography.subtitle1)
            }
        },
        elevation = 0.dp,
        backgroundColor = Color.Transparent
    )
}

@Composable
fun SelectableItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean,
    onClick: (longPress: Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val padding = animateDpAsState(if (selected) 8.dp else 0.dp).value

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick(false) },
                    onLongPress = { onClick(true) }
                )
            }
            .padding(padding),
    ) {
        content()

        if (enabled) {
            Checkbox(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                checked = selected,
                onCheckedChange = null
            )
        }
    }
}

