# Round 2.18 — Replace the Android system calendar

## Status

Planning. No code changes yet. Predecessor: Round 2.16 (now-playing + todolist
unification), Round 2.17 (TBD). Sister plan: `docs/plans/round-2-15-demo-first-onboarding.md`.

## Context

The user wants strictlykeptboy (skb) to become their **default calendar app**
on Android — to the point where tapping a `.ics` invite in Gmail, hitting
"Add to calendar" from any sharesheet, or opening "Calendar" from a launcher
shortcut all land in skb instead of Google Calendar / Etar / Samsung Calendar.
And — critically — the skb view must show the user's **real life calendars**:
Google personal, Microsoft 365 work, an Exchange work account, any iCloud or
Nextcloud calendars, *plus* the file-backed repo calendars skb already owns.
A meeting on the work Exchange calendar must appear next to a routine on the
"laragana-personal" skb repo, tinted by the source, and editable in-place
where the source allows.

In concrete user-visible terms, "replace the system calendar" means all of:

1. **`.ics` invite from email** → tap → skb opens the preview sheet with
   Accept / Tentative / Decline, writes back via CalendarContract on the
   matching account.
2. **Sharesheet → "Add to calendar"** → skb is offered, and (if user picked
   us as default) selected without a chooser.
3. **A merged week / day / agenda view** in skb that overlays Google +
   MS365 + Exchange + iCloud + every skb repo. Each source visually
   distinguishable (color, tiny source-icon, optional "read-only" badge).
4. **Editing a Google event from inside skb** writes back through
   CalendarContract; the next sync round on Google's adapter pushes the
   change upstream. Read-only calendars (some work calendars, holidays,
   subscribed `.ics` feeds) are clearly badged and not offered edit
   affordances.
5. **Reminders** for external events fire through skb's notification layer,
   not the source app's, so quiet hours / DND / per-repo notif policy still
   apply uniformly.
6. **Launcher shortcut "Calendar"** opens skb (user picks us once in
   Android Settings → Default apps).

The user explicitly raised the question of how this maps to skb's
**multi-repo model**. The answer (locked under D-2.18.f below) is: every
`(Android Account, CalendarContract.Calendars.row)` tuple becomes a
**synthetic repo** that the resolver, chip-strip, color logic, and
notification layer all treat identically to a real on-disk repo, but whose
events flow in via a `CalendarContractBridge` rather than disk I/O. Repo ID
namespace: `system/<accountType>/<accountName>`. Calendar refs inside it:
`<calendarId>` (the CalendarContract `_ID`). This means the user sees their
work Exchange calendar in the same chip strip as `laragana-personal`,
toggles it on/off the same way, sets its priority the same way, and the
resolver's existing supersedence / active-windows / kind logic Just Works
on it.

This round does **not** build a CalDAV client, an EWS client, or a Google
Calendar OAuth flow. Instead skb leverages Android's existing sync-adapter
ecosystem: the user installs DAVx⁵ for Google/iCloud/Nextcloud, uses
Android's stock Exchange ActiveSync account for MS365, and skb reads from
the unified CalendarContract surface that those adapters populate. Phase H
ships the docs + a Settings deep-link that walks the user through it.

## What "replace the system calendar" requires (research summary)

Synthesis of the research the planning sub-agent did against Android docs,
Etar's manifest, DAVx⁵'s manual, and the AOSP calendar provider source.

**1. CalendarContract surface area.** The Calendar Provider exposes five
public tables under the authority `com.android.calendar`: `Calendars`,
`Events`, `Instances` (read-only, materialized occurrences of recurring
events), `Attendees`, `Reminders`. Plus `Colors` and `ExtendedProperties`
for sync adapters. Required columns when inserting an event:
`CALENDAR_ID`, `DTSTART`, `EVENT_TIMEZONE`, and either `DTEND` (one-off) or
`DURATION` + `RRULE`/`RDATE` (recurring). Recurrence is stored as raw
RFC5545 RRULE/EXDATE strings — skb's existing dmfs lib-recur layer round-
trips into and out of those fields cleanly.

**2. Permissions.** `READ_CALENDAR` + `WRITE_CALENDAR` are runtime dangerous
permissions. They unlock *all* calendars across all accounts in one grant —
no per-account scoping at the OS layer. Mitigation (D-2.18.g): we only
request them after the user toggles "Show system calendars" in skb Settings,
with a clear explanation that the grant covers everything the device knows
about.

**3. Default-app status / RoleManager.** `RoleManager.ROLE_CALENDAR` does
**not** exist in AOSP. There is no `ROLE_CALENDAR` constant — only
`ROLE_BROWSER`, `ROLE_DIALER`, `ROLE_SMS`, `ROLE_HOME`, `ROLE_EMERGENCY`,
`ROLE_CALL_REDIRECTION`, `ROLE_CALL_SCREENING`, `ROLE_NOTES`, `ROLE_WALLET`,
`ROLE_ASSISTANT`. Calendar replacement therefore is **not** a "claim the
role" gesture; it is a "respond to the same intent filters AOSP Calendar
does + win the user-visible chooser when they tap a `.ics` and pick
'always'". For tapping a calendar event URI (`content://com.android.calendar/...`)
the AOSP calendar provider's `ContentResolver.openInputStream` flow still
routes to whatever app declares `VIEW vnd.android.cursor.item/event` with
`category.DEFAULT` — Etar shows this is the canonical filter set.

