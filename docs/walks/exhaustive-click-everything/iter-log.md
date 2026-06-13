# Exhaustive walk — iteration log

One line per grind-walk pass. Clean round = 0 new defects over the FULL list.

| Iter | Date | Items walked | Passed | New defects | Closed | Clean? |
|------|------|--------------|--------|-------------|--------|--------|
| 0 | 2026-06-08 | enumerate (527 controls, 72 screens) | — | — | — | — |
| 1 | 2026-06-08 | 30 (App Shell, Top Bar, Rail tabs, Now/3-day views) | 30 | 0 | — | partial-clean (sample) |
| 2 | 2026-06-13 | Schedule sweep (Now/Day/3-day/Week/Month/Year + event-detail + Settings top) via mobile-MCP | ~18 | 1 new (D-1) | — | NOT clean — D-1 systemic band overlap |
| 3 | 2026-06-13 | Tasks sheet grind (filters, checkbox, confirm, Done) | — | 3 new (D-3/D-4/D-5) + 1 verify (D-2) | — | NOT clean |
| 4 | 2026-06-13 | Re-walk after fix wave 1 (D-1 + D-3/D-4 merged) on read-only 5554 | — | 0 new | 3 (D-1, D-3, D-4) | partial-clean (verified batch 1) |

### Iter-3/4 notes (cycle 0 — fix wave 1 landed + verified)
- **Two fix agents** ran in parallel worktrees (skb-d1, skb-d3); both merged to main
  (D-1 `cd90f97`, D-3/D-4 `c0f9867`). 1017 + 7 new tests green.
- **Re-walk on the merged build verified all three:** schedule bands opaque (no
  bleed) across Day/3-day/Week; Done filter lists completed tasks incl. a
  freshly-marked one. **D-1/D-3/D-4 → Closed.**
- **Emulator policy fixed:** 5554 relaunched `-read-only` so future fix agents can
  boot their own coexisting read-only instances (5556/5558) instead of falling
  back onto 5554 mid-verify (which bit fix wave 1).
- **Still open:** D-2 (Week renders a single day column, not 7 — investigate intent
  vs bug), D-5 (interactive mark-done doesn't set `doneAt`, minor).
- **Next:** investigate D-2 in code; grind Reviews / Repos / Wizard+first-run /
  Event-create / Settings subcategories; then the 5 furry `claude -p` walks.

### Iter-2 notes (campaign cycle 0 — "get it to working")
Mobile-MCP tap-by-element grind, emulator-5554:
- **D-1 (major, systemic):** Day/3-day/Week time-grid event titles collide +
  bleed through each other (fixed-3-lane horizontal cascade, concurrent bands
  share pixel rows). Dominant "looks broken" issue. Fix agent dispatched in a
  background worktree (`skb-d1`), self-verifying on emulator-5556.
- **Clean this pass:** Month view (proper 7-col MON–SUN grid, today highlighted),
  event-detail sheet (title/time/repo/kind/priority/mute/Edit/Back all present),
  Settings top-level (restructured: Appearance/Library/Behaviour, search box).
- **D-2 (verify):** Week view appears to render a single full-width day column
  rather than 7 — confirm vs. sliding-window intent on the re-walk.
- **Pending iter-3+:** Tasks/todolist, Reviews, Together, Repos pane, Wizard +
  first-run routing, Event-create/Edit/Drag, all Settings subcategories, CalDAV
  wizard, Import/Export, deeplinks. Plus the 5 furry AI-Dom walks (claude -p
  authoring) once D-1 lands so the schedule reads cleanly.

### Iter-1 notes
First grind pass over the core navigation + schedule surface, on `emulator-5554`:
- **App Shell / Top Bar / Rail** — shell renders; top-bar Schedule/Reviews/Settings
  buttons + the calendar-picker + cog all present; rail Now/Day/3-day/Week/Month/Year
  tabs select + re-render (verified 3-day: now-line + base-layer band).
- **Schedule chrome** — FAB cluster (collapsing `+ New` + edit pen), overlay-picker
  trio (grid/filter/hide), bottom "No active task" bar all present + actuate.
- These also carry forward iter-4 AVD evidence (Look&Feel density chips wrap,
  Notifications lead-time chips wrap, CalDAV provider picker opens, Repos-pane
  RepoCard "Local-only — nothing to sync" footer, event-detail tz line).
- **0 defects** in the iter-1 sample (the iter-4 fixes hold).
- **Remaining ~497 controls** across Schedule detail/Tasks/Reviews/Together/Repos/
  Wizard/Settings-internals/Event-Create/CalDAV-wizard — pending iter-2+ (this is
  the multi-iteration grind the walk is designed for; AVD coord-tapping is the
  bottleneck — a `mobile` MCP tap-by-testTag session will accelerate it).
