package net.theluckycoder.familyphotos.ui.dialog

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Parcelize
data class DeletePhotosDialog(
    val photos: List<Photo>
) : Screen, Parcelable {

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()

        val scope = rememberCoroutineScope()

        val res = LocalContext.current.resources
        val snackbarHostState = LocalSnackbarHostState.current
        val navigator = LocalNavigator.currentOrThrow
        val bottomSheetNavigator = LocalBottomSheetNavigator.current

        DeleteDialogContent(
            onCancel = { bottomSheetNavigator.hide() },
            onDelete = {
                scope.launch {
                    val results = photos
                        .map { mainViewModel.deletePhotoAsync(it) }
                        .map { it.await() }

                    // TODO Somehow return the results

                    val success = results.count { it }
                    val failure = results.count { !it }

                    GlobalScope.launch {
                        if (success != 0) {
                            snackbarHostState.showSnackbar(
                                res.getQuantityString(
                                    R.plurals.images_deleted_success,
                                    success,
                                    success
                                )
                            )
                        }

                        if (failure != 0) {
                            snackbarHostState.showSnackbar(
                                res.getQuantityString(
                                    R.plurals.images_deleted_failure,
                                    failure,
                                    failure
                                )
                            )
                        }
                    }

                    bottomSheetNavigator.hide()
                    if (success != 0)
                        navigator.pop()
                }
            }
        )
    }

}


@Composable
fun DeleteDialogContent(
    onCancel: () -> Unit,
    onDelete: () -> Unit
) = Column(
    Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
        text = stringResource(R.string.confirm_delete),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )

    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        text = stringResource(R.string.confirm_delete_detail),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )

    Row {
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = onCancel
        ) {
            Text(text = stringResource(R.string.action_cancel))
        }

        Button(
            modifier = Modifier.weight(1f),
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(contentColor = Color(0xFFe53935)) // TODO
        ) {
            Text(text = stringResource(R.string.action_delete))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeleteDialogPreview() {
    AppTheme {
        DeleteDialogContent(
            onCancel = {},
            onDelete = {},
        )
    }
}
