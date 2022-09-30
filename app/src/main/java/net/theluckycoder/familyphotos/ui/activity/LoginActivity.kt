package net.theluckycoder.familyphotos.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import net.theluckycoder.familyphotos.model.UserLogin
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.viewmodel.LoginViewModel

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Vezi ca nu te lasa sa iti vezi pozele de pe telefon",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    init {
        lifecycleScope.launchWhenCreated {
            viewModel.isLoggedIn.collectLatest { isLoggedIn ->
                ensureActive()

                if (isLoggedIn) {
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                    cancel()
                }
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_DENIED && shouldShowRequestPermissionRationale(permission)
        ) requestPermissionLauncher.launch(permission)

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

@OptIn(ExperimentalMaterial3Api::class)
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
                val userLogin = UserLogin(userName.lowercase(), password)
                viewModel.login(userLogin)
            }
        ) {
            Text(text = "Login")
        }
    }
}
