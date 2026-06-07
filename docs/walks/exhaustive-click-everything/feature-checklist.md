# Exhaustive "Click Everything" — Auto-Enumerated Control Inventory

> **What this is:** the machine-enumerated inventory of every screen / destination
> and every interactive control in the strictlykeptboy Compose UI, produced by the
> ENUMERATION pass of the exhaustive "click everything" QA walk. It is deliberately
> exhaustive ("a little dumb") — obvious controls are listed too. Nothing here was
> tested or run; this is purely a source-read inventory.
>
> **Source root:** `app/src/main/java/com/eight87/strictlykeptboy/ui/`
>
> **testTag column:** the `testTag` value is pulled from `TestTag…` constants /
> string literals found in source so a later QA pass can locate each control. Where
> a tag is built from a prefix (`"$Prefix$key"`), the prefix is shown with a `…`
> suffix. `n/a` = no testTag found on that control in source.
>
> Line format:
> `- [ ] <Screen> → <Control> — <expected behavior> — testTag: <tag-or-n/a>`

---

### App Shell / Chrome (scaffold)

- [ ] App Shell → Root shell container — hosts top-bar + rail + content + bottom mini-player — testTag: `SkbAppShell`
- [ ] App Shell → Shell content host — swaps the active destination pane — testTag: `ShellContent`
- [ ] App Shell → Master/detail row (tablet two-pane) — splits master + detail panes on Expanded width — testTag: `MasterDetailRow`
- [ ] App Shell → Master pane — left list pane in two-pane layout — testTag: `MasterPane`
- [ ] App Shell → Detail pane — right detail pane in two-pane layout — testTag: `DetailPane`

### Top Bar (SkbTopBar)

- [ ] Top Bar → Top-bar surface — app chrome header (title + actions) — testTag: `ShellTopBar`
- [ ] Top Bar → Title text — shows active destination/repo title — testTag: n/a
- [ ] Top Bar → Schedule destination button — selects Schedule destination — testTag: `ShellDest-Schedule`
- [ ] Top Bar → Reviews destination button — selects Reviews destination — testTag: `ShellDest-Reviews`
- [ ] Top Bar → Settings cog — opens Settings destination — testTag: `ShellSettingsCog`
- [ ] Top Bar → (latent) Repo-switcher icon button — switches repo (currently superseded by rail avatar) — testTag: `ShellRepoSwitcher`

### Left View-Mode Rail (SkbScheduleRail)

- [ ] Rail → Rail column container — vertical view-mode rail — testTag: `ShellRail`
- [ ] Rail → Rail tab item (per view tab, rotated label) — selects that view mode — testTag: `ShellRail-<key>` (e.g. `ShellRail-Now`)
- [ ] Rail → "Now" tab — agenda/timebox now view — testTag: `ShellRail-Now`
- [ ] Rail → "Day" tab — single-day view — testTag: `ShellRail-Day`
- [ ] Rail → "3-day" tab — three-day view — testTag: `ShellRail-ThreeDay`
- [ ] Rail → "Week" tab — week view — testTag: `ShellRail-Week`
- [ ] Rail → "Month" tab — month view — testTag: `ShellRail-Month`
- [ ] Rail → "Year" tab — year view — testTag: `ShellRail-Year`
- [ ] Rail → Overlay-picker filter icon (rail bottom) — opens overlay/calendar visibility picker — testTag: `Overlay-PickerButton`
- [ ] Rail → Overlay-picker badge — shows count of hidden overlays — testTag: `Overlay-PickerBadge`
- [ ] Rail → Repo avatar (rail bottom-most) — taps to switch repo / open Repos — testTag: `IdentityAvatar`

### Mode Pill / Dom-Persona (scaffold)

- [ ] Mode Pill → Mode pill — opens mode transition modal — testTag: `ModePill`
- [ ] Mode Pill → Transition modal — confirm mode change dialog — testTag: `ModePillTransitionModal`
- [ ] Mode Pill → Typed input field — type confirmation phrase to switch mode — testTag: `ModePillTypedInput`
- [ ] Mode Pill → Confirm button — commits the mode transition — testTag: `ModePillConfirmButton`
- [ ] Dom-Persona Sheet → Picker sheet — choose dom persona — testTag: `DomPersonaPickerSheet`

---

### Schedule Pane — common chrome (SchedulePane / ScheduleFloatingActions)

- [ ] Schedule → Master pane — schedule band list — testTag: `ScheduleMasterPane`
- [ ] Schedule → Detail-empty placeholder — empty detail pane prompt — testTag: `ScheduleDetailEmpty`
- [ ] Schedule → "+ New" / Event-create FAB — opens event-create FAB menu — testTag: `EventCreateFab`
- [ ] Schedule → Event-create FAB menu — expanded create-options menu — testTag: `EventCreateFabMenu`
- [ ] Schedule → FAB menu: New Event — opens new-event sheet — testTag: `EventCreateFabMenuNewEvent`
- [ ] Schedule → FAB menu: New Routine — opens routine/template flow — testTag: `EventCreateFabMenuRoutine`
- [ ] Schedule → FAB menu: Plan Trip — opens trip wizard — testTag: `EventCreateFabMenuPlanTrip`
- [ ] Schedule → FAB menu: Import .ics — opens ICS import — testTag: `EventCreateFabMenuIcs`
- [ ] Schedule → Edit-Schedule pen FAB — opens full-screen Edit Schedule — testTag: `EditScheduleRailButton`
- [ ] Schedule → Filter FAB — opens schedule filter menu — testTag: `ScheduleFilterFab`
- [ ] Schedule → Filter menu — filter options dropdown — testTag: `ScheduleFilterMenu`
- [ ] Schedule → Layout/density FAB — opens zoom/layout controls — testTag: `ScheduleLayoutFab`
- [ ] Schedule → Superseded toggle FAB — toggles superseded events visibility — testTag: `ScheduleSupersededFab`
- [ ] Schedule → Display-TZ chip — opens display timezone picker — testTag: `DisplayTzChip`
- [ ] Schedule → Display-TZ picker — timezone choice sheet — testTag: `DisplayTzPicker`
- [ ] Schedule → TZ option: System — use system timezone — testTag: `DisplayTzOption-System`
- [ ] Schedule → TZ option: Repo — use repo-pinned timezone — testTag: `DisplayTzOption-Repo`
- [ ] Schedule → Zoom level row — picks band zoom level — testTag: `ZoomLevelRow`
- [ ] Schedule → Zoom: Auto — auto zoom — testTag: `ZoomLevel-Auto`
- [ ] Schedule → Zoom: 40 — 40dp/hr — testTag: `ZoomLevel-40`
- [ ] Schedule → Zoom: 80 — 80dp/hr — testTag: `ZoomLevel-80`
- [ ] Schedule → Zoom: 160 — 160dp/hr — testTag: `ZoomLevel-160`
- [ ] Schedule → Zoom: 320 — 320dp/hr — testTag: `ZoomLevel-320`
- [ ] Schedule → Empty-state CTA — prompt when no events — testTag: `EmptyCta`
- [ ] Schedule → Empty: Setup Wizard CTA — launches wizard — testTag: `EmptySetupWizard`
- [ ] Schedule → Empty: Plan Trip CTA — launches trip wizard — testTag: `EmptyPlanTrip`
- [ ] Schedule → Empty bat illustration — decorative empty art — testTag: `EmptyBat`
- [ ] Schedule → Empty message — empty-state copy — testTag: `EmptyMessage`
- [ ] Schedule → Partial-push degraded dot — indicates partial sync state — testTag: `PartialPushDegradedDot`

### Schedule — Day Bands / Event Bands (GroupedDayBand / BandOverlays)

