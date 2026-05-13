# strictlykeptboy — event-create FAB + template picker

## Status: ✅ DECIDED — ready for implementation. Tracked under `main.md` Phase FFF.

The user-facing entry point for *adding anything to the schedule* —
the `+` FAB visible on Calendar/Day/Timebox/Agenda surfaces. One
button, two paths:

1. **Free-form** — appointment-style event. Title + time + calendar
   + optional notes. No atomic sub-beats.
2. **From template** — searchable picker over the atomic-activity
   template library shipped by Phase XX / AAA (brush-teeth, shower,
   workout, cage-check, etc.). Materializes the template into a real
   event at the chosen time, sub-beats and all.

The wizard (Phase K) writes a *seed* repo. After that, **everything
new flows through this FAB**. Re-running the wizard is opt-in
(Settings → Lifestyle → "Add more to my lifestyle"); it never blocks
this surface.

This plan corresponds to `main.md` Phase **FFF** and uses phase
prefix **EC-**.

It assumes:

- Phase XX (atomic activities, D.70) has shipped the atomic
  template files + sub-beat schema.
- Phase AAA (lifestyle templates) ships the canonical template
  *index* this picker reads.
- `EntityWriter.write` (Phase C.5) is the single write chokepoint.
- `materialize` semantics from XX.8 (routine quick-start) generalize
  to single-template materialization: copy frontmatter, mint
  UUIDv7, write `materialized_from = "<template-id>"`,
  `materialized_at = "<iso>"`. **Undo = delete the new file**;
  no soft-delete state to clean up.
- The FAB is visible on every schedule surface (Day / Week / Month
  / Year / Agenda / Timebox) and the same composable handles all
  entry points.

## Decisions locked (EC-D series)

- **EC-D.1 (decision):** FAB long-press menu carries three entries
  in this order: `New event in <active calendar>` (one-tap
  shortcut — opens free-form pre-targeted to whichever calendar
  the user last interacted with on the visible surface),
  `Start a routine` (defers to XX.8 quick-start),
  `Paste an .ics URL` (defers to Phase P import). v1 wires the
  first entry; the other two render as enabled menu items wired
  to their respective sheets when those phases ship. **Rationale:**
  power-users overwhelmingly want one-tap to the most-frequent
  calendar; the long-press cost is non-trivial so it earns three
  affordances, not one.
- **EC-D.2 (decision):** Voice-to-event NOT in scope for Phase
  FFF — explicitly owned by Phase HH (Android Auto voice-create).
  The text-FAB path must not block on voice work. Phase HH will
  call into `EventCreateFreeFormForm`'s validation + write path
  directly with pre-filled fields; the form composable is
  designed in EC-B.1 to accept an `initialDraft: EventDraft?`
  parameter so HH can pass parsed-utterance fields without
  touching FFF internals.

---

## Phase EC-A — FAB surface + entry-point unification

- [ ] **EC-A.1** Add `EventCreateFab` composable in
  `ui/schedule/EventCreateFab.kt`. M3E `ExtendedFloatingActionButton`
  collapses to icon-only when the underlying list scrolls; expanded
  shows `+ New`. Position: bottom-end, 16dp inset, sits above the
  bottom nav rail / bottom system bar via
  `WindowInsets.navigationBars.asPaddingValues()`.
- [ ] **EC-A.2** Wire the FAB into every schedule surface that
  currently lacks one: Day (`ScheduleDayPane`), Week, Month, Year
  (when promoted), Agenda, Timebox. Single shared composable; each
  pane passes its `defaultCalendarId` + `defaultStart` (next free
  slot anchored on the visible date / hour).
- [ ] **EC-A.3** Tapping the FAB opens `EventCreateSheet` — a M3E
  bottom sheet with two segmented-button tabs at the top:
  **Free-form** | **From template**. Persist last-used tab in
  `EventCreatePrefs` (default Free-form on first launch, then
  whatever the user last used).
- [ ] **EC-A.4** Long-press FAB → menu with `New event` (default
  free-form), `Start a routine` (defers to XX.8 routine
  quick-start), `Paste an .ics URL` (defers to Phase P import).
  Phase EC v1 wires only `New event`; the other two land as
  no-op-tracked menu items with feature flags off.
- [ ] **EC-A.5** Telemetry-free instrumentation: log via `Log.d`
  in debug builds only (`adb logcat -s skb-eventcreate:*`) which
  tab was chosen, time-to-confirm, template-pick vs free-form
  ratio. Strip in release.

## Phase EC-B — Free-form path

- [ ] **EC-B.1** `EventCreateFreeFormForm` composable inside the
  sheet. Fields in order: **Title** (single-line `TextField` with
  emoji-prefix autosurface per T.4), **Start** + **End**
  (`DateTimePicker` M3E), **Calendar** (chip-bar of active
  calendars from the active repo; pre-selected to
  `defaultCalendarId`), **Notes** (multiline `TextField`,
  optional). Below the fold: **Identity** chip (defaults to repo
  default per `identity.toml`), **Reminder** (uses Phase M
  defaults if unset).
