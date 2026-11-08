package net.theluckycoder.familyphotos.ui.dialog

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.PhotoDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDateDialog(
    initialEpochSeconds: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (epochSeconds: Long) -> Unit,
) {
    val initial = remember(initialEpochSeconds) { PhotoDate.toLocalDateTime(initialEpochSeconds) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
                .toEpochMilli(),
            selectableDates = PastDates,
        )
        DatePickerDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            pickedDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
                        }
                    },
                ) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(R.string.title_select_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(PhotoDate.toEpochSeconds(date, timeState.hour, timeState.minute))
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickedDate = null }) {
                    Text(stringResource(R.string.action_previous))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object PastDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate() <= LocalDate.now()

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}