- [ ] Schedule → Day band — tappable day grouping header/band — testTag: `DayBand`
- [ ] Schedule → Day band pinned-TZ marker — shows pinned timezone — testTag: `DayBand-PinnedTz`
- [ ] Schedule → Event band (overlay) — taps to open event detail — testTag: `WeekBand` / `DayBand` (per view)
- [ ] Schedule → Band color stripe — calendar color indicator — testTag: `BandColorStripe`
- [ ] Schedule → Band kind glyph — event-kind icon — testTag: `BandKindGlyph`
- [ ] Schedule → Band author chip — author indicator — testTag: `BandAuthorChip`
- [ ] Schedule → Band off-schedule glyph — off-schedule marker — testTag: `BandOffScheduleGlyph`
- [ ] Schedule → Band superseded glyph — superseded marker — testTag: `BandSupersededGlyph`
- [ ] Schedule → Band dashed border — tentative/draft marker — testTag: `BandDashedBorder`
- [ ] Schedule → Now-line indicator — current-time line — testTag: `NowLine`
- [ ] Schedule → Event band long-press/drag — drag-to-reschedule affordance — testTag: n/a (gesture, see DragReschedule)

### Schedule — Drag Reschedule (DragReschedule*)

- [ ] Schedule → Drag-reschedule gesture — long-press + drag a band to move it — testTag: n/a
- [ ] Schedule → Recurrence prompt — appears when dragging a recurring event — testTag: `DragRecurrencePrompt`
- [ ] Schedule → Recurrence: This one — apply to single instance — testTag: `DragRecurThisOne`
- [ ] Schedule → Recurrence: This & future — apply forward — testTag: `DragRecurThisAndFuture`
- [ ] Schedule → Recurrence: Whole series — apply to entire series — testTag: `DragRecurWholeSeries`

### Schedule — Agenda / Now view (ScheduleAgendaView)

- [ ] Agenda → Agenda view container — scrollable agenda list — testTag: `ScheduleAgendaView`
- [ ] Agenda → Agenda day header — day section header — testTag: `AgendaDayHeader`
- [ ] Agenda → Agenda row — tappable agenda entry → detail — testTag: `AgendaRow`
- [ ] Agenda → Agenda empty placeholder — no-events state — testTag: `AgendaEmpty`

### Schedule — Day view (ScheduleDayView)

- [ ] Day View → Day view container — single-day timeline — testTag: `ScheduleDayView`
- [ ] Day View → Day empty placeholder — no-events state — testTag: `DayEmpty`

### Schedule — 3-Day view (ScheduleThreeDayView)

- [ ] 3-Day View → 3-day view container — three-column timeline — testTag: `ScheduleThreeDayView`
- [ ] 3-Day View → Three-day column — per-day column (tap day) — testTag: `ThreeDayColumn`

### Schedule — Week view (ScheduleWeekView)

- [ ] Week View → Week view container — week timeline grid — testTag: `ScheduleWeekView`
- [ ] Week View → Week header — week-of label header — testTag: `WeekHeader`
- [ ] Week View → Week column — per-day column (tap day) — testTag: `WeekColumn`
- [ ] Week View → Week today column — highlighted current day column — testTag: `WeekTodayColumn`
- [ ] Week View → Week band — event band within week grid — testTag: `WeekBand`

### Schedule — Month view (ScheduleMonthView)

- [ ] Month View → Month view container — month grid — testTag: `ScheduleMonthView`
- [ ] Month View → Month chip — month label/nav chip — testTag: `MonthChip`
- [ ] Month View → Month cell — tappable day cell → day — testTag: `MonthCell`
- [ ] Month View → Month today cell — highlighted today cell — testTag: `MonthTodayCell`
- [ ] Month View → Month overflow ("+N more") — expands day's events — testTag: `MonthOverflow`

### Schedule — Year view (ScheduleYearView)

- [ ] Year View → Year view container — twelve-month grid — testTag: `ScheduleYearView`
- [ ] Year View → Year-month cell block — tappable month → month view — testTag: `YearMonth`
- [ ] Year View → Year mini-month cell — day cell in mini month — testTag: `YearMonthCell`

### Schedule — Timebox view (ScheduleTimeboxView / TimeboxRow)

- [ ] Timebox → Timebox view container — timeboxed today list — testTag: `ScheduleTimeboxView`
- [ ] Timebox → Timebox row — tappable timebox entry — testTag: `TimeboxRow`
- [ ] Timebox → Timebox card — timebox card element — testTag: `TimeboxCard`
- [ ] Timebox → Timebox now card — current timebox highlight — testTag: `TimeboxNowCard`
- [ ] Timebox → Timebox regular card — upcoming timebox card — testTag: `TimeboxRegularCard`
- [ ] Timebox → Timebox regular section — grouping section — testTag: `TimeboxRegularSection`
- [ ] Timebox → Timebox external-account header — external source group — testTag: `TimeboxExternalAccountHeader`
- [ ] Timebox → Timebox empty placeholder — no-timeboxes state — testTag: `TimeboxEmpty`

### Schedule — Edit Schedule screen (EditScheduleScreen)

- [ ] Edit Schedule → Edit-schedule screen — full-screen agenda editor — testTag: `EditScheduleScreen`
- [ ] Edit Schedule → Edit-schedule agenda list — Monday-anchored agenda — testTag: `EditScheduleAgenda`

---

### Event Create — Sheet (EventCreateSheet / FreeFormForm)

- [ ] Event Create → Create sheet — bottom sheet for new event — testTag: `EventCreateSheet`
- [ ] Event Create → Tab: Free-form — switches to free-form form — testTag: `EventCreateSheetTabFreeForm`
- [ ] Event Create → Tab: Template — switches to template picker — testTag: `EventCreateSheetTabTemplate`
- [ ] Event Create → Free-form form container — manual event entry — testTag: `EventCreateFreeForm`
- [ ] Event Create → Title field — event title input — testTag: `EventCreateTitle`
- [ ] Event Create → Start field — start date/time picker — testTag: `EventCreateStartField`
- [ ] Event Create → End field — end date/time picker — testTag: `EventCreateEndField`
- [ ] Event Create → Notes field — body/notes input — testTag: `EventCreateNotes`
- [ ] Event Create → Calendar chip — picks target calendar — testTag: `EventCreateCalendarChip`
- [ ] Event Create → Repeat chip — picks recurrence rule — testTag: `EventCreateRepeatChip`
- [ ] Event Create → Private toggle — marks event private — testTag: `EventCreatePrivateToggle`
- [ ] Event Create → Confirm button — creates the event — testTag: `EventCreateConfirm`
- [ ] Event Create → Overlap dialog — warns of overlapping events — testTag: `EventCreateOverlapDialog`
- [ ] Event Create → Overlap: Schedule anyway — proceed despite overlap — testTag: `OverlapScheduleAnyway`
- [ ] Event Create → Overlap: Pick different — choose another slot — testTag: `OverlapPickDifferent`
- [ ] Event Create → Overlap: Cancel — abort create — testTag: `OverlapCancel`

### Event Create — Template picker (TemplatePickerContent / TemplateConfirmSheet)

- [ ] Template Picker → Picker container — list of templates — testTag: `TemplatePicker`
- [ ] Template Picker → Search field — filter templates — testTag: `TemplatePickerSearch`
- [ ] Template Picker → Template row — selects a template — testTag: `TemplatePickerRow`
- [ ] Template Picker → Empty placeholder — no templates state — testTag: `TemplatePickerEmpty`
- [ ] Template Picker → Empty free-form chip — fall back to free-form — testTag: `TemplatePickerEmptyFreeFormChip`
- [ ] Template Confirm → Confirm sheet — preview before materialize — testTag: `TemplateConfirmSheet`
- [ ] Template Confirm → Sub-beat row — per-beat detail — testTag: `TemplateConfirmSubbeat`
- [ ] Template Confirm → Confirm button — materialize template — testTag: `TemplateConfirmBtn`
- [ ] Template Confirm → Cancel button — dismiss — testTag: `TemplateConfirmCancelBtn`

### Event Detail — Sheet / Screen (EventDetailSheet / EventDetailScreen)

