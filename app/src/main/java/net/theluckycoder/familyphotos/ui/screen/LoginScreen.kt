package net.theluckycoder.familyphotos.ui.screen

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.network.UserLoginDto
import net.theluckycoder.familyphotos.core.data.repository.ConnectionTestResult
import net.theluckycoder.familyphotos.core.data.repository.LoginResult
import net.theluckycoder.familyphotos.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun LoginScreen(
    initialServerAddress: String,
    testConnection: suspend (String) -> ConnectionTestResult,
    loginAction: suspend (String, UserLoginDto) -> LoginResult
) {
    var serverAddress by remember(initialServerAddress) { mutableStateOf(initialServerAddress) }
    var userName by remember { mutableStateOf("") }
    // Not rememberTextFieldState(): that would put the password in the saved instance state
    val password = remember { TextFieldState() }

    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val isFormValid = serverAddress.isNotBlank() && userName.isNotBlank() && password.text.isNotBlank()

    LaunchedEffect(password) {
        snapshotFlow { password.text.toString() }.drop(1).collect { errorMessage = null }
    }

    val invalidCredentialsMessage = stringResource(R.string.login_error_invalid_credentials)
    val serverUnreachableMessage = stringResource(R.string.login_error_server_unreachable)

    val onCheckConnection: () -> Unit = {
        focusManager.clearFocus()
        isCheckingConnection = true
        errorMessage = null
        scope.launch {
            connectionStatus = testConnection(serverAddress)
            isCheckingConnection = false
        }
    }

    val onLogin: () -> Unit = {
        focusManager.clearFocus()
        if (isFormValid && !isLoading && !isCheckingConnection) {
            isLoading = true
            errorMessage = null
            scope.launch {
                val result = loginAction(
                    serverAddress,
                    UserLoginDto(userName.lowercase().trim(), password.text.trim().toString())
                )
                isLoading = false
                errorMessage = when (result) {
                    LoginResult.Success -> null
                    LoginResult.InvalidCredentials -> invalidCredentialsMessage
                    LoginResult.ServerUnreachable -> serverUnreachableMessage
                }
            }
        }
    }

    val isWide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    LoginContent(
        form = LoginFormState(
            serverAddress = serverAddress,
            userName = userName,
            password = password,
            passwordVisible = passwordVisible,
            isLoading = isLoading,
            isCheckingConnection = isCheckingConnection,
            connectionStatus = connectionStatus,
            errorMessage = errorMessage,
            isFormValid = isFormValid,
        ),
        actions = LoginActions(
            onServerAddressChange = {
                serverAddress = it
                errorMessage = null
                connectionStatus = null
            },
            onUserNameChange = {
                userName = it
                errorMessage = null
            },
            onPasswordVisibleChange = { passwordVisible = it },
            onCheckConnection = onCheckConnection,
            onLogin = onLogin,
        ),
        isWide = isWide,
    )
}

private class LoginFormState(
    val serverAddress: String = "",
    val userName: String = "",
    val password: TextFieldState = TextFieldState(),
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isCheckingConnection: Boolean = false,
    val connectionStatus: ConnectionTestResult? = null,
    val errorMessage: String? = null,
    val isFormValid: Boolean = false,
)

private class LoginActions(
    val onServerAddressChange: (String) -> Unit = {},
    val onUserNameChange: (String) -> Unit = {},
    val onPasswordVisibleChange: (Boolean) -> Unit = {},
    val onCheckConnection: () -> Unit = {},
    val onLogin: () -> Unit = {},
)

@Composable
private fun LoginContent(
    form: LoginFormState,
    actions: LoginActions,
    isWide: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isWide) {
            WideLayout(form, actions)
        } else {
            PhoneLayout(form, actions)
        }
    }
}

@Composable
private fun PhoneLayout(form: LoginFormState, actions: LoginActions) = BoxWithConstraints(
    modifier = Modifier.fillMaxSize()
) {
    val bandHeight = maxHeight * 0.32f
    // How far the brand block reaches up into the mosaic
    val overlap = 44.dp

    // The mosaic scrolls away with the content, which frees space when the keyboard is open
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PhotoMosaic(
                fade = MosaicFade.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bandHeight)
            )

            Column(
                modifier = Modifier
                    .padding(top = bandHeight - overlap)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                LoginBrand(titleStyle = MaterialTheme.typography.headlineLarge)

                Spacer(Modifier.height(28.dp))

                LoginForm(form, actions)
            }
        }
    }
}

