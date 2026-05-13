package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagEmptyBat = "EmptyBat"
const val TestTagEmptyMessage = "EmptyMessage"
const val TestTagEmptyPlanTrip = "EmptyPlanTrip"

/**
 * Phase F.5 — empty state for the day view.
 *
 * The praise term ("good boy") + tone register are hardcoded here for Phase F;
 * Phase K wires the real `identity.toml` read. The neutral fallback is what
 * we'd render when `tone_register = warm-neutral` lands.
 */
@Composable
fun EmptyScheduleState(
    modifier: Modifier = Modifier,
    neutralOnly: Boolean = false,
    praiseTerm: String = "good boy",
    /** Phase CCC.10 / HV-G.3 — locked as the ONLY in-card promotion of the trip wizard. */
    onPlanTrip: (() -> Unit)? = null,
) {
    val message = if (neutralOnly) {
        stringResource(R.string.schedule_empty_neutral)
    } else {
        stringResource(R.string.schedule_empty_primary, praiseTerm)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.about_bat),
            contentDescription = stringResource(R.string.cd_bat_mascot),
            modifier = Modifier.size(160.dp).testTag(TestTagEmptyBat),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).testTag(TestTagEmptyMessage),
        )
        if (onPlanTrip != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onPlanTrip,
                modifier = Modifier.testTag(TestTagEmptyPlanTrip),
            ) {
                Text(stringResource(R.string.schedule_empty_plan_trip))
            }
        }
    }
}
