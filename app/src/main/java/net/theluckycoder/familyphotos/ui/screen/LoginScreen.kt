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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.theluckycoder.familyphotos.data.model.UserLogin

@Composable
fun LoginScreen(loginAction: (UserLogin) -> Unit) = Scaffold {
    Box(Modifier.padding(it)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            var userName by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }

            Text(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .align(Alignment.CenterHorizontally),
                text = "Family Photos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                value = userName,
                onValueChange = { userName = it },
                label = { Text("User Name") }
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
            )

            Button(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .align(Alignment.CenterHorizontally),
                onClick = {
                    val userLogin = UserLogin(userName.lowercase().trim(), password.trim())
                    loginAction(userLogin)
                }
            ) {
                Text(text = "Login")
            }
        }
    }
}
