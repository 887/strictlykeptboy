# strictlykeptboy — cross-repo global-ID feedback (draft)

## Status: ✅ INTEGRATED — see `main.md` Phase YY and `decisions.md` D.71 / D.72 / D.73. Data-model edits at `data-model.md` Phase DM-N. Resolver edits at `resolver.md` Phase RV-N. UI scaffold at `ui-spec.md` Phase UI-MM. Shared-schedules extensions at `shared-schedules.md` Phase SH-I / SH-J. CLI surface at `cli-tooling.md` CLI-L.11–CLI-L.18. Original draft content preserved below for reference.

---

## Framing

This draft locks the mechanics for **cross-repo feedback under
multi-life isolation**. Sub-domain inside the Round 3 shared-schedules
world (D.41–D.52) and the Round 2 comments world (D.29, Phase CC), but
extended in three ways the existing plan does not yet cover:

1. **Global identity for every entity** that survives clones, rebases,
   and history rewrites, **without** requiring signing-key infra
   (Phase GG is optional and must stay optional).
2. **A device-level repo registry with per-direction isolation**, so a
   "work" repo can be locally invisible to the "kink" repo (and vice
   versa) even though both live on the same device under the same app.
3. **A file-based reaction/comment model** that scales the
   one-entity-per-file invariant from Round 1 D.2/D.3 across repos:
   reactions and comments are normal markdown+TOML files, committable,
   pushable, mergeable, diffable, and consumable by the `skb` CLI and
   AI agents alike.

The first-class use case is **dom → sub feedback** (hearts, fire,
locked, collar, good-boy) on the sub's events and journal entries,
plus **dom → sub bonus tasks**, plus **sub-logged extras** the dom can
react to. The same primitive serves coach/client, teacher/student,
team-lead/member.

This draft EXTENDS rather than replaces:
- `shared-schedules.md` (deep-link, references.toml, cross-repo state)
- `data-model.md` (file schemas, identities)
- Phase CC (in-repo comments)
- Phase NN (`references.toml` manifest)
- Phase OO (`state/<source-repo-id>/` overlay)
- Phase RR (share-this-repo)

**Locks throughout.** Every open call is decided inline (LOCK:) and
the user's INTENT — multi-life isolation, kink-positive openly,
file-based, no central server — is non-negotiable.

---

## Locked primitives (referenced by every phase below)

**Global ID format (LOCK).**

```
<repo-fingerprint>:<entity-uuid>
```

- `repo-fingerprint` = `SHA-256(first-commit's tree SHA, hex)` truncated
  to the **first 16 hex chars** (8 bytes; ~1 in 1.8e19 collision space,
  fine for personal-scale repo registries). The "first commit" is the
  unique-parentless root commit reachable from `main` at repo-creation
  time. Tree SHA (not commit SHA) is chosen because tree SHA is
  determined purely by content, surviving a future "rewrite the root
  commit's author/date" rebase.
- `entity-uuid` = the existing UUIDv7 from D.3 (events, tasks,
  recurrences, exceptions, comments, journal entries, bonus tasks).
- LOCK: fingerprint computation runs **once at app-side repo-open** and
  is cached at `.strictlykeptboy/repo-fingerprint` — **gitignored,
  never committed**. Derivable from any clone; if the cache file is
  missing the app re-derives. (Adding it to `.gitignore` is part of
  Phase K scaffold output going forward; for already-scaffolded repos
  the cache-miss path covers it.)
- LOCK: `repo-fingerprint` is stable across rebases of non-root
  commits, across force-pushes that preserve the root, and across
  arbitrary squashes that don't touch the root. It breaks **only** if
  the user intentionally rewrites the root commit, which is treated as
  "this is a new repo identity" — the app prompts to rebind state and
  feedback (see FB-G).
- LOCK: For empty repos (no commits yet), the fingerprint is deferred
  until the first commit. The wizard (Phase K) makes that first commit
  immediately on scaffold, so this is only theoretical.
