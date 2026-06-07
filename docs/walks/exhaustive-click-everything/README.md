# Exhaustive "click everything" walk — QA + devteam iteration loop

The point of this walk: **touch every interactive control on every screen,
verify it does what it claims, and don't stop until a clean re-walk finds
nothing broken.** Not a happy-path scenario — a brute-force sweep of the whole
surface.

This is run as a **QA + devteam loop where Claude plays both teams** (per the
global CLAUDE.md "QA + dev = iterate until clean" rule): a fix is finished only
when a clean re-verification round confirms it. One green pass is a draft, not a
done.

## Files in this walk

| File | Owner | What it is |
|------|-------|-----------|
| `feature-checklist.md` | **Enumeration agent** (Phase 0) | The auto-built inventory of every screen × every control. Each line is one `- [ ]` item with its expected behavior + testTag. The source of truth for "what must be touched." |
| `defects.md` | **QA-grind agent** (Phase 1, updated every iteration) | The running defect list: every control that failed, was confusing, crashed, or no-op'd. Severity-ranked, with repro + screenshot ref. |
| `iter-log.md` | **Orchestrator** | One line per walk iteration: date, # items walked, # passed, # new defects, # defects closed, clean-round? (y/n). |

## The loop

```
Phase 0  ENUMERATE   (dumb agent, file-only, no AVD)
   └─ read the UI source → emit feature-checklist.md (every control, unticked).
      Re-run whenever new UI ships so the list never goes stale.
        ↓
Phase 1  GRIND-WALK   (QA agent, owns the AVD, sequential)
   └─ for EVERY unticked item: actuate the control on the AVD, observe.
      - works as described      → tick - [x] in feature-checklist.md
      - broken / confusing / no-op / crash → log a defect in defects.md,
        leave the checklist item UNticked
      Never skip an item silently; if a control can't be reached, that's a
      defect (dead/unreachable control).
        ↓
Phase 2  FIX         (dev pass — may fan out background sub-agents per defect)
   └─ fix each open defect. After each fix: re-actuate the control AND its
      neighbours on the AVD (a fix in one place reds another — that's a NEW
      defect, log it, don't absorb it). Unit tests stay green (a red test is a
      defect). Commit per fix or per coherent batch.
        ↓
Phase 3  RE-WALK      (back to Phase 1 over the FULL list, regressions included)
   └─ walk the whole checklist again — not just the previously-failed items.
      Update defects.md (close fixed, open new). Append an iter-log.md line.
        ↓
   Repeat 1→2→3 until a re-walk produces ZERO new defects AND every checklist
   item is - [x]. THAT clean round is "done". Until then it isn't.
```

## How to actually walk it (AVD recipe)

The QA-grind agent (or the orchestrator) drives the live app, per the repo's
canonical loop:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
~/Android/Sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -n com.eight87.strictlykeptboy/.MainActivity
~/Android/Sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p | magick - -resize 50% /tmp/skb.png   # then Read it
```

- **Tap coords are NATIVE 1080×2400.** Screenshots are downscaled 50% (540-wide)
  for reading — multiply read-off coords ×2 before `adb input tap`.
- Prefer the `mobile` MCP (a11y tree + tap-by-label/testTag) when loaded — it's
  far less flaky than coordinate taps and can target the testTags the checklist
  records.
- Some controls need a specific precondition (a remote repo for "Sync now", a
  configured mirror for CalDAV list rows, strictly-kept mode for the Dom-persona
  sheet). The checklist notes these; set the precondition, then actuate.
- Clean up `/tmp/*.png` between iterations.

## defects.md entry format

```
### D-<n> — <screen> → <control> — <severity: blocker|major|minor>
- **Expected:** <what the checklist says it should do>
- **Actual:** <what happened>
- **Repro:** <steps / coords / testTag>  •  **Shot:** <path or n/a>
- **Status:** open | fixed-in <commit> | wontfix-<reason>
```

## Dispatching the agents

- **Phase 0 (enumerate):** one read-only agent over `ui/`. Re-runnable; cheap.
- **Phase 1 (grind-walk):** ONE agent at a time owns `emulator-5554` — the AVD is
  a single shared resource, so grind-walks DO NOT parallelize. Run them
  sequentially (or let the orchestrator do the walk directly).
- **Phase 2 (fix):** defects are independent → fan out background dev sub-agents
  (one per defect or per cohesive cluster), but they must NOT touch the AVD
  concurrently — code-only fixes in parallel, then the orchestrator re-walks.

## Done criteria

- [ ] `feature-checklist.md` exists and every item is `- [x]`.
- [ ] `defects.md` has zero `open` entries.
- [ ] The most recent `iter-log.md` line is a clean round (0 new defects) over
      the FULL list.

Until all three hold, the walk is still in progress — keep iterating.