- [ ] Event Detail → Detail sheet — event detail bottom sheet — testTag: `EventDetailSheet`
- [ ] Event Detail → Detail screen — full-screen event detail — testTag: `EventDetailScreen`
- [ ] Event Detail → Detail pane (two-pane) — detail pane variant — testTag: `EventDetailPane`
- [ ] Event Detail → Back button — closes detail — testTag: `EventDetailBack`
- [ ] Event Detail → Edit button — opens event editor — testTag: `EventDetailEdit`
- [ ] Event Detail → Title — event title — testTag: `EventDetailTitle`
- [ ] Event Detail → Time — start/end time — testTag: `EventDetailTime`
- [ ] Event Detail → Body — markdown body — testTag: `EventDetailBody`
- [ ] Event Detail → Calendar — calendar name — testTag: `EventDetailCalendar`
- [ ] Event Detail → Tag — event tag — testTag: `EventDetailTag`
- [ ] Event Detail → Author — author chip — testTag: `EventDetailAuthor`
- [ ] Event Detail → Completion toggle/control — mark complete — testTag: `EventDetailCompletion`
- [ ] Event Detail → Mute control — mute reminders — testTag: `EventDetailMute`
- [ ] Event Detail → Attachment — open attachment — testTag: `EventDetailAttachment`
- [ ] Event Detail → Attendees section — attendee list — testTag: `EventDetailAttendees`
- [ ] Event Detail → Attendee row — single attendee — testTag: `EventDetailAttendeeRow`
- [ ] Event Detail → Reminders section — reminder list — testTag: `EventDetailReminders`
- [ ] Event Detail → Reminder row — single reminder — testTag: `EventDetailReminderRow`
- [ ] Event Detail → External read-only banner — external source lock — testTag: `EventDetailExternalReadOnly`
- [ ] Event Detail → External source label — external origin — testTag: `EventDetailExternalSource`
- [ ] Event Detail → Superseded note — superseded explanation — testTag: `EventDetailSupersededNote`
- [ ] Event Detail → Source section — source provenance block — testTag: `EventDetailSource`
- [ ] Event Detail → Source author — source author — testTag: `EventDetailSourceAuthor`
- [ ] Event Detail → Source kind — source kind — testTag: `EventDetailSourceKind`
- [ ] Event Detail → Source repo — source repo — testTag: `EventDetailSourceRepo`
- [ ] Event Detail → Source priority — source priority — testTag: `EventDetailSourcePriority`
- [ ] Event Detail → Source linked tasks — jump to linked tasks — testTag: `EventDetailSourceLinkedTasks`

---

### Tasks Pane (TasksPane) + bottom-sheet now-playing surface

- [ ] Tasks → Tasks pane container — tasks destination — testTag: `TasksPane`
- [ ] Tasks → Destination body — tasks content body — testTag: `TasksDestinationBody`
- [ ] Tasks → Source rail — task source/list rail — testTag: `TaskSourceRail`
- [ ] Tasks → Source chip — selects a task source — testTag: `TaskSourceChip`
- [ ] Tasks → Per-list inline rail — inline list switcher — testTag: `TasksPerListInlineRail`
- [ ] Tasks → Show-inactive toggle — toggles inactive tasks — testTag: `TaskShowInactiveToggle`
- [ ] Tasks → Empty bat illustration — empty art — testTag: `EmptyTasksBat`
- [ ] Tasks → Empty message — empty copy — testTag: `EmptyTasksMessage`

### Tasks — View modes (Combined / PerList / Standing / Today / Shopping / UnifiedToday)

- [ ] Tasks → Combined view — combined task list — testTag: `CombinedView`
- [ ] Tasks → Per-list view — per-list grouping — testTag: `PerListView`
- [ ] Tasks → Per-list summary — list summary header — testTag: `PerListSummary`
- [ ] Tasks → Per-list inactive banner — inactive-list notice — testTag: `PerListInactiveBanner`
- [ ] Tasks → Standing view — standing tasks list — testTag: `StandingView`
- [ ] Tasks → Today view — today's tasks — testTag: `TodayView`
- [ ] Tasks → Today section — today grouping — testTag: `TodaySection`
- [ ] Tasks → Unified-today view — unified today rollup — testTag: `UnifiedTodayView`
- [ ] Tasks → Unified-today section — unified section — testTag: `UnifiedTodaySection`
- [ ] Tasks → Unified-today next hint — "next up" hint — testTag: `UnifiedTodayNextHint`
- [ ] Tasks → Shopping view — shopping list view — testTag: `ShoppingView`
- [ ] Tasks → Shopping row — shopping item row — testTag: `ShoppingRow`
- [ ] Tasks → Shopping checkbox — check off item — testTag: `ShoppingCheckbox`
- [ ] Tasks → Completed divider — divides done/undone — testTag: `CompletedDivider`

### Tasks — Task Row (TaskRow)

- [ ] Task Row → Row container — tappable task → detail — testTag: `TaskRow`
- [ ] Task Row → Checkbox — complete/uncomplete task — testTag: `TaskCheckbox`
- [ ] Task Row → Title — task title — testTag: `TaskTitle`
- [ ] Task Row → Author chip — author — testTag: `TaskAuthor`
- [ ] Task Row → Repo dot — owning-repo color dot — testTag: `TaskRepoDot`
- [ ] Task Row → Due chip — due date chip — testTag: `TaskDueChip`
- [ ] Task Row → List chip — owning list chip — testTag: `TaskListChip`
- [ ] Task Row → List filter chip — filter by list — testTag: `ListFilterChip`
- [ ] Task Row → Linked-timebox indicator — jump to timebox — testTag: `TaskLinkedTimebox`
- [ ] Task Row → Start button — start/play task — testTag: `TaskStartButton`
- [ ] Task Row → Prompt open pill — open keeper prompt — testTag: `TaskPromptOpenPill`
- [ ] Task Row → Prompt keeper chip — keeper prompt marker — testTag: `TaskPromptKeeperChip`
- [ ] Task Row → Prompt respond button — respond to prompt — testTag: `TaskPromptRespond`

### Tasks — Quick Add (TaskQuickAddFab)

- [ ] Quick Add → Quick-add FAB — opens quick-add sheet — testTag: `QuickAddFab`
- [ ] Quick Add → Quick-add sheet — quick-add bottom sheet — testTag: `QuickAddSheet`
- [ ] Quick Add → Input field — type new task — testTag: `QuickAddInput`
- [ ] Quick Add → Target picker — choose target list — testTag: `QuickAddTarget`
- [ ] Quick Add → Submit button — add the task — testTag: `QuickAddSubmit`

### Tasks — Task Detail Sheet (TaskDetailSheet)

- [ ] Task Detail → Detail sheet — task detail bottom sheet — testTag: `TaskDetailSheet`
- [ ] Task Detail → Detail pane (two-pane) — detail pane variant — testTag: `TaskDetailPane`
- [ ] Task Detail → Title — task title — testTag: `TaskDetailTitle`
- [ ] Task Detail → Edit button — edit task — testTag: `TaskDetailEdit`
- [ ] Task Detail → List — owning list — testTag: `TaskDetailList`
- [ ] Task Detail → Author — author — testTag: `TaskDetailAuthor`
- [ ] Task Detail → Subtask row — subtask entry — testTag: `TaskDetailSubtask`
- [ ] Task Detail → Attachment — open attachment — testTag: `TaskDetailAttachment`

### Tasks — Prompt Response Sheet (PromptResponseSheet)

- [ ] Prompt Response → Sheet — keeper-prompt response sheet — testTag: `PromptResponseSheet`
- [ ] Prompt Response → Body field — response text — testTag: `PromptResponseBody`
- [ ] Prompt Response → Attachment — attach to response — testTag: `PromptResponseAttachment`
- [ ] Prompt Response → Send button — submit response — testTag: `PromptResponseSend`
- [ ] Prompt Response → Cancel button — dismiss — testTag: `PromptResponseCancel`

### Tasks — Task List Settings Sheet

