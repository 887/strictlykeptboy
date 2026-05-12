# strictlykeptboy — locked architectural decisions

## Status: 🔒 LOCKED — decisions doc, do not punt back to user

This document is the single source of truth for every architectural choice
in `strictlykeptboy`. Every decision below was made by the orchestrating
agent on behalf of the user (per the user's explicit "make all decisions,
don't punt back" directive). Subagents fleshing out individual planning
documents MUST treat these as already-decided and elaborate on the
mechanics — never re-propose alternatives, never ask the user to choose.

If a subagent discovers an unforeseen tradeoff during planning, the
correct move is to: (a) record the tradeoff in their planning doc with
a recommended resolution, (b) implement the recommended resolution, and
(c) flag it inline. Never block.

---

## D.1 — Tech stack (locked)

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin only | Match tonearmboy/shutterboy/whisperboy |
| UI | Jetpack Compose + Material3 Expressive | Same as siblings; M3E is the v1 target |
| Cache DB | Room | Read-through cache only — Git files are the source of truth |
| Git client | JGit 6.x (Eclipse) | Mature, pure-Java, runs on Android. Native libgit2 considered and declined — JNI surface, NDK toolchain hit, sshd story worse. |
| SSH transport | `apache-sshd-osgi` via JGit `SshdSessionFactory` | Standard JGit pairing. ed25519 supported, no JSch (deprecated). |
| HTTPS transport | OkHttp under JGit, `UsernamePasswordCredentialsProvider` | Token-based; OAuth flows on top. |
| OAuth flows | GitHub OAuth Device Flow + Forgejo OAuth2 Device Flow | No embedded webview; user pastes code in browser. Survives 2FA cleanly. |
| Frontmatter | TOML via `ktoml` | Hand-editable, well-defined; better than YAML for fuzzy date edges and better than JSON for humans. |
| Markdown | `org.commonmark:commonmark` | CommonMark spec, fast, tiny. |
| Recurrence | `org.dmfs:lib-recur` | RFC5545 RRULE. Battle-tested in DAVx⁵. |
| ID format | UUIDv7 (time-sortable, opaque) | Filenames sort chronologically by default; never collide; safe to rename calendars without renaming children. |
| Background | Foreground service (`FOREGROUND_SERVICE_DATA_SYNC`) for sync; WorkManager for one-shot retries | FGS gives reliable interval sync on modern Android; WM handles backoff. |
| Build front-end | Google's Android CLI (`android` command) | Same as tonearmboy. |
| Tests, unit | Robolectric (JVM only) | Same. |
| Tests, UI | mobile-mcp over ADB, headless AVD `medium_phone` | Same. |
| App ID | `com.eight87.strictlykeptboy` | Match `com.eight87.<app>` convention. |
| minSdk / targetSdk | 26 / 36 | Match tonearmboy. |
| Distribution | GitHub Releases + Obtainium; later Play Store | Same two-track distribution as tonearmboy. **Play Store branding is openly lifestyle-positive at Mature 17+; see D.59 / K-4.** (The prior "hinting-not-blatant" register is retracted per Round 4.) |

## D.2 — Source-of-truth model (locked)

**Git is canonical. Room is a cache.** Every event/task/recurrence is a file in
a Git repository. The app builds a Room index from a filesystem scan; the
index is invalidated and rebuilt on every git HEAD change. If the Room DB is
deleted, the app can fully recover by rescanning the working tree. If a file
is hand-edited or AI-edited, the app picks up the change on the next scan.

**No index files in the repo.** Index files are merge-conflict factories.
The app scans on every checkout. Scans are fast because:

- Files are bucketed by `<year>/<month>/`, so the working set per scan is bounded.
- Room caches scan results keyed on git HEAD SHA — unchanged HEAD = no rescan.
- `git diff --name-only HEAD@{1} HEAD` after each pull tells us exactly which files changed.

## D.3 — Per-repo layout (locked)

```
<repo-root>/
  README.md                          # human-facing repo description
  AGENTS.md                          # canonical AI agent guide (schema, conventions)
  CLAUDE.md                          # symlink or pointer to AGENTS.md (Claude convention)
  .strictlykeptboy/
    schema.toml                      # schema_version, app_min_version, last_writer
    repo.toml                        # repo display name, emoji/icon, default_identity, color
  identities/
    <person-id>.md                   # display name, avatar, public keys, default-author flag
  calendars/
    <calendar-id>/
      calendar.toml                  # name, emoji, color, kind (regular|timebox),
                                     # priority, active_windows, active_hours, default_notification_group
      events/
        <yyyy>/<mm>/<event-id>.md    # one-off events
      recurrences/
        <rule-id>.md                 # RRULE-bearing recurring events
      exceptions/
        <rule-id>/<yyyy-mm-dd>.md    # per-occurrence override or cancellation
  todolists/
    <todolist-id>/
      todolist.toml                  # name, emoji, color, priority, active_windows, active_hours
      tasks/
        <yyyy>/<mm>/<task-id>.md     # dated tasks
      standing/
        <task-id>.md                 # no-deadline tasks
      recurrences/
        <rule-id>.md                 # recurring chores
  attachments/
    <sha-prefix>/<sha256>.<ext>      # content-addressable
```

**Rules:**

- One file per entity. One entity per file.
- IDs are UUIDv7. Filenames embed the ID.
- The `<yyyy>/<mm>/` bucketing applies to events and dated tasks. Standing
  tasks live in a flat `standing/` dir.
- Calendars and todolists are folders; their metadata is `<kind>.toml` at
  the folder root.
- Attachments are content-addressed by SHA-256, never by filename. The
  original filename is stored in the event/task frontmatter.
- The exception folder per recurrence rule allows safe per-occurrence
  edits without touching the rule file (avoids conflicts on the
  high-traffic rule file).

## D.4 — File schemas (locked at field-set level)

See `data-model.md` for the full field-by-field schema and worked examples.
Locked principles:

- Every file: Markdown body (free-form, AI-editable) + TOML frontmatter
  delimited by `+++` (TOML standard, distinct from `---` YAML so editors
  pick the right highlighter).
- Frontmatter is structured; body is free-form. The app reads the
  frontmatter; the body is shown verbatim in the event detail screen and
  is the place to keep notes, links, and instructions for an AI.
- Schema versioned via `.strictlykeptboy/schema.toml`. Migrations on the
  app side when bumping; never break-rewrite the user's repo without a
  version check + explicit migration commit.
- Author attribution: every event/task frontmatter has `author = "<person-id>"`
  pointing at `identities/<person-id>.md`. The active identity per repo is
  configured in app prefs and is what the app stamps on new entries.
- Git commits also carry author info; the app sets `user.name` and
  `user.email` based on the active identity. (The frontmatter `author`
  field is the authoritative source for UI display — `git blame` is the
  fallback for entries created before identity binding.)

## D.5 — Overlay / priority / active-window model (locked)

**Every calendar and todolist has the same overlay surface:**

```toml
priority = 500                   # 1..1000, higher wins on overlap resolution
active_toggle = true             # user UI toggle; false = hidden everywhere
active_windows = [               # date ranges where this calendar is in effect
  { start = "2026-05-01", end = "2026-08-31" },
  { start = "2026-09-15" }       # open-ended end
]                                # [] or missing = always active
active_hours = [                 # hour ranges within a day-of-week
  { day = "mon", from = "09:00", to = "17:00" },
  # ...
]                                # [] or missing = all hours
kind = "timebox"                 # "regular" | "timebox"   (calendars only)
```

**Resolver:** at a given `(date, time)`, compute the **active set** =
{calendar | active_toggle AND date∈active_windows AND time∈active_hours}.
Render every active calendar layered. For visual collision (two events
in the same slot), the higher-priority calendar wins the *foreground*
slot; lower-priority calendars render banded/dimmed at the edge. Detail
sheet on tap shows every overlapping event.

**Special events (vacation, birthdays, deaths, etc.):** a regular
calendar named `Special Events` with priority 999. No new model needed.

**Recurrence × active-windows interaction:** active-windows only filter
which calendars are even *considered*; once a calendar is active for the
slot, all its events/recurrences for that slot render. Active-windows do
NOT clip individual recurrence instances within an active period.

## D.6 — Recurrence model (locked)

- RFC5545 RRULE stored in `recurrences/<rule-id>.md` frontmatter.
- Recurrence is **materialized lazily at view-time** — the resolver
  generates instances for the rendered date range only. Never persist
  instances to files.
- **Exceptions are persistent files** at `exceptions/<rule-id>/<yyyy-mm-dd>.md`.
  Three exception kinds: `cancel` (instance suppressed), `override`
  (instance replaced with explicit fields), `note` (instance kept but
  body annotated).
- Editing a recurrence rule never silently rewrites past events.
  Changing a rule's `dtstart` after the fact is treated as a new rule
  (UI prompts: "split the rule from this date forward?").
- Timezone: recurrence files store TZID. App default is device tz, but
  per-recurrence override is possible. v1 renders in device tz.

## D.7 — Auth model (locked)

- **Per-repo credential binding.** One repo = one (identity, transport,
  credential) tuple. Credentials live in `EncryptedSharedPreferences`,
  keyed by repo URL hash.
- **SSH path:** on-device ed25519 keygen via BouncyCastle. Public key shown
  for one-tap copy or auto-upload via:
  - GitHub: REST API `POST /user/keys` after OAuth (scope `admin:public_key`).
  - Forgejo: `POST /api/v1/user/keys` after OAuth.
  Private key encrypted at rest with the Android Keystore-backed master
  key.
- **HTTPS path:** GitHub OAuth Device Flow → PAT issuance OR Forgejo
  Device Flow → access token. Tokens stored encrypted. Refresh handled
  transparently.
- **Manual PAT entry:** supported as a fallback for self-hosted Forgejo
  instances that don't expose OAuth.
- **Multi-account:** the same GitHub user can be different identities in
  different repos (e.g. work-self vs personal-self) by separating the
  active `identities/<person-id>.md` per repo.

## D.8 — Sync engine (locked)

- **Background sync:** foreground service with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
  Persistent silent notification (user-toggleable). Configurable interval:
  `5m / 15m / 30m / 1h / 4h / manual-only`. Default `15m`.
- **Manual sync:** top-bar two-arrow circular icon. Tap → fetch+rebase+push
  for every repo flagged "auto-sync on". Spinner while running. Result
  toast: "synced 3 repos / 1 conflict / 2 ahead".
- **Per-repo auto-sync toggle.** Shared / read-only repos default off
  (you don't push to repos you only read).
- **Pull strategy:** `git fetch` then `git rebase` onto remote. Rebase
  default avoids merge commits in the user's repo. Fast-forward when
  possible.
- **Push strategy:** push after every local commit when online; queue
  when offline; flush queue on reconnect.
- **Airplane mode / offline:** local commits proceed normally; sync
  status indicator shows `N commits ahead, no network`. Connectivity
  callback flushes.
- **Conflict resolution:** the file model (one entity per file) means
  conflicts are rare and always confined to a single TOML+body file.
  On conflict, app surfaces a Compose-rendered 3-way diff with
  pre-resolved structured fields (TOML keys) and a text editor for the
  body. "Keep mine / Keep theirs / Manual merge" buttons.
- **Commit messages:** auto-generated, format:
  `<verb> <entity-kind> "<title>" in <calendar-name>`, e.g.
  `add event "Dentist 14:00" in Personal`. User can override in advanced
  settings.
- **Git author identity:** `user.name = <identity display name>`,
  `user.email = <identity email or <id>@strictlykeptboy.local>`. Stamped
  per commit.

## D.9 — Multi-repo + read-only sharing (locked)

- Repo list in app prefs. Per-repo: display name, circular icon
  (emoji-in-SVG-wrapper OR user-picked image OR auto-generated initials
  circle), URL, transport, default identity, auto-sync toggle, default
  calendar, default todolist.
- Top-bar repo switcher: avatar + name; tap opens a filterable list
  of all configured repos with circular icons.
- **All-repos overlay view:** master setting "show all repos in unified
  view" — when on, the schedule view aggregates every active calendar
  across every repo, color-bordered by repo. Per-repo opt-out per view.
- **Read-only access** = configure a repo in app with a fetch-only
  credential or with a token that lacks push scope. App detects push
  rejection and surfaces "this repo is read-only — your local changes
  are queued but cannot push" (the user can still edit locally for
  preview; the changes never leave the device).
- **Granting others read access:** for GitHub/Forgejo, this is repo
  visibility + collaborator management at the provider level. The app
  surfaces a "share read access" deep link that opens the provider's
  collaborator screen for that repo.

## D.10 — Common-time finder (locked)

- Pick 2+ repos. Pick date range + duration + day-of-week + time-of-day
  filters. Pick which calendars-per-repo to include (default: all active).
- Algorithm: compute the *busy set* = union of every active event in
  every selected calendar across selected repos for the date range.
  Invert to free slots ≥ requested duration. Rank by:
  1. Contiguity (longer slot wins on tie)
  2. Proximity to ideal time-of-day if specified
  3. Earliest date
- Output: ranked list of free slots. Tap a slot → create event (in
  whichever repo + calendar the user picks from a quick-pick).

## D.11 — Todolist model (locked)

**Todolists share the overlay/priority/active-windows model with
calendars (D.5).** Differences:

- A todolist contains `tasks/<yyyy>/<mm>/<task-id>.md` (dated),
  `standing/<task-id>.md` (undated), `recurrences/<rule-id>.md`
  (recurring chores).
- Tasks have a `due` date (optional), `done` flag, `auto_done_at_eod`
  flag (default false — explicit user choice; calendar-bound tasks
  may set this true via template).
- A calendar event can `spawns_task = "<task-id>"` — a task entry
  generated alongside the event (e.g. "buy milk" tied to a recurring
  shopping event). Spawned tasks are real files in the linked todolist.
- Tasks have `priority` inherited from the todolist, with a per-task
  override.
- Time-of-day relevance: todolists with `active_hours` rendered
  higher in combined task views during those hours (e.g. work-tasks
  surface during 09:00–17:00 weekdays).
- A combined "today" view rolls every active todolist's dated tasks
  for today + spawned tasks from today's calendar events + standing
  tasks the user pinned. Sorted by priority then due time.

## D.12 — UI surface (locked)

- **Left nav rail** (collapsed on phone, expanded ≥600dp):
  📆 Schedule  •  ✅ Tasks  •  👥 Together  •  ⚙️ Settings
- **Top bar:**
  - Left: repo switcher (avatar + name, dropdown with filterable list)
  - Center: view title + view-mode tabs (Day / Week / Month / Timebox / Year)
  - Right: sync button (two-arrow circular) + identity icon (active person-id)
- **Schedule views:** Day, Week, Month, Timebox-mode, Year.
- **Tasks views:** Combined, Today, Per-list, Shopping, Standing.
- **Together view:** Common-time finder + shared repos overview +
  pending invites/access.
- **Settings:** Repos / Identities / Sync / Notifications / Calendars /
  Todolists / Templates / Demo / Appearance / About.
- **Wizard:** first-run + on-demand from Settings → Templates.
- **Tablet:** width ≥900dp uses NavigationSuiteScaffold with
  master-detail (schedule list ↔ event detail).
- **Android Auto:** read-only "today's schedule" via `CarAppService` and
  templated list cards. v1 read-only.

## D.13 — Wizard + templates (locked)

- **First-run flow:**
  1. Welcome (mascot)
  2. Pick path: `Start with demo` / `Start from templates` / `Empty repo`
  3. (templates path) Toggle the roles that apply: morning-bird,
     night-bat/owl, work-9-5, work-shift, school, gym-3x, gym-5x,
     swim-club, soccer-club, music-practice, master-scheduled,
     sub-scheduled, daily-chores, kinky-chores.
  4. Pick or create the Git repo (suggest a name; user can override).
  5. Authenticate the chosen provider (GitHub/Forgejo via OAuth or PAT).
  6. App scaffolds the repo, commits, pushes, opens main view.
- **Demo mode:** ships TWO coupled repos: `demo-sub` (primary) +
  `demo-dom` (read-only overlay from sub's view). SFW-but-kinky-coded
  content. See `templates-demo-wizard.md` for exact seed content.
- **Re-applying templates later:** Settings → Templates → pick template
  → choose target repo + (optionally) target calendar/todolist. Templates
  are idempotent — re-applying merges new entries by ID, leaves edits
  alone.
- **Custom templates:** the app reads `templates/` from a special
  template repo (configurable). Users can fork the default template
  repo and add their own.

## D.14 — Notifications (locked)

- **Per-event notifications:** array of lead-times in event frontmatter
  (`notifications = ["1h", "15m"]`). Each becomes a scheduled
  `AlarmManager` setExactAndAllowWhileIdle entry.
- **Notification groups:** map each calendar → a group; user toggles
  groups in Settings → Notifications. Group toggle wins over per-event
  config when off.
- **Channels:** `events`, `tasks`, `sync`, `errors`. Each with default
  importance configurable.
- **Sync notification:** silent by default. Optional "show last sync
  result" toast-style notification (Android 14+ uses notification with
  PROMOTE_TO_AMBIENT off).
- **Foreground service notification:** unavoidable on modern Android
  for periodic sync. Minimal, low-importance channel, user-toggleable
  with "you can disable sync entirely if you don't want this
  notification" warning.

## D.15 — Identity + attribution (locked)

- `identities/<person-id>.md` per repo. Frontmatter: `display_name`,
  `avatar` (path to attachment OR emoji), `email`, `public_keys`,
  `default_author = true|false` (exactly one per repo).
- Every event/task carries `author = "<person-id>"`. UI renders a small
  circular author chip on every event tile (24dp, initials or avatar).
- Author filter: top-bar dropdown "filter by author" in any view.
- Switching active identity: Settings → Identities → set active for repo.
  New entries thereafter stamp with the new identity. Old entries
  unchanged.

## D.16 — Import / export (locked)

- **iCal (.ics) export:** per-calendar or per-repo. Renders all events
  + recurrences in the date range chosen. Output: standard iCalendar
  file Thunderbird/Outlook/Apple Calendar can import.
- **iCal (.ics) import:** read an .ics file → ingest into a chosen
  target calendar as Markdown+TOML files. One-time conversion;
  re-imports diff-merge by UID.
- **CSV export (tasks):** standard CSV for spreadsheet workflows.
- **CalDAV server sync:** out of scope for v1. Hooks left in `tools/`
  for cron-driven server-side sync via a separate process.

## D.17 — Android Auto (locked, v1 read-only)

- `androidx.car.app` `CarAppService` rendering today's schedule as a
  `ListTemplate`. Each list item is one event.
- `PaneTemplate` for "next up" view.
- Voice integration via `androidx.car.app` `Action` with voice prompts.
- No event creation/edit on Auto in v1.

## D.18 — Tablet (locked)

- Width ≥600dp: nav rail expanded by default.
- Width ≥900dp: NavigationSuiteScaffold with two-pane (list + detail).
  Schedule view: month grid + day detail. Tasks: list + detail.
- Compose `WindowSizeClass` drives the breakpoints.

## D.19 — Theming + personalization (locked)

- Material3 Expressive with dynamic color (Material You) enabled by
  default. Manual color seed override per repo.
- Per-repo: circular icon (emoji-in-SVG OR user-picked photo OR
  auto-generated initials).
- Per-calendar/todolist: emoji + accent color.
- Per-event: optional emoji prefix.
- Mascot: bat-in-hoodie SVG on splash, About screen, empty states. The
  bat is happy, holding a calendar. User supplies the asset later.

## D.20 — File schema versioning + migration (locked)

- `.strictlykeptboy/schema.toml` contains `schema_version` (integer).
- App keeps a `MIN_READABLE_SCHEMA` and `LATEST_SCHEMA` constant.
- On open: if repo `schema_version > LATEST_SCHEMA` → refuse to write
  ("repo written by newer app version; update strictlykeptboy first").
- On open: if repo `schema_version < LATEST_SCHEMA` → app runs
  migration code, commits the migration with message
  `migrate schema v<old> → v<new>`, then opens normally.
- Migrations are idempotent (re-running on already-migrated repo is a
  no-op).
- v1 ships with schema_version = 1. Future bumps are explicit.

## D.21 — Performance budgets (locked)

- Cold start to schedule day view: < 600ms on Pixel 6a (matches
  tonearmboy budget).
- Pull-to-sync small repo (< 1000 entries): < 2s end-to-end.
- Render a month with ~200 events: < 200ms.
- Common-time finder over 5 repos × 30 days: < 800ms.

## D.22 — Security + privacy (locked)

- Repos can be private at the Git provider level — that's the primary
  data-at-rest encryption story for v1.
- SSH private keys + OAuth tokens: `EncryptedSharedPreferences` backed
  by Android Keystore master key.
- No telemetry. No analytics. No third-party SDK that phones home.
- Attachments are stored in the repo plaintext — if the repo is
  private, attachments are private. Users putting truly-sensitive
  files in attachments should encrypt at the file level themselves.

---

# Round 2 — v1 scope expansion (D.23 onward)

After Round 1 the user reviewed scope and directed that the entire v2
deferral pile from each deep-dive be pulled into v1, plus signed
commits demoted from required to optional, plus a `skb` CLI promoted to
**primary interface for the calendar data** (Claude editing schedules
via CLI is the main use case; the GUI is the human's window). License
constraint: prebuilt open-source components only, no GPL infestation
(Apache-2.0 / MIT / BSD / MPL-2.0 / LGPL-with-linking-exception OK).

The decisions below extend the locked set. Subagents in Round 2
elaborate the mechanics.

## D.23 — Signed commits: optional capability, not required identity

GPG/SSH-signed commits are a **capability**, not the identity model.
Primary identity stack stays:

1. Git committer email + `user.name` from active identity (always set).
2. `author = "<person-id>"` frontmatter field on every entity.
3. Signed commits — optional, off by default; adds cryptographic
   receipts; useful for shared repos where attribution must be
   verifiable.

**Why optional, not required:** the main use case is Claude editing
schedules via CLI. Signing every CLI commit is friction. Make signing
available for users who want it (verified-author chips, GitHub
verified-commits badge) without taxing the default path.

UI: Settings → Identities → tap identity → "Sign commits with GPG"
toggle → "Import GPG private key" file picker + paste-armored-text
input → "Sign with key" picker (lists imported key fingerprints).
Private keys live in `EncryptedSharedPreferences`. JGit +
BouncyCastle do the signing when the toggle is on.

Verified-author chip on entries from signed commits shows a small
verified-checkmark badge in the corner.

## D.24 — `skb` CLI: the primary interface for calendar data

**Reframe of the design center:** the GUI is one window into the
data; `skb` is another. They are peers. **Claude (and other AI
agents) use `skb` first; the GUI is for the human.**

`skb` is a small portable binary distributed as:
- A shell-script wrapper + bundled Kotlin/JVM jar (v1 default; works
  anywhere Java 17+ is available, including Claude Code's sandbox).
- A Kotlin/Native binary (later, if startup-time on JVM proves heavy
  in Claude's loop).

Subcommand surface (full spec in `cli-tooling.md`):

- `skb event add|list|edit|show|cancel`
- `skb task add|done|list|show|edit`
- `skb recurrence add|edit|cancel-instance`
- `skb cal add|list|edit` (calendars)
- `skb list add|list|edit` (todolists)
- `skb identity list|set-active|create`
- `skb attach add|list|show`
- `skb show <date>` — today/specified-date view
- `skb week|month <date>`
- `skb find-free --repos X,Y --duration 1h --range 2w`
- `skb sync [--all | <repo>]`
- `skb apply-template <template-name> [--repo X]`
- `skb migrate`
- `skb verify`
- `skb comment add|list` (D.29)
- `skb weather show <date>` (D.28)
- `skb tz convert <event-id> <new-tz>` (D.27)
- `skb caldav add|sync|remove` (D.25)
- `skb branch list|create|switch` (D.36)

Every command:

- `--json` emits machine-readable structured output (for AI consumers).
- Default stdout is human-readable.
- Errors on stderr with exit codes (0=ok, 1=usage, 2=not-found,
  3=conflict, 4=auth, 5=corrupt, 6=schema-mismatch).
- Atomic single-file writes per command — the one-entity-per-file
  invariant holds at every layer.
- Auto-commits with the same message format as the GUI.
- `--dry-run` prints the proposed change without writing.

**AGENTS.md** in every user repo **leads with the `skb` commands**,
not the file format. Claude is told: "to add an event, run
`skb event add --calendar Personal --start ... --title ...`."
File format remains documented (so Claude can read/diff directly), but
`skb` is the recommended write path.

Distribution: shipped alongside the APK in GitHub Releases, plus a
homebrew tap (`brew install 887/tap/skb`), plus a `curl ... | sh`
one-liner.

Detailed spec: `docs/plans/cli-tooling.md`.

## D.25 — Bidirectional CalDAV bridge (in v1)

Two-way sync between a calendar in the repo and a CalDAV server.
Use cases:

- Pull work calendar from Google/Microsoft/Apple/Nextcloud into a
  read-only mirror calendar that overlays the user's repo schedule.
- Push a calendar from the repo *out* to a CalDAV server so a partner
  using Thunderbird/Outlook/Apple Calendar sees it.

Libraries:
- `ical4j` (MPL-2.0, file-level copyleft, link-clean for our
  distribution).
- `dav4jvm` (MPL-2.0, the engine inside DAVx⁵).

MPL-2.0 is file-level copyleft — only modifications to ical4j/dav4jvm
themselves would trigger share-back, which we don't intend.

UX: Settings → Repos → tap repo → "+ Add CalDAV mirror" → server URL
+ credentials → discover calendars → pick which to mirror (per
calendar: pull-only mirror, push-only export, or full bidi). Bridge
runs alongside git sync on its own interval (default 30m). Conflicts
mirror the git conflict-resolution UI.

CLI: `skb caldav add|sync|remove|list`.

Detailed mechanics: extension phases in `sync-engine.md`.

## D.26 — Git LFS for large attachments (in v1)

Attachments over a threshold (default 1MB, configurable per repo)
auto-route through Git LFS. JGit has LFS support built in.

UI: per-repo "Use Git LFS for attachments larger than [N MB]" setting
in repo settings. Defaults on for new repos.

When LFS is enabled at the provider side (GitHub/Forgejo both support
it), the bridge is transparent. When not, app falls back to in-tree
storage and warns once with "this provider does not support LFS;
large attachments are stored inline".

## D.27 — Multi-timezone first-class (in v1)

Many users (the project's user explicitly) work and RP across
timezones. v1 ships:

- Per-event `tz_id` field (optional; defaults to repo tz).
- Per-recurrence `tz_id` (already in D.6).
- Repo default tz in `repo.toml` (defaults to device tz at scaffold;
  user-changeable).
- Display modes:
  - "Render in my tz" (default).
  - "Render in event tz" (per-event pin, useful for travel events).
  - "Render in <participant>'s tz" (Together-tab common-time finder).
- Multi-tz common-time finder: each participant declares their tz;
  finder evaluates each candidate slot from each participant's
  perspective (their working hours, their busy slots in their tz)
  and surfaces slots that work for everyone.
- DST handled by IANA tz database via `java.time.ZoneId`.
- UI badge in top bar when any active calendar has events in a
  non-device tz: "showing in DEVICE_TZ (some events in OTHER_TZ)"
  with one-tap toggle.

Schema field added as optional in v1 — no migration needed for repos
without it.

Detailed: extension phases in `data-model.md` + `resolver.md` +
`ui-spec.md`.

## D.28 — Weather overlay (in v1)

Library: `open-meteo` Java client (Apache-2.0). No API key required.
CC-BY data attribution.

Per-location-day forecast cached in Room. Display:

- Day view: thin weather strip above the timeline showing temp + icon
  every 3h.
- Week view: small icon per day in the header.
- Month view: tiny icon in the corner of each day cell.

Location: per-repo (defaults to device-location with permission),
optional per-event override for "trip to X" events.

Setting: "Show weather overlay" toggle (default on); "Weather
location" per repo. Data attribution in About screen.

CLI: `skb weather show <date> [--location LAT,LON]`.

## D.29 — Replies/comments on events (in v1)

Per-event comments via a sibling directory:

```
events/<yyyy>/<mm>/<event-id>.md
events/<yyyy>/<mm>/<event-id>.comments/
  <comment-id>.md   # one comment per file
```

Each comment file: TOML frontmatter (`id`, `event_id`, `author`,
`created_at`, `in_reply_to` optional) + Markdown body.

**Why file-per-comment:** preserves the no-merge-conflict invariant.
Two people commenting at the same time produce two files, no conflict.

UI: event detail sheet has a Comments section. Add comment → new file
→ commit. Per-event mute toggle (notifications when new comment
appears).

CLI: `skb comment add --event <id> --body "..."` /
`skb comment list --event <id> [--in-reply-to <id>]`.

Author chip on each comment, just like events.

## D.30 — Drag-to-reschedule + pinch-to-zoom (in v1)

Day and Week views:

- **Long-press → drag** an event chip to a new time slot. Snap to
  configured grid (15min default; configurable). Releasing commits the
  move with auto-message `move event "<title>" from <old> to <new>`.
- **Pinch-to-zoom** on the timeline: pinch-out → finer grid (5min
  steps visual); pinch-in → coarser (1h). Persists per device.

Own Compose implementation. No external dep.

## D.31 — Inline-markdown body styling (in v1)

Body editor renders Markdown inline as the user types — headings,
bold, italic, lists, links — via `noties/Markwon` (Apache-2.0) with a
thin Compose wrapper.

Toggle in editor toolbar: raw / rendered. Default: rendered.

## D.32 — Custom sticker / icon packs (in v1)

User can install a sticker pack = a directory of named images. App
indexes them for:

- `:sticker-name:` shortcut in event/task title editors.
- Icon picker for repos / calendars / todolists.

Format: `<pack-name>/<sticker-name>.{png,svg,webp}` + optional
`pack.toml` (display name, author, license). Open format, no DRM.

Pack source: local file picker (zip or unzipped dir) or HTTP URL
(app fetches once, stores locally).

## D.33 — Android Auto: voice-create in v1

Bumped from read-only (D.17) to read-only + voice-create.

Voice intent: "Schedule event tomorrow at 3pm called dentist" →
creates an event in the active repo's default calendar.

No in-Auto-screen editor (keyboard surface too narrow). No
in-Auto-screen delete (too easy to mis-tap). Pure voice for writes,
list for reads.

## D.34 — Cross-device snooze sync (opt-in, in v1)

Default: snooze stays local (D.14 mainline).

Opt-in toggle: Settings → Notifications → "Sync snoozes across
devices" → snoozes recorded in `_local/snoozes.toml` in the repo.
File is git-tracked.

Conflicts on `_local/snoozes.toml`: auto-merged with "latest wins
per snooze entry" rule. Snoozes are idempotent — snoozing an
already-snoozed alarm just extends the snooze. No conflict UI ever
surfaces this file.

## D.35 — ssh-agent forwarding (in v1, advanced)

For users on dev machines with an ssh-agent socket available, JGit's
`SshdSessionFactory` can be configured with an `AuthenticationKeySource`
that queries the agent. Advanced setting: Settings → Sync → SSH →
"Use ssh-agent if available" (default off; device-stored ed25519 key
is the default for mobile).

## D.36 — Multi-branch awareness (in v1)

A repo can have multiple branches. The app exposes the current branch
in the repo switcher (small text under repo name) and offers branch
switching for repos that have alternate branches.

Use cases:

- AI agent works on a feature branch (`claude/plan-2026-q3`) before
  proposing changes for merge into `main`.
- User experiments with a calendar arrangement on a branch without
  affecting `main`.
- Team workflows where pending changes go through PR review.

UI: Settings → Repos → branch picker; `skb branch list|create|switch`.
PR creation handled at the provider side (deep link to provider's
"Compare & pull request" page).

## D.37 — Comments / replies notification channel

Comments-on-events get their own notification channel (`comments`,
IMPORTANCE_DEFAULT). Mutable per-event. Sync notification is silent
when only comments changed (lower visual noise than events).

## D.38 — CSV import for tasks (in v1)

`skb task import-csv <file>` and Settings → Templates → "Import tasks
from CSV". Mapping: column header row required; common mappings
(`title|task|todo`, `due|due_date`, `done|completed`, `priority`,
`list|todolist`) auto-detected; user confirms mapping for unknown
columns.

## D.39 — In-app collaborator listing (deferred to v1.1, not v1)

Listing repo collaborators in-app requires elevated OAuth scopes
(`read:org` for GitHub, more for Forgejo). Adding this changes the
auth scope footprint, which has user-trust implications. **Deferred
to v1.1**: by then the project will have shipped once and have a
clearer answer on whether the scope expansion is justified.

The "share read access" deep-link to the provider's collaborator
screen (NS-E) covers the v1 use case adequately.

## D.40 — Items remaining deferred (with rationale)

These stay out of v1 because they're either rejected (not just
deferred) or genuinely don't make sense for v1:

| Item | Status | Why |
|---|---|---|
| Alpha-blend overlap | Rejected | Colorblind + screen-reader regression. Striped overlay model is better and stays. |
| Multi-finger calendar gestures | Rejected | Undiscoverable for most users. |
| Semantic TOML auto-merge | Deferred v1.1 | The file model is designed to avoid conflicts; manual UI is sufficient for the rare conflicts that surface. Adding semantic merge adds significant complexity for marginal value. |
| Web app / desktop app | Out of scope | Android-only in v1. |
| End-to-end encrypted repo contents | Out of scope | Repo can be private at the provider level; that's the v1 data-at-rest story. |
| Self-served webcal / CalDAV from the app | Out of scope | Server territory, not client. |
| In-app per-recipient deploy-key generation | Rejected | Wrong trust model; recipient generates on their device. |
| QR code for SSH public-key export | Deferred v1.1 | Marginal value over clipboard/share; zxing dep cost. |
| Branch-protection PR creation in-app | Deferred v1.1 | Provider-side workflow; deep-link is enough. |

Anything else from the original v2-deferral pile is now in v1 scope.

---

---

# Round 3 — shared schedules + simplified ("good boy") mode (D.41 onward)

After Round 2, the user named a use case that the existing scope didn't
optimize for: **a sub (or any recipient — student, employee, athlete,
team member) receives a complete schedule from someone else (Dom, coach,
teacher, manager) without ever having used a calendar app before, by
scanning a QR or opening a deep-link. The recipient consumes the
schedule; they don't have to author one. Later, they can grow into
authoring their own without losing the gifted schedules.**

The Dom never needs write access to the sub's repo. The sub never
edits the Dom's repo. Cross-repo state lives in the recipient's
repo. **Zero merge conflicts by design** — the model extends the
Round 1 one-file-per-entity invariant across repos.

License-clean throughout (Round 2 constraint stands).

## D.41 — Shared schedules: a primary use case, not a side feature

The app supports two equally-first-class entry paths:

- **Authoring path** — user creates a repo, applies templates, builds
  their schedule. (Round 1 wizard.)
- **Receiving path** — user receives a deep-link / QR from someone
  else, opens it, the app clones the referenced repo as read-only,
  drops them into the schedule view. **No wizard. No template
  picker. No repo creation.** (Round 3.)

Both paths converge: receiving users can later add their own repo
without losing the gifted schedules. Authoring users can share their
repos as gifts.

Use-case anchors (per user direction):

1. Dom prepares a "Schedule from Master" repo and sends sub a link.
   Sub installs the app, taps the link, has a complete kinky-coded
   schedule with workouts, check-ins, chores. Sub never sees the
   wizard.
2. Personal trainer sends a client a "12-week strength program" link.
   Client installs app, taps link, has a workout calendar with daily
   reminders. Client never wrote calendar code.
3. Sports club sends members a season calendar. Members tap link,
   get a complete season schedule overlaid on whatever else they
   have.
4. School / employer sends a structured calendar to a student /
   employee.

Every one of these scenarios should be **one tap from QR to working
schedule**, including auth for private repos.

## D.42 — Deep-link + app protocol registration (in v1)

The app registers two intent filters:

- **Custom scheme**: `strictlykeptboy://...`
  - `strictlykeptboy://add?url=git@github.com:dom/private-cal.git&label=Schedule+from+Master&mode=read-only#token=<one-shot-secret>`
- **Universal link**: `https://strictlykeptboy.app/add?...`
  - Verified via Android App Links (Digital Asset Links JSON at the apex domain).

URL parameters:

- `url` (required, may repeat) — git URL(s) to clone. Multiple URLs in
  one link supported (bulk-add a repo bundle).
- `label` (optional) — suggested display name for each repo. If
  multiple `url`, multiple `label` indices line up by position.
- `mode` (optional) — `read-only` (default) | `read-write` |
  `pull-only` (CalDAV-like one-way mirror).
- `priority` (optional) — `high` | `normal` | `low` — sets a uniform
  display-priority modifier for all calendars in the imported repo.
- `via` (optional) — short author label, e.g. `via=Master+%40` for
  attribution on the import-confirm screen.
- `references` (optional) — `auto` | `prompt` (default) | `ignore` —
  what to do with the referenced repo's own `references.toml` (see
  D.43).

Sensitive data goes in the URL **fragment** (after `#`), which Android
never sends in HTTP requests and is not logged in normal app
analytics:

- `token` — one-shot SSH deploy key or fine-grained PAT for cloning
  a private repo. Embedded by the share-side at link generation;
  consumed once at receive-side; cleared after first use.
- `expires` — ISO timestamp; receive-side refuses link past expiry.

QR codes encode the same URL. **Same intent both directions** — the
app's deep-link handler resolves either form to the same add-repo
flow.

## D.43 — `references.toml` manifest for cross-repo overlay

Path: `.strictlykeptboy/references.toml` in any repo.

This is **not** git submodules. (User had explicitly bad experiences
with submodules. We are not relitigating that.) It is an
**app-level manifest** that the app reads when scanning a repo and
uses to **offer** (never auto-import) other repos as siblings.

Schema:

```toml
schema_version = 1

[[reference]]
url = "git@github.com:dom/master-schedule.git"
label = "Master's schedule"  # suggested display name
priority_modifier = "high"   # "high" | "normal" | "low"
mode = "read-only"           # "read-only" | "read-write" | "pull-only"
required = false             # if true, app warns when reference is not added
credential_hint = "ssh-key:fingerprint:abc123"  # optional, helps auth UX
description = "Workouts, check-ins, weekly assignments."
default_active = true        # whether to enable the import by default

[[reference]]
url = "https://github.com/our-soccer-club/season-cal.git"
label = "Soccer season"
priority_modifier = "normal"
mode = "pull-only"
default_active = true
```

App behavior:

- When a user adds a repo, app reads its `references.toml` and
  presents a "this repo references N other repos — add them too?"
  screen with per-reference toggles.
- Already-configured repos (by URL hash) auto-dedup and show as
  "already added".
- Each reference is a **separate clone** in `~/.strictlykeptboy/repos/<id>/`.
  Independent git trees. Independent sync. Independent credentials.
- Removing a reference at the manifest level does NOT auto-uninstall;
  app prompts user to remove or keep.

## D.44 — Cross-repo state files: the core unlock

When User-Sub interacts with content from Repo-Dom (added as a
reference, read-only), the result of the interaction is **stored in
User-Sub's own primary repo** (or `_local/state/` if they have no
primary yet).

Path: `state/<source-repo-id>/<entity-id>.<state-kind>.toml`

- `<source-repo-id>` = stable hash (SHA-256 prefix) of the source
  repo's normalized URL. Survives renames.
- `<entity-id>` = UUIDv7 of the source entity (event, task, recurrence,
  comment).
- `<state-kind>` = `done` | `snooze` | `note` | `reaction` |
  `priority-override` | `mute` | `hide`.

Example — sub marks a Dom-assigned workout task as done:

```
~/.strictlykeptboy/repos/sub-own/state/
  abc123_dom-repo/
    01HZ-WORKOUT-MONDAY.done.toml
```

```toml
+++
schema_version = 1
state_kind = "done"
source_repo_url = "git@github.com:dom/master-schedule.git"
source_entity_id = "01HZ-WORKOUT-MONDAY"
source_entity_kind = "task"
author = "sub"
done_at = "2026-05-11T07:30:00+02:00"
+++

Body free-form. The sub can add a note ("did 5 extra reps", a photo
attachment ref, etc.). Optional.
```

Resolver merges state-files with source entities at view time. Done
state visually marks the source task as completed without ever
touching the source repo.

State files for ALL interactions follow this shape:

- **Done** — `done_at`, optional body for sub's note.
- **Snooze** — `until` ISO timestamp.
- **Note** — private annotation (only visible to the state-file's repo).
- **Reaction** — emoji + author. Stored as the sub's private record of
  their reaction; can become a comment in the source repo if write
  access exists.
- **Priority-override** — `priority` integer; overrides source for
  display only on this device's repos.
- **Mute** — notifications suppressed for this entity.
- **Hide** — entity not rendered (sub doesn't want to see Dom's "weigh-in"
  task on the schedule view).

**Why this works:**

- Source repos stay append-only-by-author. Zero merge conflicts on
  source.
- State repos hold all per-receiver mutations. Conflicts on state are
  vanishingly rare and idempotent (latest-wins per state file).
- The model scales: same primitive handles Dom-sub, coach-client,
  team-member, school-student.

CLI: `skb state set --done <entity-id>` / `skb state set --priority-override <calendar-id> <priority>` etc.

## D.45 — Simplified ("good boy") mode

A UI-only mode that hides advanced surfaces. **Not a feature lock** —
the user can always access full mode in one tap.

What's visible in simplified mode:

- Schedule view (single primary view: Day or "Today" depending on
  user preference at first launch).
- Task list (combined view).
- Sync button.
- Comment composer on event detail (if comments enabled in source).
- Settings → "Switch to full mode" + minimal toggles (theme, mode label).

What's hidden in simplified mode:

- Repo management UI (still works via deep-link receive; can't manage
  from inside the app).
- Identity creation / GPG / signing.
- Template picker / wizard.
- All advanced sync settings.
- Multi-view tabs (Week / Month / Year / Timebox).
- The Together tab.

**Mode label** is user-selectable from a list at first-use:

- "Simplified" (Play-Store default)
- "Focused"
- "Received Schedules"
- "Good Boy Mode"
- "Good Girl Mode"
- "Good Pet Mode"
- "Kept Mode"
- "Other..." (free text)

The user picks once; can change in Settings → Appearance → Mode label.
Play Store screenshots use "Simplified" exclusively. The hint-but-not-blatant
positioning per the project's broader register-discipline (see
`personalities` repo, `CLAUDE.md` "two surfaces" framing).

**Auto-entry into simplified mode:** when the user has only read-only
repos configured AND no own repo, app boots into simplified mode by
default. The Settings → "Switch to full mode" toggle is one-tap-
reversible.

## D.46 — First-launch deep-link bootstrap

If the app is launched via a `strictlykeptboy://add?...` intent AND
no repos are configured:

1. Skip the welcome wizard entirely.
2. Show the "Add gifted repo" screen with URL prefilled.
3. If the URL contains a `#token=` fragment, attempt to clone with
   that credential first; on success, the cred is stored and the
   token-fragment is wiped from any persisted referrer.
4. After successful clone:
   - Boot into simplified mode.
   - Display schedule view.
   - Show one-time onboarding card: "Welcome to your schedule.
     Tap any event for details. <author label> set this up for you.
     <Tap to learn more / Dismiss>."
5. If the receiving repo's `references.toml` declares additional
   repos with `default_active = true`, the app prompts ONCE
   ("This schedule references N other schedules. Add them?") with
   per-item toggles. Defaults match each reference's `default_active`.

## D.47 — Evolution path: simplified → own repo

User in simplified mode can grow into authoring without losing
gifted schedules:

1. In simplified mode, tap "Add my own events" (in event-create FAB or
   Settings → "Set up your own schedule").
2. Mini-wizard: 2–3 screens — repo name, provider, auth method.
3. App creates the new repo, runs an opt-in template-picker (the
   wizard's role-toggle screen, but skippable with "Just start
   empty").
4. App migrates `_local/state/*` into the new repo's `state/` folder.
5. App writes a `references.toml` in the new repo with all currently-
   configured gifted repos listed (cementing the relationship for
   sync across devices).
6. App keeps the user in simplified mode unless they choose otherwise
   ("You're set up. Stay in simple mode or switch to full?").

**The reverse path also works**: an authoring user can choose to
hide everything but a primary view (Settings → "Switch to simplified
mode"). Useful for focus / single-purpose-device contexts.

## D.48 — Authoring side: share-this-repo flow

In any repo settings: "Share this repo" → opens a share-config sheet:

- **Mode**: read-only (default) / read-write / pull-only.
- **Suggested label**: text input ("Schedule from Master", etc.).
- **Suggested priority modifier**: high / normal / low.
- **Auth method**:
  - "Recipient adds their own SSH key" (no token in link; sub uploads
    their key to the provider's collaborator UI).
  - "Embed a one-shot deploy key" (app generates a read-only deploy
    key via the provider's API and embeds it in the link fragment;
    24h expiry).
  - "Embed a fine-grained PAT" (app generates a read-only PAT and
    embeds it; 24h expiry).
  - "Public repo, no auth needed".
- **Output**:
  - Copy link.
  - Save QR (PNG to gallery).
  - Share via system share sheet.

Provider API requirements:

- **GitHub**: deploy-key creation needs `admin:public_key`; PAT
  creation needs `admin:public_key` and fine-grained PAT scope.
  Already in the OAuth scope set per Round 1 D.7.
- **Forgejo**: equivalent endpoints; same scope set.

CLI: `skb share <repo> [--mode ...] [--auth ...]` → prints the URL.

## D.49 — Multi-repo priority resolution (extends D.5)

Each calendar still owns its `priority` field as authored. The
resolver applies overrides in this order at render time:

1. **Local override** (per-device): `state/<source-repo-id>/<calendar-id>.priority-override.toml`
2. **Repo modifier** (from `references.toml` `priority_modifier`):
   `high` = +200, `normal` = 0, `low` = -200, applied uniformly to
   every calendar in the imported repo.
3. **Authored priority** in the source calendar's `calendar.toml`.
4. Default 500 if none of the above.

Floor / ceiling stays at [1, 1000]. Out-of-range values clamp.

Receiver can also do a global "pin this repo to top" — adds a +500
modifier to the repo's reference, no per-calendar config needed.

## D.50 — Received-repo credential storage

- **Public repos**: clone over HTTPS anonymously. No credential
  stored.
- **Private repos via deep-link deploy key**: key parsed from URL
  fragment, stored in `EncryptedSharedPreferences` keyed by repo URL
  hash. Token-fragment wiped from any persisted referrer string.
- **Private repos via OAuth**: standard Device Flow per D.7.
- **Private repos via PAT in link**: token parsed from URL fragment,
  stored encrypted, token-fragment wiped.
- **Read-only enforcement**: a repo configured as `read-only` at app
  level **refuses push** even if the credential would allow it.
  Local edits are queued and never leave the device (Round 2 NS-E
  semantics extend).
- **Token expiry**: app refreshes / re-prompts when token expires.

## D.51 — Source-repo-id stability

`source_repo_id` = SHA-256(normalized URL) truncated to 16 hex chars.
Normalization rules:

- Lowercase.
- Strip `.git` suffix.
- Strip trailing slash.
- For `git@host:owner/repo` and `https://host/owner/repo` — they
  resolve to the SAME id (the SSH-vs-HTTPS detail is transport, not
  identity).
- For renames at the provider side: the old id stays valid until the
  user explicitly re-binds. App detects "this URL 404s but the
  source_repo_id has state files" and prompts "the source repo seems
  to have moved — supply new URL?" with a `skb state rebind <old-id> <new-url>` CLI.

## D.52 — CLI surface additions for Round 3

New `skb` subcommands:

- `skb accept <url-or-qr-file>` — accept a gifted repo from a deep-link
  URL or by reading a QR PNG. The headless equivalent of the deep-link
  intent. Used by Claude to bootstrap a repo from a link the user
  pasted.
- `skb ref add|list|remove` — manage `references.toml` entries.
- `skb state set --done|--snooze|--note|--mute|--hide <entity-id>` —
  write state files.
- `skb state list [--source <repo-id>]` — list state files for a
  repo.
- `skb state rebind <old-id> <new-url>` — fix a renamed source repo.
- `skb share [<repo>] [--mode ...]` — print a share-link (or QR
  PNG path if `--qr` given) for the named repo.
- `skb mode simplified|full|toggle` — switch display mode.

All subject to the cross-cutting design rules in CLI-tooling (atomic
writes, auto-commits, `--json`, etc.).

## D.53 — CalDAV overlay (read-only, no-repo) — DEFERRED IMPLEMENTATION

A second CalDAV mode, **peer to D.25 but with no git repo**. The user
adds an external CalDAV calendar (Office 365, Google Calendar, Apple
iCloud, Nextcloud, generic RFC4791) as a **read-only priority-calendar
overlay**. Events render in the resolver alongside repo-backed
calendars; nothing is materialized to disk; nothing is ever pushed
back.

**Why separate from D.25.** D.25 assumes a repo target — every CalDAV
event becomes a Markdown file under `calendars/<id>/events/...`. That
is correct when the user wants bidirectional CalDAV ↔ git. It is
**wrong** when the user just wants Office 365's "Team Holidays"
calendar to *show up* in their priority view without polluting any
repo with files they neither own nor control.

**Why deferred.** The cross-provider test matrix is hard: live accounts
on Google + Microsoft 365 + Apple iCloud + Nextcloud, OAuth
refresh-token rotation, per-provider quota behavior, throttling-header
parsing. The plan locks the surface so future implementers don't
re-litigate the shape; ship once the testing story (recorded fixtures
+ optional `--live-caldav` integration suite) exists.

**Surface.**

- Settings: `Settings → Calendars → + Add external calendar` —
  top-level, NOT nested under any repo. Distinct surface from D.25's
  `Settings → Repos → <repo> → + Add CalDAV mirror`.
- CLI: `skb caldav add --overlay <url>` — flag on the existing
  `caldav` group rather than a new `caldav-overlay` group. Keeps the
  surface flat. `skb caldav list` shows both mirrors and overlays with
  a `kind` column.

**Resolver integration.** Overlay calendars are first-class — they
participate in priority (1..1000), master toggles, active-windows, the
priority-tiebreak comparator. The only difference from a repo-backed
calendar is that write paths refuse with a clear message and overlay
events never reach the filesystem. Implemented as an additive optional
`CalendarMeta.overlaySource: CalDavOverlayRef?` field; no separate
resolver phase required.

**Auth.** Reuses the SE-Q `CalDavCredential` sealed interface
(BasicAuth + OAuth Device Flow for Google/Microsoft +
app-specific-password for Apple). One auth surface for both modes.

**Cache.** Per-overlay Room table (separate from repo events,
`overlayId` FK). ETag / CTag / `sync-token` incremental fetch.

**Plan locations.** `main.md` Phase UU, `sync-engine.md` Phase SE-X.
No resolver phase — additive field only.

---

# Subagent task assignment

Six parallel Opus subagents flesh out the deep-dive planning docs.
Each writes ONE document to `docs/plans/<name>.md`. Each document
must follow the global CLAUDE.md plan-file convention (numbered phases
with letter prefixes, sub-step checkboxes per phase). Each subagent
writes phases scoped to their domain; the master plan at
`docs/plans/main.md` references them.

| Doc | Scope | Subagent |
|---|---|---|
| `data-model.md` | Every TOML/Markdown schema, file layout details, AGENTS.md/CLAUDE.md content, schema versioning + migrations | SA-1 |
| `sync-engine.md` | JGit usage, SSH+HTTPS auth, OAuth flows, foreground service, conflict UI, airplane mode, multi-repo coord | SA-2 |
| `ui-spec.md` | Every screen + component, nav rail, repo switcher, calendar views, task views, wizard, settings, tablet, Auto | SA-3 |
| `resolver.md` | Overlay algorithm, priority resolution, active-window evaluation, recurrence materialization, common-time finder | SA-4 |
| `templates-demo-wizard.md` | Every template's content, demo sub+Dom repos with full seed, wizard flow + role-toggle composition | SA-5 |
| `notifications-sharing-import.md` | Notification model + channels + groups, sharing mechanics, iCal/CSV import-export, identity switching, attribution UI | SA-6 |

Each subagent receives this decisions doc as context and a per-section
brief. They elaborate; they do not decide. If an unforeseen tradeoff
appears, they resolve it themselves with a recommended choice, write
the resolution inline, and continue.

## Round 2 — v1 scope expansion subagents

| Doc | Action | Scope | Subagent |
|---|---|---|---|
| `cli-tooling.md` | NEW | Full deep-dive for `skb` CLI (primary interface). Phase prefix `CLI-`. | SA-7 |
| `sync-engine.md` | EXTEND | Append phases SE-Q+ for bidi CalDAV, Git LFS, optional signed commits, ssh-agent, multi-branch | SA-8 |
| `data-model.md` | EXTEND | Append phases DM-K+ for comments folder, multi-tz fields, GPG keys on identity, AGENTS.md rewrite (CLI-first) | SA-9 |
| `ui-spec.md` | EXTEND | Append phases UI-V+ for drag-reschedule, pinch-zoom, weather, replies UI, GPG settings, Markwon body, sticker packs, multi-tz display, Auto voice-create | SA-10 |
| `notifications-sharing-import.md` | EXTEND | Append phases NS-L+ for bidi CalDAV UX, comments-on-events UI/notifications, CSV import, cross-device snooze sync, multi-branch sharing | SA-11 |
| `resolver.md` | EXTEND | Append phases RV-H+ for multi-tz semantics + multi-tz common-time + weather overlay as non-busy data layer | SA-12 |

Round 2 agents follow the same "elaborate, don't decide" rule. They
treat `decisions.md` Round 2 (D.23–D.40) as locked.

## Round 3 — shared schedules subagents

| Doc | Action | Scope | Subagent |
|---|---|---|---|
| `shared-schedules.md` | NEW | Holistic feature spec — deep-link/QR/protocol, references.toml semantics, simplified mode behavior, authoring share-flow, evolution path, end-to-end UX scenarios. Phase prefix `SH-`. | SA-13 |
| `data-model.md` | EXTEND | Phases DM-Q+ — `references.toml` schema, `state/<source-repo-id>/<entity-id>.<kind>.toml` schemas, source-repo-id derivation, conflict-free state-file design | SA-14 |
| `ui-spec.md` | EXTEND | Phases UI-FF+ — simplified-mode chrome, share-this-repo + QR generation UI, deep-link receive screen, first-launch routing, "add my own repo" mini-wizard, mode-label picker | SA-15 |
| `resolver.md` | EXTEND | Phases RV-L+ — cross-repo state-file overlay logic, multi-repo priority resolution with modifiers, source-repo-id matching, dedup logic | SA-16 |

Round 3 agents treat D.41–D.52 as locked.

---

# Round 4 — lifestyle wizard, kink-positive identity, avatar/stickers, atomic activities, cross-repo feedback, no-origin/multi-origin git (D.54–D.74)

After Round 3, six parallel drafts (`draft-lifestyle-wizard.md`,
`draft-kink-positive-identity.md`, `draft-avatar-stickers.md`,
`draft-atomic-activities.md`, `draft-global-id-feedback.md`,
`draft-no-origin-multi-origin.md`) were integrated. Round 4 locks
the following twenty-one decisions. Short-form decision IDs from
the drafts (K-1..K-7, AV pack format, AT inversion, FB global-ID,
MO no-origin etc.) are preserved inline as cross-references.

## D.54 — No empty-app start; wizard is mandatory on first launch

Every first-launch path lands the user on a populated repo. The
wizard's output (Phase K, deep-dive `draft-lifestyle-wizard.md`) is
the user's canonical starting state, not seed/sample/demo data.
Phone-only is a first-class wizard outcome (no remote required to
finish; uses `GitRepo.initLocalOnly` per D.74). Demo mode is
retired; the old `demo-sub` / `demo-dom` repo seeds are not
shipped. Phase L (demo content) is RETIRED. Bypass: a first-launch
deep-link intent (Phase QQ) takes precedence over the wizard
(gifted-schedule recipients skip the wizard per D.46).

## D.55 — Kink-positive default surface (locks K-1)

The app's default content (templates, sticker tags, alignment
copy, wizard prose, suggested event titles) is openly
kink-positive. Justification: the target audience is furries,
kinksters, and people in D/s relationships; defaulting to neutral
would underserve them. The wizard alignment screen offers
**Dominant / Submissive / Switch / Unaligned-private**; the last is
the in-wizard kink-off entry-point and propagates downstream
(hides `kink` role, hides kink templates, replaces strictly-X
phrasing with "routine"). SFW phrasing constraints apply ONLY to
surfaces that leak outside the app (lockscreen widgets, Play-Store
screenshots, neutral-mode toggle) — not to in-wizard copy. **User-
authored content is NEVER censored** across mode transitions.
Retracts the prior templates-demo-wizard.md SP-3 / SP-4 / SP-8 /
SP-9 SFW-readable register; SP-1 / SP-2 / SP-5 kept with revised
justification per KI-A.

## D.56 — Bat-mascot vs avatar-species are distinct characters

The bat-mascot (Claude-the-builder persona) is the wizard guide on
every wizard screen and on any future guided-onboarding surface.
The user's avatar species (selected on wizard Screen 2 from {bat,
fox, tiger, lion, wolf, bunny, cat, or a "choose your own"
sticker repo}) is a separate character that takes over in-app
mascot surfaces after the wizard ends. Default avatar (if user
skips Screen 2) is bat — coincidentally the same species as the
bat-mascot, but a distinct character.

## D.57 — Per-event privacy flag (locks K-2)

Every event/task/recurrence frontmatter accepts optional
`private = true` (default `false`). When true: lockscreen
notification shows generic "Scheduled event" label only (no
title, no body); countdown/agenda widgets show "—" instead of
title; notification body suppressed; in-app rendering unaffected.
Privacy mechanism, NOT SFW mechanism — works identically for
medical appointments, surprises, and kink-coded events. Per-
calendar default supported via `calendar.toml`
`default_private = true`; per-event flag wins.

## D.58 — Neutral-mode toggle scope (locks K-3)

Settings → Appearance → "Neutral mode" toggle (also set by wizard
"unaligned-private" alignment). When ON: kink-tagged templates
hidden from picker + wizard role-toggle list (`kink_coded = true`
in manifest); kink-tagged stickers replaced with neutral
counterparts in active sticker pack; kink-coded copy in wizard,
settings, and onboarding replaced with neutral phrasing; alignment
options reduce to "private organizing" with no dom/sub/switch
axis. When OFF: kink surface restored. **User-authored content is
NEVER censored** by either transition — only suggestions and
chrome change. Existing events titled "Cage check" stay "Cage
check" when neutral-mode flips on; they just don't get *suggested*
on next wizard run.

## D.59 — Play Store positioning Mature 17+ Lifestyle (locks K-4)

Content rating **Mature 17+** (covers "moderately suggestive
themes and references"; avoids the 18+ adults-only bar). Category
**Lifestyle**. Listing copy leads with unique value prop —
git-backed, atomic, AI-native, multi-repo, common-time finder;
lifestyle / D/s positioning mentioned in paragraph 2-3, not lead-
with. Screenshots show both neutral-mode AND kink-mode versions as
peer examples (4 of each). First-launch age gate (D.61). IARC
questionnaire: declare "infrequent/mild references to adult
themes" + "user-generated content" + no nudity, no explicit
sexual content, no graphic violence. Established precedent in this
category: KinkD, FET, Obedience habit tracker, Feeld, Mister — see
draft-kink-positive-identity.md KI-D for the full precedent list.

## D.60 — Content guidelines for shipped defaults (locks K-5)

What the app CAN ship: kink-coded task names and descriptions
(text only); kink-coded sticker artwork in SFW-rendered chibi-
style (collars, harnesses, cages, leashes, paw prints, locks,
bunny ears, fox tails — depicted as accessory/clothing style
only); bat-mascot with collar/harness/cage clothing-style
accessories; reaction emoji set with kink-positive entries (D.62).
What the app does NOT ship: pornography or explicit imagery;
nudity (including stylized); graphic depictions of sexual acts;
anything past Mature 17+ into 18+ adults-only. **User-authored
content in user-owned repos is unconstrained by this rule** — it
constrains only what *ships* with the app.

## D.61 — Age verification (locks K-6)

First-launch one-time age gate: modal "This app contains
references to adult lifestyle dynamics. You must be 17 or older to
use it." Buttons "I am 17 or older — continue" / "Exit". Decline
→ app finishes gracefully (returns to launcher). Confirmation
stored in encrypted app prefs (`age_confirmed_at = <ISO ts>`). NO
ID upload, NO email verification, NO third-party verification SDK
— Play Store's IARC rating + the modal constitute the regulatory
surface. Re-shown only if app data cleared.

## D.62 — Reaction set kink-positivity (locks K-7)

Phase YY (cross-repo feedback / reactions) ships a default
reaction set including: 🔒 locked, 🐾 paw, 💍 collar (rendered as
collar variant of the ring), 👍 good-boy/good-girl/good-pet
(label varies by mode-label), 🦴 bone, 🥄 spoon (aftercare).
Neutral-mode behavior: these reactions are filtered OUT of the
reaction picker when neutral-mode is on (user can't pick them);
BUT reactions received from a non-neutral-mode partner render in
the user's view with their generic counterpart (🔒 → 🔒,
🐾 → ✋, 💍collar → 💍ring, good-boy → 👍, etc.). **Visually
downgrade, never suppress.** Neutral-mode user sees that the
partner reacted, just with a neutral visual.

## D.63 — Avatar is a presence indicator, not a tamagotchi

Per Phase WW. No HP / mood / hunger. Sticker reflects what the
user is *scheduled to be doing* right now, rendered cute. Streak
counter is the only quantified element; count-only, opt-in per
activity, no shame copy. Renders in the now-card at the top of
the schedule shell.

## D.64 — Default species roster

Bat, fox, tiger, lion, wolf, bunny, cat. **Bat is the app default +
wizard guide species** (matches launcher icon and the bat-mascot
guide character). Tile order: bat first, then alphabetical (bat,
bunny, cat, fox, lion, tiger, wolf, choose-your-own).

## D.65 — Sticker pack format

WebP @ 512×512 (static or animated; decoder auto-detects).
`pack.toml` manifest with `schema_version`, `name`, `species`,
`author`, `license`, `style`, `[[sticker]]` entries each carrying
`activity_id`, `file`, `tags`, `animated`. Bundled default packs
under `app/src/main/assets/avatar-packs/<species>/` inside the
APK; user-supplied packs cloned to `<app-private>/avatar-packs/
<pack-id>/` where `<pack-id>` = SHA-256(normalized clone URL)
truncated to 16 hex chars (mirrors D.51 source-repo-id discipline).
Tag taxonomy locked at `neutral`, `kink`, `hygiene`, `workout`,
`meal`, `work`, `study`, `posture`, `rest`, `idle` (extensible —
unknown tags pass through and render unless filtered). Build-time
validator asserts neutral-set + kink-set + sub-beat IDs present in
every default pack.

## D.66 — Sticker resolution chain

Top-down, first match wins: (1) per-event override `sticker_id`
on frontmatter, (2) active sub-beat's `sticker_id`, (3) activity-
specific sticker in active pack, (4) category-generic sticker
(`workout-*` → `workout-pushup` etc.), (5) species-idle, (6) bat-
fallback (guaranteed to exist; validated at build time). Neutral-
mode filters rungs 3+4 by hiding stickers tagged `kink` (and not
also `neutral`); falls through to species-idle. Memoized on
`(activity_id, sub-beat index, pack id, neutral-mode)`.

## D.67 — Device-level sticker overrides are app-private

Per-activity sticker override storage in app-private
`EncryptedSharedPreferences` keyed
`avatar.overrides.<activity_id> → <pack-id>:<sticker-activity-id>`.
**NOT** committed to the user data repo. Rationale: device
aesthetic, not life-data; would be device-specific noise across
multi-device users; a partner reading the calendar repo has no
business with the user's per-device sticker prefs. Documented in
AGENTS.md template under "what is NOT in this repo". Per-event
sticker overrides ARE committed (additive frontmatter field —
that's user-authored content).

## D.68 — Sub-beats: additive optional event-frontmatter array

`[[subbeat]]` array on event files (and on template entries),
each entry `label`, `duration_seconds`, optional `sticker_id`.
Events without sub-beats behave exactly as today (additive, fully
backwards-compatible). Constraint: sum of `duration_seconds`
SHOULD be ≤ event's `duration_minutes * 60` (validator warns,
doesn't refuse). Sub-beats run sequentially; if shorter than the
parent event the cycle loops; if longer it truncates at event end.
Cap: 16 sub-beats per event.

## D.69 — "Choose your own" species clones a GitHub repo

User-supplied species packs clone a GitHub repo into an app-
private dir via shallow `--depth=1` clone, validated against
`pack.toml` schema and a required `activity_id = "idle"` entry.
Canonical template at
`https://github.com/eight87/strictlykeptboy-sticker-pack-template`
(hardcoded as build-time `STICKER_PACK_TEMPLATE_URL`, overridable
in app settings → Advanced). Auth reuses Phase B credential
machinery; pack repos are registered as **read-only data sources**
(refuses push always), separate from calendar-data repos. Multi-
pack-per-species: most-recently-added wins as active default; user
can override per-species in Settings.

## D.70 — Inverted habit model + atomic activities

Per Phase XX. **Default state for any past-or-current scheduled
event = `completed-by-schedule`.** Deviation is the explicit
action. The user only intervenes when they *didn't* do the thing,
or did it partially, or did it off-schedule. Rationale: the
project differentiator vs Finch / Habitica / streak-pet apps. No
guilt loop. Atomic = one entity per activity. Routines are
calendar overlays (additive `calendar.toml` fields `routine`,
`routine_id`, `routine_default_start`, `routine_can_materialize`),
not entities. Sub-beats inline in event TOML per D.68. Deviation
files at `deviations/<calendar-id>/<entity-id>/<yyyy-mm-dd>.md`,
schema `kind ∈ {skipped, partial, completed-early,
completed-late}`. **Separate from `exceptions/`** — exceptions =
scheduling-side change, deviations = post-hoc reality report.
Optional count-only streak counter per event-or-rule; no flames,
no escalation, no "you broke your streak" modals. No Wear OS;
`AlarmManager` only.

## D.71 — Global ID format: `<repo-fingerprint>:<entity-uuid>`

Per Phase YY. `repo-fingerprint` = `SHA-256(first-commit's tree
SHA, hex)` truncated to first 16 hex chars (8 bytes; ~1-in-1.8e19
collision space). Tree SHA (not commit SHA) — survives author/
date rebase of root. Stable across rebase of non-root commits,
across force-pushes preserving the root, across arbitrary squashes
not touching the root. Breaks only on intentional root rewrite
(treated as new repo identity; rebind UI in FB-A.4). Cached at
`.strictlykeptboy/repo-fingerprint` — **gitignored, never
committed**. Independent of `source_repo_id` (D.51, SHA-256 of
URL): both coexist; URL-id is the resolver/state-file key,
fingerprint is the feedback global-ID key.

## D.72 — Device-local repo registry + per-direction isolation

Per Phase YY. `<app-data>/repo-registry.toml` device-local (NEVER
synced — adding a repo on device A does not propagate to device
B). Per-repo `isolate_from` field is **per-direction (asymmetric)**:
each repo controls only what *it* refuses to see; the other side
needn't even know. `RepoRegistry.allVisibleTo(viewerFp)` is the
single chokepoint for cross-repo lookups (custom lint rule flags
external reads). Aggregation **count masking is forbidden** —
isolated sources are structurally invisible, not "N hidden".

## D.73 — Open-token reaction set + sticker-pack extension

Per Phase YY. Each reaction is a **string token**, not an emoji
codepoint. Default tokens: neutral (`thumbsup`, `heart`, `fire`,
`prayer-hands`, `sparkles`, `check`) + kink (`locked`, `collar`,
`good-boy`, `paw`, `bat`, `smirk`). Plus markdown comment body.
Schema does NOT enforce a closed set — opaque-string tokens — so
users can add `kneel`, `praise`, `mine`, etc. via sticker packs
without an app update. Sticker packs (Phase FF) re-skin tokens to
renders; renderer falls back to `❓` when a token isn't in the
active pack. Feedback files live in the **feedbacker's** repo at
`feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`.
One file per (author, target, reply_to); updating rewrites the
same file; deletion = `git rm` (no tombstones). Replies must live
in the same repo as their parent feedback. `write_back_target`
field in `references.toml` gates the "+ react" UI affordance —
opt-in by the referencing repo's author.

## D.74 — No-origin first-class + multi-origin git layer

Per Phase ZZ. **No-origin repos** (git-backed with zero
configured remotes) are a first-class state, equal in standing to
single-origin and multi-origin. `RepoConfig.remotes:
List<RemoteBinding>` (may be empty); `primaryRemote: RemoteName?`
(null iff empty). `repoId` = UUIDv7 persisted at
`.strictlykeptboy/repo-id` **committed to the repo** for cross-
device state-file consistency per Phase OO. `GitRepo.initLocalOnly`
constructor. Operations that hard-coded `"origin"` accept a
`remote: RemoteName? = primaryRemote` parameter and return
`NoRemotes` variants when `remotes.isEmpty()`. **Remote naming is
named-by-purpose** with `origin` as conventional primary (kept for
stock-git interop) and additional remotes as `mirror-<n>` by
default with user-editable `displayName`. Policy carried in
`RemoteBinding` fields, never on name. **Reconcile against
`primaryRemote` only**; non-primary remotes generate
`MirrorDivergence` signals (yellow banner, non-fatal), not auto-
merges. Manual diamond-merge ships path (a) adopt-mirror-as-
authoritative only in v1; path (b) force-push override gated
behind Settings → Advanced + typed confirmation. **Default push
policy**: first remote → `PUSH`, additional remotes → `PUSH_LAZY`.
Partial failure: primary success + mirror failure = local commit
shipped, mirror enters retry queue. **Force-push to primary is
prohibited in v1.** Per-remote auth bindings keyed on
`(repoId, remoteName)` — SSH keys per-remote by default (opt-in
reuse). `SecretsStore` re-keyed with one-shot migration. CLI:
`skb repo init --local`, `skb remote add|remove|list|set-primary|
set-policy`.
