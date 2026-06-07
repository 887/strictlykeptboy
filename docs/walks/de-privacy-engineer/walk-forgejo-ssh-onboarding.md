# Walk — Forgejo + SSH onboarding (Tobias, de-DE)

> First launch → German UI → setup wizard → on-device ed25519 keygen →
> add the public key to his self-hosted Forgejo → connect his personal
> repo `reichenbach/leben` over SSH → first sync.
>
> Persona: see `personality.md`. Device: Pixel 8, GrapheneOS, system
> locale **de-DE**, app installed via Obtainium. Forgejo at
> `git@forgejo.reichenbach.example:reichenbach/leben.git`.
>
> Feature grounding: wizard = `ui-spec.md` UI-O (7 screens); add-repo =
> UI-K.3 (6 steps); SSH keygen = `sync-engine.md` SE-C; TOFU host pin =
> SE-C.4; German UI = `translations.md` (values-de partial overrides).
>
> Format: numbered test-script steps, "he taps X → expects Y".
> ⚠️ flags a confusion point worth QA attention.

## Pre-conditions

- Fresh install, app data cleared (first launch).
- System locale already de-DE (set before install).
- Forgejo instance up; Tobias is logged into its web UI on his laptop.
- No repos configured yet.

## Steps

1. He launches strictlykeptboy for the first time → expects the app to
   route **directly into the Wizard** (UI-O), not a blank schedule
   (per UI-spec: first run with no repo → Wizard).

2. He reads screen **O.1 — Welcome**: bat-in-hoodie mascot, tagline.
   → expects the chrome to be in **German where translated**, English
   fallback otherwise. The tagline "Your schedule, kept in Git." may
   still render English if that key isn't in `values-de/` — that's
   acceptable per the partial-override design.
   ⚠️ QA: confirm there are no `???` or raw resource names
   (`o1_tagline`) showing — fallback must be clean English, never a
   missing-string artifact.

3. He taps **Next →** → expects screen **O.2 — Path picker** with three
   cards: *Start with demo*, *Start from templates*, *Empty repo*.

4. He is a privacy purist and does **not** want the SFW-but-charged demo
   content. He taps **Empty repo** ("Just the scaffolding. You fill it
   in.") → expects the wizard to skip the role-toggle screen (O.3 is
   Templates-path only) and advance toward repo setup.
   ⚠️ QA: verify O.3 (role toggles) is genuinely skipped on the Empty
   path — the progress dots should reflect a shorter path, and he must
   never see kink-coded template chips he didn't ask for.

5. At the repo step he chooses to connect his **own existing repo** (not
   create-new, not no-origin) → expects to land in the Add-repo flow
   (UI-K.3), Step 1 — Provider.

6. **Provider step**: options [GitHub] [Forgejo (gitea-compatible)]
   [Generic Git URL]. He taps **Forgejo (gitea-compatible)** → expects
   to advance to the Repo step.
   - (He could also use *Generic Git URL* since it's pure SSH, but
     Forgejo is the labelled, correct choice and unlocks the
     OAuth-based public-key auto-upload option later.)

7. **Repo step**: he selects **"Use existing repo"** and pastes the SSH
   remote `git@forgejo.reichenbach.example:reichenbach/leben.git`
   → expects the URL accepted and recognized as an **SSH** remote
   (scp-style `user@host:path`), advancing to the Auth step.
   ⚠️ QA: the scp-style SSH syntax (no `ssh://` scheme, colon before
   path) must parse correctly and be classified as SSH, not
   misread as HTTPS/generic.

8. **Auth step**: he picks **SSH** → expects the SSH sub-flow offering
   "generate ed25519" + show public key + copy/share + an
   "I added it to my account / Auto-add via OAuth scope" option.

9. He taps **Generate ed25519 key** → expects the app to generate the
   keypair **on device** (BouncyCastle `KeyPairGenerator "Ed25519"`,
   per SE-C), store the **private** key in `EncryptedSharedPreferences`
   keyed `ssh.priv.<repo-id>`, and **never** offer to upload or display
   the private half.
   ⚠️ QA — privacy-critical: confirm the **private key is never shown,
   never copyable, never in a share intent**. Only the public key gets
   the three actions. A privacy user will check this explicitly.

10. → expects the **public key** to display as a single line
    `ssh-ed25519 AAAA… strictlykeptboy-<repo-id>` with three actions:
    **Copy**, **Share**, **Upload to provider**.

