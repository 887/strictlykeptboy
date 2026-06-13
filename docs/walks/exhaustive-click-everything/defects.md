# Exhaustive walk — defect log

Running list of every control that failed the grind-walk. Format + lifecycle in
[`README.md`](README.md). Close a defect only off a clean re-walk (CLAUDE.md
"iterate until clean").

## Open

### D-1 — Schedule (Day / 3-day / Week time-grid) → event bands — major
- **Expected:** every event title is legible; concurrent events are
  distinguishable without text bleeding through / colliding.
- **Actual:** time-overlapping events render with their titles **colliding and
  bleeding through each other** — unreadable. Root: `ScheduleDayView.kt:561-641`
  fixed-3-lane horizontal cascade (Base full-width → Regular +1 step → Timebox
  +2 steps). Concurrent bands in adjacent lanes overlap rectangles, and titles
  are left-aligned single strings drawn over each other. Examples seen on demo
  data: 3-day "Evening briefing" overlaps grey "cage / mo…"; Week "Shower" over
  grey "…ddy good morning", "Morning briefing" bleeds through, "Cage on / Cage
  stays on" collide.
- **Repro:** launch → Week (or 3-day) view on the seeded demo repo, scroll the
  06:30–08:00 morning stack. • **Shot:** /tmp/laragana/skb-c0-week.png,
  skb-c0-launch.png
- **Fix direction:** higher-lane bands must fully occlude (opaque) AND every
  band's title must clip/ellipsize to its *own visible* (non-occluded) width so
  no title is painted into a neighbour's column. Verify across Day/3-day/Week at
  every zoom on the morning-stack demo. Must NOT regress the deliberate
  paint-order (later start on top) behaviour.
- **Evidence (element dump):** at 06:30 five bands report the SAME y=710 —
  "Grooming + breakfast" (w686), "Morning routine" (w78, clipped), "Morning
  alarm" (w539), "Cage on" — stacked on the same pixel row. Sub-note: 5-minute
  events (e.g. "Morning alarm" 06:30–06:35) get their time text clipped to ~6px
  height at low zoom — unreadable; fold into the same fix.
- **Status:** open — fix agent dispatched (background, verifying on emulator-5556)

### D-2 — Schedule → Week view → day columns — verify
- **Expected:** Week view shows 7 day columns side-by-side.
- **Actual:** appears to render a single full-width day column (needs
  confirmation — header was scrolled off). Possibly correct (sliding window) or a
  layout regression. • **Shot:** skb-c0-week.png
- **Status:** open (verify in re-walk)

## Closed

_(none yet)_
