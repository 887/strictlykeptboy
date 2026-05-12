# Phase V — perf baseline (2026-05-13)

> Recorded measurements for Phase V (V.1..V.5). Targets are on-device;
> Robolectric numbers are illustrative-only (JVM/shadow path).
> AVD: `medium_phone` (API 36 / Android 16, swiftshader_indirect GPU,
> headless, no-audio, no-snapshot).

Commit (pre-V): `f64012d` (Phase U).

## V.1 — Cold start budget: < 600ms to schedule day view

| Run | LaunchState | TotalTime | WaitTime |
|----:|:-----------:|----------:|---------:|
| 1   | COLD        | 1741 ms   | 1744 ms  |
| 2   | COLD        | 1566 ms   | 1570 ms  |
| 3   | COLD        | 1522 ms   | 1526 ms  |

**On-device target:** < 600 ms. **AVD measured:** ≈ 1.5–1.7 s (swiftshader).
**Status:** **over budget on AVD.** Software-rasteriser + emulated GPU
adds ≈ 800–1000 ms of frame-pipeline cost vs. real hardware. The Kotlin
side (`AppGraph` ctor + lazy chain) clocks ≈ 28 ms under Robolectric
([perf V.1] log), so the surplus is almost entirely first-frame Compose
+ Skia work. **Action:** re-measure on the user's real device when
wifi-adb lands (per CLAUDE.md "wifi-adb to the user's phone"); only
optimize if real-device is also > 600 ms.

Tracer wired: `app_oncreate` + `appgraph_init` + `mainactivity_oncreate`
+ `schedulepane_first_render` (see `perf/PerfTraceRecorder.kt`),
inspectable via Perfetto `am trace` capture.

## V.2 — Sync small repo: < 2s (50-entity repo)

Robolectric (`SyncSmallRepoBenchmarkTest`, file-transport bare):

```
[perf V.2] sync small repo (50 entities, fullScan+fetch+pullRebase): 34 ms
```

**Within budget** by two orders of magnitude on the JVM path; on-device
will be slower due to NIO overhead + Dispatchers.IO scheduling, but
nowhere near 2 s.

## V.3 — Render month with 200 events: < 200ms

Robolectric (`MonthRenderBenchmarkTest`):

```
[perf V.3] month-render 200 events + 5 rules: 3 ms (warm)
```

**Within budget** by 60× headroom. dmfs lib-recur RRULE expansion +
lane resolver are not the hotspot at this scale. Re-measure if event
count climbs past 2 k.

## V.4 — Common-time finder over 5 repos × 30 days: < 800ms

Robolectric (`CommonTimeFinderBenchmarkTest`):

```
[perf V.4] common-time 5×30: 1 ms, 20 slots
```

**Within budget** by 800× headroom. The sweep-line over the aggregated
busy set is linear in the number of busy intervals; no quadratic
behaviour observed.

## V.5 — Memory: < 150MB resident at steady state

AVD `dumpsys meminfo` 5 s after launch + after idle:

| Capture | TOTAL PSS | TOTAL RSS | SWAP PSS |
|:--------|----------:|----------:|---------:|
| t+5 s   | 179111 KB | 248660 KB | 601 KB   |
| idle    | 178227 KB | 250544 KB | 678 KB   |

**On-device target:** < 150 MB resident. **AVD measured:** ≈ 178 MB PSS
/ ≈ 250 MB RSS. **Status:** **over budget on AVD.** The bulk of this is
JGit (Apache MINA SSHD + BouncyCastle) + Compose + Room loaded eagerly
at app start. Real-device numbers will be similar in PSS (kernel pages
are kernel pages) — this is a genuine over-budget condition, not an
AVD artefact. **Action:** narrow JGit R8 keep-rules (Phase W.6 / F-marker
in `proguard-rules.pro`) to drop unused transport providers; lazy-init
the sync scheduler post-first-frame; defer BouncyCastle insertion to
first auth use. Tracking as `F41` in `refactor-solid.md`.

## Perf-discipline additions shipped this phase

- **GitRepoRegistry bounded at 50 entries** (LRU). Was unbounded; now
  uses `LinkedHashMap.removeEldestEntry`. Test:
  `GitRepoRegistryBoundedTest`.
- **`PerfTraceRecorder`** under `perf/` — narrow port over
  `android.os.Trace.beginSection / endSection`. Test:
  `PerfTraceRecorderTest` (sections unique-tag invariant + nesting +
  defensive double-end).

## R8 keep rules narrowing (V.5 follow-up — deferred)

Current `app/proguard-rules.pro` keeps `org.eclipse.jgit.**` / `org.apache.sshd.**`
/ `org.bouncycastle.**` wholesale. Per Phase V's standing discipline,
Gradle baseline-profile generation is the right hammer — defer to
Phase W release engineering (`W.6`).

## Crashes / logcat

No `AndroidRuntime:E` / `FATAL` in `adb logcat -d -t 200` after three
cold starts.

## Test suite

- 351 tests pass on `:app:testDebugUnitTest` (up from 344 pre-V).
- 7 new tests added under `app/src/test/java/com/eight87/strictlykeptboy/perf/`
  plus 1 under `git/`.
