# Walk — Add a GitHub SSH remote to the phone-only repo (on-device ed25519), push, verify cross-device sync

**Persona:** Devon Reyes (see [`personality.md`](personality.md))
**Goal:** Two weeks in, grow the existing `devon-life` no-origin repo into
a synced one by adding a **GitHub remote over SSH**, generating an
**ed25519 keypair entirely on the phone**, pushing the existing local
history (no rewrite), and confirming a second device sees the same files
conflict-free.
**Preconditions:** `devon-life` exists locally with two weeks of real
data (the calendars + todolists + deviations from the prior walks). Devon
has an empty GitHub repo `devon/devon-life` already created on the web (or
will create it). Devon is signed into GitHub on their laptop browser.
**Features exercised:** grow-into-remote path (ZZ.G — `git remote add` +
`push -u`, no history rewrite), on-device ed25519 keygen (SE-C / D.7),
public-key export (copy / share / upload-to-provider), TOFU known-hosts
with baked github.com seed (SE-C.4/.5), conflict-free single-file-per-
entity sync (D.8), per-`(repoId, remoteName)` credential keying (ZZ.A/C).

---

## Part A — Open the add-remote flow

1. With `devon-life` active, Devon goes to **Settings → Repos → devon-life
   → Remotes** (or repo settings Remotes section, ZZ.G first-class
   no-origin treatment).
   → **Expect:** a Remotes section showing **"No remotes — this is a
   local-only calendar"** with an **Add remote** action. The local/house
   badge is shown.

2. Devon taps **Add remote**.
   → **Expect:** add-remote flow. Asks for: remote URL, transport
   (SSH / HTTPS), auth method. Since this is the first remote, defaults
   per ZZ.E: name `origin`, push policy `PUSH`.

3. Devon enters the SSH URL **`git@github.com:devon/devon-life.git`** and
   selects transport **SSH**.
   → **Expect:** the URL is parsed as SSH (host `github.com`); the auth
   options narrow to SSH-key methods (generate / reuse / import).
   ⚠ **Confusion watch:** Devon is comfortable with SSH on desktop but has
   never done it on a phone. The flow must make "you need a key, we can
   make one for you" obvious. Flag if the next step is ambiguous.

---

## Part B — Generate the ed25519 key on-device

4. Devon chooses **Generate a new SSH key on this device** (the default
   for a first SSH remote per ZZ.C: adding an SSH remote generates a new
   keypair by default, with opt-in reuse).
   → **Expect:** an ed25519 keypair is generated via BouncyCastle
   (SE-C.1). The **private** key is stored in EncryptedSharedPreferences
   keyed by `(repoId, remoteName)` i.e. `ssh.priv.<repo-id>.origin`
   (ZZ.A re-keying). The private key is NEVER shown or exportable.
   ⚠ **Confusion watch:** the UI must clearly distinguish "this is your
   PUBLIC key, safe to paste anywhere" from the private key. Privacy of
   the private key is load-bearing.

5. The public key is presented (SE-C public-key export UX) as
   `ssh-ed25519 AAAA... strictlykeptboy-<repo-id>` with three actions:
   **Copy**, **Share**, **Upload to provider**.
   → **Expect:** all three actions visible. "Upload to provider" is only
   live if an OAuth token with `admin:public_key` is present (it isn't yet
   — Devon authed nothing), so it's disabled/greyed with an explanation.
   ⚠ **Confusion watch:** if "Upload to provider" looks tappable but
   silently fails, flag it. Disabled-with-reason is the correct state.

6. Devon taps **Copy** (puts the public key on the clipboard) and **also**
   taps **Share → email to myself** so the key reaches their laptop.
   → **Expect:** clipboard set; share-sheet `ACTION_SEND` text intent with
   the public-key line.

---

## Part C — Register the key on GitHub (off-device)

7. On the laptop, Devon opens **GitHub → Settings → SSH and GPG keys →
   New SSH key**, titles it "strictlykeptboy devon-life", pastes the
   `ssh-ed25519 ...` line, saves.
   → **Expect (off-app):** GitHub accepts the public key. This step is
   outside strictlykeptboy; the walk just confirms the copied key is a
   valid single-line OpenSSH public key (SE-C.8 regex
   `ssh-ed25519 ...`).
   ⚠ **Confusion watch:** if the copied key has line wraps or a missing
   comment and GitHub rejects it, that's an export-format bug — flag it.

---

## Part D — Wire the remote and push the existing history

8. Back in the app, Devon confirms / finishes the add-remote flow.
   → **Expect:** `GitRepo.addRemote` records a `RemoteBinding`
   (name `origin`, url, transport SSH, authMethod ssh-key,
   fetchEnabled+pushEnabled true, pushPolicy PUSH). `primaryRemote`
   becomes `origin`; `repoId` is unchanged (ZZ.H invariant (iii): repoId
   stable across remote-set changes). The repo's house/local badge is
   replaced by its normal synced state.

