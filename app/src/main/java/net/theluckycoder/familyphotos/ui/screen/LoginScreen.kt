package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.network.UserLoginDto

@Composable
fun LoginScreen(loginAction: (String, UserLoginDto) -> Unit) = Scaffold {
    Box(Modifier.padding(it)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            var serverAddress by remember { mutableStateOf("") }
            var userName by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }

            Text(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .align(Alignment.CenterHorizontally),
                text = stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                value = serverAddress,
                onValueChange = { serverAddress = it },
                label = { Text(stringResource(R.string.login_server_address_label)) }
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = userName,
                onValueChange = { userName = it },
                label = { Text(stringResource(R.string.login_username_label)) }
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
            )

            Button(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .align(Alignment.CenterHorizontally),
                onClick = {
                    val userLogin = UserLoginDto(userName.lowercase().trim(), password.trim())
                    loginAction(serverAddress.trim(), userLogin)
                }
            ) {
                Text(text = stringResource(R.string.login_button))
            }
        }
    }
}
