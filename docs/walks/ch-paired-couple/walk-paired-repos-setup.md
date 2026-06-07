# Walk — paired repos setup (both partners, first launch)

> Persona: **Lena Hofmann** (kept partner, repo `lena-routine`) and
> **Théo Brunner** (keeper, repo `theo-calendar`), Zürich. See
> `personality.md`.
>
> Goal of this walk: from two fresh installs, each partner creates their
> own repo, **shares it with the other with write access**, and the app
> correctly attributes each event to its author. Strictly-kept mode +
> `identity.toml` content is the *next* walk
> (`walk-strictlykept-mode-and-identity.md`); here we only get to the
> point where both repos exist and are mutually shared.
>
> Format: numbered test-script steps. "Tap X → expect Y." Confusion
> points flagged **[QA]** — those are the gold.

---

## Preconditions

- Two devices (or two AVDs), strictlykeptboy freshly installed on each.
- Lena: GitHub account, intends **SSH** auth. Théo: GitHub account,
  intends **HTTPS + OAuth device flow**.
- A private GitHub repo will back each side (created via the wizard's
  remote step, or pre-created on GitHub).

---

## Part A — Théo's first launch (free mode, HTTPS/OAuth)

1. Launch the app for the first time → **expect** the **age gate** modal
   (D.61): "This app contains references to adult lifestyle dynamics. You
   must be 17 or older to use it." with **"I am 17 or older — continue"**
   / **"Exit"**.
2. Tap **continue** → **expect** the modal dismisses once and never
   reappears (stored as `age_confirmed_at`). **[QA]** confirm it does not
   re-prompt on next launch.
3. **Expect** the **mandatory first-launch wizard** (D.54) — there is no
   empty-app state; the wizard is the only path forward. Bat-mascot guide
   on each screen.
4. Wizard storage screen → choose where the app stores data
   (Internal vs External/SAF). **Expect** the flow **blocks** until Théo
   explicitly chooses (D-2.17.a). Théo picks **Internal**. **[QA]** verify
   you cannot skip past this.
5. Wizard avatar / species screen → Théo picks a species sticker (say
   **wolf**) or leaves the default **bat**. **Expect** this becomes his
   repo's top-bar avatar (D.88).
6. Wizard **alignment** screen → options **Dominant / Submissive /
   Switch / Unaligned-private** (D.55). Théo taps **Dominant**.
7. **Expect** the wizard to **SKIP the honorific-for-dom field** on the
   next (praise + pronouns) screen, because alignment = Dominant
   (TW-K.2). **[QA]** verify the honorific field is absent, not just empty.
8. Praise + pronouns screen → Théo sets pronouns **he/him**, leaves
   praise empty/neutral, tone **warm**. Tap next.
9. Wizard remote / sync screen → choose **GitHub → HTTPS (OAuth device
   flow)**. **Expect** a device code + "open github.com/login/device".
10. Enter the code on github.com, approve → **expect** the app reports the
    remote bound. Théo points it at (or creates) private repo
    **`theo-calendar`**.
11. Finish wizard → **expect** to land on a **populated** Schedule view
    for `theo-calendar` (the wizard's output is the canonical starting
    state, not demo seed — D.54). Mode defaults to **free**.
12. **Expect** `theo-calendar` to contain at root: `mode.toml`
    (`mode = "free"`), `identity.toml`, `AGENTS.md`/`CLAUDE.md` with the
    single bridge line to identity.toml (D.83), `.strictlykeptboy/repo-id`
    committed (D.74). **[QA]** confirm `identity.toml` is committed and
    `AGENTS.md` embeds no personal-preference content beyond the one
    bridge line.

## Part B — Lena's first launch (will become strictly-kept, SSH)