- [ ] Task List Settings → Settings sheet — per-list settings — testTag: `TaskListSettingsSheet`

### Now Playing / Mini Player (playing/)

- [ ] Mini Player → Mini-player bar — bottom now-playing strip (tap to expand) — testTag: n/a
- [ ] Now Playing → Expanded body — pulled-up now-playing sheet — testTag: `ExpandedTaskBody`
- [ ] Now Playing → Chip strip — filter chips on expanded body — testTag: `ExpandedTaskChipStrip`
- [ ] Now Playing → Chip (per filter) — applies a TasksFilter — testTag: `ExpandedTaskChip-<filter>`
- [ ] Now Playing → Sub readable cue — sub/readable indicator — testTag: `SubReadableCue`
- [ ] Now Playing → Playback transport row — play/pause/skip controls — testTag: n/a
- [ ] Now Playing → Queue section — reorderable task queue — testTag: n/a
- [ ] Now Playing → Queue drag-reorder — long-press + drag to reorder queue — testTag: n/a

---

### Reviews Pane (ReviewsPane)

- [ ] Reviews → Reviews pane container — review feed — testTag: `ReviewsPane`
- [ ] Reviews → Review item (per entry) — tappable review entry — testTag: `ReviewItem-<id>`
- [ ] Reviews → Reaction button (per reaction) — react to a review — testTag: `ReviewReaction-<r>`
- [ ] Reviews → Composer input — write a review/note — testTag: `ReviewComposerInput`
- [ ] Reviews → Submit button — post review — testTag: `ReviewSubmit`
- [ ] Reviews → Mark-all-read button — clear unread — testTag: `ReviewsMarkAllRead`
- [ ] Reviews → Empty placeholder — no reviews state — testTag: `ReviewsEmpty`

---

### Together Pane (TogetherPane / TogetherInputForm / TogetherResultList)

- [ ] Together → Together pane container — common-time finder — testTag: `TogetherPane`
- [ ] Together → Input form — common-time query form — testTag: `TogetherForm`
- [ ] Together → Repo chip (per repo) — toggle a participant repo — testTag: `TogetherRepoChip-<repo>`
- [ ] Together → Participant TZ chip (per participant) — set participant timezone — testTag: `TogetherParticipantTz-<p>`
- [ ] Together → Day-of-week chip (per DOW) — toggle eligible weekday — testTag: `TogetherDowChip-<dow>`
- [ ] Together → Submit button — run common-time search — testTag: `TogetherSubmit`
- [ ] Together → Running indicator — search-in-progress — testTag: `TogetherRunning`
- [ ] Together → Result list — list of common slots — testTag: `TogetherResultList`
- [ ] Together → Result card (per slot) — tappable suggested slot — testTag: `TogetherResultCard-<i>`
- [ ] Together → Empty bat illustration — empty art — testTag: `TogetherEmptyBat`
- [ ] Together → Empty message — empty copy — testTag: `TogetherEmptyMessage`
- [ ] Together → Empty-repos prompt — needs ≥2 repos notice — testTag: `TogetherEmptyRepos`

---

### Repos Pane (ReposPane / ReposList / RepoCard)

- [ ] Repos → Repos pane container — repo list destination — testTag: `ReposPane`
- [ ] Repos → Repo switcher (pane header) — switch active repo — testTag: `ReposPane-Switcher`
- [ ] Repos → Pane empty placeholder — no repos state — testTag: `ReposPane-Empty`
- [ ] Repos → Detail-empty placeholder — empty detail pane — testTag: `ReposPane-DetailEmpty`
- [ ] Repos → Backup-reminder banner — prompts backup — testTag: `ReposPane-BackupReminderBanner`
- [ ] Repos → Backup reminder: Pick — choose backup folder — testTag: `ReposPane-BackupReminderPick`
- [ ] Repos → Backup reminder: Dismiss — dismiss reminder — testTag: `ReposPane-BackupReminderDismiss`
- [ ] Repos → SAF-revoked banner — storage access revoked notice — testTag: `ReposPane-SafRevokedBanner`
- [ ] Repos → SAF-revoked: Re-pick — re-grant storage access — testTag: `ReposPane-SafRevokedRepick`

### Repos — Repo Card (RepoCard)

- [ ] Repo Card → Card container — tappable repo card — testTag: `RepoCard`
- [ ] Repo Card → Header — card header (tap to expand) — testTag: `RepoCard-Header`
- [ ] Repo Card → Expand icon — expand/collapse card — testTag: `RepoCard-ExpandIcon`
- [ ] Repo Card → Repo icon — repo avatar/icon — testTag: `RepoIcon`
- [ ] Repo Card → Local-only badge — no-remote indicator — testTag: `RepoCard-LocalOnly`
- [ ] Repo Card → Write-target indicator — write-target marker — testTag: `RepoCard-WriteTarget`
- [ ] Repo Card → Sync-now button — trigger sync — testTag: `RepoCard-SyncNow`
- [ ] Repo Card → More-settings button — open repo settings — testTag: `RepoCard-MoreSettings`
- [ ] Repo Card → Adjust-TZ coach mark — timezone coaching hint — testTag: `RepoCard-AdjustTzCoach`
- [ ] Repo Card → Switch: Show on schedule — toggle schedule visibility — testTag: `RepoCard-Switch-ShowOnSchedule`
- [ ] Repo Card → Switch: Auto-sync — toggle auto-sync — testTag: `RepoCard-Switch-AutoSync`
- [ ] Repo Card → Switch: Wi-Fi only — toggle wifi-only sync — testTag: `RepoCard-Switch-WifiOnly`
- [ ] Repo Card → Switch: Draw tasks from — toggle task-source inclusion — testTag: `RepoCard-Switch-DrawTasksFrom`
- [ ] Repo Card → Switch: Import stickers — toggle sticker import — testTag: `RepoCard-Switch-ImportStickers`
- [ ] Repo Card → Switch: Adjust to local timezone — toggle TZ adjustment — testTag: `RepoCard-Switch-AdjustToLocalTimezone`

### Repos — Repo Switcher Chip / Dropdown (RepoSwitcherChip / RepoSwitcherDropdown)

- [ ] Repo Switcher → Switcher chip — opens repo dropdown — testTag: `RepoSwitcherChip`
- [ ] Repo Switcher → Chip leading icon — chip leading glyph — testTag: `RepoSwitcherChip-Leading`
- [ ] Repo Switcher → House glyph — system/home repo glyph — testTag: `RepoSwitcherHouseGlyph`
- [ ] Repo Switcher → Mode badge — current-mode badge — testTag: `RepoSwitcherModeBadge`
- [ ] Repo Switcher → Dropdown — repo list dropdown — testTag: `RepoSwitcherDropdown`
- [ ] Repo Switcher → Repo row — select that repo — testTag: `RepoSwitcherRow`
- [ ] Repo Switcher → System header — system-repos section header — testTag: `RepoSwitcherSystemHeader`
- [ ] Repo Switcher → System row — select system repo — testTag: `RepoSwitcherSystemRow`
- [ ] Repo Switcher → Add repo — start add-repo flow — testTag: `RepoSwitcherAdd`
- [ ] Repo Switcher → Settings — open settings — testTag: `RepoSwitcherSettings`
- [ ] Repo Switcher → Sync — sync from dropdown — testTag: `RepoSwitcherSync`
- [ ] Repo Switcher → Show-on-schedule toggle — toggle schedule visibility — testTag: `RepoSwitcherShowOnSchedule`
- [ ] Repo Switcher → Draw-tasks-from toggle — toggle task source — testTag: `RepoSwitcherDrawTasksFrom`

### Repos — Repo Settings (RepoSettingsScreen / RepoSettingsHost)

