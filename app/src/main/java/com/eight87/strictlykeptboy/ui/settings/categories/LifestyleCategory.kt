package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagCatLifestyle = "Cat-Lifestyle"

/**
 * Phase S.8 — Lifestyle category.
 *
 * Replaces the retired Demo section (D.54). Two CTAs:
 *  - Re-enter the lifestyle wizard at Screen 5 (Roles) per K.12 / LW-L.
 *  - **Phase CCC.10 / HV-G.1**: "+ Plan a trip" launches the quick-trip wizard.
 */
@Composable
fun LifestyleCategory(
    onOpenWizardAtRoles: () -> Unit = {},
    onPlanTrip: () -> Unit = {},
    modifier: Modifier = Modifier,
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
    }
}
