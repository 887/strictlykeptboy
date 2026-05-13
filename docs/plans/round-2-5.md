# Round 2.5 — Per-repo overlay control + calendar management + sticker packs

## Status: PENDING — planning complete, implementation not started

## Why this round exists

User flagged three connected gaps in the repo configuration surface:

1. **No good way to set up calendars per repo.** The per-repo settings screen is sparse — no list of the repo's calendars, no way to toggle individual calendars active/inactive, no entry-point to the `CalendarSettingsSheet` (which already exists from 2.1.B.4 — `activeWindows` / `activeHours` / `priority` / `supersedes` editor) for adding holiday windows. The data model + sheet are shipped; the routing into them from the per-repo settings screen is missing.

2. **Sticker packs treated as emoji.** The repo identity shows a single emoji where the user thinks in terms of sticker packs (Phase WW data model — `assetPackLoader` + `userPackLoader` + `CompositePackStore` are shipped; the per-repo UI never surfaces them). User can import their own packs from a git URL → no UI for that yet.

3. **Wrong overlay metaphor.** Current `Repos` pane has a binary "Show all repos in unified view" toggle. User's mental model: each repo gets its OWN show/hide for the main schedule, AND a separate "draw todos from here" flag. The three-repo use-case (sub repo + dom repo + shared-fun repo) needs all three independently overlay-able with independent todo sourcing — dom's todos shouldn't appear in sub's task list even though dom's events should appear on sub's schedule.

## Locked design decisions

