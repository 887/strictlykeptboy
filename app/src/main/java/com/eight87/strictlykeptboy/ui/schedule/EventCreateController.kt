package com.eight87.strictlykeptboy.ui.schedule

import android.content.Context
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.store.AtomicTemplate
import com.eight87.strictlykeptboy.store.AtomicTemplateLoader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.TemplateEntry
import com.eight87.strictlykeptboy.store.TemplateIndexLoader
import com.eight87.strictlykeptboy.store.TemplateMaterializer
import com.eight87.strictlykeptboy.store.TemplateMerger
import com.eight87.strictlykeptboy.store.TemplateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.OffsetDateTime

/**
 * Phase FFF / EC-A — orchestrator for the event-create flow.
 *
 * Owns sheet-open flag + draft state + template-pick + confirm-sheet
 * state + overlap-dialog state + Undo payload. Dispatches free-form
 * confirm + template materialization through [EntityWriter] +
 * [GitRepo.commitAll].
 *
 * Exposes [state] + [sheetOpen] as observable StateFlows that the
 * FAB-owning composable reads. Pure-ish: depends only on
 * provider lambdas for active repo / calendar list / neutral mode /
 * existing events, so unit-testable without an Android context for
 * the dispatch paths that don't touch the asset stream.
 */
class EventCreateController(
    private val scope: CoroutineScope,
    private val prefs: EventCreatePrefs,
    private val context: Context,
    private val activeRepoProvider: () -> RepoConfig?,
    private val calendarOptionsProvider: () -> List<CalendarOption>,
    private val neutralModeProvider: () -> Boolean = { false },
    private val existingEventsProvider: () -> List<Triple<String, OffsetDateTime, OffsetDateTime>> = { emptyList() },
) {
    private val _state = MutableStateFlow(EventCreateSheetState(tab = prefs.selected.value))
    val state: StateFlow<EventCreateSheetState> = _state.asStateFlow()

    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    private val _lastWritten = MutableStateFlow<UndoPayload?>(null)
    val lastWritten: StateFlow<UndoPayload?> = _lastWritten.asStateFlow()

    private var shippedTemplates: List<TemplateEntry> = emptyList()
    private var pendingTemplate: AtomicTemplate? = null

    /**
     * Phase 2.1.D.7 — listener invoked when a draft carrying
     * `relatedTaskId` lands as a committed event. Receives the
     * (taskId, eventId, start) triple; the caller updates the matching
     * `TaskItem.linkedEventId`/`linkedEventStart`. Optional: no-op by
     * default so existing tests + composition wiring stay untouched.
     */
    var onTaskLinked: (taskId: String, eventId: String, start: OffsetDateTime) -> Unit =
        { _, _, _ -> }

    /**
     * Phase 2.1.D.7 — opens the create sheet pre-populated with a
     * task's title + a `relatedTaskId` field. On confirm,
     * [onTaskLinked] fires so callers can mutate the originating
     * task's `linkedEventId`.
     */
    fun openSheetForTask(
        taskId: String,
        taskTitle: String,
        defaultStart: OffsetDateTime = OffsetDateTime.now().plusMinutes(15),
    ) {
        val cals = calendarOptionsProvider()
        val draft = EventDraft(
            title = taskTitle,
            start = defaultStart,
            end = defaultStart.plusMinutes(30),
            calendarId = cals.firstOrNull()?.id ?: "",
            relatedTaskId = taskId,
        )
        if (shippedTemplates.isEmpty()) loadShippedTemplates()
        _state.value = EventCreateSheetState(
            tab = EventCreateTab.FreeForm,
            draft = draft,
            calendars = cals,
            templateEntries = shippedTemplates,
            neutralMode = neutralModeProvider(),
        )
        _sheetOpen.value = true
    }

    fun openSheet(defaultStart: OffsetDateTime = OffsetDateTime.now().plusMinutes(15)) {
        val cals = calendarOptionsProvider()
        // Round 2.1.B.10 — prefer last-used calendar for the active repo
        // over the legacy `RepoConfig.defaultCalendarId` hard binding.
        val activeRepoId = activeRepoProvider()?.repoId
        val lastUsed = activeRepoId?.let { prefs.lastUsedCalendar(it) }
        val initialCalendarId = lastUsed
            ?.takeIf { id -> cals.any { it.id == id } }
            ?: cals.firstOrNull()?.id
            ?: ""
        val draft = EventDraft(
            start = defaultStart,
            end = defaultStart.plusMinutes(30),
            calendarId = initialCalendarId,
        )
        if (shippedTemplates.isEmpty()) loadShippedTemplates()
        _state.value = EventCreateSheetState(
            tab = prefs.selected.value,
            draft = draft,
            calendars = cals,
            templateEntries = shippedTemplates,
            neutralMode = neutralModeProvider(),
        )
        _sheetOpen.value = true
    }

    fun closeSheet() {
        _sheetOpen.value = false
        _state.value = _state.value.copy(
            confirmingTemplate = null,
            confirmingTemplateSubbeats = emptyList(),
            overlap = null,
        )
        pendingTemplate = null
    }

    fun setTab(tab: EventCreateTab) {
        prefs.set(tab)
        _state.value = _state.value.copy(tab = tab)
    }

    fun setDraft(draft: EventDraft) {
        _state.value = _state.value.copy(draft = draft)
    }

    fun confirmFreeForm() {
        val draft = _state.value.draft
        val errs = EventDraftValidator.validate(draft)
        if (errs.any) return
        val hit = OverlapDetector.firstOverlap(draft.start, draft.end, existingEventsProvider())
        if (hit != null) {
            _state.value = _state.value.copy(overlap = hit)
            return
        }
        writeFreeForm(draft)
    }

    fun overlapScheduleAnyway() {
        val s = _state.value
        _state.value = s.copy(overlap = null)
        when (s.tab) {
            EventCreateTab.FreeForm -> writeFreeForm(s.draft)
            EventCreateTab.Template -> pendingTemplate?.let { writeTemplate(it) }
        }
    }

    fun overlapPickDifferent() {
        val s = _state.value
        val newStart = s.draft.start.plusMinutes(15)
        _state.value = s.copy(
            overlap = null,
            draft = s.draft.copy(start = newStart, end = newStart.plusMinutes(30)),
        )
    }

    fun overlapCancel() {
        _state.value = _state.value.copy(overlap = null)
        pendingTemplate = null
    }

    fun pickTemplate(entry: TemplateEntry) {
        val tpl = loadFullTemplate(entry) ?: return
        pendingTemplate = tpl
        val firstEntry = tpl.entries.firstOrNull() ?: return
        val start = _state.value.draft.start
        _state.value = _state.value.copy(
            confirmingTemplate = entry,
            confirmingTemplateSubbeats = firstEntry.subbeats,
            confirmingTemplateStart = start.toString(),
        )
    }

    fun confirmTemplate() {
        val tpl = pendingTemplate ?: return
        val s = _state.value
        val firstEntry = tpl.entries.firstOrNull() ?: return
        val start = s.draft.start
        val end = start.plusMinutes(firstEntry.durationMinutes.toLong())
        val hit = OverlapDetector.firstOverlap(start, end, existingEventsProvider())
        if (hit != null) {
            _state.value = s.copy(overlap = hit, confirmingTemplate = null)
            return
        }
        writeTemplate(tpl)
    }

    fun cancelTemplate() {
        pendingTemplate = null
        _state.value = _state.value.copy(
            confirmingTemplate = null,
            confirmingTemplateSubbeats = emptyList(),
        )
    }

    /** EC-B.3 Undo — delete the just-written event file + commit. */
    fun undoLast(): Boolean {
        val payload = _lastWritten.value ?: return false
        _lastWritten.value = null
        scope.launch(Dispatchers.IO) {
            val cfg = activeRepoProvider() ?: return@launch
            val rootDir = File(cfg.rootDir)
            runCatching { EntityWriter.delete(rootDir, payload.event) }
            val repo = openRepo(cfg)
            runCatching { repo?.commitAll("undo: remove event '${payload.event.title}'") }
        }
        return true
    }

    private fun writeFreeForm(draft: EventDraft) {
        val cfg = activeRepoProvider() ?: run {
            closeSheet()
            return
        }
        val author = cfg.authorIdentity.name
        val event = DraftToEvent.mint(draft = draft, author = author)
        writeEvent(cfg, event, commitMessage = "add event \"${event.title}\"")
        // Phase 2.1.D.7 — reciprocal link back from this event to its
        // originating task. Fires synchronously off the UI thread; the
        // write itself is dispatched onto Dispatchers.IO above.
        if (draft.relatedTaskId.isNotEmpty()) {
            onTaskLinked(draft.relatedTaskId, event.header.id, draft.start)
        }
        closeSheet()
    }

    private fun writeTemplate(tpl: AtomicTemplate) {
        val cfg = activeRepoProvider() ?: run {
            closeSheet()
            return
        }
        val s = _state.value
        val start = s.draft.start
        val author = cfg.authorIdentity.name
        val event = TemplateMaterializer.materialize(
            template = tpl,
            start = start,
            calendarId = s.draft.calendarId.ifBlank {
                // Round 2.1.B.10 — prefer last-used over the deprecated
                // RepoConfig.defaultCalendarId binding.
                prefs.lastUsedCalendar(cfg.repoId) ?: cfg.defaultCalendarId.orEmpty()
            },
            author = author,
        )
        writeEvent(cfg, event, commitMessage = "add event from template \"${tpl.templateId}\"")
        closeSheet()
    }

    private fun writeEvent(cfg: RepoConfig, event: Event, commitMessage: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val rootDir = File(cfg.rootDir)
                EntityWriter.write(rootDir, event)
                val repo = openRepo(cfg)
                repo?.commitAll(commitMessage)
                _lastWritten.value = UndoPayload(event = event, repoId = cfg.repoId)
                // Round 2.1.B.10 — record last-used calendar for this
                // repo so the next openSheet() preselects it.
                if (event.calendarId.isNotBlank()) {
                    prefs.setLastUsedCalendar(cfg.repoId, event.calendarId)
                }
            }
        }
    }

    private suspend fun openRepo(cfg: RepoConfig): GitRepo? = withContext(Dispatchers.IO) {
        GitRepoRegistry.get(cfg.repoId) ?: runCatching {
            GitRepo.open(
                rootDir = File(cfg.rootDir),
                repoId = cfg.repoId,
                remotes = cfg.remotes,
                primaryRemote = cfg.primaryRemote,
                authorIdentity = cfg.authorIdentity,
                defaultBranch = cfg.defaultBranch,
            ).also(GitRepoRegistry::put)
        }.getOrNull()
    }

    private fun loadShippedTemplates() {
        val shipped = runCatching {
            context.assets.open("templates/index.toml").use { TemplateIndexLoader.load(it) }
        }.getOrDefault(emptyList())
        val user = loadUserTemplates()
        shippedTemplates = TemplateMerger.merge(
            shipped = shipped,
            user = user,
            packs = emptyList(),
        )
    }

    private fun loadUserTemplates(): List<TemplateEntry> {
        val cfg = activeRepoProvider() ?: return emptyList()
        val dir = File(cfg.rootDir, "templates").takeIf { it.isDirectory } ?: return emptyList()
        return dir.listFiles { f -> f.extension == "toml" }
            ?.mapNotNull { file ->
                runCatching {
                    val tpl = AtomicTemplateLoader.load(file.inputStream())
                    TemplateEntry(
                        templateId = tpl.templateId,
                        displayName = tpl.displayName,
                        category = tpl.category,
                        tags = emptyList(),
                        aliases = emptyList(),
                        neutralSafe = tpl.neutralSafe,
                        source = TemplateSource.User,
                    )
                }.getOrNull()
            } ?: emptyList()
    }

    private fun loadFullTemplate(entry: TemplateEntry): AtomicTemplate? = when (entry.source) {
        TemplateSource.Shipped -> runCatching {
            val file = entry.assetFile ?: return null
            context.assets.open("templates/$file").use { AtomicTemplateLoader.load(it) }
        }.getOrNull()
        TemplateSource.User -> {
            val cfg = activeRepoProvider()
            cfg?.let {
                val f = File(it.rootDir, "templates/${entry.templateId}.toml")
                runCatching { AtomicTemplateLoader.load(f.inputStream()) }.getOrNull()
            }
        }
        is TemplateSource.Pack -> null
    }

    /** Payload for the Undo snackbar (EC-B.3). */
    data class UndoPayload(val event: Event, val repoId: String)
}