- [ ] Repo Settings → Settings screen — per-repo settings — testTag: `RepoSettings`
- [ ] Repo Settings → Identities section — manage identities — testTag: `RepoSettings-Identities`
- [ ] Repo Settings → Per-repo identity — set repo identity — testTag: `RepoSettings-PerRepoIdentity`
- [ ] Repo Settings → Per-repo mode — set repo mode — testTag: `RepoSettings-PerRepoMode`
- [ ] Repo Settings → Auto-sync toggle — toggle auto-sync — testTag: `RepoSettings-AutoSync`
- [ ] Repo Settings → Wi-Fi only toggle — toggle wifi-only — testTag: `RepoSettings-WifiOnly`
- [ ] Repo Settings → Share — open share sheet — testTag: `RepoSettings-Share`
- [ ] Repo Settings → Sticker pack — open sticker-pack selector — testTag: `RepoSettings-StickerPack`
- [ ] Repo Settings → Calendars section — manage calendars — testTag: `RepoSettings-Calendars`
- [ ] Repo Settings → Calendar row — open a calendar — testTag: `RepoSettings-CalendarRow`
- [ ] Repo Settings → Calendar toggle — enable/disable calendar — testTag: `RepoSettings-CalendarToggle`
- [ ] Repo Settings → Calendar add — add a calendar — testTag: `RepoSettings-CalendarAdd`
- [ ] Repo Settings → Calendar new confirm — confirm new calendar — testTag: `RepoSettings-CalendarNewConfirm`
- [ ] Repo Settings → Remotes section — manage remotes — testTag: `RepoSettings-Remotes`
- [ ] Repo Settings → Remote row — open a remote — testTag: `RepoSettings-RemoteRow`
- [ ] Repo Settings → Add remote — add a remote — testTag: `RepoSettings-AddRemote`
- [ ] Repo Settings → Remove remote — remove a remote — testTag: `RepoSettings-RemoveRemote`
- [ ] Repo Settings → Remote read-only toggle — set remote read-only — testTag: `RepoSettings-RemoteReadOnly`
- [ ] Repo Settings → No-remotes CTA — prompt to add remote — testTag: `RepoSettings-NoRemotesCta`
- [ ] Repo Settings → Remove repo — remove this repo — testTag: `RepoSettings-RemoveRepo`
- [ ] Repo Settings → Confirm remove — confirm repo removal — testTag: `RepoSettings-ConfirmRemove`
- [ ] Repo Settings → Confirm delete-local — confirm local delete — testTag: `RepoSettings-ConfirmDeleteLocal`

### Repos — Identities Screen (IdentitiesScreen)

- [ ] Identities → Identities screen — identity manager — testTag: `IdentitiesScreen`
- [ ] Identities → Identity row — select/edit identity — testTag: `IdentityRow`
- [ ] Identities → Identity avatar — avatar — testTag: `IdentityAvatar`
- [ ] Identities → Add identity — add new identity — testTag: `IdentitiesAdd`

### Repos — Repo Icon Picker (RepoIconPicker)

- [ ] Repo Icon Picker → Picker container — choose repo icon — testTag: `RepoIconPicker`
- [ ] Repo Icon Picker → Emoji swatch — pick emoji icon — testTag: `RepoIconPicker-Emoji`
- [ ] Repo Icon Picker → Emoji field — type emoji — testTag: `RepoIconPicker-EmojiField`
- [ ] Repo Icon Picker → Initials button — use initials — testTag: `RepoIconPicker-Initials`
- [ ] Repo Icon Picker → Photo button — pick a photo — testTag: `RepoIconPicker-Photo`

### Repos — Sticker Pack Selector (StickerPackSelectorScreen)

- [ ] Sticker Pack → Selector screen — choose sticker pack — testTag: `StickerPackSelector`
- [ ] Sticker Pack → Radio (per pack) — select a pack — testTag: `StickerPackSelector-Radio`
- [ ] Sticker Pack → Import button — import custom pack — testTag: `StickerPackSelector-Import`
- [ ] Sticker Pack → Import dialog — import-pack dialog — testTag: `StickerPackSelector-ImportDialog`

### Repos — Repo Move Job Dialog (RepoMoveJobDialog)

- [ ] Repo Move → Dialog — move-repo progress dialog — testTag: `RepoMoveJobDialog`
- [ ] Repo Move → Cancel — cancel the move — testTag: `RepoMoveJobDialog-Cancel`
- [ ] Repo Move → Done — finish the move — testTag: `RepoMoveJobDialog-Done`

### Repos — Overlay screens

- [ ] Repos Overlay → Overlay container — repos overlay screen — testTag: `ReposOverlay`
- [ ] Repos Overlay → Back button — close overlay — testTag: `ReposOverlayBack`

---

### Add Repo Flow (AddRepoNavHost)

- [ ] Add Repo → Flow container — add-repo wizard — testTag: `AddRepo`
- [ ] Add Repo → Back button — previous step — testTag: `AddRepo-Back`
- [ ] Add Repo → Next button — next step — testTag: `AddRepo-Next`
- [ ] Add Repo → Cancel button — abort flow — testTag: `AddRepo-Cancel`
- [ ] Add Repo → Done button — complete step — testTag: `AddRepo-Done`
- [ ] Add Repo → Finish button — finish flow — testTag: `AddRepo-Finish`
- [ ] Add Repo → Add another — add another repo — testTag: `AddRepo-AddAnother`
- [ ] Add Repo → Local form — local-repo form — testTag: `AddRepo-LocalForm`
- [ ] Add Repo → Remote form — remote-repo form — testTag: `AddRepo-RemoteForm`
- [ ] Add Repo → Display field — display name input — testTag: `AddRepo-DisplayField`
- [ ] Add Repo → Name field — repo name input — testTag: `AddRepo-NameField`
- [ ] Add Repo → URL field — remote URL input — testTag: `AddRepo-UrlField`
- [ ] Add Repo → Username field — username input — testTag: `AddRepo-UsernameField`
- [ ] Add Repo → Email field — email input — testTag: `AddRepo-EmailField`
- [ ] Add Repo → PAT field — personal access token input — testTag: `AddRepo-PatField`
- [ ] Add Repo → Local branch field — local branch name — testTag: `AddRepo-BranchLocal`
- [ ] Add Repo → Remote branch field — remote branch name — testTag: `AddRepo-BranchRemote`
- [ ] Add Repo → Remote provider picker — choose provider (GitHub/Forgejo/…) — testTag: `AddRepo-RemoteProvider`
- [ ] Add Repo → Remote auth section — auth method block — testTag: `AddRepo-RemoteAuth`
- [ ] Add Repo → Auth: OAuth — OAuth device flow — testTag: `AddRepo-AuthOAuth`
- [ ] Add Repo → Auth: PAT — PAT auth — testTag: `AddRepo-AuthPat`
- [ ] Add Repo → Auth: SSH — SSH auth — testTag: `AddRepo-AuthSsh`
- [ ] Add Repo → OAuth progress — device-flow progress — testTag: `AddRepo-OAuthProgress`

---

### Trip Wizard (TripWizardNavHost)

- [ ] Trip Wizard → Wizard container — trip planning flow — testTag: `TripWizard`
- [ ] Trip Wizard → Basics step — trip basics — testTag: `TripWizard-Basics`
- [ ] Trip Wizard → Transport step — transport choices — testTag: `TripWizard-Transport`
- [ ] Trip Wizard → Anchors step — anchor events — testTag: `TripWizard-Anchors`
- [ ] Trip Wizard → Back button — previous step — testTag: `TripWizard-Back`
- [ ] Trip Wizard → Next button — next step — testTag: `TripWizard-Next`
- [ ] Trip Wizard → Confirm button — confirm trip — testTag: `TripWizard-Confirm`
- [ ] Trip Wizard → Materialize button — materialize trip events — testTag: `TripWizard-Materialize`
- [ ] Trip Wizard → Discard dialog — confirm-discard dialog — testTag: `TripWizard-DiscardDialog`

---

### Intro Wizard (wizard/intro/IntroWizardHost)

