# strictlykeptboy — shared schedules + simplified mode

## Status: 🚧 IN-PLANNING

This document is the holistic specification for the Round 3 feature
set: **a recipient receives a complete schedule from someone else via
deep-link or QR, one-tap, without ever having opened a calendar app
before.** Plus the cross-repo state model that makes the flow
conflict-free, plus the simplified ("good boy") mode the recipient
lands in, plus the evolution path from "consumer-only" into "author
of their own schedule" without losing the gifted content.

It corresponds to `main.md` Phases **MM** through **TT** and uses
the phase prefix **SH-**. The locked decisions for this round live
in [`decisions.md`](decisions.md) D.41–D.52 — every section below
elaborates a decision, never re-litigates one. Where this document
touches sibling concerns:

- File schemas for `references.toml` + cross-repo state files
  belong to [`data-model.md`](data-model.md) DM-Q+ (owner: SA-14).
  This doc names the schemas and describes the *user-facing*
  consequences; the field-by-field TOML lives there.
- UI mockups for the receive screen, share sheet, simplified-mode
  chrome, mini-wizard, mode-label picker, and onboarding card live
  in [`ui-spec.md`](ui-spec.md) UI-FF+ (owner: SA-15). This doc
  describes *behavior and flow*; that doc draws the layout.
- Resolver merge logic for state-file overlays, priority
  modifiers, source-repo-id matching, orphan handling, dedup logic
  lives in [`resolver.md`](resolver.md) RV-L+ (owner: SA-16). This
  doc describes what the *user sees*; that doc writes the maths.
- CLI flag surfaces for `skb accept | ref | state | share | mode`
  are summarized here for completeness but will be merged into
  [`cli-tooling.md`](cli-tooling.md) by SA-17 in a follow-up
  pass.

Every unforeseen tradeoff that surfaced during elaboration is
resolved inline as a **Decision:** line. No `TBD`. No punts back
to the user.

**Cross-reference table** — each `SH-` phase ↔ `main.md` Phase
mapping:

| SH phase | main.md phase | Topic |
|---|---|---|
| SH-Scenarios | (driver for MM–TT) | The four worked user-stories |
| SH-A | MM.1, MM.2, MM.3 | Intent filters + Digital Asset Links |
| SH-B | MM.4, MM.5, MM.6, MM.7, MM.8, MM.9 | URL grammar + fragment-only secrets + QR + web fallback + error cases |
| SH-C | NN.1–NN.7 | `references.toml` user-facing flow |
| SH-D | OO.1–OO.7 | Cross-repo state files (UX semantics) |
| SH-E | PP.1–PP.7 | Simplified ("good boy") mode behavior |
| SH-F | QQ.1–QQ.7 | First-launch deep-link bootstrap choreography |
| SH-G | RR.1–RR.8 | Authoring share-this-repo flow + auth methods |
| SH-H | SS.1–SS.7 | Evolution: simplified → own repo |
| SH-I | (folded into RR) | Revocation + token rotation |
| SH-J | (folded into X, deferred to cli-tooling.md) | CLI surface summary |
| SH-K | (cross-cutting) | Edge cases + cross-cutting concerns |

---

# The four worked scenarios

The whole feature is built around four concrete user stories.
Every later technical choice traces back to one of these. They are
written from both the **recipient's** perspective (the person who's
never used a calendar app before) and the **author's** perspective
(the person sending the schedule). Read these first; the rest of
the document is "how we make each step work."

## Scenario 1 — Dom shares a kink-coded schedule with sub

### Author side (Dom)

Dom already has the app, has an own repo `dom-schedule` on GitHub
(private), and has authored a schedule that mixes Dom's own
calendar with content explicitly targeted at sub — workouts,
grooming reminders, check-ins, weekly assignments. Dom is one of
the project's named target users; the register is intentional,
the data is private.

1. Dom opens the app. Top-bar repo switcher already on
   `dom-schedule`. Dom navigates **Settings → Repos → tap
   `dom-schedule` → Share this repo**.
2. The share-config sheet opens (SH-G covers the layout). Dom
   fills:
   - **Mode**: `read-only` (sub never pushes back).
   - **Suggested label**: `"Schedule from Master"`.
   - **Suggested priority modifier**: `high` (Dom's content should
     win foreground when it overlaps with sub's own content
     later).
   - **Auth method**: `Embed one-shot deploy key`. Rationale:
     sub has never used the app, sub doesn't have a GitHub
     account, sub will not paste an SSH key into anyone's
     collaborator settings. A 24h-expiry deploy-key removes the
     entire auth-onboarding burden.
   - **Expires**: default 24 hours from now.
   - **Output**: tap "Share via..." — system share-sheet opens.
3. While the sheet is still building, the app calls the GitHub
   REST API `POST /repos/{owner}/{repo}/keys` with `read_only =
   true` and a public key generated client-side from a fresh
   ed25519 keypair. (See SH-G for why client-side generation.)
   The provider returns a `key_id`; the app records it in
   `.strictlykeptboy/shared-links.toml` for revocation tracking.
4. The app assembles the URL — query params plain, secret in
   fragment — and posts it via Signal to sub.

A concrete sample URL produced in this scenario:

```
https://strictlykeptboy.app/add
  ?url=git%40github.com%3Adom%2Fdom-schedule.git
  &label=Schedule+from+Master
  &mode=read-only
  &priority=high
  &via=Master
  &references=prompt
  #token=<base64-armored-ed25519-private-key>
  &expires=2026-05-12T22%3A00%3A00Z
  &v=1
```

(Linebreaks inserted for readability; the real URL is on one
line. Note the `#` separating the fragment from the query
string.)

### Recipient side (sub)

5. sub receives the link in Signal. sub has never opened the
   app — sub has never opened a calendar app, period. sub taps
   the link.
6. Android shows the **Open with** chooser; strictlykeptboy is
   not installed yet, so the browser opens
   `https://strictlykeptboy.app/add`. The web fallback page
   (spec-only — actual page is out of scope; see SH-B) detects
   the user-agent is Android, encodes the **entire URL
   including fragment** into a Custom-Tabs handoff payload,
   and shows "Install strictlykeptboy → tap Open after install
   to receive your schedule." The fragment is preserved
   client-side in `sessionStorage`; the server never sees it
   (the browser doesn't transmit fragments on navigation, and
   the page never POSTs the fragment anywhere).
