package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.repos.ReposPane
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.settings.SettingsAccess
import com.eight87.strictlykeptboy.ui.settings.SettingsPane
import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TasksFilter
import com.eight87.strictlykeptboy.ui.tasks.TasksPane
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.together.TogetherPane
import com.eight87.strictlykeptboy.ui.together.TogetherViewModel
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WizardNavHost

/**
 * Round 2.28 / SOLID fix #8c — destination dispatch lives in its own
 * file. Previously this was a private composable in `SkbAppShell.kt`
 * (~140 LOC of `when` branches at the bottom of a 729 LOC god-file).
 * Now `SkbAppShell.kt` owns top-level composition + chrome only and
 * this file owns the exhaustive destination-to-pane mapping.
 *
 * The shell composes the active pane based on [selected]; this is
 * the single exhaustive-`when` site that knows which pane class to
 * call. Each branch is a single call-site and stays
 * argument-per-pane rather than getting a bundle — the per-pane
 * surfaces own their own shape.
 *
 * SOLID notes: this composable is package-private (file-internal
 * usage from `SkbAppShellContent`); its argument list mirrors the
 * shell's now-grouped surface but stays flat here because each
 * argument is read by exactly one branch.
 */
@Composable
internal fun SkbAppDestinationContent(
    reviewsFilter: com.eight87.strictlykeptboy.ui.reviews.ReviewsFilter =
        com.eight87.strictlykeptboy.ui.reviews.ReviewsFilter.All,
    tasksFilter: TasksFilter = TasksFilter.Today,
    tasksState: TasksViewState,
    selected: TopDestination,
    activeRepoName: String,
    scheduleState: ScheduleViewState,
    onSyncClick: () -> Unit,
    eventCreateController: com.eight87.strictlykeptboy.ui.schedule.EventCreateController?,
    onPlanTrip: () -> Unit,
    onEditSchedule: () -> Unit = {},
    /** W2-U-2 — wizard entry-point for empty-state CTA + Repos-empty CTA. */
    onSetupWizard: () -> Unit,
    calendarVisibility: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs?,
    onLongPressCalendar: ((com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit)?,
    scheduleViewModePrefs: com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs?,
    togetherViewModel: TogetherViewModel?,
    neutralMode: Boolean,
    reposState: ReposViewState?,
    secretsStore: SecretsStore?,
    settingsAccess: SettingsAccess,
    onSelectDest: (TopDestination) -> Unit,
    /**
     * Opens Settings as a full-shell overlay (tonearmboy / whisperboy
     * parity). Routed by `SkbAppShellContent` to the `settingsOpen`
     * overlay state hoisted there.
     */
    onOpenSettings: () -> Unit = {},
    /**
     * Opens Repos as a full-shell overlay (Settings parity). Routed
     * by `SkbAppShellContent` to the `reposOpen` overlay state hoisted
     * there. The `TopDestination.Repos` branch in the `when` below is
     * retained for exhaustive matching / test references but is no
     * longer reachable via the UI.
     */
    onOpenRepos: () -> Unit = {},
    onPickInternalStorage: () -> Unit,
    onWizardScaffold: suspend (WizardDraft) -> Result<Unit>,
    onWizardFinish: () -> Unit,
    onWizardFinished: () -> Unit,
    wizardEntry: com.eight87.strictlykeptboy.ui.wizard.WizardScreen?,
    onShareWithDom: () -> Unit,
    importExportState: ImportExportViewState?,
    onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    onSingleDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    onRecurringDrop: (
        (
            com.eight87.strictlykeptboy.resolver.DayBand,
            java.time.OffsetDateTime,
            com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController.RecurringChoice,
        ) -> Unit
    )? = null,
    /** Round 2.25 follow-up — host-owned full-screen event-detail opener. */
    onOpenEventDetailFullScreen: ((com.eight87.strictlykeptboy.resolver.DayBand) -> Unit)? = null,
    /** Round 2.27 / Phase D.3 — keeper-prompt response submitter. */
    onPromptRespond: ((TaskItem, String, String?) -> Unit)? = null,
    /** Round 2.27 / Phase C.3 — keeper-prompt long-press → offline marker. */
    onPromptMarkAnsweredOffline: ((TaskItem) -> Unit)? = null,
) {
    when (selected) {
        TopDestination.Schedule -> SchedulePane(
            activeRepoName = activeRepoName,
            state = scheduleState,
            onSyncClick = onSyncClick,
            eventCreateController = eventCreateController,
            onPlanTrip = onPlanTrip,
            onEditSchedule = onEditSchedule,
            onSetupWizard = onSetupWizard,
            calendarVisibility = calendarVisibility,
            onLongPressCalendar = onLongPressCalendar,
            onSingleDrop = onSingleDrop,
            onRecurringDrop = onRecurringDrop,
            onOpenEventDetailFullScreen = onOpenEventDetailFullScreen,
            viewModePrefs = scheduleViewModePrefs,
        )
        TopDestination.Tasks -> TasksPane(
            filter = tasksFilter,
            tasksState = tasksState,
            scheduleFlow = scheduleState.rendered,
            onPromptRespond = onPromptRespond,
            onPromptMarkAnsweredOffline = onPromptMarkAnsweredOffline,
        )
        TopDestination.Together -> if (togetherViewModel != null) {
            TogetherPane(vm = togetherViewModel, neutralMode = neutralMode)
        } else {
            PlaceholderScreen(stringResource(R.string.scaffold_dest_together))
        }
        TopDestination.Repos -> if (reposState != null) {
            ReposPane(
                state = reposState,
                secretsStore = secretsStore,
                onOpenTogether = { onSelectDest(TopDestination.Together) },
                onOpenWizard = { onSelectDest(TopDestination.Wizard) },
                onOpenAppSettings = onOpenSettings,
                // Round 2.7.D.2-UI — banner inputs forwarded via SettingsAccess
                // because that's the only narrow surface that already carries
                // RepoStoragePrefs + NotificationPrefs into the shell.
                repoStoragePrefs = settingsAccess.repoStoragePrefs,
                notificationPrefs = settingsAccess.notificationPrefs,
                onPickBackupFolder = settingsAccess.onPickBackupFolder,
                demoModePrefs = settingsAccess.demoModePrefs,
                onPickInternalStorage = onPickInternalStorage,
                safPermissionRevoked = settingsAccess.safPermissionRevokedFlow
                    ?.collectAsState()?.value == true,
            )
        } else {
            PlaceholderScreen(stringResource(R.string.scaffold_dest_repos))
        }
        TopDestination.Wizard -> WizardNavHost(
            onFinish = {
                onWizardFinish()
                onWizardFinished()
                onSelectDest(TopDestination.Schedule)
            },
            onCancel = {
                onWizardFinished()
                onSelectDest(TopDestination.Schedule)
            },
            onScaffold = onWizardScaffold,
            neutralMode = neutralMode,
            initialScreen = wizardEntry
                ?: com.eight87.strictlykeptboy.ui.wizard.WizardScreen.Welcome,
            onShareWithDom = onShareWithDom,
            // Round 2.17.D — storage step wiring.
            repoStoragePrefs = settingsAccess.repoStoragePrefs,
            onPickExternalStorage = settingsAccess.onPickBackupFolder,
            onPickInternalStorage = onPickInternalStorage,
            // Round 2.18 Phase I — default-calendar-app
            // onboarding card backing store.
            systemCalendarPrefs = settingsAccess.systemCalendarPrefs,
        )
        TopDestination.Reviews -> {
            // Round 2.23 Phase E (D-2.23.e) — Reviews destination now
            // renders live items from `ReviewFeedReader` when the host
            // wires `settingsAccess.reviewItemsFlow`. Falls back to the
            // Phase DDD.13 empty-state card otherwise.
            val identityState = settingsAccess.identityPrefs
                ?.state?.collectAsState()?.value
            val items = settingsAccess.reviewItemsFlow
                ?.collectAsState()?.value
                ?: emptyList()
            com.eight87.strictlykeptboy.ui.reviews.ReviewsPane(
                side = com.eight87.strictlykeptboy.ui.reviews.ReviewsSide.Boy,
                items = items,
                boyHonorific = identityState?.honorific?.ifBlank { "Sir" } ?: "Sir",
                boyPraiseTerm = identityState?.praise?.ifBlank { "good boy" } ?: "good boy",
                filter = reviewsFilter,
            )
        }
        TopDestination.Settings -> SettingsPane(
            importExportState = importExportState,
            onPickImportFile = onPickImportFile,
            onPickExportFile = onPickExportFile,
            access = settingsAccess.copy(
                // Phase CCC.10 — Settings → Lifestyle → Plan a trip.
                onPlanTrip = onPlanTrip,
            ),
        )
    }
}