- LOCK: this is **independent** of `source_repo_id` from D.51 (which
  is SHA-256 of the normalized URL). URL-based IDs survive when the
  repo has a remote; fingerprint survives when it doesn't (no-origin
  local-only repos, repos moved between providers). Both IDs coexist:
  URL-id is the *resolver/state-file* key per D.44, fingerprint is the
  *feedback global-ID* key. State-files keyed by URL-id stay as-is
  (Phase OO); feedback files keyed by fingerprint is the new layer.

**Repo registry (LOCK).**

App-private path: `<app-data>/repo-registry.toml` (NOT in any git
repo; lives in `EncryptedSharedPreferences`-adjacent app storage so
the registry itself never leaks to a synced disk).

```toml
schema_version = 1

[[repo]]
fingerprint = "a1b2c3d4e5f60718"
local_path  = "/data/data/com.eight87.strictlykeptboy/repos/sub-own"
display_name = "my schedule"
last_seen    = "2026-05-12T10:00:00+02:00"
# Optional: directions of isolation FROM this repo.
# Per-direction: this repo will not cross-resolve to any fingerprint listed.
isolate_from = []

[[repo]]
fingerprint = "f00dbabec0ffee99"
local_path  = "/data/data/com.eight87.strictlykeptboy/repos/work"
display_name = "work"
last_seen    = "2026-05-12T08:00:00+02:00"
isolate_from = ["deadc0debeeff00d"]   # work cannot see kink

[[repo]]
fingerprint = "deadc0debeeff00d"
local_path  = "/data/data/com.eight87.strictlykeptboy/repos/kink"
display_name = "kept"
last_seen    = "2026-05-12T22:00:00+02:00"
isolate_from = []                      # kink CAN see work — directional
```

- LOCK: `isolate_from` is **per-direction**. The kink repo's view does
  NOT need to know it was isolated against by work. The work repo
  doesn't even need to know kink exists by name; the fingerprint is
  enough.
- LOCK: registry is **device-local**. Adding a repo on device A does
  not propagate to device B. Phase SS (own-repo migration) does NOT
  migrate the registry; instead each device adds repos on its own,
  same as today.

**Feedback file schema (LOCK).**

Path in the **feedbacker's** repo:
`feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`

```
+++
schema_version = 1
id            = "<feedback-uuid>"           # UUIDv7, this feedback file's id
target        = "<target-fingerprint>:<target-entity-uuid>"
target_kind   = "event" | "task" | "recurrence" | "exception" |
                "journal" | "bonus" | "feedback"   # "feedback" = reply
author        = "<person-id>"                       # identities/<person-id>.md
author_repo   = "<this-repo-fingerprint>"           # redundant w/ commit author, useful for CLI
created       = "2026-05-12T20:00:00+02:00"
updated       = "2026-05-12T20:15:00+02:00"         # optional; bump on edit
reactions     = ["heart", "fire", "locked", "collar"]
reply_to      = "<feedback-uuid>"                   # optional; threads
+++

Optional markdown comment body.
```

- LOCK: multiple feedback files per target are allowed (each author
  has their own). Two authors NEVER write the same file — no merge
  conflicts by construction (same trick as Phase CC comments and
  Phase OO state files).
- LOCK: updating a reaction = rewriting the same author's file. (A
  given author has one canonical feedback file per (target, reply_to)
  tuple. Re-reacting overwrites it. The git history is the audit log.)
- LOCK: deletion = `git rm` the file. No tombstones. (Reactions are
  ephemeral by nature; the git history covers anyone curious.)
- LOCK: each reaction is a **string token**, not an emoji codepoint.
  Sticker packs (Phase FF) re-skin tokens to renders.

**Reaction set (LOCK).**

Neutral: `thumbsup`, `heart`, `fire`, `prayer-hands`, `sparkles`,
`check`.

Kink: `locked`, `collar`, `good-boy`, `paw`, `bat`, `smirk`.