7. sub taps "Install via Play Store" (or "Install via
   Obtainium" — both buttons present). Play Store installs
   the app. sub returns to the web page (still in the same
   tab); the page detects the app is installed (best effort
   via `getInstalledRelatedApps()` where available, otherwise
   a prominent "Open in app" button) and re-fires the deep-
   link with fragment intact. The app's intent filter for
   `https://strictlykeptboy.app/add` catches it.
8. **First-launch deep-link bootstrap (SH-F) kicks in**: app
   sees "no repos configured AND launched via deep-link" →
   skips the welcome wizard entirely → goes straight to the
   "Add gifted repo" screen. URL prefilled, label prefilled
   ("Schedule from Master"), mode badge says **Read-only**,
   the `via=Master` chip shows under the title.
9. sub taps **Accept**. The app:
   - Parses the fragment to extract the ed25519 private key.
   - Stores the key in `EncryptedSharedPreferences`, keyed by
     the source-repo-id (D.51).
   - Wipes the `#token=` portion of any cached referrer.
   - Spawns a JGit `cloneRepository` call with the in-memory
     credential. Progress UI: animated mascot + "Cloning your
     schedule..." with byte counter.
   - On success, the repo lands at
     `~/.strictlykeptboy/repos/<source-repo-id>/`.
10. The app reads the cloned repo's `.strictlykeptboy/
    references.toml`. If Dom listed other repos there (e.g.
    `dom-resources` with grooming product info) with
    `default_active = true`, the app prompts "This schedule
    references 1 other repo — add it too?" with a per-item
    toggle. Defaults match each reference's `default_active`.
    sub taps **Add all**.
11. The app boots into **simplified mode** (auto-detected:
    only read-only repos, no own repo). Schedule view, today
    pre-selected. sub sees:
    - 07:00 — Morning grooming (10 min)
    - 12:00 — Mid-day check-in
    - 18:00 — Gym (60 min, "lift heavy")
    - 21:00 — Evening check-in + bedtime routine
    Each entry has a small author chip (Master's avatar).
12. A one-time onboarding card overlays the top of the
    schedule (SH-F): *"Welcome to your schedule. Master set
    this up for you. Tap any event for details. Mark tasks
    done by tapping their circle. Need help? Tap here."* sub
    dismisses.
13. sub taps the **Gym** task's checkbox. The app writes a
    state file:
    `~/.strictlykeptboy/local-state/<source-repo-id>/<entity-
    id>.done.toml` (sub has no own repo yet, so the state
    lives in `_local/state/` per SH-D / D.44). The state file
    holds `done_at = "2026-05-11T19:05:00+02:00"` and an
    empty body. No commit goes to Dom's repo. The view
    re-renders: the gym task gets a struck-through label and
    a checkmark.

### Days later (the evolution moment)

14. sub wants to add a personal event — "dinner with friend
    Sat 19:00." sub taps the FAB. The FAB in simplified mode
    is a single primary action that, when no own repo exists,
    routes to **Add my own events** (SH-H).
15. Mini-wizard runs: 3 screens (provider, repo name, auth).
    sub picks GitHub, accepts the suggested `sub-schedule`
    repo name, runs through GitHub OAuth Device Flow (D.7),
    confirms.
16. The app creates the repo on GitHub (via API), initializes
    it locally with the standard `.strictlykeptboy/` skeleton,
    migrates `~/.strictlykeptboy/local-state/*` into
    `<new-repo>/state/`, writes a `references.toml` listing
    `dom-schedule` (priority modifier preserved as `high`,
    mode as `read-only`), commits, pushes.
17. The app prompts "**Stay in simple mode or switch to full?**"
    sub stays in simple. Now the FAB creates events in
    `sub-schedule` directly. sub adds the dinner event.
    Dom's content remains overlaid; sub's content is authored
    locally.

### Net result

- sub never saw the wizard.
- sub never picked a template.
- sub never authored anything until they explicitly chose to.
- Dom never had write access to sub's repo.
- sub never had write access to Dom's repo.
- All of sub's interactions (the gym checkmark, the dinner
  event) live in sub's repo; Dom's repo is untouched.
- Zero merge conflicts anywhere.

## Scenario 2 — Personal trainer ships a 12-week strength program

### Author side (the coach)

1. The coach has authored a `coach-strength-2026` repo. The
   repo is **public** on GitHub — the program is content the
   coach distributes broadly, no per-client gating.
2. Repo contains: one calendar `Strength 12W`, daily workouts
   as recurring events keyed by week × day, warm-up checklists
   as standing tasks, a `Mobility` calendar overlaid as a
   secondary track.
3. The coach is meeting a new client in person. The coach
   opens the app, **Settings → Repos → tap
   `coach-strength-2026` → Share this repo**.
4. Share-config sheet:
   - Mode: `read-only`.
   - Label: `"12-Week Strength Program"`.
   - Priority modifier: `normal` (this is *a* schedule among
     others the client has).
   - Auth method: **Public repo, no auth needed** (the
     repo is public, no token needed in the link).
   - Output: tap **Save QR** → app generates a PNG QR code
     via ZXing, saves to `Pictures/strictlykeptboy/`.
5. The coach pulls up the QR on screen.

Sample URL encoded by the QR:

```
https://strictlykeptboy.app/add
  ?url=https%3A%2F%2Fgithub.com%2Fcoach%2Fcoach-strength-2026.git
  &label=12-Week+Strength+Program
  &mode=read-only
  &priority=normal
  &via=Coach
  &v=1
```

No fragment. No expiry. The link is durable; the coach
shares the same QR with every new client.

### Recipient side (client)

6. The client, sitting across from the coach, points their
   phone camera at the QR. Android's camera app detects the
   URL and offers "Open `strictlykeptboy.app/add`."
7. App not installed. Same Custom-Tabs handoff as Scenario 1
   (SH-B). Client installs, returns, link re-fires.
8. First-launch bootstrap: skip wizard → Add-gifted-repo
   screen with URL prefilled, label `"12-Week Strength
   Program"`, mode badge `Read-only`. Client taps **Accept**.
9. App clones over HTTPS anonymously — no credential
   needed for a public repo. Cloned repo lands at
   `~/.strictlykeptboy/repos/<source-repo-id>/`.
10. No `references.toml` in this repo → no references
    prompt.
11. App boots into simplified mode. Day view. Today's
    workout shows: **Bench 5×5 — 18:00**, plus a mobility
    warmup task. Author chip on each event = coach's avatar.
12. Onboarding card: *"Welcome to your schedule. Coach set
    this up for you. Tap any workout for details."*
13. Six weeks later the client wants to take notes on a
    workout ("hit a PR on bench today, 102.5kg"). Client
    taps the event → detail sheet → comment composer
    visible per D.45. **Decision: comment composer is one
    of the few advanced surfaces always visible in simplified
    mode**, because giving a recipient a place to record
    their own reactions is a load-bearing primitive of the
    app's value. The client posts the note; it becomes a
    state file with `state_kind = "note"` in
    `_local/state/<source-id>/...`.

## Scenario 3 — Soccer club sends a season calendar to 30 members

### Author side (the club admin)

1. The club admin authored `our-soccer-club-2026-season`,
   public on Forgejo (self-hosted). One calendar `Season
   Schedule` with match dates + training sessions as
   recurring events.
2. Admin posts a link in the club's WhatsApp group. The
   link is a plain text URL (no QR needed; WhatsApp renders
   URLs as taps).
3. Many members already have strictlykeptboy installed —
   some use it for school, some for personal scheduling.
   The link works the same for everyone, installed or not.

Sample URL:

```
https://strictlykeptboy.app/add
  ?url=https%3A%2F%2Fforge.our-soccer-club.org%2Fadmin%2F2026-season.git
  &label=Soccer+2026+Season
  &mode=pull-only
  &priority=normal
  &via=Coach+Admin
  &v=1
```

`mode=pull-only` is the new bit: the season changes — match
times rescheduled by the league, weather cancellations —
are managed by the club. Members get the updates; they
never push.

### Recipient side (a member who already uses the app)

4. Member taps the WhatsApp link. Android routes to
   strictlykeptboy directly (because the App Links
   verification has already passed on this device).
5. App **is** installed and **has** repos configured. So
   first-launch bootstrap (SH-F) does NOT trigger. Instead,
   the in-app **Add gifted repo** screen opens overlaid on
   whatever view the member was in.
6. Member sees: "Add `Soccer 2026 Season`? — mode:
   pull-only, priority: normal, from Coach Admin." Per-
   reference detail visible if they expand. Member taps
   **Accept**.
7. Clone over HTTPS, anonymous. Repo lands in their repo
   list alongside existing repos. **All-repos overlay view**
   (D.9) now includes the season schedule color-bordered
   by the soccer-club's icon (auto-generated initials
   circle: "S2").
8. The member is **not** auto-switched to simplified mode
   because they're not a simplified-mode user; they have
   their own primary repo. They stay in full mode.

### Recipient side (a member who does NOT have the app)

9. Same as Scenarios 1+2: web fallback → install → re-fire
   → first-launch bootstrap → simplified mode → schedule
   view, today.

## Scenario 4 — School admin distributes term timetables

1. School admin authored `westside-high-spring-2026`,
   public on the school district's GitHub Enterprise
   instance.
2. Admin embeds the deep-link in the school's parent-portal
   PDF and in the email newsletter.
3. Student (age 14, has never used a calendar app — has
   used text messaging only) taps the link in the email
   on their phone. Web fallback → install → re-fire →
   first-launch bootstrap → simplified mode.
4. Onboarding card: *"Welcome to your schedule. Westside
   High set this up for you. Tap any class for details."*
5. The student's parent does the same. The parent already
   has the app installed for their own work calendar.
   Parent gets the in-app Add-gifted-repo screen → adds
   the term timetable as a pull-only overlay onto their
   own schedule, so they can see when the kid's school
   day is.

### Why these four

These four use cases span the matrix:

| | Author | Recipient | Auth | Mode | Receiver-has-app? |
|---|---|---|---|---|---|
| 1 | Dom | sub | embedded deploy key (private) | read-only | no |
| 2 | Coach | client | none (public) | read-only | no |
| 3 | Club | members | none (public) | pull-only | mixed |
| 4 | School | students+parents | none (public) | pull-only | mixed |

The whole protocol must cleanly handle each row. Every
section below references which row(s) it serves.

---

## Phase SH-A — custom-scheme + universal-link intent filters

Goal: Android dispatches both `strictlykeptboy://add?...` and
`https://strictlykeptboy.app/add?...` to the app's deep-link
receiver activity, with verified App Links for the HTTPS form.
Implements `main.md` MM.1–MM.3.

- [ ] **SH-A.1** Add `<intent-filter>` to the activity's
  manifest entry declaring `android:scheme="strictlykeptboy"`
  and `android:host="add"` with the `BROWSABLE` + `DEFAULT`
  + `VIEW` categories. The custom-scheme form is the always-
  works fallback when App Links verification hasn't completed
  on a device (e.g. the user installed the app via APK
  before the asset-links JSON propagated).
- [ ] **SH-A.2** Add a second `<intent-filter>` for
  `android:scheme="https"`, `android:host="strictlykeptboy.app"`,
  `android:pathPrefix="/add"`, with
  `android:autoVerify="true"`. This is the App Links form;
  if verification passes, Android stops showing the
  app-chooser sheet and goes straight to the app.
- [ ] **SH-A.3** Publish the Digital Asset Links JSON at
  `https://strictlykeptboy.app/.well-known/assetlinks.json`
  with the production SHA-256 cert fingerprint. The asset-
  links file is mandatory for App Links verification (Android
  fetches it on first install + periodically refreshes).
  Content shape per Google's documented schema:

  ```json
  [{
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.eight87.strictlykeptboy",
      "sha256_cert_fingerprints": [
        "AB:CD:EF:01:23:..."
      ]
    }
  }]
  ```

  **Decision: include both the **release** signing-key
  fingerprint and the **CI debug** signing-key fingerprint in
  the live JSON**. Debug-built APKs (used by Obtainium beta
  testers and internal QA) need App Links verification to
  cover the same surface as release builds, otherwise the
  beta experience degrades to "always shows the chooser"
  which is exactly the friction we're eliminating.
- [ ] **SH-A.4** Test matrix: app launched via (a)
  `strictlykeptboy://add?...` from a `am start` test, (b)
  HTTPS URL with App Links verified, (c) HTTPS URL with App
  Links not yet verified (user picks app from chooser), (d)
  HTTPS URL with the app not installed (browser opens
  fallback page). Cases (a)+(b)+(c) all converge on the
  same `DeepLinkReceiverActivity`; case (d) is SH-B's
  web fallback.
- [ ] **SH-A.5** `DeepLinkReceiverActivity` is a transparent
  activity with `singleTop` launch mode. On `onCreate`, it
  parses the intent's `data` URI, validates against the URL
  grammar (SH-B), and either (i) hands off to the
  add-gifted-repo flow inside the running app, or (ii)
  triggers first-launch bootstrap (SH-F) if no repos are
  configured.
- [ ] **SH-A.6** Manifest also declares `android:exported="true"`
  on the receiver activity (required for cross-app deep-
  link delivery) plus an `<meta-data>` element flagging
  this as a "shortcut target" for Android's app-shortcuts
  surface (so long-press on the app icon offers "Scan QR"
  as a quick action).

## Phase SH-B — URL grammar, fragment-only secrets, QR, web fallback, errors

Goal: nail down the wire format end-to-end. Implements
`main.md` MM.4–MM.9.

### SH-B.1 URL grammar

- [ ] **SH-B.1** Define the formal grammar. Both
  `strictlykeptboy://add?...` and `https://strictlykeptboy.app/add?...`
  share the same query-and-fragment shape. Grammar (ABNF-
  style):

  ```
  share-url    = scheme "://" host "/add?" query [ "#" fragment ]
  scheme       = "strictlykeptboy" / "https"
  host         = "add" / "strictlykeptboy.app"
  query        = qparam *( "&" qparam )
  qparam       = "url=" url-value
               / "label=" pct-encoded-text
               / "mode=" mode-value
               / "priority=" priority-value
               / "via=" pct-encoded-text
               / "references=" reference-policy
               / "v=" integer
  url-value    = pct-encoded git-clone-url
  mode-value   = "read-only" / "read-write" / "pull-only"
  priority-value = "high" / "normal" / "low"
  reference-policy = "auto" / "prompt" / "ignore"
  fragment     = fparam *( "&" fparam )
  fparam       = "token=" pct-encoded-token
               / "expires=" iso8601-timestamp
  ```

  **Decision: `v=1` is always present.** Future schema
  bumps to the link grammar are protocol-version-gated.
  An older app receiving `v=2` shows a "this link uses a
  newer link format; update strictlykeptboy first" error
  with a Play Store link — same shape as the schema-too-
  new repo-level check (D.20).

### SH-B.2 Query parameters

- [ ] **SH-B.2** Semantics per parameter, exhaustively:
  - `url` (**required**, repeatable). The git clone URL.
    SSH form `git@host:owner/repo.git` and HTTPS form
    `https://host/owner/repo.git` both accepted. The repo
    transport is inferred from URL shape.
  - `label` (optional, repeatable). Positional: the *i*th
    `label` matches the *i*th `url`. If fewer labels than
    URLs, missing labels default to the repo's
    `repo.toml` display name (post-clone read) or the
    last path segment as fallback.
  - `mode` (optional, default `read-only`). `read-only`
    means the app refuses pushes regardless of credential
    capability. `read-write` is rare — only used when the
    author actually means to give push access (and the
    embedded token has push scope). `pull-only` is a
    CalDAV-mirror style: the app pulls on a faster cadence
    (default 1h instead of 15min) and never offers an
    edit affordance on those entities.
  - `priority` (optional, default `normal`). Applies the
    same modifier to every calendar in the imported repo
    via the `references.toml` derived entry (or the in-app
    reference record if no `references.toml`). Maps to D.49:
    `high` = +200, `normal` = 0, `low` = -200.
  - `via` (optional). Short author-attribution string
    shown on the receive screen and in the onboarding
    card. Max 64 chars after percent-decoding. Free-form;
    no formatting interpretation.
  - `references` (optional, default `prompt`). Controls
    what the app does when the cloned repo's
    `references.toml` lists *additional* repos:
    `auto` adds them silently (only safe when the receiver
    has pre-trust); `prompt` shows the per-reference
    toggle sheet (the default); `ignore` does not even
    show the sheet (the gifted repo's `references` are
    treated as informational only). **Decision: default
    `prompt` because the recipient should always see what
    they're accepting**, even for trusted authors —
    silently chaining additional clones is the kind of
    invisible move that erodes trust later.
  - `v` (required). Protocol version integer.

### SH-B.3 Fragment-only secrets

- [ ] **SH-B.3** **Decision: every secret-bearing parameter
  lives in the URL fragment, never in the query string**.
  Rationale (load-bearing — this is the security-critical
  decision of the round):
  - Browsers, web servers, web analytics, and access logs
    routinely capture query strings. Fragments are
    client-side-only by HTTP design — they are never
    transmitted in a request.
  - Android's `Intent.getData()` exposes fragments
    untouched, so the app can read them.
  - The custom-scheme variant `strictlykeptboy://add?...`
    also uses the fragment for symmetry — even though
    custom-scheme URLs never traverse HTTP, keeping the
    grammar identical means the same parser handles both.
  Fragment parameters:
  - `token` — a base64-armored payload. For deploy-key
    auth, the payload is the ed25519 private key in
    OpenSSH format. For PAT auth, the payload is the
    token string. For "BYO-SSH" / public, no `token`.
  - `expires` — ISO-8601 timestamp. After this instant,
    the app refuses to use the link.

### SH-B.4 Multi-URL support

- [ ] **SH-B.4** A single link may carry multiple `url=`
  parameters (a "bundle"). Use case: a coach giving a
  client three programs (strength + mobility + cardio) in
  one tap; a school sending term + extracurriculars + bus
  schedule in one tap.

  Bundles are processed sequentially on the receive side
  with a multi-item confirmation sheet ("Add these 3
  schedules?"). Each item shows the per-position label,
  mode, etc. Reasonable maximum: 8 URLs per bundle —
  enforced client-side at the receive screen as "this
  link is too large; ask sender to split into smaller
  links" with the soft cap visible (so a sender who
  legitimately needs more retries with a CLI flag).

  **Decision: priority/mode/references/via parameters
  apply globally to every URL in the bundle, not per-
  position**. Per-position label is enough; per-position
  mode/priority would 5x the URL length and 10x the
  parser complexity. Sender who needs heterogeneous
  bundles generates multiple single-URL links.

### SH-B.5 Token wipe

- [ ] **SH-B.5** Once the deep-link is consumed:
  - The token-bearing fragment is **never persisted** to
    `EncryptedSharedPreferences` raw — only the *parsed*
    private key blob is stored, under the source-repo-id
    key.
  - Any in-memory referer / "last accepted link"
    diagnostic field strips `#token=` and replaces with
    `#token=<redacted>`.
  - The Android system clipboard is NOT read for tokens
    — links arrive via Intent, period. If a user pastes
    a link into "Add gifted repo" by hand, the app does
    parse the fragment, but does not retain the
    clipboard or write it back.
  - Logcat output (debug builds) redacts `#token=*` to
    `#token=<len=N>`.

### SH-B.6 Expiry enforcement

- [ ] **SH-B.6** On parsing a deep-link, if `expires` is
  present and `now > expires`, the receive screen shows
  an inline error:
  *"This share link expired on <date> at <time>. Ask the
  sender for a fresh link."*
  No clone is attempted. No credential is stored. The
  user can dismiss back to wherever they came from.

  Clock-skew tolerance: 5 minutes grace on both sides
  (i.e. if `now` is within `expires + 5min`, the app
  accepts and logs a warning). Beyond 5min skew, treat
  as expired. **Decision: 5 minutes is generous enough
  for ordinary device-clock drift but small enough that
  a meaningfully-expired link doesn't get a free pass.**
  If the device clock is more than 5min off real time
  AND the link is freshly issued, the app surfaces a
  separate "Your device clock seems wrong" warning
  before refusing — see SH-K.

### SH-B.7 QR codes

- [ ] **SH-B.7** QR codes encode the same URL string.
  **Decision: use ZXing for both encoding (share-side) and
  decoding (receive-side, via the system camera intent +
  app-side image decode for QR-saved-to-gallery)**. Round
  2 D.40 deferred QR for the SSH-pubkey-export use case
  ("marginal value over clipboard/share; zxing dep
  cost"); we ship ZXing here because the link-receive
  flow is the load-bearing use case of Round 3 and QR is
  how the in-person handoff (Scenario 2) works.

  Encoding:
  - QR error-correction level: `M` (15% redundancy).
    Sufficient for printed flyers; not so high that the
    matrix becomes unreadable on small phone screens.
  - PNG output, 512×512px, with a 32px white quiet zone.
  - Filename pattern: `Pictures/strictlykeptboy/share-
    <repo-name>-<yyyy-mm-dd>.png`.

  Decoding:
  - Camera path: the user uses Android's built-in
    camera + QR scanner; the scanner extracts the URL
    and routes via the normal Intent flow.
  - Gallery path: `skb accept --qr <path>` and the
    in-app "Scan QR from gallery" entry both decode a
    saved PNG using ZXing's `MultiFormatReader`.

### SH-B.8 Web fallback

- [ ] **SH-B.8** The page at `https://strictlykeptboy.app/add`
  is owned by the project's existing site (out of scope to
  build here; the spec is what the page must do):

  - Static HTML/JS hosted from the same domain as the
    asset-links JSON. No server-side processing of the
    URL — the page reads `location.search` and
    `location.hash` client-side.
  - On Android user-agent: show a prominent "Install
    strictlykeptboy" CTA with Play Store + GitHub-
    Releases (Obtainium) links. Below it: "Already
    installed? Open in app" button which triggers a
    second navigation to the same URL, now that the
    intent-filter will catch it.
  - Persist the entire URL (including fragment) in
    `sessionStorage` keyed by a short tag. After
    install, if the user returns to the same tab
    (Custom Tabs behavior), the page reads
    `sessionStorage` and offers "Open your schedule"
    that re-fires the deep-link with the fragment
    intact. **The fragment is never POSTed, never
    GETted via XHR, never sent to any server.**
  - On non-Android user-agent: show "strictlykeptboy is
    Android-only in v1" with a brief explanation and
    the calendar-author's `via` attribution (so a
    desktop recipient at least sees who sent it).
  - No analytics, no tracking, no fingerprinting. The
    page exists purely as a fallback target so the URL
    isn't a dead end.

### SH-B.9 Error cases

- [ ] **SH-B.9** Every possible failure on receive has a
  specific user-facing message and a recovery path:

  | Error | Message | Recovery |
  |---|---|---|
  | Malformed URL (missing `url`) | "This share link is missing the schedule address. Ask the sender for a fresh link." | Dismiss; offer "Paste another link" |
  | Malformed URL (unknown `mode` value) | "This share link uses a setting this version doesn't recognize. Update strictlykeptboy first." | Play Store link |
  | `v=` newer than supported | "This link uses a newer link format. Update strictlykeptboy first." | Play Store link |
  | Expired token | "This share link expired on <date>. Ask the sender for a fresh link." | Dismiss |
  | Network down at clone | "No network connection. Your link is saved — we'll retry when you're back online." | Auto-retry on connectivity callback (D.8); link held in encrypted-prefs pending-bootstrap slot |
  | Auth failed (private repo, no token, BYO-SSH path) | "This is a private schedule. You need to: (1) get an account on <host>, (2) add the key shown below as a collaborator, (3) come back here." | Show the user's SSH pubkey, copy button, "Try again" entry |
  | Auth failed (private repo, token rejected) | "The access token in this link no longer works — the sender may have revoked it. Ask for a fresh link." | Dismiss; offer "Paste another link" |
  | Repo not found (404) | "The schedule address in this link doesn't exist. Maybe the sender mistyped it?" | Dismiss; show the URL plain for copy/edit |
  | Repo not readable (403 from a token that should have worked) | "The access token works but doesn't have permission for this repo. Ask the sender to regenerate." | Dismiss |
  | Schema version too new in cloned repo | "This schedule was authored with a newer version of strictlykeptboy. Update first." | Play Store link |
  | Clock-skew warning | "Your device clock seems wrong. Trust the device clock anyway? <yes/no>" | Yes → proceed; No → dismiss |
  | Already-configured repo | "You already have this schedule (added on <date>). Refresh credentials?" | Yes → replace stored token; No → dismiss |
  | Bundle too large (>8 URLs) | "This link bundles too many schedules. Ask the sender to split into smaller links." | Dismiss |
  | Circular reference detected | "Adding this schedule would create a loop. Skipping reference X." | Continue without the looping ref |

  **Decision: every error is recoverable — the user is never
  left on a dead-end screen.** At minimum, "Dismiss" returns
  them to either the empty-state of a fresh app or the
  schedule view if they had one.

---

## Phase SH-C — `references.toml` integration (user-facing flow)

Goal: the user-visible behavior of the reference-manifest
feature. Schema details (the actual TOML fields) live in
`data-model.md` DM-Q (SA-14 owns). Resolver algorithms for
priority + dedup live in `resolver.md` RV-L+ (SA-16 owns).
Implements `main.md` NN.1–NN.7.

### SH-C.1 When the app sees a `references.toml`

- [ ] **SH-C.1** Every time the app **adds** a repo (whether
  via deep-link, in-app add-repo screen, or `skb accept`),
  after the initial clone succeeds, the app reads
  `.strictlykeptboy/references.toml` if present. Each
  `[[reference]]` entry surfaces in the UI as a row in the
  "**This schedule references other schedules**" sheet.

  The sheet is shown when at least one referenced repo is
  not already configured AND the `references=` deep-link
  parameter (SH-B.2) is `prompt` (default) or absent. If
  `references=ignore` it's not shown; if `references=auto`
  the additions happen silently with a "Added 3 referenced
  schedules" toast.

### SH-C.2 The per-reference toggle sheet

- [ ] **SH-C.2** Layout per row (UI mockup belongs in
  `ui-spec.md` UI-GG; the behavior is spec'd here):
  - Suggested label (`label` field).
  - Mode badge (`read-only` / `read-write` / `pull-only`).
  - Priority modifier chip (`high` / `normal` / `low`).
  - Source URL (small, monospace, truncated).
  - Description (`description` field) underneath, italic.
  - Toggle on the right — initial state = `default_active`
    field, default `true`.
  - Tap the row → expand to show the credential-hint and
    a "Skip" / "Add later" affordance.

  Bottom action bar: **Add selected** + **Skip all**.

### SH-C.3 Auto-dedup

- [ ] **SH-C.3** For each row, before rendering, the app
  computes the source-repo-id (SH-D.2 / D.51) from the URL
  and checks against currently-configured repos. If a
  match: the row renders **disabled** with a "Already
  added" pill on the right. Tapping the row offers
  "Refresh credentials" only.

  Dedup is by source-repo-id (URL normalization),
  **not** by raw URL. This means the SSH form and HTTPS
  form of the same repo dedup correctly — a receiver who
  has the repo configured via SSH won't re-add it via
  HTTPS from a reference.

### SH-C.4 Circular-reference detection

- [ ] **SH-C.4** Before adding a referenced repo, the app
  evaluates: "if I add this, does the resulting reference
  graph contain a cycle?" Algorithm:
  - Build a directed graph of all currently-configured
    repos: node = source-repo-id, edge from A → B if
    A's `references.toml` lists B.
  - For each candidate addition, simulate the resulting
    graph.
  - Run Tarjan's strongly-connected-components on the
    graph. Any SCC of size > 1 is a cycle.
  - If a cycle would form: refuse the addition with a
    user-visible message **"Adding `<X>` would create a
    loop with `<Y>`. Skipping."** and continue with the
    other selections.

  **Decision: cycle detection is per-add, not lazy at
  render time.** If we let cycles in and resolved them at
  render time we'd be defining "what the loop means" —
  which calendar wins, what `references` chain is followed
  — and that's a rabbit hole. Refusing the add is the
  simplest correct behavior.

### SH-C.5 `required = true` semantics

- [ ] **SH-C.5** If a reference has `required = true` and
  it's not currently configured, the *referencing* repo
  shows a warning banner in repo settings: **"This
  schedule expects a related schedule `<label>` which
  isn't added. Tap to add now."** Tapping opens the
  add-gifted-repo screen prefilled.

  Required references do NOT block clone or render of
  the referencing repo — the banner is informational.
  **Decision: required is a *strong suggestion*, not a
  hard prerequisite**, because forcing the user into
  add-second-repo flow before showing them any content
  is the kind of friction the whole feature exists to
  remove. The banner persists until either the ref is
  added or the user explicitly dismisses ("Don't show
  again for this repo").

### SH-C.6 Credential hints

- [ ] **SH-C.6** A reference may declare a
  `credential_hint = "ssh-key:fingerprint:abc"` (or
  `oauth:host:github.com` etc.). This is **not** a
  credential — it's a hint that helps the receiver's app
  match against keys they've already configured. Behavior:
  - If the hint is `ssh-key:fingerprint:<fp>` AND the
    receiver has an SSH key with that fingerprint stored,
    the receiver's app pre-selects that key for the
    referenced repo's auth.
  - If the hint is `oauth:host:<host>` AND the receiver
    has an OAuth token for that host stored, the receiver's
    app pre-selects that token.
  - Otherwise the hint is ignored.

  **Decision: hints are opportunistic, never required.**
  A missing or wrong hint never blocks the add — the
  receiver always has the option to authenticate via the
  standard flow. The hint exists to make "this repo I
  already trust references another repo on the same
  provider where I already have credentials" be a
  zero-friction add.

### SH-C.7 Removal semantics

- [ ] **SH-C.7** If a referencing repo is **updated**
  upstream and a reference is removed from its
  `references.toml`, the app does NOT auto-uninstall the
  referenced repo from the receiver's device. Instead, on
  next pull, the app surfaces "**`<X>`'s schedule no
  longer references `<Y>` — keep or remove?**" in a
  notification.

  **Decision: never auto-uninstall**. The receiver may
  have built up state files (notes, comments, done-marks)
  on the referenced repo's entities; silently removing
  the repo would orphan that state and surprise the
  receiver. Explicit user action is required.

---

## Phase SH-D — cross-repo state files (user-facing semantics)

Goal: the user-visible semantics of `state/<source-repo-id>/
<entity-id>.<kind>.toml`. Schema details belong in
`data-model.md` DM-R (SA-14 owns). Resolver merge algorithm
belongs in `resolver.md` RV-L (SA-16 owns). Implements
`main.md` OO.1–OO.7.

### SH-D.1 The core principle

- [ ] **SH-D.1** **Decision (this is the round's load-bearing
  insight): your interactions live in YOUR repo; the source
  repos are author-only.** Every "I'm done with this," "I
  want to note something about this," "mute this," "hide
  this," "I assign higher priority to this on my device"
  is a file written to the **receiver's own repo**, never
  to the source. This means:
  - Source repos stay append-only-by-author. Zero merge
    conflicts at the source-repo level.
  - Receivers can never accidentally push to a source repo
    (the read-only flag at app level enforces this
    regardless of credential capability).
  - Authors don't see "the sub marked workout-Monday as
    done" leaking into their repo log unless explicitly
    arranged via a separate write-back mechanism (out of
    scope for v1).
  - Every receiver's interactions are private to them.

### SH-D.2 Where the state files live

- [ ] **SH-D.2** Two cases:
  - **Receiver has an own repo** (created via the wizard
    OR via the SS evolution path): state files live at
    `<own-repo>/state/<source-repo-id>/<entity-id>.<kind>.toml`.
    Git-tracked. Sync to remote alongside the receiver's
    other content.
  - **Receiver has no own repo yet** (simplified-mode
    pure recipient): state files live at
    `~/.strictlykeptboy/local-state/<source-repo-id>/...`
    on the device. NOT git-tracked. Migrated into the
    new own-repo on SS.4 when the user later creates
    their own repo.

  **Decision: `_local/state/` exists on disk outside any
  git working tree.** Cleaner than a hidden uncommitted
  tree in some "placeholder repo" — if the receiver
  decides not to upgrade to an own-repo, the state is
  still on their device and the schedule view still
  reflects "done" / "hidden" marks.

### SH-D.3 UI surface for state actions

- [ ] **SH-D.3** Every state-kind has a one-tap or
  near-one-tap entry in the schedule and task views. UI
  layout belongs in `ui-spec.md`; behavior here:

  | State kind | Trigger | Result |
  |---|---|---|
  | `done` (task) | Tap the task's checkbox | Strikethrough + checkmark; writes `<id>.done.toml` |
  | `done` (event) | Long-press event → "Mark complete" | Visually-completed style; writes `<id>.done.toml` |
  | `snooze` | Swipe-left on event/task → snooze menu (5m / 1h / tomorrow / pick) | Notification rescheduled; writes `<id>.snooze.toml` |
  | `note` | Event/task detail sheet → "Add note" inline editor | Writes `<id>.note.toml` with body |
  | `reaction` | Event detail sheet → emoji picker | Writes `<id>.reaction.toml`; if source repo allows comments AND receiver has push access there, optionally surfaces "post as comment too?" |
  | `priority-override` | Settings → Repos → tap source repo → tap calendar → "Boost / lower priority" | Writes a per-calendar override file (different shape: keyed by calendar-id, not entity-id) |
  | `mute` | Event detail sheet → bell-with-slash icon | Notifications for this entity suppressed; writes `<id>.mute.toml` |
  | `hide` | Long-press event → "Hide from my view" | Entity not rendered in any view; writes `<id>.hide.toml`. Reversible from Settings → Repos → "Hidden entities" |

  Every action shows a brief toast with **Undo** for 3s.
  Undo deletes the state file (atomically; commits the
  deletion if the receiver has an own-repo).

### SH-D.4 `_local/state/` migration

- [ ] **SH-D.4** When the receiver creates their own repo
  via SS.4:
  1. Scan `~/.strictlykeptboy/local-state/` recursively
     for all state files.
  2. For each file, compute the target path inside the
     new repo: `<new-repo>/state/<source-repo-id>/<rest>`.
  3. Copy (not move) each file. The originals stay in
     `_local/state/` for 7 days as a safety net.
  4. Commit + push the migration as a single commit:
     `migrate cross-repo state from _local`.
  5. After 7 days, prune `_local/state/` entries that
     have a counterpart in the new repo.

  **Decision: copy-with-7-day-safety-net rather than
  move.** The migration commit could fail at push (no
  network, auth issue, etc.); keeping the originals
  means an interrupted migration is recoverable.

### SH-D.5 Orphan state files

- [ ] **SH-D.5** When a source repo is pulled and an
  entity that has a state file is no longer present
  (event deleted upstream, recurrence canceled), the
  state file is **not auto-deleted**. Instead:
  1. Mark the state file as orphaned: write a sibling
     `<id>.<kind>.toml.orphan` marker with the deletion
     timestamp.
  2. Surface in a "Broken state references" tray in
     Settings → Repos → tap source repo → "State for
     deleted entities" — list of orphaned state files
     with author + body for each.
  3. After **30 days**, auto-prune orphan files (delete
     both the state file and the `.orphan` marker) with
     a single commit `prune orphaned state (deleted >30d)`.
  4. If the receiver explicitly visits the broken-state
     tray and taps "Restore" → the state file is brought
     back, but since the source entity is gone, "restore"
     means "rebind to a different entity" (a future-pass
     feature; v1 only offers "Delete now").

  **Decision: 30 days is the auto-prune window**, balancing
  "the receiver might want their note about a workout the
  coach later replaced" against "infinite orphan tray
  growth is unfriendly." 30 days matches the typical
  "this isn't relevant anymore" intuition.

### SH-D.6 Cross-device sync of state

- [ ] **SH-D.6** State files written to the receiver's
  own repo are git-tracked and sync naturally between
  the receiver's devices. Device A marks a task done →
  commit pushed → Device B pulls → resolver merges the
  done-state on render.

  `_local/state/` (pre-own-repo) is per-device and does
  **not** sync. **Decision: this is acceptable because the
  pre-own-repo state of a fresh recipient is by
  definition a single-device situation** — they haven't
  invested enough in the app to be using it on a second
  device. The first time they want cross-device sync,
  they're prompted to create an own-repo (which then
  carries state with it).

### SH-D.7 State-file conflict handling

- [ ] **SH-D.7** State files are one-file-per-(entity,
  kind) so cross-device write collisions are bounded to a
  single file per state event. When they happen:
  - `done.toml`: idempotent (a `done_at` timestamp; latest
    wins; merge is automatic). No conflict UI surfaces.
  - `snooze.toml`: latest-`until`-wins.
  - `note.toml`: actual content conflict → surfaces the
    git conflict UI (Phase J.5/J.6) with the structured-
    field-vs-body merge editor.
  - `reaction.toml`: idempotent like done.
  - `mute.toml` / `hide.toml`: boolean state, latest-wins.
  - `priority-override.toml` (keyed by calendar-id):
    latest-wins.

  **Decision: only `note.toml` ever surfaces a manual
  conflict UI.** Every other state kind is naturally
  idempotent or merge-by-latest. This keeps the
  no-merge-conflicts spirit of the round.

---

## Phase SH-E — simplified ("good boy") mode behavior

Goal: precise spec of what's visible/hidden/reachable in
simplified mode, auto-entry logic, manual switching, and
the "escape hatch" for features the simplified user
occasionally needs. UI mockups belong in `ui-spec.md`
UI-FF (SA-15 owns); behavior here implements `main.md`
PP.1–PP.7.

### SH-E.1 Visible surfaces in simplified mode

- [ ] **SH-E.1** What the user CAN reach in simplified
  mode:
  - **Schedule view** — single primary view: Day or
    "Today" (user picks at first launch from a
    two-button choice, default Day).
  - **Tasks view** — Combined view only (all active
    lists rolled together; the per-list / shopping /
    standing views are hidden).
  - **Top-bar sync button** — manual sync.
  - **Top-bar identity icon** — but only as a static
    chip; tap opens read-only profile sheet, NOT the
    full identity editor.
  - **Event detail sheet** — full content, including
    the comment composer (D.45 / Scenario 2). Comments
    can be authored even without an own repo (they
    write to `_local/state/<source-id>/<event-id>.note.toml`
    with `state_kind = "note"`).
  - **Task detail sheet** — full content; checkbox is
    the primary action.
  - **FAB** — single primary action. With no own repo:
    routes to "Add my own events" (SS mini-wizard).
    With an own repo: creates an event/task in the own
    repo's default calendar/list.
  - **Settings (minimal)** — Switch to full mode,
    Mode label, Theme, Notifications (basic on/off per
    repo), About.
  - **Deep-link receive screen** — always available;
    new gifted repos can always be added in simplified
    mode (the whole point of the round).

### SH-E.2 Hidden surfaces in simplified mode

- [ ] **SH-E.2** What the user CANNOT reach in simplified
  mode:
  - Repo management UI (add/remove/configure repos
    beyond what deep-link adds).
  - Identity creation, GPG/signing settings.
  - Template picker, wizard, re-apply template.
  - Advanced sync settings (interval, push strategy,
    conflict prefs).
  - Multi-view tabs — Week, Month, Year, Timebox are
    not even tabs in the top bar; only Day or Today.
  - **Together tab** entirely hidden.
  - CalDAV bridge configuration.
  - Branch picker / branch settings.
  - Sticker pack management.
  - LFS toggle.
  - All "Advanced" settings sub-screens.

### SH-E.3 Auto-entry logic

- [ ] **SH-E.3** Simplified mode is the default mode when
  ALL of the following are true:
  - No own repo configured (i.e. no repo where the active
    identity's default_author = true).
  - At least one read-only or pull-only gifted repo
    configured.

  If both are false (no repos at all) the app is in
  first-run state and shows the welcome wizard — not
  simplified mode.

  If the user has an own repo, default is full mode
  (per Round 1+2 behavior).

  **Decision: auto-entry happens exactly once, at the
  end of the first-launch bootstrap.** After that, the
  user's mode choice persists. If they switch to full
  mode and later remove their own repo, the app does
  NOT auto-switch back to simplified — it stays in
  full mode until they explicitly switch.

### SH-E.4 Manual switching

- [ ] **SH-E.4** Both directions are one-tap and
  reversible:
  - Settings → "Switch to full mode" (in simplified) →
    tap → confirm dialog "All features will become
    visible. Switch?" → on confirm, the chrome rebuilds.
    No data migration. No commit.
  - Settings → "Switch to simplified mode" (in full) →
    same.

  The mode preference is stored in the app's
  `SharedPreferences` (not encrypted; not sensitive).
  Per-device, not per-repo.

### SH-E.5 Mode-label picker

- [ ] **SH-E.5** The user-facing **label** for the mode
  is independent of the mode itself. Available labels:
  - `Simplified` (Play Store default; what screenshots
    use)
  - `Focused`
  - `Received Schedules`
  - `Good Boy Mode`
  - `Good Girl Mode`
  - `Good Pet Mode`
  - `Kept Mode`
  - `Other...` (free-text input)

  The picker appears:
  - On first auto-entry into simplified mode, as the
    bottom of the onboarding card: "**You're in simple
    mode. Call it what you like:** <chips of options>
    Use this label going forward." (Defaults to
    "Simplified" if dismissed without selection.)
  - Anytime via Settings → Appearance → Mode label.

  The label is shown:
  - In the top-bar mode chip (small text under the
    repo switcher).
  - In Settings → "You're in <label> mode. Switch to
    full mode?"
  - In the splash/about screen briefly.

  **Decision: label is local-device-only, not stored in
  any repo file**. This is privacy register: the kink-
  coded labels never leak into git history. Play Store
  screenshots use only "Simplified."

### SH-E.6 The escape hatch for occasional advanced needs

- [ ] **SH-E.6** Sometimes a simplified-mode user needs
  a specific advanced feature. Two cases:
  - **Comment composer**: always visible per SH-E.1.
    No escape-hatch needed; it's just on.
  - **Adding a CalDAV mirror or changing a repo
    setting**: rare but legitimate. Settings → "**More
    features available**" entry → tap → confirm
    dialog "*Switch to full mode for this one
    operation? You can switch back when done.*" → on
    confirm, mode switches to full + a yellow toast
    "Switched to full mode. <Switch back when done>".
    After 10 minutes of inactivity OR explicit "Switch
    back," mode returns to simplified.

  **Decision: temporary-full-mode auto-revert is on
  10min timer**, not on completion of any specific
  task. Auto-revert by task-completion would require
  predicting what the user was about to do; auto-
  revert by time is simple and predictable.

### SH-E.7 What about the FAB in simplified mode?

- [ ] **SH-E.7** Worth its own sub-step because of the
  branching behavior:
  - **No own repo configured** → FAB icon is a `+`
    with a tiny pencil overlay; tapping opens the
    SS mini-wizard ("Set up your own schedule"). The
    text under the FAB on long-press: "Add my own
    events."
  - **Own repo configured** → FAB is a normal `+` and
    opens a quick-create sheet (title, time, default
    calendar of the own repo). No calendar picker
    visible (single default).
  - **Long-press FAB** in both cases → secondary action
    sheet: "Add task," "Add event," "Scan QR for new
    schedule."

---

## Phase SH-F — first-launch deep-link bootstrap

Goal: choreograph the precise sequence when the app is
launched via a deep-link and there are no repos. UI mockups
belong in `ui-spec.md` UI-GG; behavior here implements
`main.md` QQ.1–QQ.7.

### SH-F.1 Detection

- [ ] **SH-F.1** On `Application.onCreate`, the app
  records the launch source. If the launch was via an
  intent with `ACTION_VIEW` and a `data` URI matching
  the deep-link grammar (SH-B.1), the app holds the URI
  in a `PendingDeepLink` slot. Then the normal startup
  proceeds.

  The first UI activity to render queries the
  `PendingDeepLink` slot AND the repo count:
  - If `PendingDeepLink != null` AND `repoCount == 0`:
    → **first-launch deep-link bootstrap path** (this
    phase).
  - If `PendingDeepLink != null` AND `repoCount > 0`:
    → **in-app add-gifted-repo screen** as an overlay
    on top of the current view.
  - If `PendingDeepLink == null` AND `repoCount == 0`:
    → **welcome wizard** (Round 1).
  - If `PendingDeepLink == null` AND `repoCount > 0`:
    → **normal schedule view**.

### SH-F.2 The choreography (no-repos + deep-link)

- [ ] **SH-F.2** Step-by-step:
  1. **Skip the welcome wizard entirely.** No mascot
     splash beyond a 300ms identifying flash.
  2. **Render the "Add gifted repo" screen** with URL
     prefilled, `label`/`mode`/`priority`/`via` chips
     above the URL, big **Accept** button + tertiary
     **Paste a different link** option. Designed to be
     readable by someone who has never opened a
     calendar app.
  3. On Accept tap:
     - **Auth attempt order**: token-first (fragment
       parsed), OAuth-fallback (provider-detected from
       URL host), PAT-prompt (last resort).
     - For each method, a brief inline status message
       under the Accept button: "Authenticating..." →
       "Authenticated" or "Trying next method..." or
       error.
  4. **Clone-with-progress**:
     - Full-screen mascot animation (the calendar-bat
       waves).
     - Byte-counter + "Cloning your schedule..." text.
     - Cancel button after 5s (in case the user
       changes their mind on a slow connection).
  5. **References prompt** (SH-C.2) if applicable.
  6. **Boot into simplified mode** (the auto-entry
     condition is now true: 1+ gifted repo, no own repo).
  7. **Schedule view** with today selected.
  8. **One-time onboarding card** overlaid at the top
     (SH-F.3).

### SH-F.3 Onboarding card content

- [ ] **SH-F.3** Exact text — written for a person who
  has never used a calendar app:

  > **Welcome to your schedule.**
  >
  > **<via>** set this up for you.
  >
  > • Tap any event for details.
  > • Mark tasks done by tapping their circle.
  > • Pull down to refresh.
  >
  > Need help? Tap here.
  >
  > [chips of mode-label options, default "Simplified"]
  >
  > [Got it]

  Tapping **Got it** dismisses the card and writes a
  `seen_first_launch_card = true` flag to prefs.
  Tapping **Need help?** opens a brief help sheet
  (still local, no network) with a couple of FAQ
  entries and a "Show me again later" entry.

  If `via` is missing from the deep-link, the second
  line becomes generic: "Someone set this up for you."

### SH-F.4 Malformed/expired link on first launch

- [ ] **SH-F.4** If the deep-link parser rejects the
  URL (malformed, expired, schema-too-new, etc.), the
  first-launch path renders an error screen instead of
  the Add-gifted-repo screen:
  - Title: the specific error message from SH-B.9.
  - Body: a brief explanation.
  - Actions: **Paste a different link**, **Start
    fresh** (which kicks them into the normal welcome
    wizard).

  The error screen does NOT chain into the wizard
  automatically — a user who got here via a bad link
  is likely confused, not ready for a 6-screen wizard.

### SH-F.5 Holding the link across reinstalls

- [ ] **SH-F.5** Scenario 1's web-fallback path
  (SH-B.8) hands the link off via `sessionStorage` after
  install. On re-fire, the app's `Application.onCreate`
  catches the intent the same way as a direct tap —
  the bootstrap choreography is identical.

  **Decision: there is no special "post-install"
  handling on the app side beyond reading the intent.**
  The web fallback owns the cross-install handoff. The
  app doesn't need to know it was "just installed."

---

## Phase SH-G — authoring share-this-repo flow

Goal: the share-config sheet, every auth method's
mechanics, the output options, and the CLI parity.
UI mockups belong in `ui-spec.md` UI-HH (SA-15 owns).
Implements `main.md` RR.1–RR.8.

### SH-G.1 Entry point

- [ ] **SH-G.1** Settings → Repos → tap a repo →
  "**Share this repo**" entry. Available for every
  repo where the current device has push access (or at
  least admin-read access — needed for the deploy-key
  / PAT generation paths). For repos where the device
  has only read access (i.e. the receiver of a gifted
  repo is trying to re-share), the entry is hidden
  with a tooltip on long-press: "Re-sharing is owned
  by the original author."

### SH-G.2 The share-config sheet

- [ ] **SH-G.2** Fields, in vertical order:
  - **Mode** — radio: `read-only` (default) / `pull-only`
    / `read-write` (greyed unless the user explicitly
    enables "Advanced" with a long-press; rationale:
    `read-write` is rare and dangerous to do casually).
  - **Suggested label** — text input, default = the
    repo's display name. Max 64 chars.
  - **Suggested priority modifier** — pill row:
    `high` / `normal` (default) / `low`.
  - **From** (`via`) — text input, default = the
    user's active identity's display name. Max 64
    chars. "Leave blank for no attribution."
  - **References policy** — pill row: `prompt`
    (default) / `auto` / `ignore`. Only visible if the
    repo has a `references.toml`. Tooltip explains
    "If your schedule references others, should the
    recipient see them and choose, or add silently?"
  - **Auth method** — radio (always 4 options;
    enabled/disabled based on context):
    - "Public repo, no auth needed" (enabled only if
      the repo's GitHub/Forgejo visibility is public —
      detected via API).
    - "Recipient adds their own SSH key" (always
      enabled).
    - "Embed a one-shot deploy key (24h)" (enabled if
      the user has `admin:public_key` scope on this
      repo).
    - "Embed a fine-grained PAT (24h)" (enabled if the
      provider supports fine-grained PATs and the user
      has the relevant scope).
  - **Expires** — only visible for the "embed" auth
    methods. Pill row: `1h` / `24h` (default) / `7d`
    / `30d` / `Custom`. Custom opens a date picker
    bounded at 90d max.
  - **Output** — three buttons at the bottom:
    - **Copy link** — copies plain text URL to
      clipboard.
    - **Save QR** — writes PNG to
      `Pictures/strictlykeptboy/` via SAF.
    - **Share via...** — Android system share-sheet,
      with the link as text + the QR PNG as an
      attached image.

  As the user fills the sheet, a live **Preview**
  pane at the bottom shows the URL that'll be
  generated (with `#token=<redacted>` if applicable
  and a "click to reveal" toggle that flashes the
  token once for the author's own QA).

### SH-G.3 Auth method: public repo

- [ ] **SH-G.3** Simplest path. The link contains the
  URL, label, mode, priority, via, references — no
  fragment. The repo is cloned anonymously over HTTPS
  on the receive side. No `expires` because there's
  no token to expire.

  The link is durable; the author can reuse the same
  QR forever (Scenario 2's coach prints the QR and
  reuses for every new client).

### SH-G.4 Auth method: BYO-SSH

- [ ] **SH-G.4** "Recipient adds their own SSH key."
  Link contains URL + meta params + `auth=byo-ssh`
  hint (NOT a token). No fragment with a secret.
  Behavior:
  - Author hands the link to the recipient.
  - Recipient taps the link → receive screen → tap
    Accept → app attempts the clone → fails on auth.
  - App shows the recipient's SSH public key (already
    generated per D.7 for the device) with a **Copy**
    button and a one-screen instruction: "*This is a
    private schedule. Send the key below to the person
    who sent you this link. They'll add you as a
    collaborator. Then come back here and tap Try
    again.*"
  - The author manually adds the recipient's key on
    the provider side. (No automation here — the
    author doesn't have the recipient's GitHub
    username.)
  - Recipient retries; clone succeeds.

  **Decision: BYO-SSH is the right escape-valve when
  the embed-token methods are unavailable** (no
  `admin:public_key` scope, provider doesn't support
  fine-grained PATs, etc.) but it's not the default
  recommendation — it requires both ends to be
  technically literate, and Scenario 1's sub is not.

### SH-G.5 Auth method: embed one-shot deploy key

- [ ] **SH-G.5** The most-recommended auth method for
  private repos. Step-by-step:
  1. On sheet **Save QR** / **Copy link** / **Share
     via...** tap, the app:
     - Generates a **fresh ed25519 keypair entirely
       client-side** using BouncyCastle.
     - Posts the public key to the provider via
       `POST /repos/{owner}/{repo}/keys` (GitHub) or
       `POST /api/v1/repos/{owner}/{repo}/keys`
       (Forgejo), with `read_only = true` (GitHub) /
       `read_only` query param (Forgejo).
     - Receives the `key_id` from the provider.
     - Records the issuance in
       `.strictlykeptboy/shared-links.toml` (a private,
       app-managed file in the *source* repo):

       ```toml
       [[issued]]
       link_id = "01HZ-LINK-..."        # ULID-style
       key_id = "12345678"               # provider's key id
       key_fingerprint = "SHA256:..."    # for revocation matching
       label = "Schedule from Master"
       mode = "read-only"
       priority = "high"
       issued_at = "2026-05-11T22:00:00Z"
       expires_at = "2026-05-12T22:00:00Z"
       revoked = false
       token_hash = "sha256:..."         # not the token itself
       ```
     - Commits the `shared-links.toml` update with
       message `record share link for "<label>"
       (expires <date>)`.
  2. Embeds the **private key**, OpenSSH-armored and
     URL-safe-base64-encoded, into the URL fragment as
     `#token=<armor>&expires=<iso>`.
  3. The link is rendered / saved / shared per the
     output choice.

  **Decision: keys are generated CLIENT-SIDE
  (on-device), never server-side.** The private key
  never touches any server the project controls. The
  public half is uploaded to the *user's chosen
  provider* (GitHub or Forgejo) — that's a service the
  user already trusts with their repo. This minimizes
  the trust footprint of the share flow.

  **Decision: deploy keys are repo-scoped and read-only
  by construction**, so the blast radius of a leaked
  link is bounded to "read access to this one repo
  until the expiry." Even on a leak, the attacker
  cannot push, cannot enumerate other repos, cannot
  read user metadata, cannot perform any account-
  level action.

### SH-G.6 Auth method: embed fine-grained PAT

- [ ] **SH-G.6** Similar shape to deploy key but uses
  the provider's fine-grained PAT system:
  - GitHub: `POST /users/{username}/personal-access-tokens`
    (fine-grained), scoped to the single repo with
    `contents:read` permission.
  - Forgejo: simpler scope model — `POST /api/v1/users/
    {username}/tokens` with scope `read:repository`
    on the named repo.
  - Provider returns the token string (GitHub returns
    once; not retrievable later).
  - App embeds the token in fragment as `#token=<pat>&
    expires=<iso>`.
  - App records the issuance in `shared-links.toml`
    with `kind = "pat"` and the token's prefix
    (GitHub fine-grained tokens start with `github_pat_`)
    for later revocation matching.

  **Decision: PAT-in-link is offered as a peer to
  deploy-key-in-link, not as a fallback.** Some
  providers / orgs have policies that disable deploy
  keys but allow fine-grained PATs (or vice versa).
  Having both as first-class options means the share
  flow works across more setups without surfacing
  provider-policy complexity to the user.

### SH-G.7 Provider differences in auth-method UI

- [ ] **SH-G.7** When the user opens the share-config
  sheet, the app detects the provider from the repo URL
  and renders auth-method radio buttons accordingly:
  - **GitHub** (`github.com` host): all 4 options
    available (public, BYO-SSH, deploy-key, fine-grained
    PAT). Fine-grained PATs are GA as of late 2024.
  - **Forgejo / Codeberg / Gitea-family** (any host
    that responds to `/api/v1/version`): all 4
    options, with the simpler scope model for PATs.
  - **Self-hosted unknown** (some Git host that doesn't
    look like GitHub or Forgejo): only public + BYO-SSH
    available. Embed-token paths grey out with tooltip
    "This provider isn't supported for one-shot
    tokens. Use BYO-SSH instead."

### SH-G.8 Output: copy / save QR / share

- [ ] **SH-G.8** All three output paths produce the
  same URL. Differences:
  - **Copy link**: writes the URL to the system
    clipboard. Brief toast "Link copied." Privacy:
    Android 13+ shows the system clipboard hint —
    that's fine; this link is meant to be pasted into
    a messenger.
  - **Save QR**: encodes the URL into a PNG via
    ZXing, writes to `Pictures/strictlykeptboy/share-
    <repo-slug>-<yyyy-mm-dd>.png` via SAF. Toast
    "QR saved to Pictures."
  - **Share via...**: opens Android's system share
    sheet with both the link as text AND the QR PNG
    attached as an image. The user picks the
    destination (Signal, WhatsApp, email, etc.) and
    the share sheet handles the rest.

### SH-G.9 The `shared-links.toml` revocation ledger

- [ ] **SH-G.9** Path: `.strictlykeptboy/shared-links.toml`
  in the source repo. Visibility: in the repo
  filesystem but **not** rendered in the schedule UI —
  it's metadata, not user content. Visible to the
  author at Settings → Repos → tap repo → **Share
  history**.

  Each entry has the fields shown in SH-G.5 sample.
  The token itself is **never stored** — only a SHA-256
  hash for matching (so the author can revoke "the
  one I shared with X" without needing the raw token).
  The recipient app does NOT read this file — it's
  author-side metadata for revocation UX (SH-I).

  **Decision: the ledger lives in the *source repo*,
  not in app prefs.** Two reasons:
  - Cross-device: if the author shares from Device A
    and later wants to revoke from Device B, the
    ledger needs to follow them. Git is the natural
    sync mechanism.
  - Audit trail: the author has a permanent record
    of who they shared with and when, as part of
    their own repo history.

---

## Phase SH-H — evolution path: simplified → own repo

Goal: a simplified-mode user upgrades to authoring their
own schedule without losing any gifted content or local
state. UI mockups belong in `ui-spec.md` UI-II.
Implements `main.md` SS.1–SS.7.

### SH-H.1 Trigger

- [ ] **SH-H.1** Two entry points:
  - **FAB tap in simplified mode with no own repo**:
    routes here (per SH-E.7).
  - **Settings → "Set up your own schedule"**: explicit
    entry, always visible in simplified mode.

  In both cases the user lands on the mini-wizard
  screen 1.

### SH-H.2 Mini-wizard, screen 1: where to put it

- [ ] **SH-H.2** Title: "**Where should we put your
  schedule?**"

  Three options:
  - **GitHub** (radio, default) — uses Device Flow
    OAuth.
  - **Forgejo / self-hosted** (radio) — asks for
    server URL on the next screen.
  - **Skip — store locally for now** (small text link
    at the bottom; not a primary option).

  Below: a text input "Repo name" prefilled with
  `<username>-schedule` (or `my-schedule` if no
  username known). Validation: must be a valid
  filename per the provider's rules; live-validated
  with green/red indicator.

  **Decision: the "store locally for now" option
  exists but is intentionally minor.** A user with
  zero git provider account can still build a schedule
  on-device; we don't gate authoring on cloud setup.
  But the friction is real (no cross-device sync), so
  we surface it as a tertiary option, not a peer.

### SH-H.3 Mini-wizard, screen 2: auth

- [ ] **SH-H.3** Per the chosen provider:
  - **GitHub**: OAuth Device Flow (D.7 / B.5). Show
    the device code, "Open browser to authorize" button,
    waiting spinner. On success, "Authorized as
    `<github-username>`."
  - **Forgejo**: server URL input → discover OAuth
    endpoint → same Device Flow shape.
  - **Local-only**: no auth screen; skip directly to
    screen 3.

### SH-H.4 Mini-wizard, screen 3: optional template

- [ ] **SH-H.4** Title: "**Want to apply a template?**"

  Two large buttons:
  - **Just start empty** (default focus) — skips
    template application; the new repo has only the
    skeleton (`.strictlykeptboy/`, `identities/`,
    one default calendar `Personal`, one default
    todolist `Personal`).
  - **Pick a template** — opens the wizard's
    role-toggle screen (templates-demo-wizard.md
    TW-D). User selects roles; templates apply on
    top of the skeleton.

  **Decision: the template-pick step is optional and
  defaulted to skip.** The user came from simplified
  mode where they were happy consuming gifted content;
  the upgrade moment is about adding *one* personal
  event ("dinner with friend Sat"), not about
  authoring a whole life. Forcing a 12-toggle screen
  here would feel like an interrogation.

### SH-H.5 Post-creation steps

- [ ] **SH-H.5** After screen 3 (whether template
  applied or skipped):
  1. **Create the repo on the provider**:
     - GitHub: `POST /user/repos` with `private = true`
       (default; user can toggle to public on a
       confirmation dialog), `auto_init = false` (we
       init locally then push).
     - Forgejo: equivalent endpoint.
     - Local-only: `git init` in
       `~/.strictlykeptboy/repos/<repo-id>/`.
  2. **Initialize the local clone**:
     - Write the skeleton (`.strictlykeptboy/schema.toml`,
       `.strictlykeptboy/repo.toml`, `identities/
       <person-id>.md`, default calendar, default
       todolist).
     - If a template was picked, materialize its files.
  3. **Migrate `_local/state/*`** (SH-D.4):
     - Copy every state file under
       `~/.strictlykeptboy/local-state/` to
       `<new-repo>/state/`.
     - Single commit:
       `migrate cross-repo state from _local`.
  4. **Write `references.toml`**:
     - Enumerate every currently-configured gifted
       repo.
     - For each, write a `[[reference]]` entry with:
       - `url` = the gifted repo's clone URL.
       - `label` = the gifted repo's display name.
       - `priority_modifier` = the modifier the user
         picked at receive time (preserved from the
         link).
       - `mode` = `read-only` / `pull-only` etc.
       - `default_active = true`.
       - `credential_hint` = SSH-key fingerprint if
         that's how the receive auth resolved.
     - Single commit:
       `record references to <N> gifted schedules`.
  5. **Initial commit + push** (all the above as a
     single coordinated push to the new remote).
  6. **Prompt**: "**You're set up. Stay in simple mode
     or switch to full?**" Two buttons; default focus
     on **Stay in simple**. Persist the choice.
  7. **Cross-device sync notice**: if the user has
     signed in on this device with the same provider
     account that another device used, the new repo
     will appear on that other device on next sync —
     surface a brief note "Your other devices will
     see this repo after their next sync."

### SH-H.6 Cross-device implications

- [ ] **SH-H.6** When the user adds their primary repo
  on Device A, the `references.toml` syncs to Device B
  on the user's next pull. Device B's app:
  - Detects a new `references.toml` in a repo it
    already has configured.
  - Cross-references against Device B's currently-
    configured repos.
  - For each reference not already on Device B,
    triggers the SH-C.2 prompt sheet ("Your other
    device has these schedules — add here too?").

  **Decision: the prompt requires explicit per-device
  confirmation, NOT silent add.** The receiver might
  intentionally use different repo sets on different
  devices ("work tablet" vs "personal phone"). Auto-
  adding everything would defeat that pattern.

### SH-H.7 Reverse path: full → simplified

- [ ] **SH-H.7** A full-mode user (e.g. someone who
  came in via the welcome wizard, authored their own
  schedule from day one, now wants single-purpose
  device focus) can switch to simplified mode via
  Settings → "Switch to simplified mode." No data
  migration. The chrome rebuilds; advanced surfaces
  hide. The user's own repo stays the active default
  for new entries (the FAB creates events there).
  Switching back to full is one tap.

  **Decision: no warning dialog on full → simplified.**
  The action is reversible in one tap, no data lost.
  Warning dialogs on reversible toggles are noise.

---

## Phase SH-I — revocation + token rotation (authoring side)

Goal: the author can revoke a share-link before its
expiry, supersede an old link with a new one, and see
what's outstanding. Implements `main.md` RR (via the
shared-links ledger from SH-G.9).

### SH-I.1 Why revocation matters

- [ ] **SH-I.1** Share-links carry tokens. Tokens are
  bearer credentials — anyone with the link can clone
  the repo. Expiry is the primary defense (24h
  default), but:
  - The author may want to revoke earlier ("sub fell
    out of the dynamic," "client cancelled the program,"
    "league moved to a different platform").
  - The author may have made a mistake (shared with
    wrong contact, used wrong mode, included wrong
    references).
  - The author may want to rotate (issue a new link
    that supersedes an old one — e.g. extending
    access without sharing both old and new tokens).

### SH-I.2 Share history UI

- [ ] **SH-I.2** Settings → Repos → tap repo → **Share
  history**. Lists every entry in `shared-links.toml`,
  sorted by `issued_at` descending. Each row:
  - Label.
  - Mode badge.
  - Issued date + age.
  - Expiry countdown ("expires in 6h 12m" / "expired
    3d ago").
  - Status pill: `Active` / `Expired` / `Revoked`.
  - Three-dot menu: **Revoke now**, **Show details**,
    **Issue replacement**.

  An "Active" link with an embedded token can be
  revoked even before expiry. A link can be re-issued
  with a fresh token (same label/mode) that
  supersedes the old.

### SH-I.3 Revocation mechanics

- [ ] **SH-I.3** On **Revoke now** tap:
  1. Confirm dialog: "Revoke `<label>`? Recipients
     using this link will lose access on their next
     sync."
  2. On confirm, the app calls the provider:
     - GitHub: `DELETE /repos/{owner}/{repo}/keys/{key_id}`
       for deploy-key revocation; or `DELETE /users/
       {username}/personal-access-tokens/{token_id}`
       for PAT revocation.
     - Forgejo: equivalent endpoints.
  3. On provider success, update the
     `shared-links.toml` entry: `revoked = true`,
     `revoked_at = "<iso>"`.
  4. Commit + push: `revoke share link "<label>"`.
  5. Toast: "Revoked. Recipients will lose access on
     their next sync."

  Provider failure path (rare but possible — token
  already revoked, network down): show the error,
  offer **Retry** + **Mark as revoked locally
  anyway** (with a warning that the token might
  still work until manually cleaned up on the provider
  side).

### SH-I.4 Revocation propagation

- [ ] **SH-I.4** When a revoked link's recipient
  next pulls:
  - The cloned repo's `git fetch` returns 403 (deploy
    key removed) or 401 (PAT revoked).
  - The app catches the error and surfaces a banner
    on the gifted repo: **"`<label>` lost access on
    `<date>`. Contact the sender for a new link."**
  - Local content remains visible (the receiver's
    cached clone is intact); only further pulls fail.
  - The receiver can **Remove this schedule** from
    the banner, which deletes the local clone and the
    corresponding state-file directory (with a "Keep
    your interactions?" prompt — yes preserves the
    state in `_local/state/` for future rebind).

### SH-I.5 Token rotation

- [ ] **SH-I.5** On **Issue replacement** tap:
  1. Open the share-config sheet pre-filled with the
     old entry's mode/label/priority/auth-method.
  2. User confirms (or tweaks); on output, the app:
     - Generates the new token (deploy-key or PAT).
     - Atomically marks the old entry as
       `superseded_by = <new-link-id>` and revokes
       its provider-side credential.
     - Writes the new entry.
     - Single commit: `rotate share link "<label>"`.
  3. New link shared via the chosen output method.

  **Decision: rotation always revokes the old token,
  even if expiry was further in the future.** A user
  who rotates is signaling "the old link is no longer
  the canonical one"; leaving it alive would create
  two valid links for the same logical share and
  invite confusion.

---

## Phase SH-J — CLI surface for shared schedules (summary)

Goal: list every new `skb` subcommand introduced by
Round 3 with synopsis + worked example. **The detailed
flag-by-flag spec belongs in `cli-tooling.md`** which
will be amended by SA-17 in a follow-up pass. This phase
exists so a CLI consumer (Claude in a sandbox) can find
the surface area in one place without context-switching.

Implements `main.md` (deferred extension to Phase X).

### SH-J.1 `skb accept`

- [ ] **SH-J.1** Synopsis: `skb accept <url-or-qr-file>
  [--mode read-only|read-write|pull-only] [--label
  <text>] [--priority high|normal|low] [--no-references]
  [--json]`

  Behavior: the headless equivalent of the deep-link
  intent. Used by Claude in a sandbox to bootstrap
  a repo from a link the user pasted, or by power
  users who got a QR PNG via email and want to
  accept from the terminal.

  Example:

  ```bash
  $ skb accept 'https://strictlykeptboy.app/add?url=git%40github.com:dom/dom-schedule.git&label=Master&mode=read-only#token=...&expires=2026-05-12T22:00:00Z'
  Cloning into dom-schedule (read-only)...
  Cloned 47 events, 12 tasks, 3 recurrences.
  References found: 1
  Add 1 referenced repo? [Y/n]: Y
  Cloning into dom-resources (read-only)...
  Done. 2 repos added.
  ```

  Or from a QR file:

  ```bash
  $ skb accept --qr ~/Downloads/coach-strength.png
  Cloning into coach-strength-2026 (read-only)...
  Done. 1 repo added. No references found.
  ```

### SH-J.2 `skb ref add | list | remove`

- [ ] **SH-J.2** Synopsis:
  - `skb ref add <url> [--label <text>] [--mode ...]
    [--priority ...] [--required] [--default-active]
    [--repo <id>]`
  - `skb ref list [--repo <id>] [--json]`
  - `skb ref remove <url-or-source-id> [--repo <id>]`

  Behavior: manage `references.toml` in a target repo.
  `--repo` defaults to the CWD-walked-up repo per CLI
  conventions. `list --json` emits the parsed
  manifest as JSON for AI consumers.

  Example:

  ```bash
  $ skb ref list --repo sub-schedule
  - dom-schedule (read-only, high) — Master
  - friends-bday-calendar (pull-only, normal)
  ```

### SH-J.3 `skb state set | list | rebind`

- [ ] **SH-J.3** Synopsis:
  - `skb state set --done <entity-id>` (and `--undone`)
  - `skb state set --snooze <duration> <entity-id>`
  - `skb state set --note <text> <entity-id>`
  - `skb state set --mute <entity-id>` (and `--unmute`)
  - `skb state set --hide <entity-id>` (and `--unhide`)
  - `skb state set --priority <int> --calendar
    <calendar-id> --source-repo <id>`
  - `skb state list [--source <repo-id>] [--kind
    done|snooze|note|...] [--json]`
  - `skb state rebind <old-source-id> <new-url>` —
    move all state files keyed by `<old-source-id>` to
    a new source-repo-id derived from `<new-url>`,
    after the source repo was renamed/moved.

  Example:

  ```bash
  $ skb state set --done 01HZ-WORKOUT-MONDAY
  Marked done. Wrote state/abc123_dom-schedule/01HZ-WORKOUT-MONDAY.done.toml
  Committed: "mark task done"

  $ skb state list --source dom-schedule --json
  [
    { "entity_id": "01HZ-WORKOUT-MONDAY", "kind": "done", "done_at": "..." },
    ...
  ]
  ```

### SH-J.4 `skb share`

- [ ] **SH-J.4** Synopsis: `skb share [<repo>] [--mode
  read-only|read-write|pull-only] [--auth public|byo-ssh|
  one-shot-deploy-key|pat] [--label <text>] [--priority
  high|normal|low] [--qr <out-path>] [--expires
  1h|24h|7d|30d|<iso>] [--json]`

  Behavior: prints the URL on stdout. If `--qr` given,
  writes a PNG and prints the path. If `--json` given,
  emits a structured record with link, expiry, key-id,
  fingerprint.

  Example:

  ```bash
  $ skb share dom-schedule --auth one-shot-deploy-key \
        --label "Schedule from Master" \
        --priority high \
        --expires 24h \
        --qr ~/Pictures/sub-link.png
  Generated read-only deploy key (24h expiry).
  Recorded in shared-links.toml.
  URL: https://strictlykeptboy.app/add?...#token=...&expires=...
  QR:  /home/dom/Pictures/sub-link.png
  ```

### SH-J.5 `skb mode`

- [ ] **SH-J.5** Synopsis: `skb mode [simplified|full|
  toggle]` (no arg = print current).

  Behavior: switches the device's display mode. Used by
  Claude when arranging a single-purpose-device handoff
  scenario.

  Example:

  ```bash
  $ skb mode
  full

  $ skb mode simplified
  Switched to simplified mode.
  ```

### SH-J.6 To be merged into `cli-tooling.md`

- [ ] **SH-J.6** Each subcommand above is summarized
  here. The detailed flag spec, `--json` schema, exit
  codes, error model, and `--dry-run` behavior follow
  the cross-cutting CLI conventions in
  [`cli-tooling.md`](cli-tooling.md) (Phase CLI-B,
  CLI-C, CLI-D).

  **Decision: defer the full CLI doc merge to a
  follow-up pass (SA-17).** The CLI doc is already
  large; appending 5 more subcommands with full
  flag-by-flag detail would balloon it. The summary
  here is enough for Phase X implementation work to
  begin in parallel; the merge into `cli-tooling.md`
  can happen during integration.

---

## Phase SH-K — edge cases + cross-cutting concerns

Goal: every "but what if..." that the spec must answer to
not be hand-wavy. None of these are tradeoffs to
*decide*; they're tradeoffs to *spell out*.

### SH-K.1 Schema version too new in gifted repo

- [ ] **SH-K.1** A gifted repo's
  `.strictlykeptboy/schema.toml` may declare
  `schema_version > LATEST_SCHEMA` of the receiver's
  app version. Per D.20, the app refuses to write. For
  a gifted-repo receiver, **writing isn't the use case**
  — they want to read. Decision:
  - Refuse with clear message: "This schedule was
    authored with a newer version of strictlykeptboy.
    Update the app to view it." Play Store + Obtainium
    links.
  - Do NOT attempt to read with the old schema (risk
    of mis-parsing newer fields).
  - On the receive screen, the error appears BEFORE
    the clone completes if possible (read the schema
    file via the provider's raw-content API for HTTPS
    transports; for SSH, we have to clone first and
    then check). For SSH, clone proceeds, then schema
    check fails, then the cloned-but-unreadable repo
    is left on disk with a "needs update" badge — not
    deleted, since the user might update tomorrow.

### SH-K.2 Huge gifted repo

- [ ] **SH-K.2** A gifted repo with 10k+ events (e.g. a
  decade of soccer-club history). Clone progress UI
  handles it (SH-F.2 step 4 with byte counter +
  cancel). After clone:
  - Room indexer prioritizes the today-and-near range
    first: scan `events/<this-year>/<this-month>/`
    + `events/<this-year>/<next-month>/` before older
    months. Renders the day view within the budget
    (Phase V.1 cold-start < 600ms).
  - Older-month scans complete in the background;
    the schedule view shows a small progress chip
    "Indexing older entries (37% complete)" that
    disappears when done.
  - Cold-scan budget per D.21: 1000 entries < 500ms;
    with 10k entries, scan time grows linearly to ~5s,
    parallelizable into a background WorkManager job.

  **Decision: prioritized indexing is a non-negotiable
  for gifted repos** — the recipient expects "tap link,
  see today's schedule," not "tap link, wait 8 seconds
  for the indexer to chew through a decade."

### SH-K.3 Overlapping events between gifted repos

- [ ] **SH-K.3** Two gifted repos may have events at
  the same time (Dom's check-in at 21:00, soccer-club
  practice at 21:00 on Wednesdays). Per D.49 / RV-M:
  - The repo with the higher effective priority
    (priority_modifier + source priority) wins the
    foreground slot.
  - The other event renders as a banded edge stripe.
  - Tapping either reveals the overlap detail sheet
    with both events.

  The receiver can adjust per-repo priority via the
  per-reference toggle sheet (SH-C.2) or via Settings
  → Repos → tap repo → "Boost / lower priority" (state-
  file override per SH-D.3).

### SH-K.4 No internet at link-tap time

- [ ] **SH-K.4** Tap a link with no connectivity:
  - The receive screen still renders (URL prefilled,
    metadata visible — these are derived from the URL
    parameters, no network needed).
  - Tap Accept → clone attempt fails immediately with
    "No network connection."
  - Inline action: **Save for later** — stores the
    URL (including fragment) in
    `EncryptedSharedPreferences` under a
    `pending-bootstrap` slot.
  - On next connectivity-restore (D.8 callback), the
    app surfaces a notification "Ready to accept your
    saved schedule" → tap → re-runs the receive flow
    with the saved URL.
  - The fragment is preserved during the save; the
    same SH-B.5 token-wipe rules apply after
    successful consumption.

### SH-K.5 Receiver already has this exact repo configured

- [ ] **SH-K.5** Dedup by source-repo-id. Three cases:
  - **Same URL, no token, repo was added anonymously**:
    show "Already added on <date>. Refresh from
    source?" — Yes triggers a fresh fetch. No
    dismisses.
  - **Same URL, new token in link**: show "Already
    added. Want to refresh credentials with the new
    token?" — Yes replaces the stored credential
    (deletes old, stores new). No keeps the old.
  - **Same source-repo-id, different URL form**
    (SSH vs HTTPS of the same repo): show "You
    already have this schedule via a different
    address. Switch to the new address?" — Yes
    updates the stored remote URL on the local
    clone. No keeps existing.

### SH-K.6 Clock skew

- [ ] **SH-K.6** Per SH-B.6, expiry has a 5min grace.
  Beyond:
  - If `now > expires + 5min` AND `now - expires <
    24h`: show "This link expired <duration> ago. Your
    device clock might be off — check Settings →
    Date & time, then try the link again."
  - If `now > expires + 24h`: show "This link expired
    long ago. Ask the sender for a fresh one."
  - If `now > expires - 5min` BUT `now < expires`
    (the link is barely valid): accept silently.
  - If `expires < now - some-extreme-skew` (clock is
    fast by years): warn the user the device clock
    might be wrong — don't refuse out of hand.

### SH-K.7 Recipient privacy from author

- [ ] **SH-K.7** What does the *author* learn when a
  recipient accepts a gifted repo? **Nothing
  intrinsic.** The app does not phone home, does not
  notify the author, does not log anything to a
  central server.

  What the *author's git provider* may log:
  - Deploy keys: GitHub records the timestamp of each
    use (visible to the author in repo Settings →
    Deploy keys). So the author sees "this deploy
    key was used at <time>" but not from where (no
    IP, no device info exposed to repo owners).
  - PATs: similar — provider shows last-use timestamp
    in the PAT settings.
  - Anonymous HTTPS clones (public repos): provider
    aggregate logs only, not exposed to the repo
    owner.

  **Decision: this transparency is honest, not a
  bug.** Document in the share-config sheet's
  tooltip: "*The recipient using this link will leave
  a use-timestamp visible to you in your provider's
  settings. No other information is shared.*" Setting
  this expectation prevents nasty surprises later.

### SH-K.8 What if the receive screen is interrupted?

- [ ] **SH-K.8** Mid-clone, the user gets a phone
  call / switches apps / locks the device. JGit
  clone runs in a foreground service (D.8 / Phase J.1);
  the service keeps the clone alive in the background.
  Return to the app: the receive screen restores from
  the foreground service's state and shows current
  progress.

  If the foreground service is killed by Android
  (OOM, etc.): the partial clone is detected on next
  return to the app (`git fsck`-ish check), the
  partial directory deleted, and the receive screen
  shown with "Clone interrupted — retry?"

### SH-K.9 Receiver wants to leave a comment but author has comments disabled

- [ ] **SH-K.9** Per D.45 + SH-E.1, the comment composer
  is always visible in simplified mode. But the source
  repo's `.strictlykeptboy/repo.toml` may have
  `comments_enabled = false`. Behavior:
  - The composer is visible but renders in a
    "**For your eyes only**" mode — the composed
    text writes to a `state/<source-id>/<entity-id>.note.toml`
    state file (private note kind), NOT a comment
    in the source repo.
  - The placeholder text changes: "**Private note
    (just for you)**" instead of "Add a comment."
  - On submit, no push to source. Local state file
    only.

  **Decision: never silently drop user input.** If
  the author disabled comments, the receiver still
  gets to record their thought — it just stays
  private to them.

### SH-K.10 The receiver wants to share what they received

- [ ] **SH-K.10** A receiver of a gifted repo may want
  to forward the share-link to someone else. Per
  SH-G.1, the share-this-repo entry is hidden for
  repos where the device has only read access. But
  the receiver still has the link itself (in their
  message history). Decision:
  - The app does NOT facilitate re-sharing of someone
    else's repo from inside the app, ever. The
    "Share history" entry is author-only.
  - The original link (whatever the author sent) is
    fully usable by anyone the receiver shares it
    with manually (via copy/paste / forward). This
    is by design — the link IS the access. If the
    author wants to prevent forwarding, they should
    rotate the token after sharing with a specific
    recipient (SH-I.5).
  - Document this in the share-config sheet's
    tooltip: "*Anyone with this link can access the
    repo until it expires. Rotate the link to revoke
    earlier.*"

### SH-K.11 The receiver's own repo also has a `references.toml` — what about transitive?

- [ ] **SH-K.11** When the receiver upgrades to an
  own-repo (SH-H), the new `references.toml`
  enumerates currently-configured gifted repos. Those
  gifted repos may themselves have `references.toml`
  pointing at other repos. Decision:
  - The receiver's own repo's `references.toml`
    enumerates **direct gifts only**, not transitive.
  - Transitive references are still seen when the
    receiver adds a gifted repo (the SH-C.2 sheet
    cascades naturally), but they're recorded only
    on the gifted repo's manifest, not duplicated
    into the receiver's own.
  - Cross-device sync of references (SH-H.6) thus
    propagates only the direct gifts; transitive ones
    re-cascade on the other device via the same
    SH-C.2 flow.

### SH-K.12 Two recipients of the same gifted repo, different priorities

- [ ] **SH-K.12** Author shares Dom-schedule with sub-A
  (priority = high) and sub-B (priority = normal),
  using two separate share-links. Each link encodes
  the priority for that recipient. The recipient's
  app applies the link-encoded priority as the
  repo-modifier in their `references.toml` after
  upgrade. Per-recipient priority lives in the
  recipient's repo, not the source — so different
  recipients have different priority modifiers on the
  same source repo, conflict-free.

### SH-K.13 Receiver loses the device

- [ ] **SH-K.13** The receiver's device is lost/wiped.
  All local state files in `_local/state/` are gone
  unless the user had upgraded to an own-repo (in
  which case state is in the synced own-repo). The
  stored deploy-key for the gifted repo is also gone.
  The receiver re-installs on a new device, taps the
  original share-link again:
  - If the link hasn't expired: new clone succeeds
    with the same embedded token, repo re-added.
    State is empty unless own-repo synced.
  - If the link has expired (24h passed since
    original share): receiver asks author for a
    fresh link (SH-I.5).

  **Decision: this is fine. The system gracefully
  handles re-install via the same share-link
  protocol that handled first-install.** No special
  "device migration" path is needed for the gifted-
  repo-only use case.

### SH-K.14 Stale references in the receiver's own repo

- [ ] **SH-K.14** A receiver upgraded to an own-repo
  six months ago. One of the gifted repos in their
  `references.toml` has since been deleted upstream.
  On next pull of that gifted repo: 404. App
  surfaces:
  - Banner on the gifted repo: "**`<label>` is no
    longer available.** Was it moved? Try `skb state
    rebind` with the new URL. Or remove this
    schedule."
  - If the user has state files keyed by the deleted
    repo's source-repo-id, those are preserved per
    SH-D.5 orphan handling (30-day window before
    auto-prune).

---

## Cross-references

This document interlocks with:

- `decisions.md` D.41–D.52 — every section above
  elaborates one of these decisions.
- `main.md` Phases MM–TT — the phase-checkbox tracker
  for shipping this work.
- `data-model.md` DM-Q+ (forthcoming, SA-14) — the
  TOML schemas for `references.toml` and
  `state/<source-id>/<entity-id>.<kind>.toml`.
- `ui-spec.md` UI-FF+ (forthcoming, SA-15) — the
  layouts for the receive screen, share sheet,
  simplified-mode chrome, mini-wizard, mode-label
  picker, and onboarding card.
- `resolver.md` RV-L+ (forthcoming, SA-16) — the
  algorithms for state-file overlay merging,
  multi-repo priority resolution with modifiers,
  source-repo-id matching, and dedup logic.
- `cli-tooling.md` (existing, to be amended by
  SA-17) — the full flag-by-flag spec for `skb
  accept | ref | state | share | mode`.
- `templates-demo-wizard.md` — the role-toggle
  template screen reused as the optional template
  step in the SH-H mini-wizard.

## Notes on tick discipline

When each `SH-` phase ships, tick the checkbox and
append "shipped in change `<jj-change-id>`" to the
phase header. The corresponding `main.md` MM/NN/OO/PP/
QQ/RR/SS/TT checkbox ticks at the same time.

Plans that ship out of order are fine — the SH
phases are largely independent surface-area chunks.
Recommended order if shipping serially:

1. SH-A + SH-B (deep-link plumbing)
2. SH-C (references-manifest plumbing)
3. SH-D (state-files plumbing — depends on
   data-model.md DM-R)
4. SH-E (simplified-mode chrome)
5. SH-F (first-launch bootstrap — assembles A+B+C+E)
6. SH-G + SH-I (authoring share-flow + revocation)
7. SH-H (evolution path)
8. SH-J (CLI surface)
9. SH-K (edge cases — touched throughout, audited last)

Each phase is a self-contained piece of work for a
single subagent or single dev session.
