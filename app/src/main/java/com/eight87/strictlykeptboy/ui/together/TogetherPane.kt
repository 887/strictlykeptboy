package com.eight87.strictlykeptboy.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.TimeSlot

const val TestTagTogetherPane = "TogetherPane"
const val TestTagTogetherRunning = "TogetherRunning"

/**
 * Phase N — Together top-level pane. Owns the input + result state
 * via [TogetherViewModel]; delegates render to the three leaf
 * composables (form, list, empty). R.X.7 — leaves get the narrow
 * slices they need; the pane is the only thing that knows the VM.
 *
 * Tap on a slot is a v1 stub: shows a toast "would create event"
 * since the editor sheet lives in I-K-EE work (see brief).
 */
@Composable
fun TogetherPane(
    vm: TogetherViewModel,
    modifier: Modifier = Modifier,
    neutralMode: Boolean = false,
    onSlotTap: (TimeSlot) -> Unit = defaultSlotTapHandler(),
) {
    val context = LocalContext.current
    val options by vm.repoOptions.collectAsState()
    val input by vm.input.collectAsState()
    val result by vm.result.collectAsState()
    val widthClass = LocalWindowWidthSizeClass.current

    val inputBlock: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TogetherInputForm(
                repoOptions = options,
                state = input,
                onToggleRepo = vm::toggleRepo,
                onToggleDay = vm::toggleDayOfWeek,
                onUpdate = vm::updateInput,
                onSubmit = vm::submit,
            )
        }
    }

    val resultsBlock: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val r = result) {
                TogetherResultState.Idle -> Text(
                    text = stringResource(R.string.together_running),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
                TogetherResultState.Running -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag(TestTagTogetherRunning),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.together_running),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TogetherResultState.Empty -> TogetherEmptyState(neutralOnly = neutralMode)
                is TogetherResultState.Results -> TogetherResultList(
                    slots = r.slots,
                    onSlotTap = { slot ->
                        onSlotTap(slot)
                        Toast.makeText(
                            context,
                            context.getString(R.string.together_result_create_event_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagTogetherPane),
    ) {
        if (widthClass.isTwoPane()) {
            // Phase 2.1.H.2 — input form (master, 38%) | ranked free slots
            // (detail, 62%) on tablet.
            MasterDetailLayout(
                widthClass = widthClass,
                master = inputBlock,
                detail = resultsBlock,
            )
        } else {
            // Compact: legacy single-column flow (form stacked above results).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TogetherInputForm(
                    repoOptions = options,
                    state = input,
                    onToggleRepo = vm::toggleRepo,
                    onToggleDay = vm::toggleDayOfWeek,
                    onUpdate = vm::updateInput,
                    onSubmit = vm::submit,
                )

                when (val r = result) {
                    TogetherResultState.Idle -> Unit
                    TogetherResultState.Running -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag(TestTagTogetherRunning),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.together_running),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TogetherResultState.Empty -> TogetherEmptyState(neutralOnly = neutralMode)
                    is TogetherResultState.Results -> TogetherResultList(
                        slots = r.slots,
                        onSlotTap = { slot ->
                            onSlotTap(slot)
                            Toast.makeText(
                                context,
                                context.getString(R.string.together_result_create_event_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }
    }
}

/** Default no-op handler; tests can substitute their own. */
private fun defaultSlotTapHandler(): (TimeSlot) -> Unit = { /* v1 stub: editor sheet ships in I-K-EE */ }