11. Since this is his self-hosted Forgejo and he has *not* done an OAuth
    flow (he's an SSH-only purist), he taps **Copy** → expects the full
    public-key line on the clipboard (`ClipboardManager`).
    ⚠️ QA: "Upload to provider" should be **disabled / greyed** here
    because no OAuth token with Forgejo `write:user` scope is present
    (SE-C.7). It must not silently fail or no-op without explanation —
    show why it's unavailable.

12. He KDE-Connects the clipboard to his laptop (his real-world habit),
    opens his Forgejo web UI → **Settings → SSH Keys → Add Key**, pastes
    the public key, titles it "strictlykeptboy leben", saves
    → (out-of-app step; expects the key now registered server-side).

13. Back in the app he taps the SSH-flow's **"I added it to my account"**
    confirmation → expects to advance to the Identity step (UI-K.3
    Step 4).

14. **Identity step**: the empty repo has no `identities/` yet, so he
    **creates a new identity** — display name "Tobias", his email, no
    avatar (he doesn't want a photo on device) → expects this written to
    the repo's `identity.toml` on first commit, and selected as the
    active identity for `leben`.

15. **Defaults step** (Step 5): the empty-scaffold repo has the starter
    calendars/todolists. He sets a default calendar, a default todolist,
    and **Auto-sync interval = 15m** → expects the choices saved.
    ⚠️ QA: an *empty/scaffold* repo may have zero user calendars yet —
    verify the default-calendar dropdown handles the
    "only-scaffold-defaults" case gracefully (no empty crash, sensible
    placeholder).

16. He taps **Done** (Step 6) → expects the app to attempt the **first
    SSH connection** to his Forgejo to clone/push.

17. **First-ever SSH connection → TOFU host-key prompt** (SE-C.4). His
    Forgejo is NOT one of the baked-in first-party hosts (github.com,
    gitlab.com, codeberg.org, gitea.com), so it has no seed → expects a
    **Trust-On-First-Use prompt showing the host key fingerprint** for
    `forgejo.reichenbach.example`.
    ⚠️ QA: this is the moment a privacy user judges the app. The
    fingerprint must be **shown and verifiable** before trust, not
    auto-accepted. He compares it against `ssh-keygen -lf` output from
    his laptop.

18. He verifies the fingerprint matches his server and taps
    **Trust / Pin** → expects the host key pinned in `known_hosts.json`
    and the connection to proceed.

19. → expects the first sync to run: clone the (near-empty) repo,
    write the `identity.toml` + first commit if needed, push over SSH
    using the generated key (resolved via the per-repo `ThreadLocal`
    identity injection, SE-C.3/.6).

20. → expects the repo to appear in the **top-bar repo switcher** with a
    **green dot** once first sync completes (UI-K.3 Step 6), and the app
    to land on the **Schedule** screen for `leben`.
    ⚠️ QA: confirm the repo-switcher chip and left nav rail render and
    that the green "synced" badge (one of the 5 sync states, UI-K.2)
    appears, not a stuck spinner.

21. He pulls down / taps the **manual sync button** in the top bar once
    more → expects a no-op-clean sync (already up to date), proving the
    SSH transport is wired and the key is being read from
    encrypted storage.

22. He force-quits and relaunches the app → expects it to land on the
    `leben` Schedule directly (no wizard), repo still present, sync
    still working — i.e. the encrypted key + host pin **survived a
    cold start** (they live in Keystore-backed prefs, SE-C storage).
    ⚠️ QA: confirm secrets persist across process death (but are
    expected to NOT survive uninstall — that's by design, SE-C
    storage note; out of scope for this walk but worth a one-line
    confirmation in the test report).

## Success criteria

- App ran first-launch → Wizard → Empty-repo path with no demo/template
  content forced on him.
- Chrome shown in German where translated, clean English fallback
  elsewhere, zero missing-string artifacts.
- ed25519 keypair generated **on device**; private key never displayed,
  copied, or shared; only the public key exported.
- Public key copied; "Upload to provider" correctly disabled (no OAuth)
  with a clear reason.
- TOFU host-key fingerprint **shown and verified** before pinning.
- `reichenbach/leben` connected over SSH to his **self-hosted Forgejo**,
  first sync green, zero bytes to any third party.
- Secrets + host pin survive a cold start.

## Confusion points collected for QA

- Step 2: German fallback must be clean (no `???`/raw keys).
- Step 4: Empty path must truly skip role toggles; no stray kink chips.
- Step 7: scp-style SSH URL must classify as SSH.
- Step 9/11: private key must be uncopyable/unshareable; "Upload to
  provider" disabled-with-reason when no OAuth.
- Step 15: scaffold-only repo → default-calendar dropdown edge case.
- Step 17: self-hosted host (no seed) → TOFU fingerprint must be shown.
- Step 22: secrets persist across cold start.
