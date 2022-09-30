package net.theluckycoder.familyphotos.ui.preferences

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable

@ExperimentalMaterial3Api
@Composable
fun SwitchPreference(
    item: SwitchPreferenceItem,
    value: Boolean?,
    onValueChanged: (Boolean) -> Unit
) {
    val currentValue = value ?: item.defaultValue
    Preference(
        item = item,
        onClick = { onValueChanged(!currentValue) }
    ) {
        Switch(
            checked = currentValue,
            onCheckedChange = { onValueChanged(!currentValue) },
            enabled = item.enabled
        )
    }
}