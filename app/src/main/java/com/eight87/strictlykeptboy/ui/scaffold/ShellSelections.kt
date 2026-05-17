package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Round 2.28 / SOLID fix #9 — parent-owned selection / mode state the
 * shell reads.
 *
 *  - [neutralMode] is a boolean snapshot the parent computes from
 *    `NeutralModePrefs`; reads as a scalar.
 *  - [wizardEntryRequest] is a `MutableStateFlow` owned by the host
 *    (currently `AppGraph`); the shell observes for re-entry signals
 *    and the wizard finish-handler clears it.
 *
 * Most "selection state" in the shell (active `TopDestination`,
 * `reviewsFilter`, `tasksFilter`) is intentionally still owned inside
 * `SkbAppShellContent` via `rememberSaveable` because it's
 * shell-local — the parent has no reason to read or override it. If
 * we ever need to hoist any of those to the host, this is the place
 * they'd land.
 */
@Immutable
data class ShellSelections(
    val neutralMode: Boolean = false,
    /**
     * Phase 2.1.I.2 — external request to switch to the Wizard destination
     * and pre-position the host at a specific screen (e.g. Roles, from the
     * Settings → Lifestyle entry-point). When non-null, the shell selects
     * [TopDestination.Wizard], passes `initialScreen` down, then clears
     * the request on wizard finish. Null → no auto-routing.
     */
    val wizardEntryRequest: MutableStateFlow<
        com.eight87.strictlykeptboy.ui.wizard.WizardScreen?
    >? = null,
)