@Composable
private fun WideLayout(form: LoginFormState, actions: LoginActions) = Row(
    modifier = Modifier.fillMaxSize()
) {
    Box(
        modifier = Modifier
            .weight(0.55f)
            .fillMaxHeight()
    ) {
        PhotoMosaic(fade = MosaicFade.End, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                )
                .padding(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LoginBrand(titleStyle = MaterialTheme.typography.displaySmall)
        }
    }

    Box(
        modifier = Modifier
            .weight(0.45f)
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
            )
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        // Centred when it fits, scrolls when it doesn't (landscape phones, keyboard open)
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            LoginForm(form, actions)
        }
    }
}

@Composable
private fun LoginBrand(titleStyle: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.Start
) {
    Icon(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colorResource(R.color.ic_launcher_background))
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.app_name),
        style = titleStyle,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Text(
        text = stringResource(R.string.login_tagline),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LoginForm(form: LoginFormState, actions: LoginActions) = Column(
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    Text(
        text = stringResource(R.string.login_sign_in_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )

    ServerAddressInput(
        value = form.serverAddress,
        onValueChange = actions.onServerAddressChange,
        isLoading = form.isLoading,
        isCheckingConnection = form.isCheckingConnection,
        connectionStatus = form.connectionStatus,
        onCheckClick = actions.onCheckConnection
    )

    UsernameInput(
        value = form.userName,
        onValueChange = actions.onUserNameChange,
        isLoading = form.isLoading,
        isCheckingConnection = form.isCheckingConnection
    )

    PasswordInput(
        state = form.password,
        passwordVisible = form.passwordVisible,
        onPasswordVisibleChange = actions.onPasswordVisibleChange,
        isLoading = form.isLoading,
        isCheckingConnection = form.isCheckingConnection,
        onDoneAction = actions.onLogin
    )

    form.errorMessage?.let { error ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    LoginButton(
        onClick = actions.onLogin,
        enabled = form.isFormValid && !form.isLoading && !form.isCheckingConnection,
        isLoading = form.isLoading
    )
}

private enum class MosaicFade { Bottom, End }

/**
 * Abstract backdrop of rounded "photo tiles" in muted theme tones, fading into the background.
 * Drawn on a Canvas: no bitmaps, so it costs nothing to load and follows the theme.
 */
@Composable
private fun PhotoMosaic(fade: MosaicFade, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tones = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.surfaceVariant)
    val background = scheme.background
    val drift = rememberDrift()

    Canvas(modifier = modifier.clipToBounds()) {
        val tileWidth = 92.dp.toPx()
        val gap = 10.dp.toPx()
        val corner = CornerRadius(14.dp.toPx())
        val step = tileWidth + gap

        // A sway of a few dp; drawn with margins so the edges never show
        val phase = drift?.value ?: 0f
        val sway = if (drift != null) Offset(sin(phase) * 14.dp.toPx(), cos(phase) * 10.dp.toPx()) else Offset.Zero

        translate(sway.x, sway.y) {
            val columns = ceil(size.width / step).toInt() + 2
            for (column in -1 until columns) {
                // Seeded per column, so the pattern is the same on every frame and recomposition
                val random = Random(column * 7919 + 13)
                var y = -random.nextInt(0, 80).dp.toPx()
                while (y < size.height + 40.dp.toPx()) {
                    val height = random.nextInt(70, 170).dp.toPx()
                    val tone = tones[random.nextInt(tones.size)]
                    val alpha = 0.18f + random.nextFloat() * 0.27f
                    drawRoundRect(
                        color = tone.copy(alpha = alpha),
                        topLeft = Offset(column * step, y),
                        size = Size(tileWidth, height),
                        cornerRadius = corner
                    )
                    y += height + gap
                }
            }
        }

        val brush = when (fade) {
            MosaicFade.Bottom -> Brush.verticalGradient(
                0.35f to Color.Transparent, 1f to background, startY = 0f, endY = size.height
            )
            MosaicFade.End -> Brush.horizontalGradient(
                0.55f to Color.Transparent, 1f to background, startX = 0f, endX = size.width
            )
        }
        drawRect(brush)
    }
}

/** Slow phase for the mosaic sway; null when animations are off (system setting or previews). */
@Composable
private fun rememberDrift(): State<Float>? {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    if (!animationsEnabled || LocalInspectionMode.current) return null

    return rememberInfiniteTransition(label = "mosaic drift").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 90_000, easing = LinearEasing)),
        label = "mosaic phase"
    )
}

