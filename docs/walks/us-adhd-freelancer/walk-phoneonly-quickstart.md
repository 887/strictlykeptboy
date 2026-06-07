# Walk — Phone-only quickstart: first launch → no-origin repo → brain-dump capture

**Persona:** Devon Reyes (see [`personality.md`](personality.md))
**Goal:** From a cold first install, get a working phone-only (no-origin)
repo and split a brain-dump across a couple of todo lists — with zero
account, zero remote, zero SSH.
**Preconditions:** Fresh install of strictlykeptboy, app never launched.
No git remotes configured anywhere. Device is online (irrelevant — we
will not touch the network).
**Features exercised:** first-run wizard (D.13), no-origin repo creation
(Phase ZZ.G / D.74), todolists with overlay model (D.11/D.5), fast
capture, Markdown task bodies (Phase EE), per-repo identity (D.15/DDD).

---

## Steps

1. Devon launches strictlykeptboy for the first time.
   → **Expect:** Welcome screen with the bat-in-hoodie mascot (D.13 step 1).
   No login wall, no "create account" anywhere.
   ⚠ **Confusion watch:** if any provider sign-in appears before they can
   proceed, that is the dealbreaker frustration — fail the walk.

2. They tap **Continue** / **Get started**.
   → **Expect:** the path-choice / first-launch splash. Per ZZ.G the
   no-deep-link first-launch path offers a 2-option choice:
   **"Start a local calendar (you can sync it later)"** vs
   **"Connect to a git remote now."**
   ⚠ **Confusion watch:** the local option must be a peer first-class
   choice, NOT buried under "Advanced." If Devon has to hunt for it, flag it.

3. They tap **"Start a local calendar (you can sync it later)."**
   → **Expect:** the wizard proceeds down the local-only branch — the
   repo-picker + provider-auth steps (D.13 steps 4–5) are **skipped**
   (per ZZ.G: wizard repo-picker + auth steps are skippable; skipping
   runs the local-only scaffolder).

4. (Templates step, D.13 step 3) They are offered role toggles
   (morning-bird, work-9-5, daily-chores, etc.) or an **Empty repo** path.
   Devon wants a clean slate, taps **Empty repo** (or toggles nothing and
   continues).
   → **Expect:** no kink/lifestyle toggles are forced on; the lifestyle
   roles are optional. Devon leaves them all off.
   ⚠ **Confusion watch:** Devon's persona is plain/neutral tone — make
   sure proceeding with everything off is obviously allowed.

5. Repo naming. They are asked for a repo display name; suggestion may be
   prefilled. Devon types **`devon-life`**.
   → **Expect:** name accepted; no requirement for a remote URL.

6. Identity step (D.15 / DDD): set display name + pronouns + tone.
   Devon enters display name **"Devon"**, pronouns **they/them**, tone
   register **plain/neutral**, mode **free** (not strictly-kept).
   → **Expect:** these persist into `identities/<person-id>.md`,
   `mode.toml` (free), `identity.toml`. No honorific/praise terms required.

7. Devon taps **Finish** / **Create**.
   → **Expect:** the app runs the local-only scaffolder:
   `GitRepo.initLocalOnly(rootDir, authorIdentity)`. A real local git repo
   is created with the D.3 layout (`.strictlykeptboy/`, `AGENTS.md`,
   `CLAUDE.md` symlink, `identities/`), an initial commit is made, and
   the main view opens. **No push, no network call.**
   ⚠ **Confusion watch:** there must be NO spinner that looks like it's
   trying to reach a server, and NO error about a missing remote.

8. Main view (Schedule, Day). Devon looks at the **top bar repo switcher**.
   → **Expect:** `devon-life` shown with a small **house / "local" badge**
   (per ZZ.G no-origin repos get a local badge). The sync button is
   present but inert for a no-origin repo (per ZZ.H invariant (i): no
   foreground SyncService work when `remotes.isEmpty()`).
   ⚠ **Confusion watch:** tapping the sync button on a no-origin repo
   should be a no-op or a gentle "nothing to sync — this is a local-only
   calendar" message, NOT a red error.

