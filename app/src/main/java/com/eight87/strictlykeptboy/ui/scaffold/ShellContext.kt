package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.task.StubTaskPlaybackSource
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.settings.SettingsAccess
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.together.TogetherViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Round 2.28 / SOLID fix #9 — read-only services and state providers
 * the shell composes. Together with [ShellCallbacks] and
 * [ShellSelections] this replaces the previous ~50-param flat
 * signature on [SkbAppShell].
 *
 * **Rule:** every field here must be a stateless provider (a
 * `StateFlow`, a view-state object exposing its own flows, a service
 * handle). Do NOT put `MutableState<X>` here — that would break
 * recomposition isolation and Compose-stable invariants. Per-shell
 * UI state stays inside `SkbAppShellContent`'s `remember` block.
 */
@Immutable
data class ShellContext(
    val activeRepoNameFlow: StateFlow<String>,
    val scheduleState: ScheduleViewState,
    val tasksState: TasksViewState = TasksViewState(),
    val reposState: ReposViewState? = null,
    val secretsStore: SecretsStore? = null,
    val togetherViewModel: TogetherViewModel? = null,
    val importExportState: ImportExportViewState? = null,
    val settingsAccess: SettingsAccess = SettingsAccess(),
    val activeIconKindFlow: StateFlow<com.eight87.strictlykeptboy.ui.theming.RepoIconKind>? = null,
    val eventCreateController: com.eight87.strictlykeptboy.ui.schedule.EventCreateController? = null,
    val calendarVisibility: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs? = null,
    val taskPlaybackSource: Any = StubTaskPlaybackSource,
    val nowNextFlow: StateFlow<com.eight87.strictlykeptboy.resolver.NowNextSnapshot>? = null,
)