@Composable
private fun LoginButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.login_loading),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        } else {
            Text(
                text = stringResource(R.string.login_button),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

private val FieldShape = RoundedCornerShape(12.dp)
private val FieldMinHeight: Dp = 56.dp

/** Filled fields without the underline: the tinted container is the only outline they need. */
@Composable
private fun loginFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

@Composable
private fun ServerAddressInput(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    isCheckingConnection: Boolean,
    connectionStatus: ConnectionTestResult?,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_server_address_label)) },
        placeholder = { Text("https://example.com") },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty() && !isLoading && !isCheckingConnection) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = "Clear address"
                        )
                    }
                }

                if (isCheckingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(
                        onClick = onCheckClick,
                        enabled = value.isNotBlank() && !isLoading,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_btn_check),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        // No slot at all without a status, or the field would reserve a line of empty space
        supportingText = if (!isCheckingConnection && connectionStatus == null) null else ({
            if (isCheckingConnection) {
                Text(
                    text = stringResource(R.string.login_checking),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                connectionStatus?.let { status ->
                    when (status) {
                        ConnectionTestResult.Success -> Text(
                            text = stringResource(R.string.login_server_connected),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        ConnectionTestResult.Unreachable -> Text(
                            text = stringResource(R.string.login_server_unreachable),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )

                        ConnectionTestResult.InvalidAddress -> Text(
                            text = stringResource(R.string.login_error_invalid_url),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }),
        singleLine = true,
        enabled = !isLoading && !isCheckingConnection,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight),
        shape = FieldShape,
        colors = loginFieldColors()
    )
}

@Composable
private fun UsernameInput(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    isCheckingConnection: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_username_label)) },
        trailingIcon = {
            if (value.isNotEmpty() && !isLoading) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = "Clear username"
                    )
                }
            }
        },
        singleLine = true,
        enabled = !isLoading && !isCheckingConnection,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .semantics {
                contentType = ContentType.Username
            },
        shape = FieldShape,
        colors = loginFieldColors()
    )
}

@Composable
private fun PasswordInput(
    state: TextFieldState,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    isLoading: Boolean,
    isCheckingConnection: Boolean,
    onDoneAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    SecureTextField(
        state = state,
        label = { Text(stringResource(R.string.login_password_label)) },
        trailingIcon = {
            IconButton(
                onClick = { onPasswordVisibleChange(!passwordVisible) },
                enabled = !isLoading && !isCheckingConnection
            ) {
                Icon(
                    painter = painterResource(
                        if (passwordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                    ),
                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                )
            }
        },
        // System follows the "Show passwords" setting when hidden
        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.System,
        enabled = !isLoading && !isCheckingConnection,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        onKeyboardAction = { onDoneAction() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .semantics {
                contentType = ContentType.Password
            },
        shape = FieldShape,
        colors = loginFieldColors()
    )
}

private val previewForm = LoginFormState(
    serverAddress = "https://photos.example.com",
    userName = "alice",
    password = TextFieldState("hunter2"),
    connectionStatus = ConnectionTestResult.Success,
    isFormValid = true,
)

@Preview(name = "Phone", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun LoginPhonePreview() = AppTheme {
    LoginContent(previewForm, LoginActions(), isWide = false)
}

@Preview(name = "Tablet landscape", widthDp = 1024, heightDp = 640, showBackground = true)
@Composable
private fun LoginTabletPreview() = AppTheme {
    LoginContent(previewForm, LoginActions(), isWide = true)
}

@Preview(name = "Error", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun LoginErrorPreview() = AppTheme {
    LoginContent(
        LoginFormState(
            serverAddress = "https://photos.example.com",
            userName = "alice",
            password = TextFieldState("wrong"),
            isFormValid = true,
            connectionStatus = ConnectionTestResult.Unreachable,
            errorMessage = stringResource(R.string.login_error_invalid_credentials),
        ),
        LoginActions(),
        isWide = false
    )
}

@Preview(name = "Loading", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun LoginLoadingPreview() = AppTheme {
    LoginContent(
        LoginFormState(
            serverAddress = "https://photos.example.com",
            userName = "alice",
            password = TextFieldState("hunter2"),
            isFormValid = true,
            isLoading = true,
        ),
        LoginActions(),
        isWide = false
    )
}
