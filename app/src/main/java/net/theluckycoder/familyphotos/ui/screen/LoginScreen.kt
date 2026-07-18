package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.network.UserLoginDto
import net.theluckycoder.familyphotos.core.data.repository.ConnectionTestResult
import net.theluckycoder.familyphotos.core.data.repository.LoginResult

@Composable
fun LoginScreen(
    initialServerAddress: String,
    testConnection: suspend (String) -> ConnectionTestResult,
    loginAction: suspend (String, UserLoginDto) -> LoginResult
) = Scaffold(
    containerColor = Color.Transparent,
    modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    MaterialTheme.colorScheme.background
                )
            )
        )
) { paddingValues ->

    var serverAddress by remember(initialServerAddress) { mutableStateOf(initialServerAddress) }
    var userName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val isFormValid = serverAddress.isNotBlank() && userName.isNotBlank() && password.isNotBlank()

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
                    UserLoginDto(userName.lowercase().trim(), password.trim())
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoginHeader()

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ServerAddressInput(
                        value = serverAddress,
                        onValueChange = {
                            serverAddress = it
                            errorMessage = null
                            connectionStatus = null
                        },
                        isLoading = isLoading,
                        isCheckingConnection = isCheckingConnection,
                        connectionStatus = connectionStatus,
                        onCheckClick = onCheckConnection
                    )

                    UsernameInput(
                        value = userName,
                        onValueChange = {
                            userName = it
                            errorMessage = null
                        },
                        isLoading = isLoading,
                        isCheckingConnection = isCheckingConnection
                    )

                    PasswordInput(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        passwordVisible = passwordVisible,
                        onPasswordVisibleChange = { passwordVisible = it },
                        isLoading = isLoading,
                        isCheckingConnection = isCheckingConnection,
                        onDoneAction = onLogin
                    )

                    errorMessage?.let { error ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LoginButton(
                        onClick = onLogin,
                        enabled = isFormValid && !isLoading && !isCheckingConnection,
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginHeader(modifier: Modifier = Modifier) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Icon(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        tint = null,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background))
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Text(
        text = stringResource(R.string.login_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
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
            .height(50.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
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

@Composable
private fun ServerAddressInput(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    isCheckingConnection: Boolean,
    connectionStatus: ConnectionTestResult?,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_server_address_label)) },
        placeholder = { Text("https://example.com") },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_upload_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (value.isNotEmpty() && !isLoading && !isCheckingConnection) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = "Clear address"
                    )
                }
            }
        },
        supportingText = {
            if (isCheckingConnection) {
                Text(
                    text = stringResource(R.string.login_checking),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                connectionStatus?.let { status ->
                    when (status) {
                        ConnectionTestResult.Success -> {
                            Text(
                                text = stringResource(R.string.login_server_connected),
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        ConnectionTestResult.Unreachable -> {
                            Text(
                                text = stringResource(R.string.login_server_unreachable),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        ConnectionTestResult.InvalidAddress -> {
                            Text(
                                text = stringResource(R.string.login_error_invalid_url),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        singleLine = true,
        enabled = !isLoading && !isCheckingConnection,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )

    OutlinedButton(
        onClick = onCheckClick,
        enabled = value.isNotBlank() && !isLoading && !isCheckingConnection,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .align(Alignment.End)
            .height(40.dp)
    ) {
        if (isCheckingConnection) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = stringResource(R.string.login_btn_check),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
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

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_username_label)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_person_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    isLoading: Boolean,
    isCheckingConnection: Boolean,
    onDoneAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_password_label)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_lock_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
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
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        enabled = !isLoading && !isCheckingConnection,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDoneAction() }
        ),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
