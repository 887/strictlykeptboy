package com.eight87.strictlykeptboy.ui.wizard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

/** Round 2.18 Phase I — one-shot default-calendar-app onboarding card. */
const val TestTagWizardDefaultCalendar: String = "Wizard-DefaultCalendar"
const val TestTagWizardDefaultCalendarGotIt: String = "Wizard-DefaultCalendar-GotIt"
const val TestTagWizardDefaultCalendarOpenSettings: String =
    "Wizard-DefaultCalendar-OpenSettings"

/**
 * Round 2.18 Phase I — "Make skb your default calendar app?" wizard
 * card. Shown exactly once per install (gated by
 * [com.eight87.strictlykeptboy.system.SystemCalendarGlobalPrefs.defaultCalendarOnboardingShown]).
 *
 * No `RoleManager.ROLE_CALENDAR` exists; the actual mechanism is for
 * the user to win the system chooser the next time they tap a `.ics`.
 * The card explains that and offers a power-user shortcut to the OS
 * default-apps screen.
 *
 * Two buttons:
 *  - "Got it" — fires [onAdvance].
 *  - "Open default apps now" — launches `Settings.ACTION_MANAGE_DEFAULT_APPS`,
 *    then fires [onAdvance].
 */
@Composable
fun DefaultCalendarStep(
    onAdvance: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTagWizardDefaultCalendar),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.wizard_default_calendar_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.wizard_default_calendar_blurb),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAdvance,
                modifier = Modifier.testTag(TestTagWizardDefaultCalendarGotIt),
            ) { Text(stringResource(R.string.wizard_default_calendar_got_it)) }
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }
                    onAdvance()
                },
                modifier = Modifier.testTag(TestTagWizardDefaultCalendarOpenSettings),
            ) {
                Text(stringResource(R.string.wizard_default_calendar_open_settings))
            }
        }
    }
}
