# Draft — No-origin and multi-origin Git layer

## Status: ✅ INTEGRATED — see `main.md` Phase ZZ (and inline edits to B / I / J / NN / PP / QQ / K) and `decisions.md` D.74. Sync-engine edits in `sync-engine.md` Phase SE-Y. Original draft content preserved below for reference.

## Framing

The current locked plan (`main.md` Phases B / I / J, deep-dive
`sync-engine.md` Phases SE-A through SE-L, plus the shared-schedules
phases NN / PP / QQ) implicitly assumes every repo is configured
against exactly one remote, conventionally referred to as `origin`,
typically `origin/main`. Concretely:

- `RepoConfig` (`sync-engine.md` SE-F) carries a single `remoteUrl`
  and a single `authMethod`.
- `GitRepo.fetch` / `pullRebase` / `push` (`sync-engine.md` SE-B.4..B.6)
  hard-code `setRemote("origin")` and `setUpstream("origin/main")`.
- The Add-repo flow (`main.md` I.2) requires a URL.
- `SecretsStore` key naming (`sync-engine.md` SE-C "Storage") namespaces
  every credential by `<repoId>` where `repoId = sha256(remoteUrl)` —
  i.e. one credential set per repo, indexed by *the* URL.
- The wizard (Phase K) and first-launch deep-link bootstrap (Phase QQ)
  both produce a repo with exactly one configured remote.

This draft introduces two orthogonal extensions:

1. **No-origin repos** — git-backed repos with zero configured remotes
   (the full commit-history, exception files, D.3 layout — all of it —
   but nothing to fetch from or push to). First-class state, not a
   degraded mode.
2. **Multi-origin sync** — repos with N ≥ 2 configured remotes, with
   real fan-out push policy, fan-in fetch+merge semantics, per-remote
   auth bindings, and conflict UX for the diamond.

These extensions touch (and only touch) the Git infrastructure phases.
They do not change the on-disk D.3 layout, do not change the resolver,
do not change Room cache, and do not change the markdown/TOML schema.

Slot-in points in existing plans:

| Existing phase | What this draft modifies |
|---|---|
| `main.md` B (Phase B) | B.2: GitRepo gains no-remote constructor + per-remote methods. B.3: RepoStore stores `List<RemoteBinding>` per repo. B.7: PAT entry is one-per-remote. |
| `main.md` I (Phase I) | I.2: Add-repo flow gets a "no remote, local only" branch and a "+ add another remote" affordance. I.3: Repo settings exposes the remotes list. |
| `main.md` J (Phase J) | J.2/J.3: scheduler iterates remotes per repo. J.5/J.6: conflict UI handles N-way diamonds. J.7: read-only is per-remote, not per-repo. |
| `sync-engine.md` SE-B | All `"origin"` literals become `RemoteName` parameters; new `addRemote`/`removeRemote`/`renameRemote` operations. |
| `sync-engine.md` SE-C/D/E | Credentials keyed by `(repoId, remoteName)`, not just `repoId`. |
| `sync-engine.md` SE-F | `RepoConfig` gains `List<RemoteBinding>` and `primaryRemote: RemoteName?` (null = no-origin). |
| `sync-engine.md` SE-I | Pull strategy becomes fetch-all-remotes → reconcile. |
| `sync-engine.md` SE-K | Push strategy gets per-remote policy. |
| `main.md` NN (`references.toml`) | NN.2 reader / NN.3 writer aware of multi-origin entries. |
| `main.md` PP (Simplified mode) | PP.2: hide remotes list in simplified mode for no-origin repos. |
| `main.md` QQ (First-launch) | QQ.1: cover the "no deep-link AND user picks local-only" branch. |

---

## Phase MO-A — No-origin: data model + GitRepo surface

The foundation. Make every layer below the UI tolerate zero remotes.

