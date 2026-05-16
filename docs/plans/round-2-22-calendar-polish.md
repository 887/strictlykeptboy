# Round 2.22 — Calendar UX polish + close-outs

## Status: DRAFT

## Context

Round 2.21 closed the per-overlay zoom + grouping + picker work. Three
loose threads remain visible-or-tracked:

1. **F48 — `IdentityAvatar` top-bar inconsistency.** Tracked in
   `refactor-solid.md` as medium-priority since the user picks a
   non-bat species in the wizard. Per D.88 (a repo IS an identity),
   the top-bar avatar + repo-row circles must reflect the ACTIVE
   repo's `iconSpecies` / `iconEmoji` / `iconPhoto`. Significant
   wiring has shipped (`activeRepoIconKind` flow + `RepoCard.RepoCircle`
   using `repo.toIconKind()` + `ShellTopBar.IdentityAvatar(iconKind=...)`).
   This round confirms the surfaces, formalises the documented
   fallback chain in D-2.22.a, adds the `IdentityAvatarFallbackTest`
   coverage, and closes F48.
2. **Phase DD — drag-to-reschedule.** DD.4 + DD.5 (pinch + zoom
   persistence) shipped in Round 2.21. DD.1 / DD.2 / DD.3 / DD.6
   (long-press drag + grid snap + drop-commit + recurrence drag
   prompt) remained "Round 4 — later". Adjacent to the grouping work
   that just landed and the natural close-out for Phase DD.
3. **Phase FF — Custom sticker / icon packs.** Most of the substance
   shipped through Phase WW (WW.1 default pack format + resolver
   chain, WW.4 user-pack loader, WW.5 storage + picker) and Round
   2.5.C (`StickerPackSelectorScreen`, `AvatarPackPrefs`,
   `CompositePackStore`, `userPackLoader.cloneFrom`). The FF
   substeps may already be fully or substantially covered — audit
   and dispose (retire vs. open vs. scope).

## Locked design decisions

- **D-2.22.a — IdentityAvatar fallback chain (formalises F48).**
  Resolution order, per `RepoConfig.toIconKind`:
  1. `iconSpecies != null` → `RepoIconKind.Sticker(species)`.
     a. Sticker resolver returns BitmapHit → render bitmap.
     b. Sticker resolver returns DrawableFallback + species=="bat" →
        render `R.drawable.about_bat`.
     c. Sticker resolver returns DrawableFallback + species!="bat" →
        render `AutoInitials(species[0], seedColorFromName(species))`.
  2. `iconEmoji != null` → `RepoIconKind.Emoji(glyph)`.
  3. `iconPhotoUri != null` → `RepoIconKind.Photo(uri)`.
  4. else → `RepoIconKind.AutoInitials(initialsFromName(displayName),
     seedColorFromName(displayName))`.
  Same chain applied identically by `ShellTopBar.IdentityAvatar`,
  `RepoCard.RepoCircle`, and `RepoSwitcherDropdown.RepoCircle` (all
  three already delegate to `repo.toIconKind()` + `RepoIcon`).

- **D-2.22.b — Drag-to-reschedule semantics.**
  - Gesture: long-press on event band → drag follows finger → drop.
  - Surfaces: Day, Week, 3-day views. **Not** Schedule (agenda list),
    Month, Year.
  - Snap: 15-minute grid by default (`DRAG_SNAP_MINUTES = 15`).
    Pure function `snapToGrid(timestamp, minutes)` for test-ability.
  - Visual: ghost band follows finger; snapped target time labelled
    on the ghost.
  - Drop commit: rewrites `events/<yyyy>/<mm>/<id>.md` with new
    start/end (duration preserved). Auto-commit message:
    `move event "<title>" from <old-iso> to <new-iso>` via
    `GitRepoRegistry.commitAll`.
  - Recurring instance drop → AlertDialog with three choices:
    1. **This instance only** → write `exceptions/<rule-id>/<original-date>.md`
       with `kind = "move"`, `new_start`, `new_end`.
    2. **This and future** → cap existing RRULE with
       `UNTIL = day-before-drop`; create new RRULE rooted at drop date.
    3. **Entire series** → rewrite rule file with new `dtstart`
       (preserves DURATION).
  - Cancelled drag (drop on origin or outside grid bounds) → no-op,
    no commit.

- **D-2.22.c — Phase FF disposition (decided after audit, B.3 below).**
  If WW + 2.5.C cover everything substantive → mark FF as RETIRED
  with cross-references. If real gaps remain → scope minimal
  sub-steps and ship inside this round only if cheap; otherwise defer
  with explicit note.

## Phases

### Phase A — F48 IdentityAvatar close-out (shipped in commit `870ee05`)

- [x] **A.1** Audit confirms the wiring already exists end-to-end:
  `AppGraph.activeRepoIconKind` (per-repo derived flow) →
  `SkbAppShell.activeIconKind` → `ShellTopBar.IdentityAvatar(iconKind=
  activeIconKind)`; `RepoCard.RepoCircle` (in ReposPane) and
  `RepoSwitcherDropdown.RepoCircle` both call `repo.toIconKind()`.
  `RepoConfig.toIconKind` is the single chokepoint; `StickerBadge`
  handles the species-specific (bat → about_bat, other → monogram)
  fallback inside `RepoIcon`.