- **D-2.5.a — Per-repo overlay toggles replace the unified-view boolean.** `ReposViewState.unifiedView` is dropped. Each `RepoConfig` gains two independent fields: `showOnSchedule: Boolean` (default true for the active repo, true for foreign repos, false for shared-but-not-overlaid) and `drawTasksFrom: Boolean` (default true for the active repo, false for foreign repos by default — user opts in per-repo).
- **D-2.5.b — Per-repo settings is a real edit surface, not a list-card.** Tapping a repo in the Repos pane opens a full per-repo settings screen with sections:
  - **Calendars** — list of all calendars in this repo, each row showing emoji + name + active-toggle + priority + chevron. Tap chevron → `CalendarSettingsSheet` (2.1.B.4 reuse — multi-range `activeWindows`, per-DOW `activeHours`, supersedes). "+ Add calendar" button at bottom.
  - **Sticker pack** — current pack chip + "Switch pack" → opens a new `StickerPackSelectorScreen` (bundled packs + user-installed packs + "Import from URL"). Stickers, NOT emoji.
  - **Identity** — pronouns / praise / honorific (reuse from settings's IdentityCategory, scoped to this repo's `identity.toml`).
  - **Mode** — Lifestyle six-radio for this repo (reuse `ModeCategory`, scoped to this repo's `mode.toml`).
  - **Sync** — auto-sync interval / manual sync button / read-only status (existing).
  - **Remotes** — list of remotes + add/edit/remove (existing).
- **D-2.5.c — Each repo card in `Repos` pane gets two toggles** in the row: a 📅 "show on schedule" chip-toggle and a ✓ "draw tasks" chip-toggle. These map directly to `RepoConfig.showOnSchedule` / `RepoConfig.drawTasksFrom`. The avatar tap still sets the active write-target repo (D.88 preserved).
- **D-2.5.d — Schedule + Tasks resolver consume the per-repo flags.** `SchedulePane` filters `RepoSnapshot.repos` to `repo.showOnSchedule == true` before union. `TasksPane` filters to `repo.drawTasksFrom == true`. The existing `CalendarVisibilityPrefs` per-calendar layer still works on top — repo-level is a coarser filter.
- **D-2.5.e — Sticker pack UI is the canonical per-repo identity surface.** Emoji selectors stay only as a fallback for users who don't want a sticker pack (one of the bundled packs is "minimal — emoji only").
- **D-2.5.f — No new TOML schema on disk.** `RepoConfig` is local prefs (`EncryptedSharedPreferences`); adding `showOnSchedule` + `drawTasksFrom` is a prefs migration only.

## Phase ordering

```
2.5.A  Per-repo overlay toggles    (PREREQ — RepoConfig prefs migration + Repos card UI)
  ↓
2.5.B  Per-repo settings screen     (calendar list with CalendarSettingsSheet entry + sections)
2.5.C  Sticker pack subview         (Phase WW UI surface inside repo settings)
2.5.D  Schedule + Tasks consumption  (resolver filters on per-repo flags)
  ↓
2.5.E  AVD smoke + release
```

## Phase 2.5.A — Per-repo overlay toggles

- [ ] **2.5.A.1** Migrate `RepoConfig` (`prefs/RepoConfig.kt` or wherever it lives): add `showOnSchedule: Boolean = true` and `drawTasksFrom: Boolean = false` (false for non-active repos by default; the active repo flips to true on activation). One-time migration that initializes both fields from the old `unifiedView` boolean: `unifiedView = true` → all repos `showOnSchedule = true`; `unifiedView = false` → only active repo true.
- [ ] **2.5.A.2** Drop `ReposViewState.unifiedView` flow + the "Show all repos in unified view" Switch in `ReposPane`. Drop the unified-view indicator chip in `ShellTopBar` (2.1.B.7). The information is now per-repo on each card.
- [ ] **2.5.A.3** Each repo card in `ReposList` gains two small FilterChip-like toggles in the row, between the mode badge and the sync icon:
  - 📅 chip (selected = `showOnSchedule`)
  - ✓ chip (selected = `drawTasksFrom`)
  - Both with explicit content-descriptions for a11y.
- [ ] **2.5.A.4** Plumb the new flags through `RepoStore.update(repoId) { copy(showOnSchedule = …) }` etc. Add `RepoOverlayPrefsTest` covering migration + flag round-trip.

## Phase 2.5.B — Per-repo settings screen expansion

- [ ] **2.5.B.1** Rewrite `RepoSettingsScreen` to be a sectioned scroll with the six sections from D-2.5.b. Each section is a labelled `Card` or `Column` with header + content.
- [ ] **2.5.B.2** **Calendars section.** Lists all calendars in the repo via `CalendarRegistry.calendarsFor(repoId)`. Each row: emoji + display name + `Switch` for `activeToggle` + priority text + chevron. Tap chevron → opens `CalendarSettingsSheet` (2.1.B.4 — sheet already exists, just route into it). "+ Add calendar" button at bottom opens a small "New calendar" dialog → writes a new `calendars/<uuid>/calendar.toml` via existing `RoutineCalendarConfig.write` + `CalendarActivityConfig.write`.
- [ ] **2.5.B.3** **Identity section.** Reuse `IdentityCategory` (Settings) Composable, parameterized by `repoRoot: File`. Per-repo `IdentityTomlCodec.readOrDefault(repoRoot)` instead of the global `IdentityPrefs`. Edits round-trip through 2.1.J's debounce+commit pattern, scoped to the repo.
- [ ] **2.5.B.4** **Mode section.** Reuse `ModeCategory` Composable similarly, parameterized by `repoRoot`. The six lifestyle-radios from 2.2.B.4 work as-is; per-repo `mode.toml` already exists (2.1.K.1).
- [ ] **2.5.B.5** **Sync + Remotes sections.** These already exist in the current `RepoSettingsScreen` — preserve them as sections in the new sectioned layout (don't rewrite).

## Phase 2.5.C — Sticker pack subview

- [ ] **2.5.C.1** New `StickerPackSelectorScreen` (`ui/repos/StickerPackSelectorScreen.kt`):
  - Lists all packs via `packStore.allPacks()` = `userPackLoader.loadAll() + assetPackLoader.loadAll()`
  - Each pack row: pack name + thumbnail (one sample sticker) + "bundled" / "custom" badge + radio for "active for this species"
  - "+ Import custom pack" button at bottom → opens import flow (URL entry + git clone via `userPackLoader.cloneFrom(url)`)
  - Per-species active pack stored in `AvatarPackPrefs` (already exists)
- [ ] **2.5.C.2** New "Sticker pack" section in `RepoSettingsScreen` (between Identity and Mode):
  - Shows current pack name + thumbnail (one sample)
  - "Switch pack" button → pushes `StickerPackSelectorScreen` into the same detail-pane navigation
- [ ] **2.5.C.3** Drop the "emoji selector" affordance from the wizard's Identity screen / from `IdentityCategory` (it stays available as a fallback inside one bundled "minimal" sticker pack). Update copy so "emoji" never appears where "sticker" should — the user thinks in packs.
- [ ] **2.5.C.4** Test: `StickerPackImportTest` covering URL → clone → register → activate flow with a local test fixture (no network).

## Phase 2.5.D — Schedule + Tasks consumption

- [ ] **2.5.D.1** `SchedulePane` view-model filters `RepoSnapshot.repos` to `repos.filter { it.showOnSchedule }` before passing to `Renderer`. Bands from `repo.showOnSchedule == false` repos are excluded from rendering entirely (one level above the per-calendar visibility filter).
- [ ] **2.5.D.2** `TasksPane` view-model filters todolists to `repos.filter { it.drawTasksFrom }` before populating `TasksUiState.activeTodolistIds`. Tasks from `drawTasksFrom == false` repos never reach the Combined view.
- [ ] **2.5.D.3** Update `EmptyScheduleState` selector (2.2.C.8) to account for the per-repo filter — if every repo has `showOnSchedule = false`, that's a new "(d) all repos hidden from schedule" empty state with CTA "Open Repos to enable an overlay".
- [ ] **2.5.D.4** Tests:
  - `PerRepoOverlayResolverTest` — given repo-A (`showOnSchedule = true`) + repo-B (`showOnSchedule = false`), assert that resolver output contains only repo-A's bands.
  - `PerRepoTaskFilterTest` — given repo-A (`drawTasksFrom = true`) + repo-B (`drawTasksFrom = false`), `TasksUiState.activeTodolistIds` contains only repo-A's todolists.
  - **Three-repo use-case integration test** — seed sub-repo (own, both flags true), dom-repo (foreign, showOnSchedule=true, drawTasksFrom=false), shared-fun-repo (foreign, both flags true). Assert: schedule renders all three's events; tasks come from sub + shared-fun only (NOT dom).

## Phase 2.5.E — AVD smoke + release

- [ ] **2.5.E.1** AVD walkthrough on `emulator-5554`:
  - Wipe data → wizard → finish first repo → Repos pane shows the new card layout with 📅 + ✓ toggles
  - Add a second repo via "+ Add account" → toggle 📅 = on, ✓ = off → return to Schedule → verify both repos' events overlay; tasks only from first repo
  - Open repo settings → Calendars section → tap a calendar → `CalendarSettingsSheet` opens; add an `activeWindow` for "2026-12-20 → 2027-01-05" (holiday) → save
  - Sticker pack section → "Switch pack" → import a custom pack from a local `file://` URL → activate
  - Screenshots to `docs/qa/2-5/`
- [ ] **2.5.E.2** Release via `scripts/build-release-apk.sh --gh-release`.
- [ ] **2.5.E.3** Status flip — `## Status: ✅ DONE` on this file + main.md Round 2.5 section.

---

## Total scope

**~20 sub-steps across 5 phases.** No new TOML schemas (RepoConfig is local prefs only). Reuses every piece of work shipped in 2.1.B (CalendarSettingsSheet, CalendarActivityConfig, CalendarRegistry), 2.1.J/K (IdentityTomlCodec, ModeTomlCodec), 2.2.B (Lifestyle six-radio), Phase WW (sticker pack stores). What's new is **the per-repo settings screen as a real edit surface** + **per-repo overlay flags replacing the binary unified toggle** + **the sticker pack UI surface** that the data layer has been waiting for.

## The three-repo use-case is the acceptance test

After this round, the user can:
1. Wizard-scaffold their own sub-repo (kept-by-AI default) — `showOnSchedule = true`, `drawTasksFrom = true`
2. Add their dom's shared repo via deeplink — `showOnSchedule = true`, `drawTasksFrom = false` (dom's todos don't leak into sub's task list)
3. Add a shared "fun" repo (date nights, mutual kink scenes) — `showOnSchedule = true`, `drawTasksFrom = true`
4. See all three overlay on Schedule with cross-repo author chips (2.1.B.5 / 2.2.C.2)
5. See sub + shared-fun tasks in Tasks (dom's filtered out)
6. Set the shared-fun repo to `showOnSchedule = false` when on vacation, dom's events still visible
7. Per-calendar `activeWindows` on holiday calendars (e.g. "PTO 2026-12-20..2027-01-05") via `CalendarSettingsSheet`
