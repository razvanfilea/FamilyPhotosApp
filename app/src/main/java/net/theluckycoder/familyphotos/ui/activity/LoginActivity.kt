package net.theluckycoder.familyphotos.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.model.UserLogin
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.viewmodel.LoginViewModel

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.isLoggedIn.collectLatest { isLoggedIn ->
                ensureActive()

                if (isLoggedIn) {
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                    cancel()
                }
            }
        }

        val view = ComposeView(this)
        view.setContent {
            AppTheme {
                Surface {
                    val isLoggedIn by viewModel.isLoggedIn.collectAsState(true)
                    if (!isLoggedIn) {
                        LoginContent(viewModel)
                    }
                }
            }
        }

        setContentView(view)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LoginContentPreview() = AppTheme {
    Surface {
        LoginContent()
    }
}

@Composable
private fun LoginContent(viewModel: LoginViewModel = viewModel()) {
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
                viewModel.login(userLogin)
            }
        ) {
            Text(text = "Login")
        }
    }
}
