# strictlykeptboy — Claude instructions

Git-backed Android calendar + timeboxing + todolist + lifestyle organizer. Kotlin + Jetpack Compose + Material3 Expressive + Room + JGit + dmfs lib-recur. Designed AI-native: every event, task, recurrence rule, deviation, and reaction is a single hand-editable Markdown-with-TOML-frontmatter file in a Git repo, so Claude can read and write users' schedules the same way the users can.

Sister app to [tonearmboy](https://github.com/887/tonearmboy) (music player), [whisperboy](https://github.com/887/whisperboy) (audiobook player), [shutterboy](https://github.com/887/shutterboy) (photo gallery) — same toolchain, same release pipeline, same Obtainium distribution path, same SOLID + AVD-loop discipline.

## Architectural decisions (locked)

See `docs/plans/decisions.md` for the full locked-decisions doc (D.1..D.87 + K-1..K-7 kink-positive positioning). Highlights:

- **Language:** Kotlin only. No Java.
- **UI:** Jetpack Compose + Material3 Expressive. No Android Views.
- **Cache DB:** Room read-through cache only; **Git files are source of truth**, Room is rebuildable from disk.
- **Git:** JGit 6.x + apache-sshd-osgi (SSH) + OkHttp (HTTPS+OAuth).
- **Auth:** GitHub OAuth Device Flow + Forgejo OAuth Device Flow + manual PAT; per-`(repoId, remoteName)` keying via EncryptedSharedPreferences.
- **Serialization:** ktoml available, hand-rolled TOML codec for v1 (full round-trip control); kotlinx.serialization for JSON-backed prefs.
- **Recurrence:** dmfs lib-recur (RFC5545 RRULE).
- **Build front-end:** Google's Android CLI (same as tonearmboy).
- **App ID:** `com.eight87.strictlykeptboy`
- **minSdk:** 26  /  **targetSdk:** 36
- **No-origin repos are first-class** (Phase ZZ / D.74). Phone-only calendars work without ever configuring a remote.
- **Kink-positive openly** (D.55 + K-1..K-7). Mature 17+ Play Store positioning; per-event `private = true` privacy flag is orthogonal to neutral-mode.

## Repo convention this app produces

Each user-data repo this app reads/writes follows the layout in `docs/plans/decisions.md` §D.3:

- One file per entity. One entity per file. **Never create an index file** — the bucket dirs ARE the index.
- Files are bucketed `<calendars|todolists>/<id>/(events|tasks)/<yyyy>/<mm>/<entity-id>.md`.
- IDs are UUIDv7 (time-sortable).
- File format: TOML frontmatter (between `+++` fences) + Markdown body.
- Add an event = create a new file. Edit an event = rewrite the single file.
- Cancel a recurring instance = create `exceptions/<rule-id>/<yyyy-mm-dd>.md` with `kind = "cancel"`. Never edit the rule file to silently drop dates.
- Inverted habits (D.54): default state = `completed-by-schedule`. Deviation = explicit `deviations/<id>/<yyyy-mm-dd>.md` write.
- Supersedence (D.75..D.78): a vacation overlay pauses routine calendars via `supersedes` field + `overrides/<event-id>.md` per-event opt-outs.
- Mode + identity (D.83/D.84): per-repo `mode.toml` (free / strictly-kept) + `identity.toml` (praise / pronouns / honorific / tone register). AGENTS.md references identity.toml but does not embed its content.

Each user-data repo this app produces ships with its own `AGENTS.md` / `CLAUDE.md` describing this convention to agents that work on the *user's calendar repo*. The one you're reading now is for agents working on the *strictlykeptboy app itself*.

## Required CLIs and MCP servers

Same setup as tonearmboy. Phase 0 of `docs/plans/main.md` tracks each.

### Android CLI

The April 2026 `android` command from Google wraps everything we need.

```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android
chmod +x ~/.local/bin/android
```

**JDK quirk (load-bearing):** The CLI's bundled JRE is minimized — missing `java.rmi`, which Gradle 9.1's Kotlin DSL classpath fingerprinter loads. **Direct `./gradlew` calls fail at configuration time with `java.lang.NoClassDefFoundError: java/rmi/Remote`.** Fix:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug
```

This is the canonical incantation for direct Gradle calls in this repo. `android run --apks=…` works without it (uses the bundled toolchain).

**Worktree caveat:** `local.properties` is gitignored, so subagent worktrees start without the SDK path. Always export `ANDROID_HOME=$HOME/Android/Sdk` alongside `JAVA_HOME` when running Gradle in an agent worktree.

Useful subcommands:

```bash
android docs search <query>     # FIRST place to look for Android API questions
android docs fetch <kb-url>
android skills list --long      # browse official Android skills
android info
```

### `mobile` MCP server (UI driving)

Registered at project scope in `.mcp.json` + allowed in `.claude/settings.json` (both committed). When a Claude Code session starts in this repo with `enableAllProjectMcpServers: true`, the `mcp__mobile__*` tools become available.

```bash
# Re-register on a fresh checkout if needed:
claude mcp add mobile --scope project -- npx -y @mobilenext/mobile-mcp@latest
```

Gives you: list ADB targets, install APKs, launch the app, read the accessibility tree (the screen, the way Playwright reads the DOM), tap by label / coordinates, assert UI state.

### `android-skills` MCP server

Registered at project scope. Surfaces Google's official Android Skills (Compose migration, Navigation 3, Edge-to-Edge, AGP 9, R8 config, etc.). **Consult these before hand-rolling any Android-specific pattern** that could be load-bearing on platform conventions.

```bash
claude mcp add android-skills --scope project -- npx -y android-skills-mcp
```

### Test target

- **Headless AVD `medium_phone`** (Android 16 / API 36, RSS ~3.2 GB) — primary target:
  ```bash
  ~/Android/Sdk/emulator/emulator -avd medium_phone \
    -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect &
  ```
- **wifi-adb to the user's phone** — long-term home once notification + lockscreen behaviour matters.
- **Waydroid** declined (would need root).

## Test loop

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:installDebug
~/Android/Sdk/platform-tools/adb shell am start -n com.eight87.strictlykeptboy/.MainActivity
```

### UI changes are verified on the running AVD

Any change that touches Compose UI (layout, composable structure, navigation, theming, sheet behaviour, anything visible) **MUST** be verified by installing the rebuilt debug APK on the running headless AVD (`emulator-5554`) and inspecting the result. Robolectric unit tests do not catch real-device layout bugs (overflow, clipping, off-screen widgets, scroll behaviour under the rail, sheet scrim z-order, density-target surprises).

Canonical loop:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
~/Android/Sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -n com.eight87.strictlykeptboy/.MainActivity
~/Android/Sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p | magick - -resize 50% /tmp/skb.png
# then Read /tmp/skb.png via the Read tool
```

The AVD is 1080×2400 native — too big to read comfortably. Pipe screencaps through `magick - -resize 50%` to land at 540×1200; tap coords are still computed against the native 1080×2400 (multiply scaled image coords by 2).

Clean up `/tmp/*.png` periodically — they accumulate fast across sessions.

Prefer `mobile-mcp` tools when loaded (a11y tree + tap-by-label is more precise than coord input). Fall back to `adb exec-out screencap -p` + visual inspection via Read when mobile-mcp isn't available.

**Do not report a UI task as done on the strength of unit tests + a successful build alone.**

Raw logcat for crash diagnosis:

```bash
adb logcat -s strictlykeptboy:* AndroidRuntime:E *:S
adb logcat -d -t 200   # last 200 lines, useful after a crash
```

## File conventions

- Single-module to start (`:app`). Split into `:core` / `:data` / `:ui` only when the single-module size warrants it.
- Package root: `com.eight87.strictlykeptboy`. Current sub-packages: `git/`, `git/auth/`, `store/`, `cache/`, `resolver/`, `sync/`, `ui/scaffold/`, `ui/schedule/`, `ui/tasks/`, `ui/repos/`, `ui/wizard/`, `ui/components/`, `theme/`.
- Composable functions: PascalCase, no `@Composable` on private helpers unless they take a Modifier.
- ViewModels: one per screen, talk to the data layer via repository / store interfaces.
- No DI framework in v1 (Hilt / Koin) — pass dependencies as constructor params. The future `AppGraph` is the composition root. Add DI later if/when the manual wiring hurts.
- No reflection-based JSON. Use `kotlinx.serialization` if any serialization is needed. TOML uses our hand-rolled codec in `store/` (ktoml is wired for v2).
- All file IO via `java.nio.file.Path` (not `java.io.File`) for symlink correctness — the `CLAUDE.md → AGENTS.md` symlink in produced repos must round-trip.
- All suspend functions for I/O switch to `Dispatchers.IO` internally; UI never blocks main thread.
- Per-repo `Mutex` in `GitRepo` serializes writes (JGit `Repository` is read-safe but writes MUST be serialized).

## Design principles — SOLID, applied to Kotlin + Compose

The codebase follows SOLID where it earns its keep. Kotlin + Compose change *how* the principles cash out (top-level functions instead of `interface ServiceImpl`, sealed types instead of Visitor, `Flow<T>` instead of Observer wiring), but the underlying tests still apply. **When introducing a new file or refactoring an existing one, sanity-check it against these five questions.** When in doubt, prefer the principle over the shortcut.

- **S — Single Responsibility.** A type / file / composable should have *one reason to change*. If you can describe what a class does without "and", "also", or "plus", you're probably fine. If a single file is editable for three independent reasons (e.g. *day-band rendering* + *view-mode persistence* + *navigation routing*), split it. Soft heuristic: anything past ~500 LOC of non-trivial Kotlin deserves a second look; past ~800 LOC almost always needs splitting.
- **O — Open/Closed.** Prefer adding a new sealed-class case / new strategy implementation over modifying an existing `when` / `if` chain that already covers the abstraction. Sealed types + exhaustive `when` are the Kotlin-native way to express "open for extension". *Caveat:* don't pre-build extension points for cases that don't exist yet — closed-by-default, opened only when a second variant arrives. (Examples in this codebase that pass: `FetchResult` / `PullResult` / `PushResult` / `CommitResult` sealed hierarchies in `git/Results.kt`; `CredentialBinding` strategy in `git/auth/CredentialBindings.kt`; `ViewMode` sealed type in `resolver/`.)
- **L — Liskov Substitution.** Subtypes (or sealed-type variants) must honour the contract of the parent. A `CredentialBinding.SshBinding` that throws `NotImplementedError` is a Liskov violation in spirit even though it compiles — we accept that one deferred bind only because Phase I gates the UI that would exercise it; future variants must satisfy `configure(TransportCommand)` totally. In Compose, this means: if a composable promises to render in a `Modifier.fillMaxWidth()` parent, every implementation should.
- **I — Interface Segregation.** Don't pass a fat type when a narrow one would do. If a screen needs only `tasks.byTodolist()`, take a `TaskSource` (one method) — not the whole `CacheDatabase` (12 DAOs). In Compose this manifests as: don't pass a god-state object down five levels; pass the three fields the leaf actually reads.
- **D — Dependency Inversion.** High-level modules (UI, resolver, sync orchestrator) depend on abstractions, not concrete classes. ViewModels / composables take repository *interfaces* or function-typed parameters; concrete Room DAOs / JGit wrappers / OkHttp clients live behind those interfaces. The (future) `AppGraph` is the composition root — the *only* place that knows the concrete types. (Current example: `CredentialResolver` interface + `ProductionCredentialResolver` impl; `RepoStore` accepts `SharedPreferences` injection for `openForTest`.)

These are evaluation criteria, not religion — small ad-hoc helpers don't need their own interface, and one-off composables don't need to be split for principle's sake. But anything load-bearing (`GitRepo`, `RepoStore`, the `Renderer`, the `SyncScheduler`, the wizard scaffolding) should pass all five.

**SOLID refactor + standing-discipline plan:** see [`docs/plans/refactor-solid.md`](docs/plans/refactor-solid.md) for the running list of SOLID audit findings — work them in declared priority unless the user picks otherwise.

## Plan file

The phased build plan lives at [`docs/plans/main.md`](docs/plans/main.md). Sub-step checkboxes per the global CLAUDE.md rule.

When working on a phase:

- Tick its sub-steps (`- [x]`) in the same commit that lands the work.
- Add `shipped in commit <id>` to the phase header when *all* its sub-steps are ticked.
- Mark the whole plan `## Status: ✅ DONE` once every phase is ticked.
- If a phase header has no sub-step checkboxes, *write them first*. No vibes-based progress.

Plans + drafts in this repo:

- `docs/plans/main.md` — top-level phase index (Phase A through Round-4-additions WW / XX / YY / ZZ + AAA / BBB / CCC / DDD)
- `docs/plans/decisions.md` — locked architectural decisions D.1..D.87 + K-1..K-7
- `docs/plans/data-model.md` — file schemas + AGENTS.md content (DM-A..DM-AA)
- `docs/plans/sync-engine.md` — Git layer + auth + sync orchestration (SE-A..SE-Y)
- `docs/plans/ui-spec.md` — every screen + component + navigation (UI-A..UI-VV)
- `docs/plans/resolver.md` — overlay algorithm + recurrence + supersedence + off-schedule + common-time
- `docs/plans/templates-demo-wizard.md` — templates + wizard surfaces + SP retractions
- `docs/plans/notifications-sharing-import.md` — notifications + sharing + briefings
- `docs/plans/shared-schedules.md` — cross-repo + review-feed + dom-persona pointer
- `docs/plans/cli-tooling.md` — `skb` CLI surface
- `docs/plans/refactor-solid.md` — standing SOLID audit + per-phase findings
- `docs/plans/draft-*.md` — integrated draft files (✅ INTEGRATED, content preserved)

## Subagent dispatching

Subagents working on this repo run in worktrees (or inline if the change is small). Each agent prompt must:

- Name the phase + sub-steps it owns.
- Be told to tick checkboxes and add the commit ID to the phase header as it lands work.
- Be told to keep the work scoped to its phase (no opportunistic refactors of unrelated code).
- Be told to never modify `~/.claude/` files (those are not under this repo).
- Be told to consult `android docs search <query>` before hitting general web search for Android API questions.
- Be told to consult the `android-skills` MCP for any pattern Google has codified (Compose migration, Navigation 3, edge-to-edge, etc.).
- Be told to run the SOLID self-check against the diff before declaring the phase done.
- Be told to AVD-smoke-test any UI-affecting work before declaring done — per the loop above.

The user has standing authorization to autonomously work through Round 1 (Phases A–W) with opus subagents; see `~/.claude/projects/-home-laragana-workspace/memory/project_strictlykeptboy_autonomous_mode.md` if available.

## Editorial — user-facing copy

The user follows Paul Graham's *Keep Your Identity Small* for their personal writing, but **strictlykeptboy is kink-positive by design** — the wizard alignment + lifestyle copy + dom-persona register + praise terms / pronouns / honorifics live in `identity.toml` per user choice. App chrome copy outside the identity-driven surfaces (settings labels, error messages, About text) stays plain and factual. Translation workflow (when added) follows the tonearmboy / shutterboy pattern: user + Claude per-language in dedicated sessions, no community PRs assumed.

## Release workflow

Same pattern as tonearmboy / shutterboy. The user vibes from their phone with the Claude app, says "ship a new build of strictlykeptboy", Claude runs the local build, user pulls via [Obtainium](https://github.com/ImranR98/Obtainium) on their phone — auto-detects the new GitHub Release.

**Local build is the primary path. Zero CI minutes by default.**

Canonical commands (live now via `scripts/build-release-apk.sh`):

```bash
# Full one-shot: build + push to GH Releases + install on connected device
scripts/build-release-apk.sh --gh-release --install

# Just publish to GH Releases (Obtainium pulls from there)
scripts/build-release-apk.sh --gh-release

# Local APK only, no upload, no install
scripts/build-release-apk.sh
```

What `--gh-release` does:

1. Builds `release/strictlykeptboy-<version>-<sha7>.apk` (debug-signed by default; set `STRICTLYKEPTBOY_RELEASE_KEYSTORE` + `STRICTLYKEPTBOY_RELEASE_KEY_ALIAS` + `STRICTLYKEPTBOY_RELEASE_KEY_PASSWORD` env vars for production signing).
2. Generates release notes from `git log <prev-tag>..HEAD` plus a "Verify build" table containing the commit hash and APK SHA-256.
3. Creates the GitHub Release `v<version>-<sha7>` with the APK attached.
4. Pushes the local annotated tag to `origin`.

The `.github/workflows/release.yml` fallback is **tag-only and self-disabling**: it triggers when a `v*` tag is pushed, then queries the matching release; if an APK is already attached (which is true after the local script ran), it exits 0 without rebuilding. Saves CI minutes by default; only runs when a tag shows up without a matching APK (e.g. tag pushed from the GitHub web UI). For a CI-signed release, set the repo secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

When a phase asks for a release, the happy path is `--gh-release --install` against the connected AVD / wifi-adb phone.

`scripts/start-avd.sh` boots the headless `medium_phone` AVD and (optionally) attaches scrcpy for mirroring; `--no-mirror` for headless-only, `--kill` to stop.

## Open-source licenses

When Phase W release engineering lands: every dep in the APK gets inventoried by the [Licensee](https://github.com/cashapp/licensee) Gradle plugin (same as tonearmboy / shutterboy). Allowed SPDX ids: `Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`. New SPDX → ship the canonical text at `app/src/main/assets/licenses/<spdx>.txt`. Currently deferred — track via Phase W.

## Currently in flight (autonomous-mode markers)

- **Round 1 (Phases A–W):** active autonomous implementation. Latest seam committed in main: `9b20722` (Phase J sync orchestration). Phase K (lifestyle wizard) in flight via opus subagent at the time of writing.
- **Tests:** 174 passing under `:app:testDebugUnitTest` as of Phase J commit.
- **AVD-validated:** Phase F (5-rail scaffold + empty state) and Phase G (5 schedule views) and Phase H (5 task views) and Phase I (repo management flows) and Phase J (sync button + foreground service) all installed + smoke-tested on `emulator-5554`.
