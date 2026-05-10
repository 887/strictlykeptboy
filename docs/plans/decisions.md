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

## Subagent task assignment

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