---

### Now the actual point: capture a brain-dump

Devon has a head full of stuff and wants it OUT fast. They'll create
three todo lists — **work**, **errands**, **someday** — and dump into them.

9. Devon taps the **Tasks** destination in the left nav rail (✅ Tasks).
   → **Expect:** Tasks screen. Since the repo is empty, an empty state
   with a CTA to create a list / add a task.

10. They create the first todolist. Tap **+ / New list** → name **`work`**.
    Accept defaults (priority inherited, active_hours empty for now).
    → **Expect:** `todolists/work/todolist.toml` created and committed;
    list appears.

11. Repeat for **`errands`** and **`someday`**.
    → **Expect:** three lists exist; each is its own folder with
    `todolist.toml` (D.11). All committed locally.

12. **Fast capture into `work`.** Devon opens `work`, taps the add-task
    affordance, and bangs out, one after another:
    - "Send fintech client the revised onboarding flow"
    - "Nonprofit: export accessibility audit to PDF"
    - "Game studio — pick up settings-menu wireframe again"
    → **Expect:** each task is one file under
    `todolists/work/tasks/<yyyy>/<mm>/<task-id>.md` (or `standing/` if no
    due date). Capture is 1–2 taps from the list view; the previous task
    saves and the field is ready for the next.
    ⚠ **Confusion watch:** measure taps-to-capture. If adding a second
    task requires navigating away and back, that violates the capture-
    friction requirement — flag it hard.

13. **Half-finished capture.** Devon starts typing "buy —" then gets
    distracted and taps away / saves it incomplete into **`errands`**.
    → **Expect:** the partial task is saved without complaint, no required
    fields blocking the save. Title can be a fragment.
    ⚠ **Confusion watch:** if a non-empty title or a due date is *required*
    to save, that punishes exactly the capture pattern this user lives by.

14. **Errands + someday dump.** Into `errands`: "renew domain", "pick up
    dry cleaning", "buy — " (the fragment from step 13). Into `someday`:
    "learn Blender for the game studio mockups", "redo personal portfolio".
    → **Expect:** tasks land in the correct lists.

15. **Add a body to one task.** Devon opens "Send fintech client the
    revised onboarding flow" and types a Markdown body:
    ```
    - waiting on their brand tokens
    - **deadline Friday**
    - [figma link](https://example.com/figma)
    ```
    → **Expect:** body stored in the task `.md` file; detail sheet renders
    the Markdown M3E-themed (bold, bullet list, primary-tinted link) via
    Markwon (Phase EE).

16. **Set time-of-day relevance on `work`.** Devon edits the `work` list
    and sets `active_hours` to Mon–Fri 09:00–17:00.
    → **Expect:** per D.11, during 09:00–17:00 weekdays the `work` list's
    tasks surface higher in the combined "today" task view; outside those
    hours `errands`/`someday` aren't drowned out by work.
    ⚠ **Confusion watch:** Devon's whole object-permanence need rides on
    this. Verify the combined/today view actually re-ranks by active_hours.

17. Devon switches to the **Tasks → Today / Combined** view mid-afternoon
    (simulate a weekday ~14:00).
    → **Expect:** `work` tasks ranked above `errands`/`someday` (active
    hours match); sorted by priority then due time (D.11). No tasks lost.

18. Devon backgrounds the app and reopens it.
    → **Expect:** everything persists (it's committed to git + cached in
    Room). The repo still shows the local badge; still no network activity.

---

## Pass criteria

- [ ] Reached a working repo with NO account and NO remote.
- [ ] No-origin repo clearly badged; sync button non-destructive.
- [ ] Three lists created; capture is ≤2 taps per task from the list view.
- [ ] Fragment / partial titles save without required-field blocking.
- [ ] Markdown body renders themed.
- [ ] `active_hours` re-ranks the combined today view by time of day.

## QA notes / likely defects

- Watch step 2/3 for the local option being demoted below a remote option.
- Watch step 7/8 for any network/spinner/error on a purely local repo.
- Watch step 12/13 for capture friction and required-field gates.