- [ ] **MO-A.1** `RepoConfig` (currently in `sync-engine.md` SE-F.1): replace `remoteUrl: String` + `transport` + `authMethod` with `remotes: List<RemoteBinding>` (may be empty) and `primaryRemote: RemoteName?` (null iff `remotes.isEmpty()`). `RemoteBinding` carries `name: RemoteName`, `url: String`, `transport: Transport`, `authMethod: AuthMethod`, `fetchEnabled: Boolean`, `pushEnabled: Boolean`, `readOnlyDetected: Boolean`. `repoId` stays SHA-256 of the repo's local path UUID (NOT of any URL — see MO-A.4), removing the hidden coupling to a single URL.
- [ ] **MO-A.2** `GitRepo` constructors: add `GitRepo.initLocalOnly(rootDir, authorIdentity)` that does `git init` with no `remote add`. The existing `init`/`clone` constructors keep working but now accept `remotes: List<RemoteBinding> = emptyList()`.
- [ ] **MO-A.3** Methods that currently hard-code `"origin"` (SE-B.4 `fetch`, SE-B.5 `pullRebase`, SE-B.6 `push`) gain a `remote: RemoteName? = primaryRemote` parameter. When `remotes.isEmpty()`, calling any of these returns `FetchResult.NoRemotes` / `PullResult.NoRemotes` / `PushResult.NoRemotes` (new sealed-variant cases) without throwing — local commit/log/status/diff continue to work.
- [ ] **MO-A.4** `repoId` derivation: stop using `sha256(remoteUrl)`. Generate a UUIDv7 at `init`/`clone` time, persist in `.strictlykeptboy/repo-id` (single-line, gitignored locally OR committed — locked decision needed: see open questions). This breaks SE-C "Storage" key naming, fixed in MO-A.5.
- [ ] **MO-A.5** `SecretsStore` key migration: re-key from `<kind>.<repoId>` to `<kind>.<repoId>.<remoteName>`. Add a one-shot migration that rewrites existing keys with `remoteName = "origin"`. Document in `sync-engine.md` Phase SE-C "Storage" section.
- [ ] **MO-A.6** `GitStatus`: extend with `localOnlyCommits: Int` (commits not present on ANY remote). For no-origin repos this equals total commit count; for normal repos it equals `commitsAhead` of the primary remote; for multi-origin it equals commits not yet pushed to *all* push-enabled remotes.
- [ ] **MO-A.7** Robolectric tests: init a no-origin repo, commit 3 files, verify `status()` works, `log()` returns 3 commits, `push(null)` returns `NoRemotes`, `pullRebase(null)` returns `NoRemotes`.

---

## Phase MO-B — Multi-origin: remote naming + management API

Decide naming and expose remote CRUD on `GitRepo`.

- [ ] **MO-B.1** **Remote naming decision (LOCK):** named-by-purpose. Conventions: `origin` for the canonical pushing destination (kept as a default name to minimize cognitive load and to remain compatible with users opening the repo with stock git on a desktop), additional remotes named `mirror-<n>` by default with a user-editable display label stored in `RemoteBinding.displayName`. **No semantic meaning attached to remote name** — the name is just a git-level identifier, and policy (`primary` / `push-target` / `fetch-only`) is carried in `RemoteBinding` fields. Document this in `decisions.md` as a new locked decision (drafting note: add to integration list).
- [ ] **MO-B.2** `GitRepo.addRemote(binding: RemoteBinding)`, `removeRemote(name: RemoteName)`, `renameRemote(from, to)`, `listRemotes(): List<RemoteBinding>`. Wraps `git.remoteAdd()` / `remoteRemove()` / `remoteSetUrl()` plus `RepoConfig` persistence. Mutually exclusive lock with sync ops via the existing per-repo `Mutex` (SE-B.10).
- [ ] **MO-B.3** Reserved-name guard: refuse remote names containing `/`, whitespace, or matching ref-pattern characters; refuse the literal string `HEAD`. Refuse duplicate names.
- [ ] **MO-B.4** Per-remote refspec: each `RemoteBinding` stores `fetchRefspec: String = "+refs/heads/*:refs/remotes/<name>/*"`. v1 keeps the default; the field exists so the multi-branch phase (SE-U) and any future shallow/mirror config doesn't require a schema change.
- [ ] **MO-B.5** `references.toml` (`main.md` NN) integration: extend the schema to allow `remotes = ["url1", "url2"]` (array) in addition to the current single-URL form. NN.1 (schema) + NN.2 (reader) + NN.3 (writer) all updated. Backwards-compat: a string-valued `url = "..."` is read as a one-element list. Receivers offered the repo via deep-link can opt into adding all listed remotes, or just the first.
- [ ] **MO-B.6** Robolectric tests: open a no-origin repo, add two remotes, verify `git config --list` shows both; rename one; remove one; verify config matches.

