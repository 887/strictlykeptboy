# Audit — Round 2.1: multi-repo + cross-repo sharing surface

Auditor: opus-subagent, READ-ONLY, 2026-05-13.
Scope: how multiple git repos and their calendars actually surface to
the user today, and where the architecture diverges from the genesis
intent ("multiple calendar repos and multiple timeboxing schedules I
can activate or deactivate with toggles", "give people read only
access to my calendar repo", "the repositories can have one or MORE
calendars.. they are not analogous to calendars").

## A. Current repo model (in-code, as shipped)

### A.1 Storage shape
- `git/RepoConfig.kt` lines 19-86: each repo is a `RepoConfig` carrying
  `repoId`, `displayName`, `rootDir`, `remotes`, `primaryRemote`,
  `defaultCalendarId`, `defaultTodolistId`, icon affordance, plus the
  Phase O share-back-reference fields (`readOnlyViaShare`,
  `sourceRepoLabel`, `sourceRepoBackLink`). One repo, one config row.
- `git/RepoStore.kt` lines 32-172: persistent list of `RepoConfig`,
  exposed as `state: StateFlow<List<RepoConfig>>`. CRUD + per-remote
  helpers. No notion of "active repo" here — that lives in the UI
  layer.
- `git/GitRepoRegistry.kt`: process-wide registry of opened JGit
  handles keyed by `repoId`. Multi-repo at the data layer is fully
  first-class.

### A.2 "Active repo" representation
- `ui/repos/ReposViewState.kt` lines 30-50:
  `_activeRepoId: MutableStateFlow<String?>` initialised from
  `SharedPreferences("repos_view_v1") / "active_repo_id"`, falling back
  to `store.list().firstOrNull()?.repoId`. Persists across launches.
- Same file lines 37-55: a parallel `_unifiedView: MutableStateFlow<Boolean>`
  ("all repos at once" master toggle, persisted under
  `"unified_view"`). Phase I.6.
- `composition/AppGraph.kt` lines 205-254: `activeRepoName:
  MutableStateFlow<String>` defaults to literal `"demo-repo"` and is
  used to drive the top-bar avatar via `activeRepoIconKind`. Note this
  is a SEPARATE flow from `ReposViewState.activeRepoId` — name vs id —
  with no observed bridge between them.
- `ui/scaffold/SkbAppShell.kt` lines 164-235: the shell receives
  `activeRepoNameFlow` + `activeIconKindFlow` and threads `activeRepoName`
  into `SchedulePane` (line 329, 336) and `TasksPane` purely as a header
  display string.

### A.3 What the schedule actually consumes
- `ui/schedule/ScheduleViewState.kt` lines 40-75: takes one
  `snapshotFlow: StateFlow<RepoSnapshot>` + one `sourcesFlow:
  StateFlow<Renderer.Sources>`. The renderer fans out over whatever
  repos are in the snapshot — multi-repo aware at the resolver layer.
- `composition/AppGraph.kt` lines 198-219:
  ```
  // Phase F→G integration is still pending: live RepoStore → Room
  // bridge has not landed. Until then, the schedule pane and the
  // Auto surface both observe these empty flows.
  val snapshot: MutableStateFlow<RepoSnapshot> =
      MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
  ```
  The snapshot is a hard-coded empty `MutableStateFlow` placeholder.
  Nothing reads `RepoStore`, opens repos via `GitRepoRegistry`, scans
  files via `cache/Indexer.kt`, and pushes a real snapshot into this
  flow. The "active repo" string is purely cosmetic for now.

### A.4 Top-bar UX
- `ui/scaffold/SkbAppShell.kt` lines 444-491: top bar shows a title
  (current destination name — "Schedule" / "Tasks" / etc.), a mode
  pill, a sync button, an `IdentityAvatar` (bat avatar = repo
  affordance, taps into Repos destination per D.88), and a horizontal
  strip of all 7 `TopDestination` icon buttons.
- The repo *switcher* lives ONLY inside the Repos destination
  (`ui/repos/RepoSwitcherDropdown.kt` rendered inside
  `ReposPane.kt`). There is no top-bar dropdown for fast switching
  while you're looking at the schedule.

## B. Calendar-vs-repo relationship

### B.1 Data layer
- One repo can host N calendars: per the repo convention in
  `/CLAUDE.md` and `decisions.md` D.3 — `calendars/<id>/(events|...)/`.
- `resolver/Types.kt` `CalendarMeta` (lines 70-89) carries a
  `repo: RepoRef` back-pointer, plus `activeToggle: Boolean`,
  `activeWindows: List<DateRange>`, `activeHours: List<HourRange>`,
  `priority: Int`, `supersedes: List<CalendarRef>`. The
  *resolver-facing* calendar model is exactly what the intent calls
  for: per-calendar on/off toggle, date-window activation, hourly
  windows (e.g. work calendar 09:00-17:00 Mon-Fri), priority overlay,
  and supersedence relationships.
- `RoutineCalendarConfig.kt` + `SupersedenceConfig.kt` (under
  `store/`) persist the calendar-level config into TOML inside the
  repo — i.e. the canonical `activeWindows / activeHours / supersedes`
  fields live next to the calendar.

### B.2 UI layer — the leak
The UI does NOT yet surface calendars-first. The cracks:
- `ui/repos/ReposPane.kt` is the destination the user reaches via the
  top-bar avatar / "Repos" rail item. It renders:
  - the "all-repos unified view" master Switch (line 274-278)
  - `RepoSwitcherDropdown` (line 299-307) listing repos
  - per-row settings entry → `RepoSettingsScreen` (which has fields for
    *one* default calendar id per repo).
  There is NO calendar list anywhere in this destination. The user
  cannot see "my repo bat-cal contains 3 calendars (morning routine,
  work, swimming club) and the second one is active 09-17 Mon-Fri".
