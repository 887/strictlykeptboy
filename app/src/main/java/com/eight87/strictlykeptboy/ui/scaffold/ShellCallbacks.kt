package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
import com.eight87.strictlykeptboy.ui.trip.TripDraft
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

/**
 * Round 2.28 / SOLID fix #9 — every action lambda the shell hoists
 * up to the host (typically MainActivity). Splitting these out keeps
 * [ShellContext] free of `() -> Unit` noise and makes the action
 * surface trivially callable from previews / tests by leaving every
 * field default to a no-op.
 *
 * Behaviour: the field defaults match what the previous flat-signature
 * defaults were so the migration is byte-identical at every call site.
 */
@Immutable
data class ShellCallbacks(
    val onPersistTab: (ScheduleViewTab) -> Unit = {},
    val onWriteTask: (TaskQuickAddRequest) -> Unit = {},
    val onSyncClick: () -> Unit = {},
    val onWizardScaffold: suspend (WizardDraft) -> Result<Unit> = { Result.success(Unit) },
    val onWizardFinish: () -> Unit = {},
    /** Phase CCC.8 — trip-wizard materializer (writes overlay calendar + commits). */
    val onTripMaterialize: suspend (TripDraft) -> Result<Unit> = { Result.success(Unit) },
    val onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    val onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    /**
     * Phase 2.1.I.4 — share-with-dom CTA from the wizard's last screen.
     * Caller wires this to ShareSheet with the just-scaffolded repo + the
     * `allowWriteBack` checkbox pre-set.
     */
    val onShareWithDom: () -> Unit = {},
    /**
     * Round 2.1.B.2 / B.4 — long-press handler for calendar chips.
     * Host opens [com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet].
     */
    val onLongPressCalendar: ((com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit)? = null,
    /**
     * Round 2.22 / Fix 3 — inline priority writer fired from the
     * overlay-picker per-row OutlinedTextField. Default no-op so
     * tests / previews don't have to plumb the writer.
     */
    val onOverlayPriorityChange:
        ((com.eight87.strictlykeptboy.resolver.CalendarMeta, Int) -> Unit) = { _, _ -> },
    /**
     * Round 2.23.2 — inline color writer fired from the overlay-picker
     * Color row. Default no-op so tests / previews don't have to
     * plumb the writer.
     */
    val onOverlayColorChange:
        ((com.eight87.strictlykeptboy.resolver.CalendarMeta, Int) -> Unit) = { _, _ -> },
    /**
     * Round 2.16.B — temporary "Start" affordance handler exposed on
     * task rows.
     */
    val onStartTask: ((String) -> Unit)? = null,
    /**
     * Round 2.17.D — "Keep inside the app" CTA on the wizard's storage
     * step. MainActivity wires this to write
     * `ParentLocation.Internal(filesDir/strictlykeptboy)` + the `.skb-root`
     * marker. Default is a no-op so previews / tests don't have to plumb
     * it. The wizard auto-advances when prefs flip.
     */
    val onPickInternalStorage: () -> Unit = {},
    /**
     * Round 2.22 / Phase B UI follow-up — single-instance drop handler
     * for drag-to-reschedule. MainActivity wires to
     * [com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController].
     */
    val onSingleDrop:
        ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    /** Round 2.22 / Phase B UI follow-up — recurring-rule drop handler with branch choice. */
    val onRecurringDrop: (
        (
            com.eight87.strictlykeptboy.resolver.DayBand,
            java.time.OffsetDateTime,
            com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController.RecurringChoice,
        ) -> Unit
    )? = null,
    /**
     * Round 2.27 / Phase D.3 — keeper-prompt response submitter. The
     * host translates `(task, body, attachment)` into a
     * [com.eight87.strictlykeptboy.store.PromptResponseWriter.write]
     * on the right repo root + calId + ruleId.
     */
    val onPromptRespond: ((TaskItem, String, String?) -> Unit)? = null,
    /**
     * Round 2.27 / Phase C.3 — long-press handler for keeper-prompt
     * rows: writes a synthetic "(marked answered offline)" response
     * file so the row clears without opening the sheet.
     */
    val onPromptMarkAnsweredOffline: ((TaskItem) -> Unit)? = null,
)