- [ ] **EC-B.2** Validation rules: title non-empty after trim,
  end ≥ start, end - start ≤ 24h (sanity cap; multi-day events go
  through a different flow flagged for EC-Q in v2). Inline
  M3E `Supporting text` error styling — never a snackbar.
- [ ] **EC-B.3** Confirm → build `Event` data class →
  `EntityWriter.write(event)` → single commit
  `add event "<title>" on <date>"`. Close sheet, scroll the parent
  view to the new event, show a brief `Snackbar` "Event added" with
  `Undo` action (deletes the just-written file + commits the
  deletion).
- [ ] **EC-B.4** Recurrence sub-flow: a small `Repeat` chip under
  Start/End opens a secondary sheet with M3E `RadioGroup` of
  presets (Once / Daily / Weekly on \<day> / Monthly on \<n>
  / Custom RRULE). Custom RRULE opens a textfield. Saved as a
  recurrence file (`recurrences/<id>.md`) per Phase C/E; the
  `Event` becomes a rule + the materialization for "this week's
  first instance" so the surface sees something immediately.
- [ ] **EC-B.5** Per-event `private = true` flag (D.57 / K-2)
  surfaces as a small `Lock` toggle in the form footer. Defaults
  to repo's `mode.toml.default_private`. Tooltip explains: "Body
  visible only to you; title still surfaces in the schedule."

## Phase EC-C — Template picker path

- [ ] **EC-C.1** `TemplatePickerContent` composable. Top: pill
  `TextField` search (matches L&F / Settings / About visual
  language — `RoundedCornerShape(28.dp)`, transparent indicators,
  `surfaceContainerHigh` background, leading `Search` icon).
  Below: grouped LazyColumn over template sections.
- [ ] **EC-C.2** Template index source: read shipped templates from
  `assets/templates/index.toml` (Phase AAA) PLUS the user's own
  saved-as-template files at `templates/` in the active repo PLUS
  any custom template-pack URL (Phase S.7 — clone deferred to
  Phase WW). Merge into one `List<TemplateEntry>` with
  `source = SHIPPED | USER | PACK("<pack-id>")`.
- [ ] **EC-C.3** Section grouping in the picker: **Self-care** /
  **Workout** / **Routine** / **Kink** (only when neutral-mode
  OFF) / **Your templates** (only when user has any) /
  **<pack-name>** (per active custom pack). Each section header
  in `colorScheme.primary`, count badge in trailing slot.
- [ ] **EC-C.4** Each row = `ListItem` with `leadingContent`
  colored circular badge (icon from template TOML, tint from a
  hash of template-id for visual stability), `headlineContent`
  template display name, `supportingContent` duration + sub-beat
  count (`"5 min · 7 sub-beats"`), `trailingContent` info icon
  → opens read-only template-details sheet.
- [ ] **EC-C.5** Search filters across `displayName` + `tags` +
  `aliases`. Neutral-mode toggle (D.58 / K-3) hides any template
  with `kink` in `tags`. Empty-state copy:
  `settings_template_picker_no_results` when filter matches
  nothing.
- [ ] **EC-C.6** Tap a row → `TemplateConfirmSheet`: shows the
  picked template's title (editable — user can override), Start
  time (defaults to next free 15-min slot anchored on visible
  date), Calendar chip-bar, sub-beat preview list (read-only,
  each row = label + duration). Confirm button bottom-right.
- [ ] **EC-C.7** Confirm materializes:
  `TemplateMaterializer.materialize(template, start, calendarId,
   titleOverride)` → builds `Event` with copied frontmatter +
  `[[subbeat]]` array + `materialized_from = "<template-id>"` +
  `materialized_at = "<iso>"` → `EntityWriter.write` → single
  commit `add event from template "<id>" on <date>"`. Same
  snackbar + Undo affordance as EC-B.3.

## Phase EC-D — Save-as-template (round trip)

- [ ] **EC-D.1** From a free-form event detail sheet (Phase G.7),
  add an overflow menu item **Save as template**. Pre-fills a
  `SaveTemplateSheet` with the event's frontmatter, lets the user
  set `templateId` (slug-validated), `displayName`, `tags`,
  `neutral_safe`. Sub-beats are NOT auto-derived; user adds them
  in a follow-up.
- [ ] **EC-D.2** Writes to `templates/<templateId>.toml` in the
  active repo. Single commit
  `save template "<displayName>"`. Picker (EC-C) auto-rediscovers
  on next open via the same template-index loader.
- [ ] **EC-D.3** Edit-template sheet: tap the info icon on a
  USER-source row in the picker (EC-C.4) → editor (read-only for
  SHIPPED + PACK sources, editable for USER). Same form as
  EC-D.1.
- [ ] **EC-D.4** Delete-template: trash icon in the USER-source
  editor footer. Confirmation dialog. Single commit
  `delete template "<id>"`. Existing materialized events keep
  their `materialized_from` reference (dangling-but-fine — the
  template-id is just a string).