---

## Phase MO-C — Multi-origin: per-remote auth binding

Auth has been single-credential-per-repo. Each remote may use entirely different auth (e.g. SSH key for the primary forge, HTTPS+OAuth token for a mirror).

- [ ] **MO-C.1** `CredentialBinding` (SE-F.3): change from a per-repo resolver to a `(repoId, remoteName) → CredentialBinding` resolver. Cache key updated. Thread-local repo-id used by `StrictlyKeptBoySshSessionFactory` (SE-C.3) becomes a thread-local `(repoId, remoteName)` pair.
- [ ] **MO-C.2** `SecretsStore` key layout (already migrated in MO-A.5): `ssh.priv.<repoId>.<remoteName>`, `oauth.token.<repoId>.<remoteName>`, etc. SSH keypair generation (SE-C.1) becomes per-remote — adding a second remote that uses SSH generates a *new* keypair for that remote unless the user explicitly says "reuse the keypair from `<other-remote>`" in the add-remote UI.
- [ ] **MO-C.3** Per-remote auth method UI: in the add-remote flow (and repo-settings remotes list), each row independently selects `OAuthGitHub | OAuthForgejo | ManualPat | SshKey | None (public read-only)`. The currently-locked Phase B / Phase I screens assume one auth choice per repo; this phase delivers the per-remote variant.
- [ ] **MO-C.4** `transportConfigCallbackFor(repoId, remoteName)` replaces the existing `transportConfigCallbackForSsh(repoId)` and `transportConfigCallbackForHttps(repoId)` factories (SE-C.6, SE-D analogous). The orchestrator passes both ids when invoking transport.
- [ ] **MO-C.5** Re-auth error surfaces (SE-L "User-facing messaging matrix") become per-remote: `Auth(provider, remoteName)`. Banner text gains the remote display label, e.g. "GitHub session for `mirror-family` expired — sign in again". Recovery flow re-runs Device Flow scoped to that remote only.
- [ ] **MO-C.6** Test: a repo with `origin` over SSH and `mirror-1` over HTTPS+OAuth — verify fetch from each uses its own credential and that revoking the OAuth token does not impact SSH fetches.

---

## Phase MO-D — Multi-origin: fetch + reconcile algorithm

Replace the SE-I "fetch from origin, rebase onto origin/main" pipeline with N-way fetch + reconcile.