- [ ] Intro Wizard → Wizard container — first-run intro — testTag: `IntroWizard`
- [ ] Intro Wizard → Manifesto — intro manifesto text — testTag: `IntroWizard-Manifesto`
- [ ] Intro Wizard → Supported scenarios — scenario list — testTag: `IntroWizard-SupportedScenarios`
- [ ] Intro Wizard → Picker — scenario picker — testTag: `IntroWizard-Picker`
- [ ] Intro Wizard → Confirm — confirm picker — testTag: `IntroWizard-Confirm`
- [ ] Intro Wizard → Empty card row — empty scenario row — testTag: `IntroWizard-Card-Empty`
- [ ] Intro Wizard → Rich-demo card row — pick rich demo — testTag: `IntroWizard-Card-RichDemo`
- [ ] Intro Wizard → Rich-demo pill — rich-demo selector pill — testTag: `IntroWizard-RichDemo-Pill`

### Age Gate (wizard/AgeGateScreen)

- [ ] Age Gate → Gate screen — 17+ confirmation — testTag: `AgeGate`
- [ ] Age Gate → Accept button — confirm age — testTag: `AgeGate-Accept`
- [ ] Age Gate → Decline button — decline / exit — testTag: `AgeGate-Decline`

### Setup Wizard (wizard/WizardNavHost + screens/)

- [ ] Wizard → Wizard container — repo-setup wizard — testTag: `Wizard`
- [ ] Wizard → Welcome step — welcome screen — testTag: `Wizard-Welcome`
- [ ] Wizard → Identity step — identity setup — testTag: `Wizard-Identity`
- [ ] Wizard → Species step — species/persona pick — testTag: `Wizard-Species`
- [ ] Wizard → Roles step — role selection — testTag: `Wizard-Roles`
- [ ] Wizard → Lifestyle step — lifestyle setup — testTag: `Wizard-Lifestyle`
- [ ] Wizard → Templates step — pick templates — testTag: `Wizard-Templates`
- [ ] Wizard → Git step — git/remote setup — testTag: `Wizard-Git`
- [ ] Wizard → Scaffold step — scaffold preview — testTag: `Wizard-Scaffold`
- [ ] Wizard → Share-with-Dom step — share to dom — testTag: `Wizard-ShareWithDom`
- [ ] Wizard → Done step — finish screen — testTag: `Wizard-Done`
- [ ] Wizard → Preview pane (tablet) — live preview — testTag: `Wizard-PreviewPane`
- [ ] Wizard → Back button — previous step — testTag: `Wizard-Back`
- [ ] Wizard → Next button — next step — testTag: `Wizard-Next`
- [ ] Wizard → Skip button — skip step — testTag: `Wizard-Skip`
- [ ] Wizard → Done button — complete wizard — testTag: `Wizard-Done`
- [ ] Wizard → Discard dialog — confirm-discard — testTag: `Wizard-DiscardDialog`

---

### Settings Pane — chrome (SettingsPane)

- [ ] Settings → Settings pane container — settings root — testTag: `SettingsPane`
- [ ] Settings → Category list — scrollable category list — testTag: `SettingsCategoryList`
- [ ] Settings → Category row (per category) — opens that category — testTag: `SettingsCategory-<name>`
- [ ] Settings → Settings content — selected-category content host — testTag: `SettingsContent`
- [ ] Settings → Search field — searches settings rows — testTag: n/a (Icons.Filled.Search present)
- [ ] Settings → Back button — leave settings/category — testTag: `SettingsBack`
- [ ] Settings → Overlay container — settings overlay screen — testTag: `SettingsOverlay`
- [ ] Settings → Overlay back — close overlay — testTag: `SettingsOverlayBack`

### Settings — Category: Appearance (AppearanceCategory)

- [ ] Appearance → Category container — appearance settings — testTag: `Cat-Appearance`
- [ ] Appearance → Theme selector (dark/light/auto/system) — sets theme mode — testTag: n/a
- [ ] Appearance → Base-theme / palette selector — sets base palette — testTag: n/a
- [ ] Appearance → Tint-by-repo-avatar toggle — tint chrome by repo — testTag: n/a
- [ ] Appearance → Custom chrome-tint control — custom tint color — testTag: n/a
- [ ] Appearance → Density selector (compact/comfortable/spacious) — sets density — testTag: n/a
- [ ] Appearance → Font-scale slider — adjusts font scale — testTag: n/a
- [ ] Appearance → Neutral-mode toggle — discreet/neutral mode — testTag: n/a
- [ ] Appearance → Sticker section — sticker-pack section — testTag: `Cat-Appearance-StickerSection`
- [ ] Appearance → Sticker-pack chip — opens sticker-pack selector — testTag: `Cat-Appearance-StickerPackChip`

### Settings — Category: Identity (IdentityCategory)

- [ ] Identity → Category container — identity settings — testTag: `Cat-Identity`
- [ ] Identity → Praise term field — set praise term — testTag: n/a
- [ ] Identity → Pronouns field — set pronouns — testTag: n/a
- [ ] Identity → Honorific field — set honorific — testTag: n/a
- [ ] Identity → Tone register selector — set tone — testTag: n/a

### Settings — Category: Notifications (NotificationsCategory)

- [ ] Notifications → Category container — notification settings — testTag: `Cat-Notifications`
- [ ] Notifications → Channels pane — per-channel settings — testTag: `Cat-Notifications-ChannelsPane`
- [ ] Notifications → Leads pane — lead-time settings — testTag: `Cat-Notifications-LeadsPane`
- [ ] Notifications → Defaults card — default reminder config — testTag: `Cat-Notifications-Defaults`
- [ ] Notifications → Defaults channel picker — pick default channel — testTag: `Cat-Notifications-Defaults-Channel`
- [ ] Notifications → Lead-time chip (per preset) — choose default lead time — testTag: `Cat-Notifications-Defaults-Chip-<v>`
- [ ] Notifications → Custom chip — custom lead time — testTag: `Cat-Notifications-Defaults-Chip-Custom`
- [ ] Notifications → Custom dialog — custom lead-time dialog — testTag: `Cat-Notifications-Defaults-CustomDialog`

### Settings — Category: Mode (ModeCategory)

- [ ] Mode → Category container — mode settings — testTag: `Cat-Mode`
- [ ] Mode → Mode selector (free / strictly-kept) — set repo mode — testTag: n/a
- [ ] Mode → Mode confirmation control — confirm mode transition — testTag: n/a

### Settings — Category: Lifestyle (LifestyleCategory)

- [ ] Lifestyle → Category container — lifestyle settings — testTag: `Cat-Lifestyle`
- [ ] Lifestyle → Neutral toggle — neutral-mode toggle — testTag: `Cat-Lifestyle-Neutral`
- [ ] Lifestyle → Trip: Upcoming — show upcoming trip — testTag: `Cat-Lifestyle-Trip-Upcoming`
- [ ] Lifestyle → Trip: Last — show last trip — testTag: `Cat-Lifestyle-Trip-Last`
- [ ] Lifestyle → Trip: None — no trip — testTag: `Cat-Lifestyle-Trip-None`

### Settings — Category: Repos (ReposCategory)

- [ ] Repos Cat → Category container — repos settings — testTag: `Cat-Repos`
- [ ] Repos Cat → Repo row (per repo) — open repo settings — testTag: `Cat-Repos-Row-<id>`
- [ ] Repos Cat → Import/Export entry — open import/export — testTag: `Cat-Repos-ImportExport`

### Settings — Category: Calendars (ListsCategories)

- [ ] Calendars Cat → Category container — calendars settings — testTag: `Cat-Calendars`

### Settings — Category: Todolists (ListsCategories)

- [ ] Todolists Cat → Category container — todolists settings — testTag: `Cat-Todolists`

### Settings — Category: Templates (TemplatesCategory)

- [ ] Templates Cat → Category container — templates settings — testTag: `Cat-Templates`

### Settings — Category: Sync (SyncCategory)

- [ ] Sync Cat → Category container — sync settings — testTag: `Cat-Sync`

### Settings — Category: CalDav (CalDavCategory)