## Phase EC-E — Conflict + overlap + sanity

- [ ] **EC-E.1** Overlap detection: on confirm in EC-B.3 / EC-C.7,
  scan the target calendar for events that overlap the proposed
  `[start, end)` window. If any, surface a M3E `AlertDialog` —
  "This overlaps with **<other-title>** at **<other-time>**"
  with options **Schedule anyway** / **Pick a different time**
  / **Cancel**. Default focus = Pick a different time. No silent
  overwrite.
- [ ] **EC-E.2** Routine-overlay awareness (XX.7): if the target
  calendar has `routine = true` AND `routine_can_materialize =
  false`, refuse and explain — routines are quick-started via the
  long-press FAB → Start a routine path, not added as one-off
  events.
- [ ] **EC-E.3** Read-only / no-permission guard: if the active
  repo is share-imported with `readOnlyViaShare = true` (Phase O),
  the FAB renders disabled with a tooltip "This is a shared
  read-only calendar." Same for any calendar whose
  `calendar.toml.write_policy = "read-only"`.
- [ ] **EC-E.4** Multi-repo guard: if more than one repo is
  active, the calendar chip-bar groups by repo header
  (`<repo-display-name>` strip with the repo's avatar at left).
  Writes always go to the chip's owning repo. No cross-repo
  writes.
- [ ] **EC-E.5** Past-date guard: confirm proceeds for past-dated
  events (logging "did this earlier" is a valid use case) but the
  snackbar text changes to "Logged in the past." Phase XX's
  inverted habit model handles the resulting
  `completed-by-schedule` state cleanly.

## Phase EC-F — Visual + a11y polish

- [ ] **EC-F.1** Sheet height: `WindowInsets.ime` aware — when the
  soft keyboard opens, the form shifts up; sub-beat preview list
  becomes scrollable inside the sheet. No FAB visible while sheet
  is open.
- [ ] **EC-F.2** Focus order: title → start → end → calendar →
  notes. `TalkBack` reads the section labels; the chip-bar
  announces "Calendar, <name>, button" not just the name.
- [ ] **EC-F.3** Density-respect: every field uses
  `AdaptiveSpacing.minInteractive` (Phase R.5) so tablet-density
  expands the touch targets without breaking phone-density
  layouts.
- [ ] **EC-F.4** Theme-respect: pill search field uses
  `colorScheme.surfaceContainerHigh` and adapts to neutral-mode
  / theme picker / dynamic color per Phase T. No hard-coded
  colors.
- [ ] **EC-F.5** Empty-state in picker: when filtered to zero
  results, show a small inline tip "Try a different word, or
  switch to Free-form" with a tappable "Free-form" chip that
  flips the segmented-button tab.

## Phase EC-G — Tests + AVD smoke

- [ ] **EC-G.1** Robolectric unit tests:
  `EventCreateFreeFormFormTest` — required-field validation,
  end-before-start error, recurrence-preset selection round trip,
  `private` toggle persistence.
- [ ] **EC-G.2** Robolectric:
  `TemplatePickerContentTest` — index merge (shipped + user +
  pack), neutral-mode filter, search by tag, search by alias,
  section grouping order stable.
- [ ] **EC-G.3** Robolectric:
  `TemplateMaterializerTest` — frontmatter copy, sub-beat
  preservation, `materialized_from` written, UUIDv7 minted,
  single-commit semantics through `EntityWriter`.
- [ ] **EC-G.4** Robolectric: `EventCreateOverlapTest` — overlap
  dialog fires, "Schedule anyway" writes, "Pick a different
  time" reopens picker with start shifted, read-only-share
  refuses with disabled FAB.
- [ ] **EC-G.5** AVD smoke: install rebuilt debug APK on
  `emulator-5554`, exercise (a) free-form add → see event on
  Day view, (b) template pick → see materialized event with
  sub-beats in detail sheet, (c) save-as-template round trip via
  picker, (d) overlap dialog actually fires when scheduling on
  top of an existing event. Screencap each.

---

## Notes

- **Why FFF, not folding into Phase G or XX:** the FAB is the
  *user's primary write path* — it earns its own phase. Phase G
  handles read/render surfaces. Phase XX defines the atomic
  template substrate. Phase FFF is the bridge: where the user
  consciously *adds*.
- **Why save-as-template lives here, not in Phase XX:** XX is
  the data model + resolver + inversion engine; save-as-template
  is a UI affordance. Keeping them apart keeps the SOLID-S
  boundary clean.
- **Multi-calendar UX is intentionally tab-flat:** segmented
  buttons (Free-form / From template) read as two equal-weight
  affordances. Bias-by-frequency is handled by the "remember
  last-used tab" pref in EC-A.3, not by visually demoting one.
- **Re-run wizard from Settings (Phase K.12 / LW-L) is the
  *bulk* path; this FAB is the *single-item* path.** Both write
  the same file shape; both go through `EntityWriter`. No
  duplicate write code.