- [ ] **MO-D.1** Fetch pass: for each remote `r` in `repo.remotes` where `r.fetchEnabled`, run `git.fetch().setRemote(r.name)`. Collect a `Map<RemoteName, FetchResult>`. Network errors on one remote do not abort the others; they are accumulated and surfaced per-remote.
- [ ] **MO-D.2** **Reconcile policy (LOCK):** designate one remote as `primaryRemote` (settable in repo settings; defaults to the first remote added). The local branch is rebased onto `<primaryRemote>/<branch>`. Non-primary remotes' tips are surfaced as **divergence signals** but not auto-merged. Reasoning: real fan-out diamonds are rare in practice (the user's own multiple mirrors usually all receive the same pushes), and when they DO diverge it's almost always because a mirror got an out-of-band write that the user must see and decide about.
- [ ] **MO-D.3** Divergence detection: after fetch, for each non-primary remote `r`, compute `commitsBehind = revWalk(<r>/<branch>..HEAD).count`, `commitsAhead = revWalk(HEAD..<r>/<branch>).count`. When `commitsAhead > 0` (mirror has commits we don't), raise a `MirrorDivergence(remoteName, count)` signal — non-fatal, surfaced as a yellow banner in repo settings ("`mirror-family` has 3 commits not on your primary — review or merge").
- [ ] **MO-D.4** Manual diamond merge UI (deferred bulk of work to MO-F): the divergence banner has a "review" action that lists the divergent commits and offers two recovery paths — (a) *adopt mirror as authoritative* (rebase local + push to primary), (b) *override mirror* (force-push from primary to the mirror after explicit confirmation). v1 ships (a) only; (b) is gated behind a Settings → Advanced toggle and a typed confirmation.
- [ ] **MO-D.5** SE-I "Algorithm" rewrite: step 1 becomes "fetch all fetchEnabled remotes"; step 2 becomes "reconcile against primary"; step 2.5 (new) "compute divergence for non-primary remotes"; step 3 (push) deferred to MO-E.
- [ ] **MO-D.6** Test: 3-remote repo where mirror-1 has a commit not present on origin; verify fetch succeeds on both, rebase onto origin completes, `MirrorDivergence` is emitted for mirror-1, no auto-merge occurs.

---

## Phase MO-E — Multi-origin: push fan-out policy

Push needs to handle N remotes with different policies and partial failure.

- [ ] **MO-E.1** **Push policy (LOCK):** per-remote `pushPolicy` field on `RemoteBinding`. Values: `PUSH` (push every sync), `PUSH_LAZY` (push only on manual sync or post-commit, not on scheduled ticks), `NEVER` (read-only mirror). Default for the first remote added is `PUSH`; default for additional remotes is `PUSH_LAZY`. Justification: covers the "primary forge + slow remote backup" pattern without surprising users with bandwidth use.
- [ ] **MO-E.2** Push pass: iterate remotes in order `[primary, then non-primary by add-time]`. For each remote `r` where `r.pushPolicy.shouldPushNow(triggerKind)`, attempt push. Collect a `Map<RemoteName, PushResult>`.
- [ ] **MO-E.3** Partial-failure semantics: if push to primary succeeds but push to `mirror-1` fails (auth, network, branch protection), the local commit is considered "shipped" (it's on the primary), `mirror-1` enters a per-remote retry queue (WorkManager exponential backoff per SE-L), and a `PartialPushDegraded(remoteName, reason)` signal goes up. UI shows a small dot on the mirror's row, not a full red banner — the primary succeeded.
- [ ] **MO-E.4** Conversely: if push to primary fails, treat as the existing SE-K push-rejected flow (block subsequent push attempts to that primary until resolved). Do NOT try to push to non-primaries when primary failed — that risks the mirror getting ahead of the canonical source.
- [ ] **MO-E.5** `readOnlyDetected` (SE-K) is now per-remote (`RemoteBinding.readOnlyDetected`). The repo-level "this repo is read-only" banner (J.7) only fires when *every* remote is read-only OR the repo is no-origin and the user has never been offered an "add remote" CTA.
- [ ] **MO-E.6** SE-L error taxonomy update: `PushRejected(reason, remoteName)`. Recovery action UI gains the remote label.
- [ ] **MO-E.7** Test: 3-remote repo, mirror-2 returns 403 on push, primary + mirror-1 succeed; verify primary is up to date, mirror-1 is up to date, mirror-2 has degraded marker, repo as a whole is not red-banner'd.

---

## Phase MO-F — Multi-origin: conflict UI for N-way diamonds

The existing SE-J 3-way diff UI assumes one upstream side. With multiple remotes we may have multiple sides of divergence simultaneously.

- [ ] **MO-F.1** Confirm scope: in practice, conflict during the SE-I rebase happens between local and the PRIMARY remote only (MO-D.2). Non-primary divergence does not enter the conflict UI; it enters the divergence-banner UI from MO-D.3. So the SE-J conflict UI itself needs minimal change.
- [ ] **MO-F.2** Conflict UI labels (SE-J `oursContent` / `theirsContent`): "remote" becomes the primary remote's display label, e.g. `theirsContent` shown as "Remote (`origin` — strictlykeptboy@gitea.example.com)".
- [ ] **MO-F.3** Diamond-merge mini-flow (referenced by MO-D.4 path-a): when the user picks "adopt mirror as authoritative", the orchestrator runs a virtual rebase of local commits onto `<mirror>/<branch>`. If that rebase conflicts, the standard SE-J conflict UI fires, again with the mirror's label shown in place of "remote". This reuses the entire conflict pipeline without inventing a 4-way UI.
- [ ] **MO-F.4** Force-push affordance (MO-D.4 path-b) lives in repo settings → Remotes → `<remote>` → Advanced. Confirmation requires typing the remote URL. Force-push is implemented via `git.push().setForce(true).setRemote(remoteName)`. **No force-push to primary, ever, in v1.** (Even with the typed confirmation gate, this remains a footgun; deferred to v2 or never.)
- [ ] **MO-F.5** Test: contrived divergence where local, primary, and mirror-1 all have different commits on the same file; verify rebase-onto-primary conflict fires the SE-J UI with primary's label; verify "adopt mirror" path fires a second SE-J pass with mirror's label.

---

## Phase MO-G — No-origin: UI representation + grow-into-remote path

The UI surfaces (Phase I, Phase K, Phase QQ, Phase PP) need first-class no-origin treatment.

- [ ] **MO-G.1** Add-repo flow (`main.md` I.2) gains a top-level branch selector: "Create local-only" vs "Connect to a remote". Local-only path goes directly to identity + default-calendar selection — no provider, no URL, no auth. This is offered as a *peer* option, not buried under "advanced".
- [ ] **MO-G.2** Repo switcher (I.1) sync-status badge for no-origin repos: a small house icon ("local") instead of a sync/error badge. Tap shows "this calendar lives only on this phone — tap to add a remote".
- [ ] **MO-G.3** Repo settings (I.3) Remotes section: when `repo.remotes.isEmpty()`, the section header says "No remotes — this repo lives only on this device" with a prominent "Add a remote" button. When non-empty, lists remotes with per-remote rows (URL, transport, auth method, push policy, last-sync, error). Each row has "Edit" and "Remove" + the section has "+ Add another remote".
- [ ] **MO-G.4** Add-remote-later flow: opening a no-origin repo's "Add a remote" creates the first `RemoteBinding`, then offers an initial push. JGit handles this cleanly (`git remote add origin <url>` + `git push -u origin main` even with extant local history works without rewriting). Explicit note in this draft: history is NOT rewritten; the full local commit graph is what gets pushed. The plan should make this explicit in the UI copy too ("your existing history will be pushed as-is").
- [ ] **MO-G.5** Simplified mode (`main.md` PP) — PP.2: when the active repo is no-origin, ALL remote/sync UI is hidden including the sync button in the top bar (there is nothing to sync). The sync-status badge becomes a permanent "local" badge. PP.6 auto-entry condition extends to: "simplified by default when only read-only repos OR only no-origin repos are configured and the user has no own remote repo".
- [ ] **MO-G.6** First-launch (`main.md` QQ) — QQ.1: when launched WITHOUT a deep-link AND with no repos, the user is shown a 2-option splash: "Start a local calendar (you can sync it later)" vs "Connect to a git remote now". The local path goes straight to a usable empty calendar in under 5 seconds — this is the simplified-mode default per PP.6.
- [ ] **MO-G.7** Wizard (`main.md` K.1) — the "repo picker" + "auth" steps become skippable; if skipped the wizard runs the local-only scaffolder, creating a repo via `GitRepo.initLocalOnly` (MO-A.2). K.4 "Initial commit + push" becomes "Initial commit (+ push if any remote configured)".

---

## Phase MO-H — Cross-cutting: tests, docs, CLI parity

Sweep the supporting surfaces.

- [ ] **MO-H.1** Update `data-model.md` (no schema change but a note: the on-disk D.3 layout is unchanged, no `origin`-related files exist on disk, all remote state is in the per-device `RepoConfig`).
- [ ] **MO-H.2** Update `cli-tooling.md` for `skb`: `skb repo init --local` (no-origin), `skb remote add <name> <url>`, `skb remote remove <name>`, `skb remote list`, `skb remote set-primary <name>`, `skb remote set-policy <name> push|push-lazy|never`. Maintains git-CLI familiarity.
- [ ] **MO-H.3** Update `sync-engine.md` SE-O "Testing strategy": add a no-origin test (init local, commit, verify no network ops attempted on sync tick) and a multi-origin test (two `git init --bare` directories standing in as two remotes, push/fetch to both, simulate partial failure).
- [ ] **MO-H.4** Update `sync-engine.md` SE-P "Edge cases + invariants" with new invariants: (i) `repo.remotes.isEmpty() ⇒ no foreground SyncService work for that repo`, (ii) `primaryRemote != null ⇔ remotes.isNotEmpty()`, (iii) `repoId is stable across remote-set changes`.
- [ ] **MO-H.5** Robolectric end-to-end: create no-origin repo, add origin, add mirror-1, push to both, simulate mirror-1 going read-only, verify primary still pushes, remove mirror-1, verify repo still functions.

---

## Integration notes

When this draft is approved and ready to fold into `main.md`:

1. **`docs/plans/main.md`**:
   - Renumber `MO-A`..`MO-H` into the existing letter sequence (likely as new phases between B and J, or as a contiguous block after VV in the v1 extension area).
   - Edit B.2 to reference `GitRepo.initLocalOnly` and per-remote operations.
   - Edit B.3 to note `RepoConfig` now carries `List<RemoteBinding>`.
   - Edit B.4/B.5/B.6/B.7 to clarify auth is per-remote.
   - Edit I.2 to include the local-only branch.
   - Edit I.3 to include the Remotes section.
   - Edit J.1/J.2 to iterate remotes per repo.
   - Edit J.5/J.6 to add the diamond-merge mini-flow.
   - Edit J.7 to scope read-only to per-remote.
   - Edit NN.1/NN.2/NN.3 for multi-remote `references.toml`.
   - Edit PP.2/PP.6 for no-origin auto-entry.
   - Edit QQ.1 for the no-deep-link local-only first-launch path.
   - Edit K.1/K.4 for the local-only wizard path.

2. **`docs/plans/sync-engine.md`**:
   - SE-B: replace `"origin"` literals; add `addRemote`/`removeRemote`/`renameRemote`.
   - SE-C/D/E: rekey credentials by `(repoId, remoteName)`.
   - SE-F: `RepoConfig` schema migration.
   - SE-I: rewrite algorithm for fetch-all + reconcile-against-primary.
   - SE-J: minor label changes only.
   - SE-K: per-remote push policy + partial-failure handling.
   - SE-L: `PushRejected` and `Auth` gain `remoteName`.

3. **`docs/plans/decisions.md`**:
   - Add a new locked decision: "Remote naming is per-purpose with `origin` as the conventional primary; policy is carried in `RemoteBinding`, not in the name."
   - Add: "Reconcile against `primaryRemote` only; non-primary remotes generate divergence signals, not auto-merges."
   - Add: "Default push policy is `PUSH` for the primary and `PUSH_LAZY` for additional remotes."
   - Add: "No-origin repos are a first-class state, equal in standing to single-origin and multi-origin repos."
   - Add: "Force-push to the primary remote is prohibited in v1; force-push to non-primary remotes is gated behind a typed-confirmation Advanced toggle."

4. **`docs/plans/shared-schedules.md`**:
   - Note that share-link generation (Phase RR) may produce links carrying multiple URLs (one per remote of the shared repo); the receiving side's link parser already supports `?url=A&url=B` per MM.6, so no parser change is needed, but the share-config sheet (RR.2) gains a "include mirror remotes in the link" toggle (default off — privacy).

5. **`docs/plans/cli-tooling.md`**: add the `skb remote ...` subcommands listed in MO-H.2.

## Open questions for the user before integration

1. **`.strictlykeptboy/repo-id` placement (MO-A.4):** committed to the repo (stable cross-device) or local-only (cleaner repo, but each device generates its own)? Trade-off: committing makes cross-device state-file references (Phase OO) trivially consistent; not-committing keeps the repo cleaner but requires deriving a stable id from some other invariant (the repo's earliest commit SHA?).
2. **Remote naming default (MO-B.1):** confirm `origin` + `mirror-<n>` is acceptable. Alternative was strictly named-by-purpose with no `origin` default. The proposed lock keeps `origin` for stock-git interop.
3. **Fan-in reconcile default (MO-D.2):** confirm "reconcile against primary only" is the right default. Alternative is "fast-forward against any remote that fast-forwards, signal divergence only when truly diamonded", which is more permissive but harder to reason about.
4. **Force-push gate (MO-F.4):** is the typed-confirmation Advanced toggle acceptable, or should force-push to non-primary remotes also be entirely v2-deferred?
5. **Per-remote SSH keys (MO-C.2):** generating a separate keypair per remote is more secure (revocable independently) but slightly more setup friction. Confirm default is "new keypair per remote, opt-in to reuse" rather than the inverse.
6. **`references.toml` multi-remote field name (MO-B.5):** `remotes = [...]` (array) vs keeping `url` singular and adding `mirrors = [...]`. The former is cleaner; the latter is more backwards-compatible.
