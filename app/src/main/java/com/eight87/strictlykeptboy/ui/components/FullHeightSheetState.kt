package com.eight87.strictlykeptboy.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * [SheetState] for a modal bottom sheet that opens straight to full height and never settles in
 * the partially-expanded anchor (the app-wide sheet convention).
 *
 * Replaces the material3 1.5.0-deprecated `rememberModalBottomSheetState(skipPartiallyExpanded =
 * true)`: starts [SheetValue.Hidden] and only enables the Hidden / Expanded anchors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullHeightSheetState(): SheetState =
    rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