- [x] **A.2** `IdentityAvatarFallbackTest` lands in
  `app/src/test/.../ui/components/` — 7 pure-JVM tests pinning the
  D-2.22.a chain (species→Sticker, emoji→Emoji, species wins over
  emoji, displayName→AutoInitials with stable seed colour, empty-name
  guarded).
- [x] **A.3** `:app:testDebugUnitTest` green; new tests pass.
- [x] **A.4** F48 marked ✅ RESOLVED in
  `docs/plans/refactor-solid.md` with the landing commit SHA.
- [x] **A.5** Commit.

### Phase B — DD drag-to-reschedule

- [ ] **B.1** Pure `snapToGrid(epochSeconds, gridMinutes): Long` +
  duration-preserving move helper. `DragRescheduleMathTest` (snap +
  ghost coords + drop math).
- [ ] **B.2** Long-press + drag gesture wiring on `DayBand` in
  `ScheduleDayView` (which `ScheduleThreeDayView` reuses three of).
  Use `Modifier.pointerInput { detectDragGesturesAfterLongPress }`.
  Render ghost band that follows finger; show snapped target time
  label.
- [ ] **B.3** `WeekDayColumn` (Week view) parallel wiring — same
  gesture, same ghost. 3-day view auto-inherits via reused
  `ScheduleDayView`.
- [ ] **B.4** Drop handler: classify event as single-instance vs.
  recurring-instance. Single-instance path → rewrite event file +
  `GitRepoRegistry.commitAll("move event \"<title>\" from <old-iso>
  to <new-iso>")`. `DragRescheduleSingleInstanceTest`.
- [ ] **B.5** Recurring path → AlertDialog with three branches.
  - This-instance-only → write
    `exceptions/<rule-id>/<original-date>.md` `kind="move"`
    `new_start` `new_end`.
  - This-and-future → split rule file (UNTIL on old rule + new rule
    from drop date forward).
  - Entire-series → rewrite rule's `dtstart`.
  `DragRescheduleRecurringPromptTest` (mock prompt outcome × 3).
- [ ] **B.6** Cancelled drag (no movement, off-grid drop) →
  no-write. Covered in `DragRescheduleMathTest`.
- [ ] **B.7** AVD smoke: try `adb input swipe` long-press + drag on
  Day view; screencap before/after. If gesture firing is unreliable
  (same limit as Round 2.1.B / 2.21 — Compose `combinedClickable`
  long-press + drag through `adb input swipe`), document and rely on
  unit tests + the manual on-device verification path.
- [ ] **B.8** Tick DD.1 / DD.2 / DD.3 / DD.6 in `main.md` Phase DD
  with the landing commit SHA. Mark Phase DD ✅ DONE.
- [ ] **B.9** Commit.

### Phase C — Phase FF audit + disposition

- [ ] **C.1** Read each FF substep against WW + 2.5.C surfaces.
- [ ] **C.2** Per substep, mark (a) shipped via WW/2.5.C with
  cross-ref, (b) genuinely open + small, (c) genuinely open +
  substantial.
- [ ] **C.3** Update Phase FF header in `main.md` with disposition
  outcome.
- [ ] **C.4** If any (b) cheap items appear — ship inline. If only
  (a) or (c), close FF as RETIRED / mark remaining substeps with
  explicit Round-N deferrals.
- [ ] **C.5** Commit.

### Phase D — Close-out

- [ ] **D.1** Tick every Round-2.22 checkbox above with the landing
  commit SHA on each phase header.
- [ ] **D.2** Set `## Status: ✅ DONE` at the top of this file.
- [ ] **D.3** Append D-2.22.a / D-2.22.b / D-2.22.c to
  `docs/plans/decisions.md` as D.110 / D.111 / D.112.
- [ ] **D.4** Add Round 2.22 entry to `docs/plans/main.md` at the
  top of the Round-2 section, dated 2026-05-16.
- [ ] **D.5** Run `scripts/build-release-apk.sh --gh-release --install`.

## Out of scope

- Drag-to-reschedule on grouped-bands (single-vs-children semantics
  is a separate problem; the user explicitly carved it out in Round
  2.21's "out of scope" — defer further).
- Drag-to-reschedule on Schedule (agenda list) — list view has no
  spatial axis.
- Snap interval picker UI — `DRAG_SNAP_MINUTES` const for now;
  future round can wire to a setting if asked.

## Verification gates

- Existing tests stay green; +5–7 new tests across
  `IdentityAvatarFallbackTest`, `DragRescheduleMathTest`,
  `DragRescheduleSingleInstanceTest`,
  `DragRescheduleRecurringPromptTest`.
- AVD sweep: drag-on-Day if `adb input swipe` cooperates; otherwise
  unit tests + manual on-device note.
- `:app:assembleDebug` + install + screencap at the end of each phase
  per the CLAUDE.md loop.