Plus markdown comment body. Custom packs can introduce new tokens
(stored as opaque strings; renderer falls back to `❓` when a token
isn't in the active pack). LOCK: the **schema does not enforce a
closed set** — opaque-string tokens — so users can add `kneel`,
`praise`, `mine`, etc. via sticker packs without an app update.

---

## Phase FB-A — Repo fingerprint derivation + cache

Foundation. Everything depends on this.

- [ ] **FB-A.1** `RepoFingerprint.computeOrLoad(repoPath: Path): String` — read `.strictlykeptboy/repo-fingerprint` if present and valid; else compute from `git log --reverse --max-count=1 --format=%T` (tree SHA of root commit), SHA-256, truncate to 16 hex chars, write cache. (LOCK: cache file is plain text, one line, gitignored.)
- [ ] **FB-A.2** Append `.strictlykeptboy/repo-fingerprint` to the scaffold's `.gitignore` writer (Phase K.3). For already-scaffolded repos, lazy-append on first open if missing.
- [ ] **FB-A.3** Empty-repo handling: if `git rev-list` is empty, return `null` and the caller defers feedback writes until first commit lands. (Wizard always makes a first commit, so user never hits this path.)
- [ ] **FB-A.4** Root-rewrite detection: on every repo open, recompute the fingerprint into a transient and compare against the cached value. Mismatch → surface "this repo's identity has changed" banner with options [re-bind feedback / treat as new repo / cancel open]. Re-bind walks `feedback/<old-fingerprint>/` and rewrites paths + frontmatter `target` strings to the new fingerprint, in one commit. (LOCK: rare path; root-rewrite is destructive enough that surfacing it is the right answer.)
- [ ] **FB-A.5** `skb repo fingerprint [--repo X]` CLI subcommand — prints the fingerprint. `--json` envelope for AI consumers. Useful for Claude composing a feedback file by hand.
- [ ] **FB-A.6** Robolectric tests: fingerprint stable across rebase-of-non-root, across squash-of-non-root commits, changes only on root rewrite. Test the cache-miss re-derivation path.

---

## Phase FB-B — Repo registry

The device-level mapping `fingerprint → local-path + display-name + isolation`. Required for any cross-repo resolution.

- [ ] **FB-B.1** `RepoRegistry` Kotlin object backed by `<app-data>/repo-registry.toml`. ktoml round-trip. Auto-create on first read.
- [ ] **FB-B.2** Auto-register on every successful repo open (Phase B, Phase QQ deep-link flow, Phase SS own-repo mini-wizard) — write `[[repo]]` entry with fingerprint, local path, display name from repo's `repo.toml`, last_seen = now.
- [ ] **FB-B.3** `RepoRegistry.list(): List<RegisteredRepo>` + `.byFingerprint(fp): RegisteredRepo?` + `.allVisibleTo(viewerFp): List<RegisteredRepo>` — the last applies isolation filtering. (LOCK: filtering is per-direction — `viewerFp`'s `isolate_from` excludes targets, but a target's `isolate_from` containing `viewerFp` is NOT consulted here. See FB-F for symmetric vs asymmetric isolation discussion — LOCKED to asymmetric: each repo controls only what *it* refuses to see.)
- [ ] **FB-B.4** Settings → Repos → tap repo → "Isolation" section: list every other registered repo with a toggle "Hide [other] from this repo's view". Toggling on appends the other fingerprint to this repo's `isolate_from`. (LOCK: UI clearly labels "this hides the OTHER repo FROM this repo. To hide THIS repo from the other, configure it from the other repo's settings." — directional clarity is essential and worth verbose copy.)
- [ ] **FB-B.5** `skb repo registry list|isolate|unisolate` CLI surface. `skb repo registry isolate --from <fingerprint> --hide <fingerprint>` for headless agent control.
- [ ] **FB-B.6** Registry update on remove-repo (Phase I.5) — drop the entry. Registry is rebuildable from scratch by re-opening repos, so loss is recoverable.
- [ ] **FB-B.7** Robolectric tests: two repos registered, third repo registered with isolate_from = [first], assert `allVisibleTo(third)` excludes first AND `allVisibleTo(first)` still includes third (directional).

---

## Phase FB-C — Feedback file schema + writer

The data primitive. One file per (author, target, reply_to) — same one-file-per-entity invariant as the rest of the app.

- [ ] **FB-C.1** `FeedbackFile` Kotlin schema (`id`, `target`, `target_kind`, `author`, `author_repo`, `created`, `updated`, `reactions`, `reply_to`, body). ktoml round-trip; preserve key ordering per Round 1 D.4.
- [ ] **FB-C.2** Filesystem path builder: `feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`. Same year/month bucketing principle does NOT apply — feedback files are bucketed by target, not by date, because resolver lookups are target-keyed.
- [ ] **FB-C.3** Writer: atomic write + auto-commit, commit message `add feedback for <target-fingerprint-short>:<entity-short>` or `update feedback ...` or `remove feedback ...`. Same author-stamping as D.4/D.8 (git committer + frontmatter `author`).
- [ ] **FB-C.4** Validation: refuse to write if `target` doesn't parse as `<16-hex>:<uuid7>`. Refuse if `reactions` contains duplicates. Refuse if both `reactions` is empty AND body is empty AND no `reply_to` (no-op feedback). Refuse if `reply_to` references a feedback file in a different repo (replies are written in the same repo as their parent — LOCK: forces threads to live in one repo, simplifies resolver and keeps the audit chain coherent).
- [ ] **FB-C.5** Indexer (Phase D): scan `feedback/**` in every registered repo at startup + on git HEAD change; populate a `FeedbackEntry` Room table (`target_fingerprint`, `target_uuid`, `author`, `repo_fingerprint`, `created`, `reactions` as JSON array, `reply_to`, body excerpt).
- [ ] **FB-C.6** AGENTS.md content (Phase C.7, DM-K rewrite): document the feedback file format and the `skb react` / `skb comment` shortcuts as primary path.

---

## Phase FB-D — `skb react` and `skb comment` CLI

Primary write surface per D.24 (CLI is the AI-agent path).

- [ ] **FB-D.1** `skb react add --target <global-id> --reactions heart,fire,locked [--repo X] [--body "..."] [--reply-to <feedback-uuid>]` — creates or updates the active-identity's feedback file for this target. `--repo` defaults to walk-up-found per X.11.
- [ ] **FB-D.2** `skb react remove --target <global-id>` — `git rm` the active-identity's feedback file. No-op if absent (exit 0).
- [ ] **FB-D.3** `skb react list --target <global-id>` — list all feedback files for the target across all registered visible repos. `--json` shape: `[{repo, author, reactions, created, body, replyTo}]`. Respects isolation (uses `RepoRegistry.allVisibleTo(<this-repo-fp>)`).
- [ ] **FB-D.4** `skb comment add --target <global-id> --body "..."` — shorthand for `skb react add` with empty reactions and a body (same file). `skb comment list --target <global-id>` mirrors `skb react list` but flattens by thread order.
- [ ] **FB-D.5** Target resolution helpers: `skb react add --target-event <uuid>` (resolves to `<this-repo-fp>:<uuid>`), `--target-event <repo-fp>:<uuid>` for cross-repo. Same flag shape for `--target-task`, `--target-journal`, `--target-bonus`.
- [ ] **FB-D.6** Exit codes per D.24: 0 ok, 2 target-not-found (when --strict given; default emits warning and writes anyway since the target may live in an isolate'd-out repo), 3 conflict, 5 corrupt.
- [ ] **FB-D.7** `--dry-run` prints the proposed file path + content envelope without writing.

---

## Phase FB-E — Cross-repo resolver extension

Extends Phase E + Phase OO. On event/task/journal/bonus display, surface aggregated feedback from all visible repos.

- [ ] **FB-E.1** `FeedbackResolver.aggregate(targetGlobalId): AggregatedFeedback` — given a target global-ID, query the `FeedbackEntry` Room index across every repo in `RepoRegistry.allVisibleTo(<viewer-repo-fp>)`. Return `{reactionsByToken: Map<String, List<Author>>, comments: List<Comment>, threads: List<Thread>}`.
- [ ] **FB-E.2** Threading: comments are grouped by `reply_to`. Top-level (`reply_to = null`) form thread roots; replies nest under their parent. Order within a thread = `created` ascending. (LOCK: linear `created` order, not hierarchical reply chains. Replies-to-replies appear at the same depth visually but maintain `reply_to` linkage for re-rendering depth in a future iteration if needed.)
- [ ] **FB-E.3** Aggregation memoization keyed by `(targetGlobalId, set-of-visible-repo-HEADs)`. Invalidated when any visible repo's HEAD changes (Phase D.3 incremental indexer hook).
- [ ] **FB-E.4** Feedback drawer UI surface (Phase G.7 / H.6 event-detail/task-detail sheet extension): below the event/task body, render a "Feedback" section with reaction tallies (grouped icons + counts + tap-to-expand author list) and a comment thread. (LOCK: drawer is always present even when empty, with a "+ react" affordance — discoverability matters.)
- [ ] **FB-E.5** Author chip on each reaction/comment shows source repo's display name (small chip, e.g. `from "kept"`) when the feedback is cross-repo. Same-repo feedback omits the chip (it's just "from <author-identity>"). LOCK: the repo display name is the *receiving* repo's local label for the source repo, not the source repo's own self-label — privacy-preserving (sub can label dom's repo whatever they want without dom knowing).
- [ ] **FB-E.6** "+ react" tap opens a reaction picker (Phase FF sticker-pack-driven) wired to `skb react add` semantics; selecting writes a feedback file in the **viewer's** repo (i.e., the repo currently selected in the top-bar repo switcher, NOT the source repo of the target entity). LOCK: feedback always lives in the feedbacker's repo.

---

## Phase FB-F — Isolation enforcement + privacy tests

The hard property: two isolate-paired repos cannot leak via any UI surface.

- [ ] **FB-F.1** Resolver path: every cross-repo lookup goes through `RepoRegistry.allVisibleTo(<viewer-fp>)`. No direct registry iteration anywhere else. Lint rule (custom Detekt or a simple grep CI check) flags any code outside `RepoRegistry` that reads `repo-registry.toml`.
- [ ] **FB-F.2** Aggregation count masking: when isolation excludes some feedback, the UI shows no "N hidden" hint, no count, no shadow. The isolated source is **structurally invisible**, not "redacted". (LOCK: "1 reaction from a private source" is a leak. We do not do this.)
- [ ] **FB-F.3** Notification suppression: if a feedback file is written in repo A targeting an entity in repo B, but repo B has `isolate_from = [A]`, repo B's notification channel does NOT fire on the new file. Repo B is structurally unaware.
- [ ] **FB-F.4** Search/autocomplete masking: when composing a feedback target via global-ID picker, isolated repos do not appear in the picker. CLI `skb react list` from inside repo B does not enumerate feedback in isolated repo A. The author chip (FB-E.5) cannot render an isolated repo's display name even by accident, because the lookup table itself is filtered.
- [ ] **FB-F.5** Robolectric privacy tests:
  - Two repos in registry. Leave a heart from repo-A on an event in repo-B. Open repo-B's event detail; assert the heart surfaces, attributed to repo-A's display name.
  - Mark repo-B as `isolate_from = [A's fingerprint]`. Re-open repo-B's event detail; assert the heart no longer surfaces, no count masking, no breadcrumb. Snapshot the rendered Compose tree and grep for any string containing repo-A's display name OR fingerprint — assert zero hits.
  - Mark repo-A as `isolate_from = [B's fingerprint]` (additionally / instead). Open repo-A and view *its* schedule; assert repo-B's feedback (if any back-direction existed) is suppressed there too. Verify directionality: setting isolation on one side does not auto-symmetrize.
  - `skb react list --target <repo-B-entity>` invoked from inside repo-B's working tree returns zero entries when isolation is set. Invoked from inside repo-A's working tree it returns the heart it wrote, because repo-A's view of itself isn't filtered.
- [ ] **FB-F.6** Robolectric thread test: comment in repo-A `reply_to`s another comment in repo-A on a target in repo-B; render repo-B's feedback drawer; assert both comments appear with correct threading. Then isolate; assert both vanish.
- [ ] **FB-F.7** Robolectric bonus-task round-trip test: dom writes `bonus/<uuid>.md` in shared repo; sub sees it as optional in their todolist; sub edits with `completed_at`; dom pulls and sees completion. Coverage spans Phase FB-G.

---

## Phase FB-G — Bonus tasks (dom → sub) + sub-logged extras

Specialized variants on the same primitives.

- [ ] **FB-G.1** Bonus-task path: `bonus/<task-uuid>.md` at calendar root in a **shared repo** (i.e., a repo both dom and sub can access, typically via Phase RR share-this-repo with `read-write` mode or a third "shared" repo both pull from). Frontmatter mirrors `TaskFile` but adds `bonus = true`, `assigned_by = "<person-id>"`, `optional = true`. Standard UUIDv7. Same atomic-write commit message convention.
- [ ] **FB-G.2** Sub-side todolist view (Phase H.1 / H.2): tasks with `bonus = true` render with a distinct bonus-icon prefix, no schedule-pressure badging (no "OVERDUE" coloring), filed in a dedicated "Bonus" group at the bottom of the combined view. (LOCK: bonus tasks are NEVER promoted to the dated-tasks section even if `due` is set — `bonus = true` overrides scheduling pressure.)
- [ ] **FB-G.3** Completion: sub edits the task file (or runs `skb task done --bonus <id>`) which sets `completed_at = "<iso-datetime>"` and optionally a `note = "..."` body addition. The same file is rewritten; commit message `complete bonus task "<title>"`. Dom sees on next pull.
- [ ] **FB-G.4** If the shared repo is unavailable (sub cloned dom's repo read-only), `bonus/` lives in dom's repo; sub's completion lives as a state-file (`state/<dom-repo-fp>/<task-uuid>.done.toml`) per Phase OO. Resolver merges. LOCK: bonus task completion gracefully degrades to the existing state-file pattern when write-back isn't available.
- [ ] **FB-G.5** Dom → bonus assignment via deep-link offer (Phase MM extension): `strictlykeptboy://bonus?target=<sub-repo-fp>:<task-uuid>&title=...&due=...&priority=...` — opens on sub's device with one-tap accept that materializes the task in sub's chosen todolist (own repo).
- [ ] **FB-G.6** Journal entries: `journal/<yyyy-mm-dd>.md` at calendar root (NOT under any calendar/todolist — locked as a separate top-level tree per the brief). UUIDv7 in frontmatter; the date in the filename is for human navigation only. Multiple entries per day allowed via numeric suffix `journal/<yyyy-mm-dd>-<n>.md` if needed. Schema: `id`, `created`, `author`, free-form body. Indexed and global-ID'd same as everything else.
- [ ] **FB-G.7** Journal entries are reactable / commentable via the same FB-C/FB-D/FB-E machinery — no new code path. Dom reacts to sub's journal entry → feedback file in dom's repo targeting sub-repo-fp:journal-entry-uuid.
- [ ] **FB-G.8** LOCKED: journal entries do NOT appear in the schedule overlay (resolver does not generate render rows from `journal/`). They appear in a dedicated Journal tab (added to nav rail per Phase F.1 — extends the Schedule/Tasks/Together/Settings list to Schedule/Tasks/Journal/Together/Settings), or in simplified mode (Phase PP) as a single "Journal" entry beneath the task list.

---

## Phase FB-H — `references.toml` + share-flow extensions

Extend Phase NN and Phase RR so feedback write-back is opt-in and discoverable.

- [ ] **FB-H.1** Extend `references.toml` schema (DM-Q) with `write_back_target = "<repo-fingerprint>"` field per reference. Optional; when present, signals: "this referenced repo is willing to receive feedback files written by us, addressed to entities owned by that fingerprint."
- [ ] **FB-H.2** Resolver / UI hook: only references that have `write_back_target` set get a visible "leave feedback" affordance for entities owned by that fingerprint. Other references' entities are view-only (no `+ react` button surfaced). LOCK: opt-in by the referencing repo's author, never by the receiver-of-feedback.
- [ ] **FB-H.3** Share-this-repo flow (Phase RR) UI: a checkbox "Allow this share's recipient to leave feedback on my entries" → when set, the generated share link is paired with a `write_back_target = <this-repo-fingerprint>` line that's auto-written into the recipient's `references.toml` when they accept (Phase QQ).
- [ ] **FB-H.4** Symmetry: if dom shares a repo to sub with write-back enabled, AND sub shares back to dom, both `references.toml` files carry `write_back_target` entries pointing to each other. Feedback flows in both directions.
- [ ] **FB-H.5** Revoking write-back: edit the local `references.toml` and remove the line. UI: Settings → Repos → tap repo → "References" → tap reference → "Allow feedback" toggle. Removing closes the UI affordance immediately. Already-written feedback files persist (we don't reach into history) but no new ones will surface in the leave-feedback picker.
- [ ] **FB-H.6** CLI: `skb ref set-write-back <reference-url> --enable|--disable` for headless control.

---

## Phase FB-I — Test fixtures + Robolectric end-to-end

Independent of FB-F.5 unit tests; FB-I assembles full scenarios.

- [ ] **FB-I.1** Fixture: build two repos (`fixture-sub` + `fixture-dom`) with full D.3 scaffolding, a known event in sub's calendar, register both in a test `RepoRegistry`. Helper: `TestRegistry.with(subFp, domFp).build()`.
- [ ] **FB-I.2** End-to-end test "dom hearts sub's event": call `skb react add` programmatically as dom against sub's event-global-ID; commit lands in dom's repo; sub's resolver query for the event surfaces the heart with `from "dom"` chip. Assert reaction count, author identity, source repo display name all correct.
- [ ] **FB-I.3** End-to-end test "isolation hides heart": mutate registry to add `isolate_from`; re-query; assert heart absent, no count masking, no fingerprint leak in rendered string.
- [ ] **FB-I.4** End-to-end test "reply thread": dom hearts + comments; sub replies via `skb comment add --reply-to <dom-feedback-uuid>`; assert thread order, parent-child linkage, both visible in either-direction view (when no isolation).
- [ ] **FB-I.5** End-to-end test "bonus task round-trip": dom writes a bonus task in a shared third repo, sub completes via `skb task done --bonus`, dom pulls, sees `completed_at`. Then variant where shared repo is unavailable and completion lives as a state-file in sub's own repo — assert resolver merge produces the same rendered "completed" state.
- [ ] **FB-I.6** End-to-end test "journal reaction": sub writes a journal entry, dom hearts it via `skb react add --target-journal <sub-fp>:<journal-uuid>`, sub views journal tab and sees the heart.
- [ ] **FB-I.7** End-to-end test "root-rewrite rebind": simulate a force-push that rewrites the root commit; assert fingerprint changes; assert the rebind UI path (FB-A.4) walks every `feedback/<old-fp>/` and rewrites correctly, in one commit, with all targeting frontmatter updated.
- [ ] **FB-I.8** Performance budget: aggregating feedback for a single event across 5 repos with ~100 feedback files each: < 50ms warm Room query. Matches D.21 budgets.

---

## Phase FB-J — Documentation + AGENTS.md rewrite

Land the user-facing and AI-agent-facing docs.

- [ ] **FB-J.1** AGENTS.md content (extends DM-K + Phase C.7): document the feedback file format, the `skb react` / `skb comment` / `skb repo fingerprint` commands as primary path, with examples. LOCKED phrasing: feedback is *first-class, file-based, and reaction tokens are open-ended* — agents are encouraged to use the existing token set but may invent new ones via sticker packs.
- [ ] **FB-J.2** README.md content in produced repos: explain the `feedback/` directory layout to humans, with the privacy note "feedback you write here lives in YOUR repo, addressed to entities in other repos — anyone with read access to your repo can see your reactions."
- [ ] **FB-J.3** Settings → About → "How feedback works" link to a brief in-app help page explaining the model in two paragraphs.
- [ ] **FB-J.4** `cli-tooling.md` extension: full `skb react|comment|repo fingerprint|repo registry|ref set-write-back` reference with examples, exit codes, `--json` envelopes.

---

## Integration notes

Exact edits to land when this draft is promoted to a real plan:

**`main.md`** — insert a new round-4 (or extend round-3) section with phases FB-A through FB-J. Recommended placement: after Phase UU and before Phase VV (the homescreen-countdown widget), so the round-3 shared-schedules world (MM–TT) chains naturally into the cross-repo feedback world. Cross-reference from Phase CC (replies/comments) — add a note "extended cross-repo in Phase FB-*" and from Phase NN (`references.toml`) — add "`write_back_target` field defined in FB-H.1" and from Phase OO (state files) — add "feedback files are a peer primitive to state files, defined in FB-C; both keyed by their respective `<source-repo-id>` (URL hash) vs `<repo-fingerprint>` (root tree hash)."

**`shared-schedules.md`** — add cross-reference sections to SH-C (`references.toml`) noting the `write_back_target` extension, SH-D (state files) noting feedback files as a sibling primitive (state = receiver's mutations on sender's entities; feedback = receiver's reactions on sender's entities), SH-G (share-this-repo) noting the "Allow recipient feedback" checkbox lands in FB-H.3.

**`data-model.md`** — extend DM-Q (references.toml) with `write_back_target` field; new DM-section for the feedback file schema mirroring DM-K's comments-file shape but with the cross-repo `target` global-ID; new DM-section for the `journal/` tree schema; new DM-section for the `bonus/` task variant.

**`decisions.md`** — append a new D.54 locking the global-ID format (`<repo-fingerprint>:<entity-uuid>`, fingerprint = SHA-256(root-tree-SHA)[:16]), a D.55 locking the device-local repo registry with per-direction isolation, and a D.56 locking the open-token reaction set with sticker-pack extension.

**`cli-tooling.md`** — append CLI-section for `skb react add|remove|list`, `skb comment add|list`, `skb repo fingerprint`, `skb repo registry list|isolate|unisolate`, `skb ref set-write-back`, `skb task done --bonus`. All with `--json` envelopes and `--dry-run` per the X.5/X.6 cross-cutting rules.

**`resolver.md`** — extend RV-L (cross-repo state-file overlay) with the parallel feedback-file overlay path, sharing the visible-repo iteration helper from `RepoRegistry.allVisibleTo`.

**`ui-spec.md`** — extend UI-W (comments UI) with the cross-repo feedback drawer described in FB-E.4–FB-E.6; extend UI-FF (simplified mode chrome) with the Journal tab + bonus-task group in the combined task view; extend the Phase F nav-rail spec (F.1) from four entries to five with Journal inserted between Tasks and Together.

---

## Locked decisions inline (summary)

Every locked-as-default decision in this draft:

- Global ID = `<repo-fingerprint>:<entity-uuid>`; fingerprint = SHA-256(root-tree-SHA)[:16]; cached at `.strictlykeptboy/repo-fingerprint` (gitignored).
- Independent of `source_repo_id` (URL-based); both coexist.
- Repo registry is device-local; not synced.
- Isolation is per-direction (asymmetric); each repo controls only what it refuses to see.
- Feedback files live in the feedbacker's repo at `feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`.
- One file per (author, target, reply_to); updates rewrite the same file.
- Reaction tokens are opaque strings; sticker packs (Phase FF) re-skin; default set covers neutral + kink.
- Replies must live in the same repo as their parent feedback.
- Bonus tasks (`bonus/<task-uuid>.md`) override scheduling pressure regardless of `due`.
- Journal entries (`journal/<yyyy-mm-dd>.md`) are NOT in the schedule overlay; they get their own nav-rail tab.
- `write_back_target` in `references.toml` gates the "+ react" UI affordance; opt-in by the referencing repo.
- Aggregation count masking is forbidden — isolated sources are structurally invisible, never "N hidden".
- Author chips for cross-repo feedback show the *receiving* repo's local label for the source repo, never the source's self-label.
- Root-rewrite of a repo is treated as a new identity; rebind path exists but requires explicit user action.
- Resolver memoization keyed on (target, set-of-visible-repo-HEADs); cleared on any HEAD change.
