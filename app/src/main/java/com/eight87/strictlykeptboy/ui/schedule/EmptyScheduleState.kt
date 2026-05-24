package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
const val TestTagEmptyCta = "EmptyCta"

/** W2-U-2 — prominent "Set up your calendar" CTA on the empty-state. */
const val TestTagEmptySetupWizard = "EmptySetupWizard"

/**
 * Round 2.2.C.8 — three-state empty schedule kind.
 *
 * - [NoRepos] — no repos configured (welcome state)
 * - [NoActiveCalendars] — repos configured, every calendar is paused/inactive
 * - [NoEvents] — calendars active, the current range happens to be empty
 *
 * Selected by the pure helper [selectEmptyKind] so the branch logic
 * stays unit-testable without Compose runtime.
 */
enum class EmptyScheduleKind { NoRepos, NoActiveCalendars, NoEvents, AllReposHidden }

/**
 * Pure selector for the four-state empty branch.
 *
 * Round 2.5.D.3 — adds [AllReposHidden] when repos are configured but
 * every single one has `showOnSchedule = false`.
 *
 * Inputs are caller-aggregated counts so we don't reach across
 * `repoStore` / `calendarRegistry` / `CalendarVisibilityPrefs` directly
 * — keeps this function trivially testable.
 */
fun selectEmptyKind(
    repoCount: Int,
    calendarCount: Int,
    activeCalendarCount: Int,
    showOnScheduleRepoCount: Int = repoCount,
): EmptyScheduleKind = when {
    repoCount <= 0 -> EmptyScheduleKind.NoRepos
    showOnScheduleRepoCount <= 0 -> EmptyScheduleKind.AllReposHidden
    calendarCount <= 0 || activeCalendarCount <= 0 -> EmptyScheduleKind.NoActiveCalendars
    else -> EmptyScheduleKind.NoEvents
}

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
    /**
     * Round 2.2.C.8 — three-state empty branch. Defaults to [EmptyScheduleKind.NoEvents]
     * (the legacy single-state behaviour). Hosts that have wired counts in
     * pick via [selectEmptyKind].
     */
    kind: EmptyScheduleKind = EmptyScheduleKind.NoEvents,
    /** Routes to `TopDestination.Repos` for (a). */
    onOpenRepos: (() -> Unit)? = null,
    /** Routes to Settings → Lists / Calendars master list for (b). */
    onOpenCalendars: (() -> Unit)? = null,
    /** Routes to the EventCreate sheet for (c) — "+ Add event". */
    onAddEvent: (() -> Unit)? = null,
    /**
     * W2-U-2 — prominent "Set up your calendar" CTA. When non-null,
     * renders a filled [Button] above the per-kind text CTAs so a
     * first-time user staring at an empty schedule has an obvious
     * next step that lands them in the 10-step wizard. Caller wires
     * to `graph.setWizardEntryRequest(WizardScreen.Welcome)`.
     */
    onSetupWizard: (() -> Unit)? = null,
) {
    val message = when (kind) {
        EmptyScheduleKind.NoRepos -> stringResource(R.string.schedule_empty_no_repos)
        EmptyScheduleKind.NoActiveCalendars -> stringResource(R.string.schedule_empty_no_active_cals)
        EmptyScheduleKind.AllReposHidden -> stringResource(R.string.schedule_empty_all_repos_hidden)
        EmptyScheduleKind.NoEvents -> if (neutralOnly) {
            stringResource(R.string.schedule_empty_neutral)
        } else {
            stringResource(R.string.schedule_empty_primary, praiseTerm)
        }
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
        Spacer(Modifier.height(12.dp))
        // W2-U-2 — prominent setup CTA. Rendered above the per-kind
        // text CTAs whenever wired, regardless of [kind] — so even the
        // [NoEvents] case (e.g. wizard backed-out, sample event date
        // is empty) still surfaces an obvious wizard entry-point.
        if (onSetupWizard != null) {
            Button(
                onClick = onSetupWizard,
                modifier = Modifier.testTag(TestTagEmptySetupWizard),
            ) {
                Text(stringResource(R.string.schedule_empty_cta_setup_wizard))
            }
            Spacer(Modifier.height(8.dp))
        }
        when (kind) {
            EmptyScheduleKind.NoRepos -> if (onOpenRepos != null) {
                TextButton(onClick = onOpenRepos, modifier = Modifier.testTag(TestTagEmptyCta)) {
                    Text(stringResource(R.string.schedule_empty_cta_open_repos))
                }
            }
            EmptyScheduleKind.NoActiveCalendars -> if (onOpenCalendars != null) {
                TextButton(onClick = onOpenCalendars, modifier = Modifier.testTag(TestTagEmptyCta)) {
                    Text(stringResource(R.string.schedule_empty_cta_open_calendars))
                }
            }
            EmptyScheduleKind.AllReposHidden -> if (onOpenRepos != null) {
                TextButton(onClick = onOpenRepos, modifier = Modifier.testTag(TestTagEmptyCta)) {
                    Text(stringResource(R.string.schedule_empty_cta_open_repos))
                }
            }
            EmptyScheduleKind.NoEvents -> if (onAddEvent != null) {
                TextButton(onClick = onAddEvent, modifier = Modifier.testTag(TestTagEmptyCta)) {
                    Text(stringResource(R.string.schedule_empty_cta_add_event))
                }
            }
        }
        if (onPlanTrip != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onPlanTrip,
                modifier = Modifier.testTag(TestTagEmptyPlanTrip),
            ) {
                Text(stringResource(R.string.schedule_empty_plan_trip))
            }
        }
    }
}
