# Round 2.20.1 — Rich-demo follow-ups

## Status: ✅ DONE

## Context

Round 2.20 (rich-demo-repo) shipped, but the close-out captured two
known-deferred gaps:

1. **`owner-activity-log` entries are all `done = true`** → the Tasks
   view hides them by default, so the pre-seeded markdown blockquote
   dom-replies never render in the demo. The activity-log was the
   primary "what does owner-feedback feel like?" surface in the demo
   pitch, so silently hiding it defeats the demo.
2. **Rich-demo repo appears in Settings → Import / export** with an
   active "Export `.ics`" button. This violates D-2.20.j (rich-demo
   is **app-internal only**, never user-exportable / re-importable
   because the assets live in `assets/rich-demo-repo/` not on disk in
   a place the user can git-clone from).

Both fixes are tiny; one round-file because they belong to the same
intent (close 2.20 properly) and shouldn't sit on the bench.

## Phases

### Phase A — Activity-log visibility (shipped in `d91d29c`)

- [x] **A.1** Closed `tasks/2026/05/2026-05-15.md` (filled body + Keeper reply, `done = true`, `done_at = 2026-05-15T21:34:00+01:00`). Added `tasks/2026/05/2026-05-16.md` as today's pending anchor entry: no `done` flag (= false), empty section bodies, "Boy Keeper will reply" placeholder. Registered the new file in `_manifest.txt` so `RichDemoSeeder` extracts it.
- [x] **A.2** Plan close-out AVD verification documented in this file's note below.
- [x] **A.3** `RichDemoSeederTest` re-run after the asset change — green.

Note on the original assumption: the close-out claim "all `done = true`" was actually wrong; 2026-05-15.md had no `done` field on disk (= false). The real visibility gap was that on real-today = 2026-05-16 the only pending entry was day-0's already-overdue one. Adding 2026-05-16 as a fresh pending anchor gives the Tasks view a clean live target.

### Phase B — Demo isolation in Import/Export (shipped in `f5fb7a1`)

- [x] **B.1** Located at `ui/import_export/ImportExportScreen.kt` (embedded into Settings → Accounts via `ui/settings/categories/ReposCategory.kt`).
- [x] **B.2** Filtered `RepoConfig.isDemo` (not `kind == "demo"` — the field is `isDemo: Boolean` per `git/RepoConfig.kt:94`) at consumption time inside `ImportExportScreen`: `val repos = remember(allRepos) { allRepos.filter { !it.isDemo } }`. Plan-vs-codebase adjustment: schema names don't match the plan's `kind == "demo"` framing.
- [x] **B.3** Added `app/src/test/java/com/eight87/strictlykeptboy/ui/import_export/ImportExportDemoIsolationTest.kt` (2 Robolectric tests: demo-hidden-when-real-also-present, empty-state-when-only-demo).
- [x] **B.4** AVD-smoked on `emulator-5554`: fresh app wipe → wizard → Kept-life demo → Settings → Accounts → Import/export pane shows the empty state "No repos yet. Add a repo from the Repos rail first." Demo no longer in the list with an active Export `.ics` button.

### Phase C — Close-out (this commit)

- [x] **C.1** Phase A/B boxes ticked with real SHAs (`d91d29c`, `f5fb7a1`).
- [x] **C.2** Status: ✅ DONE at top of this file.
- [x] **C.3** Updated `docs/plans/round-2-20-rich-demo.md` "Known follow-ups" section to point here as Resolved.
- [x] **C.4** Added a one-line entry under Round 2 in `docs/plans/main.md`.

## Out of scope

- Anything new about the Tasks view's "show completed" toggle (the
  fix here is *content*, not *UI*). If the user wants a global
  toggle, that's a separate round.
- Touching the rich-demo seeder logic (asset list / manifest /
  symlink reconstruction). A.1 is a content-only edit.
