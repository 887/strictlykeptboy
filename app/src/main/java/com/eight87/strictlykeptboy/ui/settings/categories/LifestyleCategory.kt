package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs

const val TestTagCatLifestyle = "Cat-Lifestyle"
const val TestTagCatLifestyleNeutralToggle = "Cat-Lifestyle-Neutral"

/**
 * Phase S.8 — Lifestyle category. 2.1.E.4 — Neutral mode toggle
 * relocated from Appearance: it is a content-mode switch (K-1..K-7),
 * not a visual one. Appearance keeps a deeplink chip back here so the
 * keyword search still finds "neutral" / "kink".
 *
 * Sections:
 *  - Re-enter the lifestyle wizard at Screen 5 (Roles) per K.12 / LW-L.
 *  - **Phase CCC.10 / HV-G.1**: "+ Plan a trip" launches the quick-trip wizard.
 *  - 2.1.E.4 — Neutral mode toggle.
 */
@Composable
fun LifestyleCategory(
    onOpenWizardAtRoles: () -> Unit = {},
    onPlanTrip: () -> Unit = {},
    modifier: Modifier = Modifier,
    neutralPrefs: NeutralModePrefs? = null,
) {
    CategorySurface(
        testTag = TestTagCatLifestyle,
        title = stringResource(R.string.settings_lifestyle_title),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.settings_lifestyle_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenWizardAtRoles,
            modifier = Modifier.testTag("$TestTagCatLifestyle-OpenWizard"),
        ) {
            Text(stringResource(R.string.settings_lifestyle_open_wizard))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_lifestyle_plan_trip_blurb),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPlanTrip,
            modifier = Modifier.testTag("$TestTagCatLifestyle-PlanTrip"),
        ) {
            Text(stringResource(R.string.settings_lifestyle_plan_trip))
        }
        if (neutralPrefs != null) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_lifestyle_neutral_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.settings_lifestyle_neutral_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var neutralOn by remember { mutableStateOf(neutralPrefs.isEnabled()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_appearance_neutral_mode))
                Switch(
                    checked = neutralOn,
                    onCheckedChange = { neutralOn = it; neutralPrefs.setEnabled(it) },
                    modifier = Modifier.testTag(TestTagCatLifestyleNeutralToggle),
                )
            }
        }
    }
}
