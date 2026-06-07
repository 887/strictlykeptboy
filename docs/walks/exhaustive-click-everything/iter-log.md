# Exhaustive walk — iteration log

One line per grind-walk pass. Clean round = 0 new defects over the FULL list.

| Iter | Date | Items walked | Passed | New defects | Closed | Clean? |
|------|------|--------------|--------|-------------|--------|--------|
| 0 | 2026-06-08 | enumerate (527 controls, 72 screens) | — | — | — | — |
| 1 | 2026-06-08 | 30 (App Shell, Top Bar, Rail tabs, Now/3-day views) | 30 | 0 | — | partial-clean (sample) |

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