13. On Lena's device: launch → **age gate** → continue (steps 1–2).
14. Wizard storage → **Internal**. Avatar → species (say **cat**).
15. **Alignment** screen → Lena taps **Submissive**.
16. **Expect** the praise + pronouns screen to now **show the
    honorific-for-dom field** (because alignment = Submissive). Lena will
    fill these in the *next* walk; for setup she can accept defaults
    (`good boy`/`Sir`/she-her — she'll override to French values later).
    **[QA]** verify the honorific field IS present here (mirror of step 7).
17. Wizard remote → **GitHub → SSH**. **Expect** on-device **ed25519 key
    generation** offered (no manual key paste required). Generate → copy
    the public key → add it as a deploy/SSH key on GitHub → bind to
    private repo **`lena-routine`**.
18. Finish wizard → **expect** populated Schedule for `lena-routine`.
    **Mode still defaults to free at this point** — strictly-kept is
    enabled in the next walk. **[QA]** confirm the default mode is `free`,
    not strictly-kept, even though alignment = Submissive. Alignment ≠
    mode; they are independent (D.55 vs D.84).

## Part C — Mutual sharing (write access, via the provider)

> Sharing in strictlykeptboy is **provider-native collaborator access**,
> not an app-internal grant. The app's "I share with" row deep-links to
> the provider's people/collaborators screen (UI-M.3). Write vs read-only
> is then **detected** from the credentials each side holds (D.88,
> `readOnlyDetected`).

19. **Lena grants Théo write access to `lena-routine`:** Lena opens
    **Together tab → "I share with"** → row for `lena-routine` →
    **"Manage on GitHub"** → **expect** a deep link to GitHub's
    Settings → Collaborators for that repo. Lena adds **Théo** as a
    collaborator with **write**.
20. **Théo adds `lena-routine` to his app:** Théo opens **Settings →
    Repos → "+ Add repo"** (UI-K.3) → clone `lena-routine` over the
    HTTPS/OAuth he already authorised. **Expect** the repo appears in his
    repo picker. **[QA]** because Théo has write (collaborator), expect
    **no read-only chip** on `lena-routine` in Théo's repo list.
21. **Théo grants Lena write access to `theo-calendar`** (symmetric):
    Together → "I share with" → `theo-calendar` → Manage on GitHub → add
    **Lena** as collaborator with **write**.
22. **Lena adds `theo-calendar` to her app:** Settings → Repos →
    "+ Add repo" → clone `theo-calendar` over SSH. **Expect** it appears;
    **no read-only chip** (she has write). This mutual-write link is what
    later lets the common-time finder read both busy sets and lets Lena
    drop a shared trip overlay.

## Part D — Per-repo identity + author attribution

23. **Author attribution is per-repo, derived from each repo's identity /
    the committing device** — there is no global app account (D.88). On
    Théo's device, switch the **top-bar repo switcher** to `lena-routine`
    → **expect** the top-bar **avatar + identity context switches** to
    Lena's (her species, her praise term once set), because the active
    repo *is* the identity (D.88). **[QA]** confirm the top-bar avatar
    reflects the **active** repo, not a fixed account avatar.
24. **Théo authors a test event into `lena-routine`** (e.g. a placeholder
    "morning anchor 07:00"). Commit/sync. → **expect** the event carries
    an **author chip** identifying **Théo** as the author when viewed from
    either device. **[QA]** the chip must show *who wrote it* (Théo),
    distinct from *whose repo it is* (Lena's). This author-vs-owner
    distinction is the whole point of a kept/Dom-authored calendar.
25. **Lena authors a test event into her own `lena-routine`** → **expect**
    that event's author chip shows **Lena**. Two authors, one repo, both
    chips correct.
26. **Sync both directions** → **expect** conflict-free merge (one file
    per entity, no index files; D.3). Both events present on both devices.
    **[QA]** confirm no merge-conflict prompt for two events authored
    concurrently in different files.

---

## Exit state (precondition for the next two walks)

- `lena-routine`: owned by Lena (SSH), **shared write to Théo**,
  currently **free** mode, alignment Submissive, honorific field was
  offered. Author chips work for both Lena and Théo.
- `theo-calendar`: owned by Théo (HTTPS/OAuth), **shared write to Lena**,
  **free** mode, alignment Dominant.
- Both repos appear in both devices' repo pickers with **no read-only
  chip** (mutual write).

## Confusion points to watch (summary for QA)

- **[QA]** Honorific field present iff alignment = Submissive/Switch;
  absent for Dominant (steps 7, 16).
- **[QA]** Default mode is `free` even for a Submissive-aligned repo;
  alignment and mode are independent (step 18).
- **[QA]** Sharing routes through the *provider's* collaborator screen,
  not an in-app grant; read-only-vs-write is *detected* from credentials
  (steps 19–22). A user expecting an in-app "invite" button may be
  confused.
- **[QA]** Author chip (who wrote) vs repo owner / active identity (whose
  repo) must stay distinct (steps 23–25).
- **[QA]** Top-bar avatar follows the *active* repo, surprising if the
  user expects a personal account avatar (step 23).