- [ ] CalDav Cat → Category container — CalDAV settings — testTag: `Cat-CalDav`

### Settings — Category: Access (AccessCategory)

- [ ] Access Cat → Category container — access/sharing settings — testTag: `Cat-Access`
- [ ] Access Cat → Empty placeholder — no grants state — testTag: `Cat-Access-Empty`
- [ ] Access Cat → Access row (per grant) — manage a grant — testTag: `Cat-Access-Row-<id>`

### Settings — Category: AutoTablet (AutoTabletCategory)

- [ ] AutoTablet → Category container — auto-tablet layout settings — testTag: `Cat-AutoTablet`
- [ ] AutoTablet → Mode: Off — disable auto-tablet — testTag: `Cat-AutoTablet-Mode-Off`
- [ ] AutoTablet → Mode: Auto — auto-detect — testTag: `Cat-AutoTablet-Mode-Auto`
- [ ] AutoTablet → Mode: On — force two-pane — testTag: `Cat-AutoTablet-Mode-On`
- [ ] AutoTablet → Max-width slider — pane max-width — testTag: `Cat-AutoTablet-MaxSlider`
- [ ] AutoTablet → Open-detail toggle — open detail pane behavior — testTag: `Cat-AutoTablet-OpenDetail`
- [ ] AutoTablet → Repo-show toggle (per repo) — show repo in pane — testTag: `Cat-AutoTablet-RepoShow-<id>`

### Settings — Category: Storage (StorageCategory)

- [ ] Storage → Category container — storage settings — testTag: `Cat-Storage`
- [ ] Storage → Folder row — open storage-folder screen — testTag: `Cat-Storage-FolderRow`
- [ ] Storage → Adopt row — open adopt-folder sheet — testTag: `Cat-Storage-AdoptRow`
- [ ] Storage → Backup/Restore row — open backup/restore — testTag: `Cat-Storage-BackupRestoreRow`

### Settings — Category: About (AboutCategory)

- [ ] About → Category container — about page — testTag: `Cat-About`
- [ ] About → Easter-egg trigger — tap-to-trigger easter egg — testTag: `Cat-About-EasterEgg`

---

### Settings — Storage Folder Screen (StorageFolderScreen)

- [ ] Storage Folder → Screen container — storage folder picker — testTag: `StorageFolderScreen`
- [ ] Storage Folder → Change button — change folder (SAF) — testTag: `StorageFolderScreen-Change`
- [ ] Storage Folder → Switch-to-internal toggle — use internal storage — testTag: `StorageFolderScreen-SwitchInternal`

### Settings — Adopt Folder Sheet (AdoptFolderSheet)

- [ ] Adopt Folder → Sheet — adopt existing folder — testTag: `AdoptFolderSheet`
- [ ] Adopt Folder → Confirm — adopt the folder — testTag: `AdoptFolderSheet-Confirm`
- [ ] Adopt Folder → Cancel — dismiss — testTag: `AdoptFolderSheet-Cancel`

### Settings — Backup/Restore Screen (BackupRestoreScreen)

- [ ] Backup/Restore → Screen container — backup/restore page — testTag: `BackupRestoreScreen`
- [ ] Backup/Restore → Export row — export archive — testTag: `BackupRestore-ExportRow`
- [ ] Backup/Restore → Restore-archive row — restore from archive — testTag: `BackupRestore-RestoreArchiveRow`
- [ ] Backup/Restore → Restore-folder row — restore from folder — testTag: `BackupRestore-RestoreFolderRow`

### Settings — External Calendars Screen (ExternalCalendarsScreen)

- [ ] External Cals → Screen container — system-calendar integration — testTag: `ExternalCalendarsScreen`
- [ ] External Cals → Show switch — show external calendars — testTag: `ExternalCalendars-Show`
- [ ] External Cals → Allow-edit switch — allow editing external — testTag: `ExternalCalendars-AllowEdit`
- [ ] External Cals → Publish switch — publish to system — testTag: `ExternalCalendars-Publish`
- [ ] External Cals → Grant button — grant calendar permission — testTag: `ExternalCalendars-Grant`
- [ ] External Cals → Open-settings button — open system settings — testTag: `ExternalCalendars-OpenSettings`
- [ ] External Cals → Permission-needed notice — permission prompt — testTag: `ExternalCalendars-PermissionNeeded`
- [ ] External Cals → Calendar row (per calendar) — toggle a calendar — testTag: `ExternalCalendars-Cal-<id>`
- [ ] External Cals → Connect-accounts row — open connect-accounts — testTag: `ExternalCalendars-ConnectAccountsRow`
- [ ] External Cals → Calendar-defaults row — open calendar defaults — testTag: `ExternalCalendars-CalendarDefaultsRow`
- [ ] External Cals → Calendar-defaults button — calendar defaults action — testTag: `ExternalCalendars-CalendarDefaultsButton`
- [ ] External Cals → Default-app badge — default-app indicator — testTag: `ExternalCalendars-DefaultAppBadge`
- [ ] External Cals → Suppress section — suppression list — testTag: `ExternalCalendars-SuppressSection`
- [ ] External Cals → Suppress row (per source) — suppress a source — testTag: `ExternalCalendars-SuppressRow-<id>`

### Settings — Connect Accounts Screen (ExternalAccountsConnectScreen)

- [ ] Connect Accounts → Screen container — connect external accounts — testTag: `ConnectAccountsScreen`
- [ ] Connect Accounts → CalDAV card — CalDAV account card — testTag: `ConnectAccounts-CalDavCard`
- [ ] Connect Accounts → CalDAV button — connect CalDAV — testTag: `ConnectAccounts-CalDavButton`
- [ ] Connect Accounts → Exchange card — Exchange account card — testTag: `ConnectAccounts-ExchangeCard`
- [ ] Connect Accounts → Exchange button — connect Exchange — testTag: `ConnectAccounts-ExchangeButton`
- [ ] Connect Accounts → Configured card — configured-account card — testTag: `ConnectAccounts-ConfiguredCard`
- [ ] Connect Accounts → Configured button — manage configured account — testTag: `ConnectAccounts-ConfiguredButton`

---

### Calendars — Filter Chip Strip (CalendarFilterChipStrip)

- [ ] Calendar Filter → Chip leading icon (per calendar) — calendar glyph on chip — testTag: `Calendar-ChipLeadingIcon`
- [ ] Calendar Filter → Read-only lock icon — read-only calendar marker — testTag: `Calendar-ChipReadOnlyLock`

### Calendars — Settings Sheet (CalendarSettingsSheet)

- [ ] Calendar Settings → Settings sheet — per-calendar settings — testTag: `CalendarSettings-Sheet`
- [ ] Calendar Settings → Active toggle — enable/disable calendar — testTag: `CalendarSettings-ActiveToggle`
- [ ] Calendar Settings → Emoji field — calendar emoji — testTag: `CalendarSettings-Emoji`
- [ ] Calendar Settings → Hex field — calendar color hex — testTag: `CalendarSettings-Hex`
- [ ] Calendar Settings → Color swatch (per swatch) — pick color — testTag: `CalendarSettings-Swatch-<i>`
- [ ] Calendar Settings → Add-range button — add active range — testTag: `CalendarSettings-AddRange`
- [ ] Calendar Settings → Save button — save settings — testTag: `CalendarSettings-Save`

### Calendars — Color Picker (CalendarColorPicker / ColorPickerDialog)

- [ ] Color Picker → Picker container — color picker — testTag: `CalColorPicker`
- [ ] Color Picker → Color swatch (per swatch) — pick color — testTag: `CalColorSwatch`
- [ ] Color Picker → Hex input — type hex color — testTag: `CalColorHex`

### Calendars — Overlay Picker Screen (OverlayPickerScreen)

