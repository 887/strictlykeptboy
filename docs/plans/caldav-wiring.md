# CalDAV mirror — finish-wiring plan

The CalDAV mirror engine (`app/src/main/java/com/eight87/strictlykeptboy/caldav/`,
~1665 LOC + ~510 LOC tests) was built under Phase Y but left **orphaned** — no
shell entry-point, no boot scheduling, OAuth providers stubbed. QA iter-4 made
it reachable; this plan tracks finishing it to the README's "any CalDAV server"
claim.

## Status: 🚧 IN PROGRESS

## What already exists (built + unit-tested)

- `caldav/discovery/` — PROPFIND + `.well-known` discovery, `PropfindParser`.
- `caldav/CalDavHttp` — OkHttp client with per-credential auth.
- `caldav/ical/` — `IcalCodec`, `IcalRepoMapper` (iCal ⇄ repo `.md` files).
- `caldav/sync/CalDavMirrorWorker` — one pull/push/bidi pass, ETag-journaled,
  `If-Match` conflict surfacing. `MockWebServer`-backed `CalDavMirrorWorkerTest`.
- `caldav/sync/CalDavScheduler` — per-mirror coroutine ticker + per-host rate limit.
- `caldav/store/CalDavMirrorStore` + `caldav/auth/CalDavSecretsStore` — encrypted-prefs.
- `caldav/ui/AddMirror{Screen,ViewModel,State}` — provider→credentials→discover→
  pick→mode→confirm wizard. `AddMirrorViewModel` is DI'd + unit-tested.

## Phase A — Reachable + honest (QA iter-4) — DONE

- [x] **A.1** `CalDavCategory` hosts the real `AddMirrorScreen` + lists mirrors
  (was a disabled "coming soon" button). Wired `reposFlow` at the SettingsPane
  call site. shipped in commit f2a633b
- [x] **A.2** Strings de-stubbed (no "coming soon"); README claim reconciled to
  what's actually wired vs in progress.

## Phase B — Make it sync (boot-wire the scheduler)

- [ ] **B.1** Build a `workerFactory: (CalDavMirror) -> CalDavMirrorWorker` in the
  composition root: resolve the mirror's repo `rootDir` (from `RepoStore`), build
  `CalDavHttp` from the stored credential (`CalDavSecretsStore`), construct
  `MirrorFileSink` over the repo's git layer.
- [ ] **B.2** Construct one app-scoped `CalDavScheduler(mirrorStore, workerFactory)`
  in `AppGraph`; call `startPeriodicTicks()` at boot; `close()` on teardown.
- [ ] **B.3** Add a per-mirror **"Sync now"** button in `CalDavCategory` →
  `scheduler.runOnce(mirrorId)`; surface `CalDavSyncResult` (incl. `Conflicted`).
- [ ] **B.4** `RECEIVE_BOOT_COMPLETED` re-arm parity with reminders (share the
  boot receiver) so periodic ticks resume after reboot.
- [ ] **B.5** On-device test against a real Nextcloud / Fastmail app-password
  account (mock-counterparty for CI): add mirror → pull populates repo files →
  edit on device → two-way pushes back.

## Phase C — OAuth providers (Google / Microsoft 365)

- [ ] **C.1** Wire `CalDavOAuthConfigs` device-flow into the credentials step so
  Google / M365 mirrors get a real `Bearer` token (today the wizard accepts a
  pasted token in the password slot — see `AddMirrorViewModel.buildCredential`).
- [ ] **C.2** Token refresh + storage parity with the git OAuth bindings.

## Phase D — Polish

- [ ] **D.1** i18n the `AddMirrorScreen` copy (currently hardcoded English) through
  `strings.xml` + `EnumLabels`.
- [ ] **D.2** Mirror management: edit interval, delete mirror (+ purge credentials
  + journal), pause toggle.
- [ ] **D.3** uk-nhs-nurse walk CalDAV-dependent steps un-blocked (handoff §2b).
