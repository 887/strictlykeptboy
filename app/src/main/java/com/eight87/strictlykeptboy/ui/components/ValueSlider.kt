package com.eight87.strictlykeptboy.ui.components

import androidx.annotation.IntRange
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Controlled (value-hoisted) slider on top of the material3 1.5 [SliderState] API.
 *
 * material3 1.5.0 deprecated the `Slider(value = …, onValueChange = …)` overload in favour of
 * `Slider(state, onValueChange, …)`. Every slider in this app keeps its value in the caller
 * (prefs / form state), so this wrapper owns the [SliderState] and re-syncs it from [value] on
 * each composition — the same contract the deprecated overload implemented internally — while
 * [onValueChange] reports user drags back to the caller, which stays the single source of truth.
 */
@Composable
fun ValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    @IntRange(from = 0) steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}