- `ui/settings/CalendarVisibilityPrefs.kt` (Phase S.5 / S.6): a
  `VisibilityState` of `VisibilityEntry(id, label, visible,
  activeFromIso, activeUntilIso)` lives in a single flat
  SharedPreferences blob, keyed by `id` only. NO `repoId` qualifier
  → either calendar IDs are globally unique (they are UUIDv7, so
  that's safe) or the UI has no way to scope visibility "the morning
  calendar in repo A is on, in repo B is off". The label field is the
  only thing distinguishing two calendars in the picker.
- The flat list has primitive `activeFromIso / activeUntilIso` —
  a SINGLE [from, until] half-open range only. Plan-spec says
  `activeWindows: List<DateRange>` with multiple ranges (semester,
  vacation gap, etc.) — the prefs model is a partial implementation
  that cannot express "school year sept-june with the dec-jan winter
  gap inactive". The richer `List<DateRange>` lives ONLY at the
  resolver-facing `CalendarMeta` level, not in the prefs UI layer.
- There is NO settings UI that maps `activeHours` (the hourly
  windowing — "work 09:00-17:00 Mon-Fri"). The data model has the
  shape (`HourRange(day, from, to)`), the resolver can act on it, but
  there is no editor for it.

### B.3 Verdict for B
- The user wants to manage CALENDARS. The UI today forces them through
  REPOS (top-bar avatar → Repos → tap a repo → Settings → default
  calendar id field). Calendars are second-class citizens in the UI
  even though they are first-class in the data model and the resolver.
- This is the exact failure mode the user named: "you're not really
  using the multi repositories and are making calendars manageable by
  repository".

## C. Intent checks (point-by-point against `prompts.md`)

| Intent signal | Status | Evidence |
|---|---|---|
| "multiple calendar repos" — N repos as peers | PARTIAL | `RepoStore` is list-backed and works for N repos; `GitRepoRegistry` opens N JGit handles. But schedule snapshot is a hard-coded empty stub (`AppGraph.kt:208`), so multi-repo data never flows into the UI yet. |
| "multiple timeboxing schedules I can activate or deactivate with toggles" | DATA YES, UI NO | `CalendarMeta.activeToggle` + `VisibilityEntry.visible` exist, but the visibility prefs UI lives in Settings → Lists, NOT in the chip strip above the schedule view. Toggling is at least 3 taps away. |
| "set timeframes when these become active / deactivate" | DATA RICH, UI PRIMITIVE | `activeWindows: List<DateRange>` + `activeHours: List<HourRange>` modelled at resolver level; `VisibilityEntry` only stores a single `activeFromIso / activeUntilIso` pair; no UI for hourly windows; no UI for multiple ranges. |
| "show who the person creating calendar events was" | PARTIAL | `EventInput.author: PersonRef?` exists (`resolver/Types.kt:144`); `EventDetailSheet.kt:188-200` renders author id when present. `author` is identity-driven, not cross-repo-aware: when alex writes into bat's repo, the resolver still tags `author` correctly because `EventInput.author` flows from the file's frontmatter; what's missing is a visible repo-of-origin chip on the schedule view itself (only in detail sheet). |
| "give people read only access to my calendar repo" | YES | Phase O shipped `commit 53195ac`: `ShareLink` codec, `ShareSheet`, `ReadOnlyBanner`, `ForeignEventSourceChip`, deep-link intent-filter, `RepoConfig.readOnlyViaShare`. |
| "I might have a sub who gives me access to their calendar and whoms calendars and timebox I fill with naughty things" — read-WRITE share | PARTIAL | Phase RR slice landed: `ShareLink.allowWriteBack`, `ShareSheet` write-back checkbox, `ShareAcceptResolver`. But RR.3-RR.6 (SSH key path, deploy-key embed, fine-grained PAT, public-repo) are deferred. The `mode=read-write` flag flows through; no UI surface confirms "this remote is writable as a dom into bat's repo" beyond the per-remote treatAsReadOnly toggle. |
| "simplified → own fork" — sub leaves dom and starts their own thing | YES (data layer) | Phase SS shipped: `RepoForker`, `ForkDialog`, `:cli ForkCommand`. SS.6 (mode-choice prompt) + SS.7 (reverse path) are deferred. |
| "no-origin repos are first-class" — Phase ZZ / D.74 | YES (data + UI) | `GitRepo.initLocalOnly`, `RepoConfig.remotes` may be empty, per-remote keying, "Create local-only" branch in `AddRepoNavHost`, "local" badge in switcher, sync button hidden in simplified mode when active repo is no-origin. |
| Cross-repo authorship in resolver | DATA YES | `MaterializedInstance.author` carries through; `ForeignEventSourceChip` renders source-repo chip ONLY when `RepoConfig.readOnlyViaShare = true`. There is no general "show event-origin repo on the day band" surface for the non-share, plain multi-repo case. |
| Per-repo unified view | DATA YES, RESOLVER NOT WIRED | `ReposViewState.unifiedView` toggle persists; `ScheduleViewState` consumes a single `snapshotFlow` so the gate point exists; but the bridge that filters the snapshot to `{activeRepo}` vs `{allRepos}` is not implemented (snapshot is empty stub). |

## D. Gap analysis — the concrete leaks of "repo-as-container"

1. **Top-bar IS the repo identity, not the schedule view.** D.88
   locks "a repo IS an identity"; `activeRepoIconKind` drives the
   avatar. That's fine as identity-context, but it means the *only*
   surface presenting the data is implicitly mono-repo. The schedule
   view's title is the destination name ("Schedule"), not the union
   "personal-cal + bat-cal-readonly".

2. **No filter-chip strip above the schedule.** The genesis prompt is
   explicit: "ways to tick on and off or manage your multiple
   calendar overlays or prioritize them". The user expects, on top of
   the day grid: a horizontal chip rail like Google Calendar's left
   list — "Morning routine [on] / Work 09-17 [on] / Swimming Tue Thu
   [on] / Dom's check-ins [readonly]". None of this exists in
   `SchedulePane.kt`.

3. **`activeRepoId` is single-valued.** The data model supports
   "show all repos at once" (unified toggle + resolver's `List<RepoEntry>`).
   The UI binds the schedule to ONE active repo via
   `activeRepoName: String`. There is no concept of a *set* of
   active calendars-across-repos in `SkbAppShell` / `SchedulePane`.

4. **CalendarVisibilityPrefs is global, flat, and weakly-keyed.** It
   stores `id → visible + single date range`. No `repoId` qualifier,
   no list-of-ranges, no hourly windows, no per-day-of-week rule.

5. **Repo settings exposes ONE `defaultCalendarId` per repo** — the
   create-FAB writes to a single default. Multi-calendar authoring
   (pick from a chooser when adding an event) requires this to be a
   list, or for the create sheet to enumerate calendars itself.

6. **Together pane is repo-keyed, not calendar-keyed.**
   `TogetherRepoOption(id, label)` in `AppGraph.kt:223-231` is built
   from `RepoStore.state`. So "find a time together" finds free time
   across REPOS, not across CALENDARS — meaning alex's "work
   calendar" inside alex's repo cannot be excluded from the
   free-time computation independently of the rest of alex's repo.

7. **No `state/<source-repo-id>/` (Phase OO) shipped in app code.**
   `grep` for "state/" / "cross-repo state" returns the share + RR
   surfaces but no `ReactionStore` / `state-file writer`. Reactions /
   write-backs into shared repos still go to the recipient's own
   repo, which is the right design — but the writer / reader for the
   `state/<source-id>/<entity>.<kind>.toml` layout isn't there yet.

8. **`activeRepoName` defaults to literal "demo-repo".** No wiring
   from `ReposViewState.activeRepoId` → `AppGraph.activeRepoName`.
   `MainActivity` is the suspected gluepoint (not read in this
   audit), but the disconnect at the graph level is concerning.

## E. Round 2.1 — proposed sub-steps

Phase header: **2.1-Multirepo — calendars-first, repos-as-plumbing**

Locked design decisions for this round:

- **D-2.1.a — Calendars are the user-facing primary; repos are
  plumbing.** The schedule view shows the union of all enabled
  calendars across all repos. The top-bar avatar continues to drive
  the *write* target (which repo the next created event goes into),
  but the *read* surface is unified by default.
- **D-2.1.b — Per-calendar (not per-repo) enable toggles.** Calendar
  chips live above the schedule grid; toggling one chip toggles its
  visibility globally. Per-repo "hide all from this repo" is a
  derived bulk action, not the primary unit.
- **D-2.1.c — Time-window activation lives in the calendar's own
  `calendar.toml`** (`activeWindows: List<DateRange>` +
  `activeHours: List<HourRange>` + `activeToggle: Boolean`). Stored
  IN the source repo so it round-trips through git for that
  calendar's author. The local `CalendarVisibilityPrefs` becomes a
  PHONE-LOCAL override layer (visible/hidden on this device only),
  not the source of truth for active-windows.
- **D-2.1.d — "Active repo" still exists for write-targeting only.**
  Renamed conceptually to "default authoring identity". Repo
  switching is a write-context switch, not a view-context switch.
- **D-2.1.e — Calendar IDs are globally unique (UUIDv7) so
  cross-repo references don't need `repoId/calendarId` compound keys
  in UI prefs.** Display labels disambiguate when two calendars
  share a name across repos.
- **D-2.1.f — Author chip on every event band when source repo
  differs from the active write-target repo.** Generalize Phase O's
  `ForeignEventSourceChip` to all multi-repo views, not just shares.

### Sub-step checkboxes

- [ ] **2.1.A** Wire `RepoStore.state → AppGraph.snapshot`. Build
      `RepoSnapshot` from configured repos via `cache/Indexer` →
      Room → `RepoScanner.scanAll`. Land the F→G bridge that's
      blocked by the comment at `AppGraph.kt:198-203`. Without
      this everything else is theoretical.

- [ ] **2.1.B** Introduce `CalendarRegistry` (new file:
      `app/.../resolver/CalendarRegistry.kt` or
      `app/.../store/CalendarRegistry.kt`) that exposes
      `StateFlow<List<CalendarMeta>>` aggregated across all configured
      repos. Source of truth = the `calendars/<id>/calendar.toml`
      files via `RoutineCalendarConfig.kt` + `SupersedenceConfig.kt`.

- [ ] **2.1.C** Add `CalendarFilterChipStrip` composable above
      `SchedulePane` content. Renders chip per `CalendarMeta`, with:
      label (emoji + display name), tinted by `colorSeed`, toggle
      state from `CalendarVisibilityPrefs.visible`. Tap = toggle on
      this device. Long-press = open per-calendar settings sheet.

- [ ] **2.1.D** Extend `CalendarVisibilityPrefs.VisibilityEntry`
      with a `repoId: String` qualifier (migration: existing entries
      get `repoId = ""` and are matched on `id` alone for back-compat).
      Drop the `activeFromIso / activeUntilIso` fields — those move
      to `calendar.toml` per D-2.1.c. The prefs file holds only the
      phone-local on/off bit + priority ordering.

- [ ] **2.1.E** Calendar-settings sheet (new
      `ui/calendars/CalendarSettingsSheet.kt`): edit `activeToggle`,
      `activeWindows` (multi-range editor with "+ add range" /
      "infinite end" affordance), `activeHours` (per-day-of-week
      from-to editor), `priority`, `supersedes` (multi-select of
      other calendars). Writes back to the calendar's own
      `calendar.toml` via existing `RoutineCalendarConfig` writer.

- [ ] **2.1.F** Generalize `ForeignEventSourceChip` to render on any
      event band whose `repo` differs from the current
      `defaultWriteRepo`. Decouple from the `readOnlyViaShare`
      flag; that flag continues to drive the red read-only banner
      and the de-saturated background, but the *source-repo chip*
      now appears for unified-view too.

- [ ] **2.1.G** Rename `activeRepoName` to `defaultWriteRepoName` in
      `AppGraph` + propagate the rename downstream (this is a
      grep-and-replace + javadoc pass; the underlying flow shape
      stays). Wire it to read from `ReposViewState.activeRepoId →
      repoStore.get(...) → displayName`. Kill the literal
      `"demo-repo"` default; default to first configured repo's
      name, or empty string if no repos configured.

- [ ] **2.1.H** Top-bar shows a unified-view indicator chip when
      `ReposViewState.unifiedView = true`. When false, the avatar
      stays as today (drives write target + identity). When true,
      the schedule reads from all repos and shows a small "all
      repos" badge near the title; the avatar becomes the
      write-target picker only.

- [ ] **2.1.I** Schedule view-state takes a `visibilityFlow:
      StateFlow<CalendarVisibilityState>` and filters
      `RepoSnapshot.calendars` / `Renderer.Sources.events` /
      `Renderer.Sources.rules` accordingly. Resolver layer already
      consumes `CalendarMeta.activeToggle` + `activeWindows` +
      `activeHours`, so the filtering point is purely the
      visibility-prefs layer + the on-device chip toggle. No
      resolver change required for the activity-window evaluation —
      `ActiveSetEvaluator.kt` already implements it.

- [ ] **2.1.J** Together pane: switch
      `TogetherRepoOption(repoId, label)` to
      `TogetherCalendarOption(calendarId, repoId, label)`. The
      multi-select picker now lists calendars (with their source
      repo as a subtitle) rather than repos. Free-time computation
      becomes "free across this set of calendars".

- [ ] **2.1.K** Drop `RepoConfig.defaultCalendarId` as a hard
      authoring binding. Replace with "last-used calendar per repo"
      (transient prefs) + a calendar picker in `EventCreateSheet`
      that defaults to last-used. Multi-calendar repos no longer
      need a hard "default" — the create flow asks every time
      (with smart default).

- [ ] **2.1.L** Settings → Lists category becomes a calendars master
      list (across all repos) with: drag-to-reorder priority,
      checkbox toggle for "show on schedule", row-tap →
      `CalendarSettingsSheet` (2.1.E). The current per-repo settings
      surface (`RepoSettingsScreen`) keeps repo-level concerns only
      (display name, icon, remotes, identity, sync interval) — NOT
      calendar selection.

- [ ] **2.1.M** AVD-smoke + tests: assert that with 2 configured
      repos containing 3 calendars total, the chip strip renders 3
      chips; toggling a chip hides the corresponding day-band
      entries; toggling unifiedView off hides chips for the other
      repo. Robolectric tests for `CalendarRegistry` aggregation +
      `CalendarVisibilityPrefs` migration.

- [ ] **2.1.N** Plan-file ticks: tick 2.1.A..2.1.M as they ship with
      jj change-id headers per the global CLAUDE.md rule. Mark this
      audit obsolete once Round 2.1 lands.

## F. Notes for the implementer

- The resolver is multi-repo-ready. Don't touch
  `ActiveSetEvaluator.kt` or `Renderer.kt` — they already evaluate
  per-`CalendarMeta` toggles + windows + hours. The work is
  exclusively in: (a) wiring real data INTO `RepoSnapshot`, (b)
  surfacing calendar-level controls in the UI, (c) decoupling write
  context from read context.
- Phase OO (`state/<source-repo-id>/` cross-repo state) is still
  un-shipped per the prompts grep. Reactions / write-backs into
  shared repos are theoretically modelled but no
  `ReactionStore.write()` exists yet. Out of scope for 2.1 — track
  separately if the user wants reaction surfaces.
- D.88 "repo IS an identity" stays intact under D-2.1.d: the avatar
  + chrome continue to reflect the active *write* identity / repo.
  The calendars-first read surface is layered on top, not in
  conflict.
- The Phase O share machinery (`ShareLink`, `ShareSheet`,
  `ReadOnlyBanner`, `ForeignEventSourceChip`, `RepoForker`,
  `ShareAcceptResolver`, `ReviewFeedWriter`) is solid. 2.1 does not
  refactor share; it generalizes the foreign-event chip to
  non-share multi-repo and adds the calendar-level UI on top.