**4. The intent-filter set to claim (replicating Etar's surface area):**

- `<intent-filter>` on MainActivity:
  - Existing `MAIN + LAUNCHER` — add `category.APP_CALENDAR` so Android
    classifies us as a calendar.
  - New: `VIEW` + `category.DEFAULT` + data `mimeType="time/epoch"`
    `scheme="content"` — the "go to date" intent (`content://com.android.calendar/time/<ms>`).
- New alias `EventDetailActivity` (or route into MainActivity with a
  Compose nav arg):
  - `VIEW` + `category.DEFAULT` + `mimeType="vnd.android.cursor.item/event"`
- New alias `EventEditActivity`:
  - `EDIT`, `INSERT` + `category.DEFAULT` + `mimeType="vnd.android.cursor.item/event"`
  - `EDIT`, `INSERT` + `category.DEFAULT` + `mimeType="vnd.android.cursor.dir/event"`
- New alias `IcsImportActivity`:
  - `VIEW` + `category.DEFAULT` + `mimeType="text/calendar"` (file/content scheme)
  - `VIEW` + `category.BROWSABLE` + `category.DEFAULT` + scheme `http`/`https`
    matching `.ics` (path-pattern `.*\\.ics`) — keep an eye on PendingIntent
    flags here; Etar does it via path pattern.

**5. Sync-adapter contract.** A sync adapter declares an
`android.accounts.AccountAuthenticator` (which makes an Account row show up
in *Settings → Accounts*) **plus** an `AbstractThreadedSyncAdapter` bound
to the `com.android.calendar` authority. Inside `onPerformSync` it reads /
writes Events / Reminders / Attendees with the magic
`CALLER_IS_SYNCADAPTER=true` query parameter, which unlocks additional
columns (sync-state, dirty, deleted, original_sync_id) the OS hides from
ordinary apps. DAVx⁵ is the reference implementation; AOSP's own
exchange/google adapters follow the same shape.

**6. DAVx⁵ as the "outside world" gateway.** DAVx⁵ is open-source
(bitfireAT/davx5-ose), syncs CalDAV → CalendarContract, and is the de-facto
way to land Google / iCloud / Nextcloud / Fastmail / mailbox.org on
Android without their official apps. It maps RFC5545 RRULE/EXDATE
directly; it emits per-exception event rows with `ORIGINAL_SYNC_ID`/
`ORIGINAL_TIME`; it maps `CLASS:PRIVATE` → `ACCESS_PRIVATE`. From skb's
perspective, DAVx⁵ is invisible: we just see calendars in
CalendarContract. **We will not vendor DAVx⁵, will not auto-install it,
will not bundle a competing CalDAV client.** Phase H ships docs + a
Settings link to its Play / F-Droid page.

**7. The two-worlds problem.** Skb already has a complete file-backed
calendar model: `RepoSnapshot` → `CalendarMeta` → resolver → `LayeredView`.
External calendars live in a relational DB owned by other processes. We
need to fuse them in `CalendarRegistry.state` without forcing the resolver
to know about Android. Solution (D-2.18.a, D-2.18.f): a new
`CalendarContractBridge` produces `Flow<List<CalendarMeta>>` *and*
`Flow<List<EventInput>>` for the visible date window, slotted into the
*same* aggregator stage that today consumes `RepoSnapshot`. Resolver
remains pure / disk-agnostic / OS-agnostic.

## Locked design decisions

These are derived from user intent ("push it as far as you can, multi-repo
bridge included") and are not open questions.

- **D-2.18.a — Bridge direction: read-in primarily, write-out secondarily,
  write-back to source via CalendarContract for two-way edit.** External
  calendars flow *into* skb's resolver via a new
  `system/CalendarContractBridge` that publishes synthetic `CalendarMeta`
  + materialized events for the visible window. Skb's repo events do
  **not** automatically appear in CalendarContract — that's gated behind a
  separate Settings toggle "Make skb visible to other Android apps" which
  enables the Phase G sync adapter. Defended: most users want their
  *outside* calendars in skb (read-in), and a smaller subset want their
  skb life visible to e.g. Google Maps' commute predictions / Wear OS
  faces (write-out, opt-in). Doing both lets the user pick which side of
  the bridge they need without forcing the other.

- **D-2.18.b — Account & sync adapter: yes, skb publishes itself as a
  "strictlykeptboy" Android Account.** Gated behind the Settings toggle
  in (a). When enabled, every active skb repo becomes one CalendarContract
  `Calendars` row under account
  `(name="<repoId>@local", type="com.eight87.strictlykeptboy")`. This
  makes skb events visible to *every* other calendar-aware app on the
  device (Wear OS, Auto, Google Now, third-party widgets) for free — the
  point of CalendarContract is exactly this kind of fan-out. Defended:
  building our own surface for each consumer is infinite work; one
  sync-adapter surface is finite work.

- **D-2.18.c — Google / MS365 / Exchange sync: not built by skb.** We do
  not implement CalDAV, EWS, EAS, Google's REST API, or Microsoft Graph.
  Users pair skb with DAVx⁵ (CalDAV: Google, iCloud, Nextcloud, Fastmail,
  Posteo, mailbox.org) and Android's stock Exchange account for MS365 /
  on-prem Exchange. Phase H ships a settings screen "Connect external
  calendars" that links out to DAVx⁵ on Play / F-Droid and to *Settings
  → Add account → Exchange* via the canonical
  `Settings.ACTION_ADD_ACCOUNT` intent with
  `EXTRA_ACCOUNT_TYPES=["com.android.exchange"]`. Defended: each of those
  protocols is months of work, and the result would just duplicate what
  DAVx⁵ already does well. Better to be a great consumer of the existing
  ecosystem.

- **D-2.18.d — Intent-filter set to claim.** Exactly the Etar surface
  area (see research §4): `time/epoch`, `vnd.android.cursor.item/event`,
  `vnd.android.cursor.dir/event`, `text/calendar`, `MAIN +
  APP_CALENDAR + LAUNCHER`. Plus a path-pattern filter for `https://*/*.ics`
  with `category.BROWSABLE` so .ics links in a browser also land in skb.
  Defended: Etar is the AOSP-derived reference replacement; matching its
  set means we appear in every system chooser the stock app appears in.
  Adding more filters than that risks intercepting unrelated stuff
  ("dialer-style intent overreach is a well-known anti-pattern"). The
  manifest already declares custom deep-link schemes from Phase MM and
  Phase O.2 — those stay untouched and are orthogonal to this work.

- **D-2.18.e — Repo mapping: every external Calendar = one synthetic
  repo.** Repo ID format `system/<accountType>/<accountName>` (e.g.
  `system/com.google/someone@gmail.com`). Each `CalendarContract.Calendars`
  row inside that account becomes one `CalendarRef` with id =
  `cal-<calendarContractId>`. `displayName` =
  `CALENDAR_DISPLAY_NAME`. `colorSeed` = `CALENDAR_COLOR` (raw int,
  hashed into the M3E tint pipeline the same way repo seeds are).
  Synthetic repos appear in `CalendarRegistry.state` *flagged* via a new
  `CalendarKind.External` enum case (extension of the existing
  `enum class CalendarKind { Regular, Timebox, External }`). The chip
  strip, settings UI, resolver, supersedence, active-windows all already
  work because they operate on `CalendarMeta`, not on disk. Defended:
  reusing existing resolver infrastructure is the entire point of skb's
  architecture; building a parallel "external events" pipeline would
  double the surface area for no win.

- **D-2.18.f — Two-way edit policy: write back via CalendarContract when
  `CALENDAR_ACCESS_LEVEL >= CAL_ACCESS_CONTRIBUTOR`, fall back to
  read-only badge otherwise.** When the user edits an external event in
  skb, the edit flow writes via `ContentResolver.update(Events.CONTENT_URI,
  ...)` without `CALLER_IS_SYNCADAPTER` — the OS routes the change back
  to the owning sync adapter, which pushes upstream on its next sync.
  For `CAL_ACCESS_READ` / `CAL_ACCESS_FREEBUSY` / `CAL_ACCESS_NONE`,
  skb shows the event as a non-editable badge ("Read-only — owned by
  Google Calendar"). Recurring-event edits offer the standard "this
  event / this and following / all" tri-choice (CalendarContract requires
  the caller to do this — there's no built-in helper). Defended:
  half-baked two-way edit (silently failing on read-only calendars)
  would be worse than no edit; we surface the constraint instead.

- **D-2.18.g — Permission UX: lazy, scoped, reversible.**
  `READ_CALENDAR` / `WRITE_CALENDAR` are NOT in the manifest at install
  time as `<uses-permission>`. Wait — they have to be, manifest-declared,
  to be requestable. Correction: they ARE declared in the manifest, but
  we never call `requestPermissions` until the user toggles
  *Settings → External calendars → Show system calendars* to ON. The
  toggle's secondary line explains: "Grants skb access to every calendar
  on this device, including ones from other accounts. You can revoke
  this anytime in Android Settings." `WRITE_CALENDAR` is requested only
  when the user first tries to edit an external event. If denied, skb
  falls back to read-only treatment of external events globally and the
  toggle reverts. Defended: bundling them at install is louder than the
  feature deserves; lazy request with clear copy keeps the trust
  budget intact.

- **D-2.18.h — Widget + lockscreen scope: deferred to Round 2.19.** Skb
  already has the Phase VV countdown widget and Phase EEE "now" widget.
  A *month-view homescreen calendar widget* like Google Calendar ships is
  a separate large piece of work (RemoteViews layout, weekly grid,
  AppWidgetProvider data binding through CalendarContract + repo
  resolver). Push to 2.19. Defended: ship the data-layer bridge + the
  intent-filter claim + the two-way edit + the sync-adapter surface in
  one round; widget is its own surface that can be developed
  independently against the bridge once it lands.

- **D-2.18.i — Reminder ownership: skb owns notification UX for events it
  renders, even when the event lives in CalendarContract.** Skb's
  existing `ReminderBroadcastReceiver` + `Reminder.kt` layer (Phase F)
  schedules and fires alarms. For external events we read
  `CalendarContract.Reminders` rows at sync time, translate to skb's
  internal `Reminder` records, and respect skb's per-repo notif policy +
  quiet-hours + dom-persona register. We do NOT disable the source
  adapter's own reminder service — that requires deeper integration than
  CalendarContract allows. Net effect: external events with reminders
  may fire twice (once from Google Calendar's notifier, once from skb).
  Mitigation: a Settings toggle "Suppress system calendar notifications"
  that opens the OS notification-channel settings for the source apps so
  the user can mute them. Defended: silencing other apps from inside
  skb is not technically possible (no IPC for "please don't notify"); a
  link-out is the honest UX.

## Multi-repo mapping

Concrete answer to the user's open question.

```
Skb repos (file-backed)              Synthetic system repos (CalendarContract)
─────────────────────────────        ───────────────────────────────────────────
laragana-personal/                   system/com.google/someone@gmail.com/
  calendars/                           cal-1234   (Personal)
    work/calendar.toml                 cal-5678   (Birthdays — READ_ONLY)
    routines/calendar.toml           system/com.google/someone@gmail.com#contacts/
                                       cal-7777   (Holidays in Germany — READ_ONLY)
laragana-work/                       system/com.android.exchange/someone@local/
  calendars/                           cal-9999   (Calendar — CONTRIBUTOR)
    meetings/calendar.toml             cal-9998   (Team Acme — OWNER)
                                     system/at.bitfire.davdroid/icloud-alex/
                                       cal-2222   (Family iCloud)
```

Naming convention:

- **Repo ID** for synthetic repos: `system/<accountType>/<accountName>`.
  `accountType` is the raw Android account-type string (e.g.
  `com.google`, `com.android.exchange`, `at.bitfire.davdroid`,
  `com.microsoft.office.outlook.USER_ACCOUNT`). `accountName` is the
  display login. Both are URL-safe-percent-encoded when stored.
- **Calendar ref ID** inside a synthetic repo: `cal-<_ID>` where `_ID` is
  the `CalendarContract.Calendars` row primary key on this device.
  Stable across syncs (CalendarContract guarantees the `_ID`), unstable
  across reinstalls (so we never persist these IDs to disk — they're
  derived live from CalendarContract every time the bridge runs).
- **CalendarMeta.kind** for these is the new `CalendarKind.External`.
- **CalendarMeta.repo** points at a synthetic `RepoRef` whose
  `RepoConfig.rootDir` is `null` and whose `kind` field is a new
  `RepoKind.System` (extending the demo / git / no-origin trio).
- **Active windows, supersedence, priority, color overrides** for these
  synthetic calendars are stored in **app prefs**, not in any
  `calendar.toml` (there's no disk to write to). A new
  `SystemCalendarPrefsStore` keyed by `(accountType, accountName,
  calendarId)` holds the overrides. Defended: round-tripping these into
  a synthetic `calendar.toml` would imply they belong in a Git repo
  somewhere, which they don't.
- **Chip-strip rendering**: synthetic calendars get a tiny source-icon
  badge (Google `G`, Outlook `O`, iCloud cloud, generic gear for
  unknown account types) in addition to the existing color dot. Same
  chip widget, additional adornment slot.

The user sees one merged list everywhere: Schedule day/week/agenda views,
notification briefings, chip strip, repo-switcher avatar menu. The
existing `ActiveSetEvaluator` already merges across repos — synthetic
repos slot in identically.

## Phase order

- **Phase A** — Bridge data layer (CalendarContract → resolver).
- **Phase B** — Permission UX + Settings toggle.
- **Phase C** — UI surfacing (chip strip + visual treatment + repo
  switcher).
- **Phase D** — Two-way edit (insert / update / delete via
  CalendarContract).
- **Phase E** — Intent-filter set (VIEW .ics, EDIT/VIEW event URIs,
  INSERT).
- **Phase F** — Reminder + alarm parity (CalendarContract.Reminders →
  skb notif layer; suppress-system docs link).
- **Phase G** — Sync adapter (publish skb repos as "strictlykeptboy"
  Android Account).
- **Phase H** — DAVx⁵ / Exchange integration docs (no code — just a
  Settings screen + outbound links).
- **Phase I** — Default-app discovery onboarding ("make skb your
  default calendar?").
- **Phase J** — Tests + AVD smoke.
- **Phase K** — Plan-file close.

## Phase A — Bridge data layer

- [ ] **A.1** New file `app/src/main/java/com/eight87/strictlykeptboy/system/CalendarContractBridge.kt`.
  Queries `CalendarContract.Calendars` with a projection of `[_ID,
  ACCOUNT_NAME, ACCOUNT_TYPE, CALENDAR_DISPLAY_NAME, CALENDAR_COLOR,
  CALENDAR_ACCESS_LEVEL, OWNER_ACCOUNT, IS_PRIMARY, SYNC_EVENTS,
  VISIBLE]`. Filter `VISIBLE = 1` and `SYNC_EVENTS = 1`. Returns a
  cold `Flow<List<SystemCalendar>>` re-emitted on a `ContentObserver`
  registered against `Calendars.CONTENT_URI`.
- [ ] **A.2** New `data class SystemCalendar(val id: Long, val
  accountName: String, val accountType: String, val displayName:
  String, val color: Int, val accessLevel: Int, val ownerAccount:
  String?, val isPrimary: Boolean)` in
  `system/SystemCalendar.kt`.
- [ ] **A.3** Extend `enum class CalendarKind` in
  `resolver/Types.kt` with `External`. Audit every existing exhaustive
  `when (kind)` site for compilation breakage; add an explicit
  `External -> Regular`-equivalent fallback unless the site has
  external-specific behavior.
- [ ] **A.4** Extend `RepoKind` (in `git/RepoConfig.kt` or wherever
  it lives) with `System`. Synthetic `RepoConfig` for system repos
  has `rootDir = null`, `remoteName = null`, `colorSeed` =
  hash(accountType + accountName).
- [ ] **A.5** New `system/SystemCalendarsRepository.kt` — combines
  the `Flow<List<SystemCalendar>>` from `CalendarContractBridge` into
  `Flow<List<CalendarMeta>>` of `kind = External`, repo =
  synthetic `RepoRef("system/<accountType>/<accountName>")`.
- [ ] **A.6** New `system/SystemEventsBridge.kt` — queries
  `CalendarContract.Instances` for a given time window
  `[begin, end)` (`Instances.CONTENT_BY_DAY_URI` with appended
  begin/end), projection includes `EVENT_ID`, `BEGIN`, `END`,
  `ALL_DAY`, `TITLE`, `DESCRIPTION`, `EVENT_LOCATION`,
  `EVENT_TIMEZONE`, `CALENDAR_ID`, `RRULE`, `STATUS`,
  `ACCESS_LEVEL`. Maps each row to skb's `EventInput` (or a new
  sibling `ExternalEventInput` if the resolver needs distinguishing
  metadata such as `accessLevel`).
- [ ] **A.7** Decision sub-step: extend `EventInput` with optional
  `external: ExternalSource?` (account type + account name +
  CalendarContract event ID + accessLevel + ownerAccount). Defended:
  resolver still treats all events uniformly; only the writeback
  layer + UI badges read `external`.
- [ ] **A.8** Aggregator wiring in `composition/AppGraph.kt`:
  `CalendarRegistry` already takes `synthesizedSnapshot:
  StateFlow<RepoSnapshot>`. Extend `IndexerSnapshotPublisher` to
  fold `SystemCalendarsRepository.state` into its emitted
  `RepoSnapshot.calendars` list. Mirror for events via
  whichever publisher feeds the resolver's event stream.
- [ ] **A.9** Date-window contract: the resolver currently
  materializes for a per-view window. `SystemEventsBridge` must take
  the same window as input and re-emit on window change. Confirm via
  reading `resolver/Renderer.kt` how the window flows in today; wire
  identically.
- [ ] **A.10** Color mapping: `CALENDAR_COLOR` is a raw `0xAARRGGBB`
  int. Translate to skb's M3E tint pipeline by populating
  `CalendarMeta.colorSeed` with the truncated int hash. Verify
  Material You harmonization still applies.
- [ ] **A.11** Access-level → editability flag exposed on
  `CalendarMeta` (new field `val externalAccessLevel: Int? = null`).
  `null` for non-external; one of the
  `CalendarContract.Calendars.CAL_ACCESS_*` constants for external.
- [ ] **A.12** `ContentObserver` lifecycle owned by
  `SystemCalendarsRepository` — registered in a `coroutineScope`
  tied to the app lifecycle, NOT to a view-model. Same pattern as
  `RepoStore`.
- [ ] **A.13** Empty-permission state: if `READ_CALENDAR` not
  granted, all flows emit `emptyList()` (no SecurityException
  bubbling up). Granted state checked on each query, not cached.
- [ ] **A.14** New `system/SystemCalendarPrefsStore.kt` for
  user-defined overrides (priority, active toggle, active windows,
  supersedence) keyed by `(accountType, accountName, calendarId)`.
  Backed by `SharedPreferences` (JSON via `kotlinx.serialization`).
- [ ] **A.15** Overlay step in `SystemCalendarsRepository` — apply
  prefs overrides to the synthesized `CalendarMeta` before emit.
  Mirrors `CalendarRegistry.applyOverlay` semantically.

## Phase B — Permission UX + Settings toggle

- [ ] **B.1** Add `<uses-permission android:name="android.permission.READ_CALENDAR" />`
  and `WRITE_CALENDAR` to `AndroidManifest.xml`. Comment them with
  the round + phase reference.
- [ ] **B.2** New settings screen
  `ui/settings/ExternalCalendarsScreen.kt`. Top-level toggle "Show
  system calendars in strictlykeptboy". Off by default. Secondary
  toggle (disabled until top-level is on): "Allow editing system
  calendars" → gates `WRITE_CALENDAR` request and the write path.
- [ ] **B.3** Settings entry-point in
  `ui/settings/SettingsScreen.kt` — new row "External calendars"
  with subtitle showing N visible calendars when on,
  "Off" when off.
- [ ] **B.4** Permission request flow uses Activity Result API
  (`rememberLauncherForActivityResult` + `RequestPermission`
  contract). On denial, toggle reverts visually and a snackbar
  explains.
- [ ] **B.5** Per-calendar visibility toggles inside the External
  Calendars screen — list every `SystemCalendar` grouped by Account,
  with a switch that writes to `SystemCalendarPrefsStore`. Hidden
  calendars are filtered out of `CalendarMeta` emission.
- [ ] **B.6** First-run nudge: when the user first opens skb after
  an OS-level account add (detected via `AccountManager` listener),
  show a one-shot snackbar "New calendar accounts detected — show
  them in skb? [Settings]".
- [ ] **B.7** Quiet-hours / dom-persona / mode register sourcing for
  external events: external events get the *active* repo's
  identity, not their own (system repos have no
  `identity.toml`). Decision rationale in plan body.
- [ ] **B.8** Strings — every new copy string in
  `values/strings.xml` only (per repo translation policy).
- [ ] **B.9** Privacy doc paragraph in
  `docs/plans/decisions.md` (new D.88) clarifying skb does not
  exfiltrate calendar data — purely local.

## Phase C — UI surfacing

- [ ] **C.1** `CalendarFilterChipStrip` adornment slot — show a
  source-icon glyph (small leading icon) on chips whose
  `CalendarKind == External`. Icon mapping table:
  `com.google` → Google "G" mark (vector); `com.android.exchange`
  / `eas` / `com.microsoft.*` → Outlook "O"; `at.bitfire.davdroid`
  → cloud; default → gear.
- [ ] **C.2** Read-only badge — `CAL_ACCESS_READ` /
  `CAL_ACCESS_FREEBUSY` chips render with a lock glyph and
  `disabled = true` for edit affordances (the chip itself is still
  toggleable for visibility).
- [ ] **C.3** Repo switcher / avatar menu — synthetic repos appear
  in the avatar menu under a section header "System calendars"
  separating them from the file-backed repos. Selecting a synthetic
  repo as "active" is **not** allowed (no identity, no writes by
  default) — its row is non-clickable in the active-repo picker but
  still rendered for visibility.
- [ ] **C.4** Day-band rendering — `DayBand` already carries
  `colorSeed` + `kind`. External events surface a 2-pixel left edge
  strip in the source's color, with the existing band color used for
  the body. Defended: lets the user spot which events are
  external without losing skb's color identity.
- [ ] **C.5** Agenda view header — external account groups get a
  divider label with the account email, similar to how repo
  groupings work today.
- [ ] **C.6** Empty-state copy when `READ_CALENDAR` denied + toggle
  on: "skb needs the Calendar permission to see your system
  calendars. [Grant] [Open Settings]".
- [ ] **C.7** Event detail sheet — for external events, header shows
  "From Google Calendar (someone@gmail.com)" or analogous;
  attendees rendered from `CalendarContract.Attendees`.
- [ ] **C.8** Attendees rendering — for read-only event view, list
  attendees with RSVP status icons (accepted / tentative / declined
  / no response). Map `Attendees.ATTENDEE_STATUS` constants.
- [ ] **C.9** Reminder list in detail sheet — read
  `CalendarContract.Reminders` for the event; render skb-style
  reminder cards.

## Phase D — Two-way edit

- [ ] **D.1** New `system/CalendarContractWriter.kt` — pure write
  surface: `insertEvent`, `updateEvent`, `deleteEvent`,
  `respondToInvite(eventId, response)`.
- [ ] **D.2** Editor flow detection: in the event-edit sheet
  view-model, if `meta.kind == External` and `accessLevel >=
  CAL_ACCESS_CONTRIBUTOR`, edits write via
  `CalendarContractWriter` instead of `EntityWriter`. If
  `< CAL_ACCESS_CONTRIBUTOR`, the editor opens read-only with a
  "Read-only — owned by [account]" header.
- [ ] **D.3** Field mapping — skb's `EventInput` → `ContentValues`
  for `Events.CONTENT_URI`. Required columns:
  `CALENDAR_ID = meta.external.calendarId`, `DTSTART`, `DTEND`,
  `EVENT_TIMEZONE`, `TITLE`, `DESCRIPTION`, `EVENT_LOCATION`,
  `ALL_DAY` (0/1), `STATUS`, `ACCESS_LEVEL`. Round-trip emoji + body
  via `DESCRIPTION` (skb's frontmatter has no equivalent in
  CalendarContract; emoji goes into title prefix, body into
  description).
- [ ] **D.4** Recurrence — translate skb's dmfs-lib-recur
  `RecurrenceInput` to RFC5545 `RRULE` string (lib-recur exposes a
  serializer). Write to `Events.RRULE`. EXDATE → `Events.EXDATE`.
- [ ] **D.5** Recurring-event edit tri-choice prompt ("this event
  only / this and following / all"). Implementation per
  CalendarContract docs:
  - "this only" → insert a new event row with
    `ORIGINAL_ID = parentEventId` + `ORIGINAL_INSTANCE_TIME = start`.
  - "this and following" → set the parent's `RRULE` UNTIL to the
    instance start; insert a new recurring event from the instance.
  - "all" → update the parent event row.
- [ ] **D.6** Delete flow — soft-delete via
  `ContentResolver.delete(eventUri)` (the source adapter handles
  the upstream push). Recurring tri-choice mirrors D.5.
- [ ] **D.7** RSVP write — `Attendees.ATTENDEE_STATUS` update for
  the current user's attendee row. Surface as Accept / Tentative /
  Decline buttons in the detail sheet for events where
  `Events.SELF_ATTENDEE_STATUS` is settable.
- [ ] **D.8** Conflict-handling — if the upstream sync rejects an
  edit, the source adapter marks the event `DIRTY = 1` and may
  surface its own retry / error UX. Skb shows a passive
  "Sync pending" badge while `DIRTY = 1`.
- [ ] **D.9** Optimistic update — UI assumes write success
  immediately, since CalendarContract writes are synchronous to the
  local DB even before upstream sync.
- [ ] **D.10** Write-permission gate — block all write paths when
  `WRITE_CALENDAR` not granted; show a re-permission inline cue.

## Phase E — Intent filter set

- [ ] **E.1** Edit `AndroidManifest.xml` — add `<category
  android:name="android.intent.category.APP_CALENDAR" />` to the
  existing MAIN/LAUNCHER intent-filter on MainActivity.
- [ ] **E.2** Add new `<intent-filter>` on MainActivity for
  `VIEW + time/epoch + scheme=content` (the "go to date"
  intent).
- [ ] **E.3** Add `activity-alias` `EventDetailActivity` pointing at
  MainActivity with intent-filter `VIEW + DEFAULT +
  vnd.android.cursor.item/event`.
- [ ] **E.4** Add `activity-alias` `EventEditActivity` pointing at
  MainActivity with two intent-filters:
  - `EDIT + INSERT + DEFAULT + vnd.android.cursor.item/event`
  - `EDIT + INSERT + DEFAULT + vnd.android.cursor.dir/event`
- [ ] **E.5** Add `activity-alias` `IcsImportActivity` pointing at
  MainActivity with intent-filter `VIEW + DEFAULT +
  text/calendar` (scheme=file/content) and a second filter
  `VIEW + BROWSABLE + DEFAULT + scheme=https +
  pathPattern=".*\\.ics"`.
- [ ] **E.6** Routing in `MainActivity.onCreate` /
  `MainActivity.onNewIntent` — read the incoming intent's action +
  data and dispatch to the right Compose destination:
  - `VIEW time/epoch` → schedule screen pinned to that date.
  - `VIEW vnd.android.cursor.item/event` → event detail sheet for
    the event ID.
  - `EDIT/INSERT vnd.android.cursor.item/event` →
    event-edit screen prefilled from extras
    (`EXTRA_EVENT_BEGIN_TIME` / `EXTRA_EVENT_END_TIME` / `TITLE`,
    etc.).
  - `VIEW text/calendar` → `IcsImportFlow` (Phase E.7).
- [ ] **E.7** New `system/IcsParser.kt` — parse `text/calendar`
  payload using the ical4j-jvm or the same iCal library DAVx⁵
  pulls in (dmfs has `lib-recur` already; check for an ical4j
  equivalent already in skb's deps; if absent, add the
  smallest viable parser dep). Returns one or more
  `EventInput`s + attendee + reminder records.
- [ ] **E.8** `IcsImportScreen` — preview the parsed event, let user
  pick destination (any visible calendar including external ones
  the user has WRITE access to). Default destination = the user's
  primary external calendar if known, else the active skb repo's
  primary calendar.
- [ ] **E.9** Default-handler test plan — manual: install skb,
  long-press a `.ics` in Gmail, hit "Always" with skb selected,
  verify subsequent taps go straight to skb.
- [ ] **E.10** Backwards-compat check — verify the existing
  `strictlykeptboy://event` deep links (Phase MM) still resolve;
  the new filters are additive and shouldn't collide.

## Phase F — Reminder + alarm parity

- [ ] **F.1** Read `CalendarContract.Reminders` for every
  external event surfaced in the visible window.
- [ ] **F.2** Translate to skb's internal `Reminder` records (store
  in-memory only — not persisted to disk since the source is
  CalendarContract).
- [ ] **F.3** Schedule via the existing
  `ReminderBroadcastReceiver` + `AlarmManager` path. Tag each
  scheduled alarm with the external event ID so re-sync can
  cancel-and-reschedule cleanly.
- [ ] **F.4** Quiet-hours / DND respect — runs through skb's
  existing notif policy layer unchanged.
- [ ] **F.5** Dom-persona register — for external events the active
  repo's identity supplies the praise/honorific copy; if no active
  repo, fall back to a neutral copy bank.
- [ ] **F.6** Cancellation on event delete / window leave —
  observer-driven; when the source row disappears, cancel matching
  alarms.
- [ ] **F.7** "Suppress system calendar notifications" Settings row
  — opens the OS notification settings for the user to mute the
  source apps. Detect installed candidates via PackageManager;
  show one row per detected candidate (Google Calendar, Outlook,
  Samsung Calendar, Etar, etc.).
- [ ] **F.8** Reboot survival — alarms scheduled for external
  events must re-arm via `BootCompletedReceiver` (existing).
  Ensure the re-arm step queries CalendarContract for the *current*
  visible window, not a persisted snapshot.

## Phase G — Sync adapter (skb as Android Account)

- [ ] **G.1** New `system/SkbAccountAuthenticator.kt` extends
  `AbstractAccountAuthenticator`. Account type
  `com.eight87.strictlykeptboy`. One account per skb repo, named
  `<repoId>@local`.
- [ ] **G.2** New `system/SkbAuthenticatorService.kt` —
  bound service exposing the authenticator. Manifest entry +
  `res/xml/authenticator.xml` (`accountType`, `icon`, `label`).
- [ ] **G.3** New `system/SkbCalendarSyncAdapter.kt` extends
  `AbstractThreadedSyncAdapter`. In `onPerformSync`:
  - Diff skb's repo events for this account against
    `CalendarContract.Events` rows where `ACCOUNT_TYPE =
    "com.eight87.strictlykeptboy"` and `ACCOUNT_NAME =
    accountName`.
  - Insert / update / delete with `CALLER_IS_SYNCADAPTER=true`.
  - One Calendar row per repo's logical "calendar" (e.g.
    `routines`, `work`, `meetings`) — the Calendar Provider
    permits one account to own many `Calendars` rows.
- [ ] **G.4** `SkbSyncService` — bound service exposing the
  sync adapter via `getSyncAdapterBinder()`.
- [ ] **G.5** `res/xml/sync_calendar.xml` — sync-adapter metadata:
  `contentAuthority="com.android.calendar"`,
  `accountType="com.eight87.strictlykeptboy"`,
  `supportsUploading="false"` (data flows one way — skb → OS).
- [ ] **G.6** Account creation flow — gated behind Settings toggle
  "Make skb visible to other Android apps". When toggled on, call
  `AccountManager.addAccountExplicitly` for each active repo;
  toggled off, `removeAccountExplicitly` cleans up.
- [ ] **G.7** Trigger sync on disk change — when `GitRepo.commitAll`
  lands, call `ContentResolver.requestSync` for the matching
  account / `com.android.calendar` authority.
- [ ] **G.8** Calendar-row idempotency — store the `Calendars._ID`
  per (repoId, skbCalendarId) in app prefs; reuse on subsequent
  syncs.
- [ ] **G.9** Event idempotency — set `Events._SYNC_ID` to skb's
  event UUIDv7 so subsequent syncs match without ambiguity.
- [ ] **G.10** Access level — write `CAL_ACCESS_OWNER` on the
  Calendar row so other apps see the skb-published events as
  fully owned (and skb itself can still edit them through the
  normal write path).
- [ ] **G.11** Color seed — pass `CALENDAR_COLOR` from the repo's
  `colorSeed` so external apps see the skb identity color.
- [ ] **G.12** Reminders mirror — skb's repo reminders publish as
  `CalendarContract.Reminders` rows so other apps (Wear OS) see
  them.

## Phase H — DAVx⁵ / Exchange integration docs

- [ ] **H.1** New screen `ExternalAccountsConnectScreen` linked from
  the External Calendars settings page. Three cards:
  - "Google / iCloud / Nextcloud / Fastmail (CalDAV)" → button
    "Install DAVx⁵" → `Intent.ACTION_VIEW` to F-Droid /
    Play Store listing for `at.bitfire.davdroid`.
  - "Microsoft 365 / Exchange" → button "Add Exchange account"
    → `Settings.ACTION_ADD_ACCOUNT` with
    `EXTRA_ACCOUNT_TYPES=["com.android.exchange"]`.
  - "Already configured" → opens
    `Settings.ACTION_SYNC_SETTINGS`.
- [ ] **H.2** Help copy — short explainers per card. Reference
  DAVx⁵'s docs link.
- [ ] **H.3** Detect installed-state — if DAVx⁵ is installed
  (`PackageManager.getApplicationInfo("at.bitfire.davdroid")`),
  replace the "Install" button with "Open DAVx⁵" via
  `getLaunchIntentForPackage`.
- [ ] **H.4** No bundling, no auto-install — purely outbound links.
- [ ] **H.5** Doc page in `docs/external-calendars.md` mirroring
  the same content for users who read the repo.

## Phase I — Default-app discovery onboarding

- [ ] **I.1** Onboarding step (one-shot card in the existing intro
  wizard from Round 2.15): "Make skb your default calendar app?"
  with a button that opens `Settings.ACTION_MANAGE_DEFAULT_APPS` or
  the closest available analog. Since `RoleManager.ROLE_CALENDAR`
  doesn't exist, the actual mechanism is: the user is shown the
  intent-chooser the next time they tap a `.ics` and picks "Always".
  The onboarding card explains this instead of promising a
  one-click default.
- [ ] **I.2** Detection of "is skb the default?" — heuristic via
  `PackageManager.resolveActivity(Intent(ACTION_VIEW).setType("text/calendar"), MATCH_DEFAULT_ONLY)`
  and comparing the resolved package to ours. Surface a
  "You're the default" badge on the External Calendars
  settings screen when true.
- [ ] **I.3** Re-prompt logic — never. Show the card once during
  Round-2.15-style intro; if dismissed, never re-show.
- [ ] **I.4** Power-user shortcut in Settings: "Calendar app
  defaults" row that opens
  `Settings.ACTION_MANAGE_DEFAULT_APPS`.

## Phase J — Tests + AVD smoke

Unit / Robolectric tests (target list, ~15 tests):

- [ ] **J.1** `CalendarContractBridgeTest` — Robolectric shadow of
  CalendarContract; verify projection + filter + observer rewires.
- [ ] **J.2** `SystemCalendarsRepositoryTest` — merge + overlay +
  prefs override.
- [ ] **J.3** `SystemEventsBridgeTest` — Instances query window;
  RRULE round-trip with one rule + one EXDATE.
- [ ] **J.4** `CalendarContractWriterTest` — insert / update /
  delete; recurring tri-choice for the three cases (this only /
  this+following / all).
- [ ] **J.5** `IcsParserTest` — round-trip a Google-emitted .ics
  invite, an Outlook-emitted one, an Apple-emitted one (capture
  fixtures from real-world samples — public test fixtures are
  available in DAVx⁵'s test suite under MIT).
- [ ] **J.6** `IntentRoutingTest` — MainActivity receives each of
  the new intent-filter actions and routes to the correct nav
  destination.
- [ ] **J.7** `SkbCalendarSyncAdapterTest` — publishes events for a
  repo, second run is idempotent (no duplicates).
- [ ] **J.8** `CalendarKindExternalEnumTest` — every existing
  `when (kind)` site compiles and behaves with the new variant.
- [ ] **J.9** `ExternalCalendarsViewModelTest` — toggle flows,
  permission denial.
- [ ] **J.10** `AccessLevelEditabilityTest` — read-only / contributor
  / owner gating of the edit flow.
- [ ] **J.11** `ReminderTranslationTest` — Reminders → skb internal
  reminder records; reboot survival.
- [ ] **J.12** `SystemCalendarPrefsStoreTest` — JSON serialization
  round-trip.
- [ ] **J.13** `DefaultAppDetectionTest` — mock PackageManager
  resolution.
- [ ] **J.14** `RecurrenceTriChoiceWriteTest` — verify ORIGINAL_ID /
  ORIGINAL_INSTANCE_TIME / RRULE-UNTIL pattern.
- [ ] **J.15** `SyncAdapterAuthorityTest` — manifest authority
  string matches CalendarContract authority constant exactly.

AVD smoke scenarios (real headless `medium_phone` AVD; 10+):

- [ ] **J.16** With Google account + DAVx⁵ + READ_CALENDAR granted,
  Google calendar events appear in skb's week view tinted by the
  source color.
- [ ] **J.17** Tap a `.ics` file from a downloaded test fixture —
  skb appears in the chooser, opens the preview, imports into a
  selected destination.
- [ ] **J.18** Set skb as default for `text/calendar` via
  "Always" in the chooser — subsequent tap goes straight to skb.
- [ ] **J.19** Edit a Google event title from inside skb — observe
  the change in Google Calendar app on the same device after a
  forced sync.
- [ ] **J.20** Edit a read-only "Holidays in Germany" event — skb
  shows the read-only header, no save button.
- [ ] **J.21** Add a one-off event via the
  `INSERT + vnd.android.cursor.dir/event` intent fired from
  another app — skb opens its edit sheet prefilled.
- [ ] **J.22** Enable "Make skb visible to other Android apps" —
  observe a "strictlykeptboy" account row in
  *Settings → Accounts*, see skb repo events in another calendar
  app (e.g. Etar installed alongside).
- [ ] **J.23** Toggle a system calendar off in skb's chip strip —
  events disappear from the week view but stay in Google Calendar.
- [ ] **J.24** Set quiet hours, observe an external event
  reminder respecting the quiet window (no fire / deferred fire
  per skb policy).
- [ ] **J.25** Revoke `READ_CALENDAR` in Android Settings — skb
  silently empties the system-calendar surface, shows the
  permission-revoked empty state.
- [ ] **J.26** Tablet form factor (`pixel_tablet` AVD) — two-pane
  surface still renders the new chip-strip adornments correctly.

## Phase K — Plan-file close

- [ ] **K.1** Tick every phase header with the landing jj change ID.
- [ ] **K.2** Mark `## Status: ✅ DONE`.
- [ ] **K.3** Cross-reference from `docs/plans/main.md`.
- [ ] **K.4** Update `docs/plans/decisions.md` with the new
  D.88..D.95 entries that map to D-2.18.a..i.
- [ ] **K.5** Update root `CLAUDE.md` "Currently in flight" section.

## Verification (cross-phase)

In addition to the AVD scenarios above:

- Permission flow rendered on both phone and tablet AVDs.
- ICS fixtures captured from real Outlook + Gmail + Apple Mail
  invites parse round-trip.
- Recurring-event tri-choice manually exercised against a
  Google-account recurring event with DAVx⁵ in the loop.
- `Settings → Apps → Default apps → Calendar app` (or analog)
  exposes skb as a candidate.
- `adb shell dumpsys content` shows skb as a registered
  CalendarContract content observer when "Show system
  calendars" is on.
- `adb shell dumpsys account` shows the `com.eight87.strictlykeptboy`
  account type when Phase G is enabled.

## What is intentionally NOT in scope

- **Home-screen month-view calendar widget** — Round 2.19.
- **Auto integration for external calendars** — the existing Phase
  Q car-app service stays repo-only; binding external calendars
  into Auto needs its own design pass.
- **Native CalDAV / EWS / EAS / Google REST / Microsoft Graph
  clients** — D-2.18.c locks this out; DAVx⁵ + stock Exchange
  account cover the surface.
- **iOS / wearOS / desktop parity** — out of scope for this
  Android round.
- **Attendees autocomplete from Android contacts** — defer; the
  read-only attendee view ships here, write isn't gated on it.
- **`.vcs` legacy iCalendar v1 format** — `.ics` only.
- **Calendar SUBSCRIPTION URLs** (subscribe-to-public-`.ics`-feeds)
  — that's an ICSdroid-style separate concern; DAVx⁵ also handles
  it for users who care.
- **Federated identity / Tasks.org tasks-side integration** —
  separate plan when it surfaces.

## References

- [Calendar provider overview](https://developer.android.com/identity/providers/calendar-provider)
- [CalendarContract.Events](https://developer.android.com/reference/android/provider/CalendarContract.Events)
- [CalendarContract.Instances](https://developer.android.com/reference/android/provider/CalendarContract.Instances)
- [Common Intents — Calendar](https://developer.android.com/guide/components/intents-common)
- [AbstractThreadedSyncAdapter](https://developer.android.com/reference/android/content/AbstractThreadedSyncAdapter)
- [Create a sync adapter](https://developer.android.com/training/sync-adapters/creating-sync-adapter)
- [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager) (note: no `ROLE_CALENDAR`)
- [Android roles — AOSP](https://source.android.com/docs/core/permissions/android-roles)
- [DAVx⁵ technical information](https://manual.davx5.com/technical_information.html)
- [DAVx⁵ system integration FAQ](https://www.davx5.com/faq/system-integration)
- [bitfireAT/davx5-ose source](https://github.com/bitfireAT/davx5-ose)
- [Etar Calendar AndroidManifest.xml](https://github.com/Etar-Group/Etar-Calendar/blob/master/app/src/main/AndroidManifest.xml)
- [AOSP packages_apps_calendar manifest](https://github.com/aosp-mirror/platform_packages_apps_calendar/blob/main/AndroidManifest.xml)
- [AOSP CalendarProvider2.java](https://github.com/aosp-mirror/platform_packages_providers_calendarprovider/blob/master/src/com/android/providers/calendar/CalendarProvider2.java)
- [Exchange ActiveSync — Wikipedia](https://en.wikipedia.org/wiki/Exchange_ActiveSync)
- [ICSImport reference app](https://github.com/danielegobbetti/ICSImport)
- [Grokking Android: intents of the calendar app](https://www.grokkingandroid.com/intents-of-androids-calendar-app/)