- [ ] Overlay Picker → Screen container — overlay visibility picker — testTag: `Overlay-PickerScreen`
- [ ] Overlay Picker → Explainer — explanatory text — testTag: `Overlay-PickerExplainer`
- [ ] Overlay Picker → Repo header — per-repo header — testTag: `Overlay-PickerRepoHeader`
- [ ] Overlay Picker → Repo name — repo label — testTag: `Overlay-PickerRepoName`
- [ ] Overlay Picker → Overlay row (per overlay) — toggle an overlay — testTag: `Overlay-PickerRow`
- [ ] Overlay Picker → Toggle — show/hide overlay — testTag: `Overlay-PickerToggle`
- [ ] Overlay Picker → Priority control — overlay priority — testTag: `Overlay-PickerPriority`
- [ ] Overlay Picker → Edit button — edit overlay color — testTag: `Overlay-PickerEdit`
- [ ] Overlay Picker → Color row — color choices — testTag: `Overlay-PickerColorRow`
- [ ] Overlay Picker → Color swatch (per swatch) — pick overlay color — testTag: `Overlay-PickerColorSwatch-<i>`
- [ ] Overlay Picker → Hex input — type overlay hex — testTag: `Overlay-PickerHexInput`

---

### Import / Export (ImportExportScreen)

- [ ] Import/Export → Screen container — import/export hub — testTag: `ImportExportScreen`
- [ ] Import/Export → Preview pane — preview pane — testTag: `ImportExport-PreviewPane`
- [ ] Import/Export → Detail-empty placeholder — empty detail — testTag: `ImportExport-DetailEmpty`
- [ ] Import/Export → Import button (per format) — start an import — testTag: `ImportBtn-<fmt>`
- [ ] Import/Export → Export button (per format) — start an export — testTag: `ExportBtn-<fmt>`

### Import / Export — ICS Import Screen (IcsImportScreen)

- [ ] ICS Import → Screen container — .ics importer — testTag: `IcsImportScreen`
- [ ] ICS Import → Destination dropdown — choose destination calendar — testTag: `IcsImportDestinationDropdown`

### Import / Export — Import Preview Sheet (ImportPreviewSheet)

- [ ] Import Preview → Sheet — preview before import — testTag: `ImportPreviewSheet`
- [ ] Import Preview → Confirm button — confirm import — testTag: `ImportPreviewConfirm`
- [ ] Import Preview → Cancel button — abort import — testTag: `ImportPreviewCancel`

---

### Sharing (ShareSheet / ForkDialog / banners)

- [ ] Share → Share sheet — repo share sheet — testTag: `ShareSheet`
- [ ] Share → Mode: Read-only — share read-only — testTag: `Share-Mode-ReadOnly`
- [ ] Share → Mode: Read-write — share read-write — testTag: `Share-Mode-ReadWrite`
- [ ] Share → Allow-writeback toggle — allow writeback — testTag: `Share-AllowWriteBack`
- [ ] Share → Include-mirrors toggle — include mirrors — testTag: `Share-IncludeMirrors`
- [ ] Share → Single-use toggle — single-use link — testTag: `Share-SingleUse`
- [ ] Share → Single-use note — single-use explanation — testTag: `Share-SingleUseNote`
- [ ] Share → Expiry: None — no expiry — testTag: `Share-Expiry-None`
- [ ] Share → Expiry: 7 days — 7-day expiry — testTag: `Share-Expiry-7d`
- [ ] Share → Expiry: 30 days — 30-day expiry — testTag: `Share-Expiry-30d`
- [ ] Share → Link field — generated share link — testTag: `Share-LinkField`
- [ ] Share → QR preview — share QR code — testTag: `Share-QrPreview`
- [ ] Share → Copy button — copy link — testTag: `Share-Copy`
- [ ] Share → Send button — send/share link — testTag: `Share-Send`
- [ ] Fork Dialog → Dialog — fork shared repo — testTag: `ForkDialog`
- [ ] Fork Dialog → Confirm input — type confirmation — testTag: `Fork-ConfirmInput`
- [ ] Fork Dialog → Confirm button — fork the repo — testTag: `Fork-Confirm`
- [ ] Fork Dialog → Cancel button — dismiss — testTag: `Fork-Cancel`
- [ ] Share → Read-only banner — read-only repo banner — testTag: `ReadOnlyBanner`
- [ ] Share → Mirror-divergence banner — mirror diverged warning — testTag: `MirrorDivergenceBanner`
- [ ] Share → Foreign-event chip — foreign-event marker — testTag: `ForeignEvent-Chip`

---

### Shared Components (components/)

- [ ] Components → Sync button — trigger sync — testTag: `SyncButton`
- [ ] Components → Sync success check — success indicator — testTag: `SyncSuccessCheck`
- [ ] Components → Sync error dot — error indicator — testTag: `SyncErrorDot`
- [ ] Components → Pin action — pin item — testTag: `PinAction`
- [ ] Components → Pin menu — pin options menu — testTag: `PinMenu`
- [ ] Components → Event-title emoji preview — emoji preview — testTag: `EventTitleEmojiPreview`

---

## Counts

| Group | Subtotal |
|---|---|
| App Shell / Chrome | 5 |
| Top Bar | 6 |
| Left View-Mode Rail | 11 |
| Mode Pill / Dom-Persona | 5 |
| Schedule — common chrome | 29 |
| Schedule — Day Bands / Event Bands | 11 |
| Schedule — Drag Reschedule | 5 |
| Schedule — Agenda / Now | 4 |
| Schedule — Day view | 2 |
| Schedule — 3-Day view | 2 |
| Schedule — Week view | 5 |
| Schedule — Month view | 5 |
| Schedule — Year view | 3 |
| Schedule — Timebox view | 8 |
| Schedule — Edit Schedule screen | 2 |
| Event Create — Sheet | 16 |
| Event Create — Template picker | 9 |
| Event Detail — Sheet / Screen | 27 |
| Tasks Pane + bottom-sheet | 8 |
| Tasks — View modes | 14 |
| Tasks — Task Row | 13 |
| Tasks — Quick Add | 5 |
| Tasks — Task Detail Sheet | 8 |
| Tasks — Prompt Response Sheet | 5 |
| Tasks — Task List Settings Sheet | 1 |
| Now Playing / Mini Player | 8 |
| Reviews Pane | 7 |
| Together Pane | 12 |
| Repos Pane | 9 |
| Repos — Repo Card | 15 |
| Repos — Repo Switcher Chip / Dropdown | 13 |
| Repos — Repo Settings | 22 |
| Repos — Identities Screen | 4 |
| Repos — Repo Icon Picker | 5 |
| Repos — Sticker Pack Selector | 4 |
| Repos — Repo Move Job Dialog | 3 |
| Repos — Overlay screens | 2 |
| Add Repo Flow | 23 |
| Trip Wizard | 9 |
| Intro Wizard | 8 |
| Age Gate | 3 |
| Setup Wizard | 17 |
| Settings Pane — chrome | 8 |
| Settings — Appearance | 10 |
| Settings — Identity | 5 |
| Settings — Notifications | 8 |
| Settings — Mode | 3 |
| Settings — Lifestyle | 5 |
| Settings — Repos | 3 |
| Settings — Calendars | 1 |
| Settings — Todolists | 1 |
| Settings — Templates | 1 |
| Settings — Sync | 1 |
| Settings — CalDav | 1 |
| Settings — Access | 3 |
| Settings — AutoTablet | 7 |
| Settings — Storage | 4 |
| Settings — About | 2 |
| Settings — Storage Folder Screen | 3 |
| Settings — Adopt Folder Sheet | 3 |
| Settings — Backup/Restore Screen | 4 |
| Settings — External Calendars Screen | 14 |
| Settings — Connect Accounts Screen | 7 |
| Calendars — Filter Chip Strip | 2 |
| Calendars — Settings Sheet | 7 |
| Calendars — Color Picker | 3 |
| Calendars — Overlay Picker Screen | 11 |
| Import / Export | 5 |
| Import / Export — ICS Import Screen | 2 |
| Import / Export — Import Preview Sheet | 3 |
| Sharing | 21 |
| Shared Components | 6 |

**Grand total enumerated controls: 527**