9. Devon taps the **sync** button (or an explicit **Push** action). This
   is the first connection to github.com over SSH.
   → **Expect:** TOFU host-key verification runs. Because github.com is in
   the baked-in `known_hosts_seed.txt` (SE-C.5), the host key verifies
   against the seed silently — **no scary host-key prompt** for a
   first-party host.
   ⚠ **Confusion watch:** Devon should NOT get a "trust this unknown
   host?" dialog for github.com. If they do, the seed isn't loading.

10. The grow-into-remote push runs against the extant local history (ZZ.G:
    `git push -u origin main`, **no history rewrite**).
    → **Expect:** all two weeks of commits — calendars, todolists, the
    deviation files, the brain-dump — push to GitHub. `git log` history is
    preserved (same commit SHAs, nothing squashed). Local-only commits
    count (`GitStatus.localOnlyCommits`) drops to 0 after a successful
    push.
    ⚠ **Confusion watch:** verify nothing was rewritten — the persona
    explicitly cares that their existing local history is preserved, not
    re-initialized.

11. Devon verifies on the laptop: `git clone
    git@github.com:devon/devon-life.git` (or browse on GitHub).
    → **Expect:** the full D.3 layout present —
    `.strictlykeptboy/repo-id` (committed, ZZ.A), `AGENTS.md`,
    `CLAUDE.md` symlink round-trips, `calendars/`, `todolists/`,
    `deviations/`. Same `repo-id` value the phone uses.

---

## Part E — Cross-device, conflict-free sync

Devon wants the Pixel 6a desk phone (and the laptop) to show the same
schedule. Simulate a second device.

12. **Second device, second key.** On the Pixel 6a, Devon installs
    strictlykeptboy, and in the first-launch splash chooses **"Connect to
    a git remote now"**, entering the same SSH URL. They **generate a new
    ed25519 key on THIS device** and register it on GitHub as a second key
    (per the per-device key model — each device gets its own keypair;
    keys are not copied between devices, SE / "no need to copy a key").
    → **Expect:** Pixel 6a clones `devon-life` over SSH with its own key.
    Both devices' public keys are valid for the GitHub account
    independently.
    ⚠ **Confusion watch:** the app must NOT ask Devon to copy the private
    key from the Pixel 8. Per-device keygen is the intended model.

13. **Edit on device A, sync, pull on device B.** On the Pixel 8, Devon
    adds a focus block to "Deep work" for tomorrow and lets it sync (push).
    On the Pixel 6a, Devon taps sync (fetch + pull-rebase).
    → **Expect:** the new focus-block file appears on the Pixel 6a. Since
    it's a brand-new file (one file per entity, no index file, D.8),
    there is nothing to conflict with — clean fast-forward / rebase.

14. **Concurrent non-overlapping edits (the conflict-free claim).** On the
    Pixel 8, Devon edits the 09:00 block's body. On the Pixel 6a (before
    pulling), Devon adds a *different* task to the `errands` list. Both
    devices sync.
    → **Expect:** because the two edits touch **different files**, the
    pull-rebase merges cleanly with no conflict (D.8 conflict-free-by-
    design: one file per entity, no shared index). Both changes survive on
    both devices.
    ⚠ **Confusion watch:** any merge-conflict UI appearing for edits to
    *different* entity files would contradict the core design claim —
    flag immediately.

15. **(Stretch) Genuine same-file conflict.** On both devices, edit the
    SAME task's title before either syncs, then sync both.
    → **Expect:** a real conflict on that one file surfaces the 3-way diff
    conflict UI (SE-J), scoped to the single file, with the remote labeled
    by `origin`'s display name. Everything else still syncs clean.

---

## Pass criteria

- [ ] No-origin repo grows a remote with NO history rewrite; existing
      commits preserved.
- [ ] ed25519 key generated on-device; private key never shown/exported.
- [ ] Public key exports as a valid single-line OpenSSH key GitHub accepts.
- [ ] github.com verifies against the baked-in seed (no scary host prompt).
- [ ] Push ships all two weeks of data; `localOnlyCommits` → 0.
- [ ] Second device uses its OWN generated key (no private-key copying).
- [ ] Edits to different files merge conflict-free across devices.
- [ ] A genuine same-file edit surfaces a scoped 3-way conflict UI.

## QA notes / likely defects

- Step 4/5: private vs public key clarity; private must be unexportable.
- Step 9: host-key seed not loading → spurious TOFU prompt for github.com.
- Step 10: any history rewrite/squash on the grow-into-remote push.
- Step 14: false conflicts on different-file concurrent edits would break
  the headline "conflict-free by design" promise.
