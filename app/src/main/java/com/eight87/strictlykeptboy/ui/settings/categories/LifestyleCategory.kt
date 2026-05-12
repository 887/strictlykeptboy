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
 * Replaces the retired Demo section (D.54). Single CTA that re-enters
 * the wizard at Screen 5 (Roles) per K.12 / LW-L.
 */
@Composable
fun LifestyleCategory(onOpenWizardAtRoles: () -> Unit = {}, modifier: Modifier = Modifier) {
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
    }
}
