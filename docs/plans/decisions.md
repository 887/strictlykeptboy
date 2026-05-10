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
| Distribution | GitHub Releases + Obtainium; later Play Store | Same two-track distribution as tonearmboy. Play Store branding stays hinting-not-blatant per user direction. |

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
