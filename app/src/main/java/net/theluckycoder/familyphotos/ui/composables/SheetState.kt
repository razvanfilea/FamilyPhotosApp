package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/** A modal bottom sheet state that opens fully expanded, without the partially expanded stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedOnlySheetState(): SheetState =
    rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
