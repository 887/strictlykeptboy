# Round 2.17 — Storage folder choice + import/export/restore

## Context

D-2.7.b (locked in `docs/plans/round-2-7.md`) hard-codes the repo
working tree to `filesDir/repos/<repoId>/` and frames the user's SAF
pick as a *mirror only* — `MirrorLocation { None | External(uri,label) }`
in `app/src/main/java/com/eight87/strictlykeptboy/prefs/RepoStoragePrefs.kt:124-137`.
Every repo scaffolds at `filesDir.resolve("repos")` in
`MainActivity.kt:562` and the only "external" surface is a per-repo
bare-mirror under `<base>/<repoId>.git` written by
`MirrorReconciler.kt:108-134`.

The user has overridden that decision:

> "we should be doing is the main strictlykeptboy folder, the repo
> we're creating is a subfolder."
> "internal in app storage only if the user chooses that route with a
> hint that it's easier for stickers and stuff in internal storage."
> "you should also be able to choose an existing folder from settings
> and there should be a settings import/export function … restore the
> app from that folder through settings."

So the storage model now is: there is ONE parent folder for the whole
app. Repos are direct subfolders of it. The parent is either
`filesDir/strictlykeptboy/` (private app storage) or a SAF-tree-URI
folder the user picked outside the app (the "external" route). The
bare-mirror dance of 2.7 is collapsed into "the parent IS the working
tree location" — no separate mirror, no duplicate copy, no SAF→File
resolver dance for the *primary* write path.

This also aligns with the repo's standing portability story (D.74:
no-origin repos are first-class, phone-only must work) and the
agent-native ethos from
`~/.claude/projects/-home-laragana-workspace/memory/project_strictlykeptboy_agent_native.md`
— a user's strictlykeptboy folder on `/storage/emulated/0/Documents/`
is reachable from Termux, from `adb pull`, from another agent on a
laptop after a `syncthing` round-trip. The current mirror model hides
the real data behind app-private storage; this round inverts that.

## Locked design decisions

- **D-2.17.a — Parent folder is the unit of "where the app stores
  things".** `ParentLocation` replaces `MirrorLocation`. Two variants:
  `Internal(absPath = filesDir/strictlykeptboy)` and
  `External(treeUri, label, cachedRealPath?)`. There is always
  exactly one parent; `None` is gone. The default on first launch
  (before the user answers the question) is `Internal`, but the
  scaffold flow blocks until the user has explicitly chosen.

- **D-2.17.b — External path uses SAF tree URI for the consent
  handshake + the cached real path for JGit I/O.** SAF is the only
  way Android 11+ lets the user grant a stable, persistable tree
  permission outside the app sandbox; raw `File` paths under
  `/storage/emulated/0/` work for JGit only if the user has
  separately granted `MANAGE_EXTERNAL_STORAGE` (a Play-Store
  high-risk permission we will not request). The compromise: the
  user picks via `ACTION_OPEN_DOCUMENT_TREE`, we reuse
  `SafTreeUriResolver.resolveRealPath` (only works for the primary
  volume — that's fine, picker copy makes that clear) to derive an
  absolute path, and JGit reads/writes there with bare `java.io.File`
  on the strength of the SAF permission grant. If the user picks an
  SD card / cloud provider, `resolveRealPath` returns null and the
  picker is rejected with a clear error ("Pick a folder on internal
  storage — SD cards and cloud folders aren't supported yet").

- **D-2.17.c — User picks ANY directory; the app creates / reuses a
  `strictlykeptboy/` subfolder inside it.** A user pointing the picker
  at `Documents/` ends up with `Documents/strictlykeptboy/<repoId>/`.
  Pointing it at an *existing* `Documents/strictlykeptboy/` short-
  circuits to using that folder as-is (detected by `.skb-root` marker
  — see D-2.17.d). Pointing at any other folder containing a
  `strictlykeptboy/` child that already carries a marker also short-
  circuits. Why: keeps users' Documents/ clean, avoids the "the app
  vomited 15 repo folders into my Downloads" surprise, and makes the
  parent self-describing.

- **D-2.17.d — `.skb-root` marker file pins the parent.** A small TOML
  at `<parent>/.skb-root` with `version = 1`, `app_id =
  "com.eight87.strictlykeptboy"`, `created_at = <iso8601>`,
  `created_by = <device-name>`. Created on parent-folder
  initialisation. Used to (a) detect "this folder is already ours" on
  re-pick, (b) drive the "adopt existing folder" Settings flow, (c)
  refuse to nuke a non-skb folder during Restore. Plain TOML so the
  user / a peer agent can hand-edit if needed.

- **D-2.17.e — Strictly one parent at a time.** Per-repo overrides
  ("this repo lives over there, that one lives over here") are
  rejected for v1: too much UX surface, settings sprawl, and the
  user's intent ("the main strictlykeptboy folder, the repo we're
  creating is a subfolder") reads as singular. Switching parent =
  moving every repo (Phase E.5 move-job).

- **D-2.17.f — Migration from D-2.7.b.** On first launch after this
  round ships, if `filesDir/repos/<x>/` exist and no parent prefs
  exist, write `Internal(filesDir/strictlykeptboy/)` to prefs and
  *move* (not copy) every `filesDir/repos/<x>/` into the new parent.
  Record `migrated_from_d_2_7_b = true` in prefs so we don't run twice.
  Existing mirror prefs (`MirrorLocation.External`) are read as a
  hint and the user gets a one-time toast: "Your previous backup
  folder is no longer mirrored — open Settings to move repos there
  instead." We do NOT auto-promote a mirror to a parent: the user
  should consent again with the new framing.

- **D-2.17.g — Restore is destructive + irreversible; gated by typed
  confirmation.** "Restore from folder" wipes the in-memory
  `RepoStore`, deletes `filesDir/strictlykeptboy/`'s contents (or the
  configured external parent's contents — see D-2.17.h), then
  re-hydrates from the chosen source. User must type `restore` into a
  text field before the action enables, plus a 3-second hold on the
  destructive button. No system "Are you sure" — we own the dialog.

- **D-2.17.h — Restore can target the live parent or a backup
  archive.** Two sub-actions:
  - *Restore from current parent folder* — re-scan the parent, drop
    every `repoStore` entry, register every `<parent>/<repoId>/` that
    looks like a git repo. Used to recover after a `RepoStore` prefs
    corruption / a manual `adb pull`-and-edit cycle.
  - *Restore from backup archive* — pick a `.skb-backup.tar.gz`,
    extract into the parent (replacing it), then run the same re-scan.

- **D-2.17.i — Backup export format is `.skb-backup.tar.gz` with a
  manifest.** Single `tar.gz` containing the entire parent folder
  contents plus a top-level `manifest.toml` (skb version, schema
  version, repo count, created_at, parent_label). Tar preserves
  symlinks (required for `CLAUDE.md → AGENTS.md` symlinks per
  produced-repo convention). We do NOT zip — Java's `ZipOutputStream`
  loses symlinks. Apache Commons Compress is already on the classpath
  via JGit transitives? — verify in A.1 and add explicitly if not.

- **D-2.17.j — Adoption is permissive but never overwrites.** When
  the user "Choose existing folder", the discovered repos are added
  to `RepoStore` with `displayName` derived from each repo's
  `repo.toml` (or `<repoId>` fallback). If a `displayName` collides
  with an existing entry the adopted one gets ` (adopted)` appended.
  We never modify the on-disk repo during adoption — only register it.

- **D-2.17.k — SAF permission revocation is non-fatal but blocks
  writes.** On boot we verify the persisted tree URI permission is
  still granted (`contentResolver.persistedUriPermissions`). If
  revoked, the app boots, surfaces a red banner on Repos +
  Schedule ("Strictlykeptboy lost access to its folder — re-pick
  it"), and disables write paths until re-picked. We do NOT fall
  back to `Internal` automatically: silent fallback is the kind of
  thing that produces "where did all my data go" bug reports.

- **D-2.17.l — Refactor, don't rewrite.** `RepoStoragePrefs`,
  `SafTreeUriResolver`, `MirrorReconciler` are *refactored*:
  - `RepoStoragePrefs` keeps its `EncryptedSharedPreferences`-backed
    file, prefs schema is bumped to `repo_storage_v2.xml` with
    one-time read of `_v1.xml` for migration.
  - `SafTreeUriResolver` keeps its current `resolveRealPath` and
    grows an `appendSubfolder` helper for D-2.17.c.
  - `MirrorReconciler` is renamed to `ParentReconciler` and its
    `mirror` remote work is **removed**, replaced with a
    "make sure every repo on disk under the parent is in
    `RepoStore`" reconcile. Existing `"mirror"` remotes on repos
    are pruned the first time `reconcile()` runs after migration.

## Phase order

Strict dependency chain:

- **Phase A** — data layer: replace `MirrorLocation` with
  `ParentLocation`, add `.skb-root` marker, write the migration.
- **Phase B** — SAF launcher refactor in `MainActivity`: split the
  current single picker into "pick parent" + drop the mirror-launcher
  surface.
- **Phase C** — repo scaffolding: `WizardScaffolder` + add-repo flow
  honour the configured `ParentLocation`.
- **Phase D** — wizard integration: new "Where should we keep your
  repos?" screen, shown only when no parent has been confirmed.
- **Phase E** — Repos pane: pre-add gate for the same question when
  the user `+`'s a repo before any parent has been confirmed; settings
  surface to change folder + adopt existing.
- **Phase F** — Settings: backup export action.
- **Phase G** — Settings: restore action (both flavours).
- **Phase H** — tests + AVD smoke.
- **Phase I** — plan close-out + supersede D-2.7.b in older plans.

## Phase A — Data layer rework — shipped in 870c884

- [x] **A.1** Inventory `apache-commons-compress` availability on the
  app classpath (transitive via JGit?). If absent, add
  `org.apache.commons:commons-compress` to `app/build.gradle.kts`
  with a comment naming Phase F (backup tar.gz). — Not present
  transitively (only JGit + sshd + bouncycastle on the `org.apache`
  chain); wired explicitly at `commonsCompress = "1.27.1"`.
- [x] **A.2** Rename `prefs/RepoStoragePrefs.kt` → keep filename,
  bump `PREFS_FILE` to `repo_storage_v2`, replace `MirrorLocation`
  sealed type at `RepoStoragePrefs.kt:124-137` with
  `ParentLocation { Internal(absPath) | External(treeUri, label,
  cachedRealPath?) }`. Update all call-sites; expect compile errors
  in `MainActivity.kt:79-170`, `MainActivity.kt:540-555`,
  `sync/MirrorReconciler.kt:35-106`, settings screens. Compile sweep
  fixed `MainActivity`, `BackupLocationCategory`, `ReposPane`,
  `AppGraph`.
- [x] **A.3** Add `prefs/SkbRootMarker.kt` — helpers
  `read(parent: File)`, `write(parent: File, deviceName: String)`,
  `isSkbRoot(parent: File): Boolean`. Marker format per D-2.17.d.
  Reuse the hand-rolled TOML codec in `store/`.
- [x] **A.4** Add `prefs/ParentLocationMigrator.kt`: idempotent
  one-shot. Reads `repo_storage_v1.xml`, reads existing
  `filesDir/repos/<x>/` children, moves them under
  `filesDir/strictlykeptboy/<x>/`, writes the marker, writes
  `Internal` into v2 prefs, sets `migrated_from_d_2_7_b = true`.
  Survives partial completion (`mv -n` semantics).
- [x] **A.5** Wire the migrator into `composition/AppGraph.kt`'s init
  block (run on `Dispatchers.IO`, idempotent guard via prefs flag).
  Migrator runs from `parkRuntimes()`; on success the migrator
  also fires `ParentReconciler.pruneStaleMirrorRemotes()` to drop
  the now-dead `mirror` remote from every repo.
- [x] **A.6** Robolectric test:
  `ParentLocationMigratorTest` — pre-seed `filesDir/repos/{a,b}/`,
  run migrator, assert (a) both repos now under
  `filesDir/strictlykeptboy/`, (b) marker present, (c) prefs
  records `Internal`, (d) running it twice is a no-op. Added
  4 tests including a partial-recovery (mv -n) scenario.
- [x] **A.7** Robolectric test: `RepoStoragePrefsV2Test` —
  round-trip `Internal`, round-trip `External`, read-from-v1-then-
  upgrade. Added 5 tests; `RepoStoragePrefsTest` also rewritten
  for v2 (4 tests) plus 5 `SkbRootMarkerTest` cases.
- [x] **A.8** Rename `sync/MirrorReconciler.kt` →
  `sync/ParentReconciler.kt`. Drop bare-mirror logic
  (`ensureBareMirror`, `bareFileFor`, the `mirror` remote
  manipulation). New surface: `suspend fun reconcile():
  List<AdoptedRepo>` — scans the configured parent, returns
  newly-adoptable repos (not yet in `RepoStore`). Keep
  `applyToAll()` as a `pruneStaleMirrorRemotes()` helper that runs
  once during migration to clean up the dead `"mirror"` remotes.
  `MirrorReconcilerTest` rewritten as `ParentReconcilerTest`
  (5 tests).
- [x] **A.9** Add `prefs/ParentLocationGate.kt` — single source of
  truth for "do we have a confirmed parent yet?" used by wizard
  + add-repo to short-circuit the question. Returns `Confirmed |
  NeedsPicking`. Confirmed iff `ParentLocation` is set AND (for
  External) the URI permission is still granted AND the marker
  resolves on disk.

## Phase B — MainActivity SAF launcher refactor — shipped in 528c894

- [x] **B.1** Rename `openTreeLauncher` (`MainActivity.kt:93-132`)
  → `parentPickerLauncher`. Behaviour: on grant, call
  `SafTreeUriResolver.resolveRealPath`; if null, Toast
  "Internal storage only — try a folder on this phone" and bail.
- [x] **B.2** Inside the launcher: compute
  `<picked>/strictlykeptboy/` directory (create if missing), check
  for `.skb-root` marker, create marker if missing. Write
  `ParentLocation.External(treeUri, label, cachedRealPath =
  "<picked>/strictlykeptboy")` to prefs. Re-pick of a folder that
  is *itself* already an skb-root short-circuits to using it as-is
  per D-2.17.c.
- [x] **B.3** Move the SAF "label derivation" block currently inline
  at `MainActivity.kt:108-112` into `SafTreeUriResolver.deriveLabel`.
  3 new tests in `SafTreeUriResolverTest`.
- [x] **B.4** Drop the `Toast` wired to `mirrorReconciler.applyToAll`
  at `MainActivity.kt:122-131` — replace with a Toast that reports
  how many repos got adopted (calling `ParentReconciler.reconcile()`
  and reading the size of its returned list). New string
  `parent_adopted_n_repos`.
- [x] **B.5** Expose `graph.parentPickerHandle = {
  parentPickerLauncher.launch(null) }` (rename from
  `backupPickerHandle`) so Compose surfaces can fire it. Both
  call-sites in `MainActivity` migrated; the Phase A
  `@Deprecated backupPickerHandle` alias has been removed.
- [x] **B.6** Add a SECOND launcher `restoreArchivePickerLauncher`
  using `ActivityResultContracts.OpenDocument()` with MIME type
  `application/gzip` — Phase G consumes this. Routed through a
  `pendingRestoreArchiveHandler: ((Uri) -> Unit)?` so Phase G can
  attach its handler without re-registering the launcher.

## Phase C — Repo scaffolding honours the parent — shipped in 1084a29

- [x] **C.1** `WizardScaffolder.materialize`: change `parentDir`
  param semantics — caller now passes the *strictlykeptboy parent*,
  not `filesDir/repos`. Scaffolder writes
  `<parent>/<repoId>/`. KDoc updated to call out the new contract
  (caller passes the canonical parent; scaffolder no longer
  resolves `filesDir/strictlykeptboy` or the SAF cache path).
- [x] **C.2** Both wizard call sites updated.
  `MainActivity.kt` wizard-scaffold now reads
  `graph.repoStoragePrefs.location?.workingDir(filesDir) ?:
  RepoStoragePrefs.defaultInternalDir(this)` and passes that as
  `parentDir`. Demo seeder kept at `filesDir/demo-repos/...` per
  the brief — demos are app-private by design, not user-data.
- [x] **C.3** `AddRepoNavHost` Local + Remote branches: working
  tree path now lands under the parent. Threaded a `parentDir:
  File` parameter through `ReposPane` → `ReposDetailPane`, derived
  from `repoStoragePrefs?.location?.workingDir(context.filesDir)`
  with the internal-default fallback. Both AddRepoNavHost call
  sites (compact + tablet detail) updated; the third usage is the
  wizard scaffold in MainActivity above.
- [x] **C.4** Added the debug-only invariant as a top-level
  helper `warnIfRepoOutsideParent(...)` in `git/RepoConfig.kt`,
  invoked from all three new-repo construction sites (wizard +
  both AddRepo branches). `BuildConfig.DEBUG`-gated `Log.w` only;
  proper enforcement + a move-job for repos that violate the
  invariant arrive with Phase E.5.
- [x] **C.5** Helper `ParentLocation.workingDir(filesDir: File): File`
  added on the sealed type. Non-null overload alongside the
  existing nullable `workingDir()` — Internal returns its
  configured path, External returns `cachedRealPath` or falls
  back to `filesDir/strictlykeptboy` when the cache is unpopulated.

## Phase D — Wizard "Where to store?" screen — shipped in f9bed3b

- [x] **D.1** Add `ui/wizard/WizardStorageStep.kt` —
  `@Composable fun StorageStep(onPickExternal, onPickInternal,
  onBack, onNext)`. Two big cards: "Save on your phone (recommended)"
  with the explanatory hint about stickers + portability, and
  "Keep inside the app" with the "private to this app, gone if you
  uninstall" caveat. RadioButton or Card-as-toggle, per the lifestyle-
  step pattern at `WizardNavHost.kt` (look for `LifestyleStep`).
- [x] **D.2** Insert the step into `WizardNavHost.kt` after the
  Welcome step and before the existing first scaffold step. Test
  tag `Wizard-Storage`. Skip-able? — **No**, the user must pick
  before scaffolding. The screen is auto-skipped when the
  `ParentLocationGate` already reports `Confirmed` (returning user
  who blew away a repo, re-runs the wizard, etc.).
- [x] **D.3** "Pick external" CTA calls
  `graph.parentPickerHandle()`. The wizard listens via a
  `LaunchedEffect` keyed on `prefs.state` and advances when state
  transitions from `Internal-default` / unconfirmed to
  `External(...)`.
- [x] **D.4** "Keep inside the app" CTA calls
  `graph.repoStoragePrefs.set(ParentLocation.Internal(
  filesDir.resolve("strictlykeptboy").absolutePath))`, writes the
  `.skb-root` marker, advances.
- [x] **D.5** Robolectric snapshot test for the new step
  (`WizardStorageStepTest`): rendering, "Pick external" callback,
  "Keep inside the app" callback.
- [x] **D.6** Update existing wizard tests that drive Welcome → next
  to expect the new Storage step in between.

## Phase E — Repos pane gate + settings folder management

- [ ] **E.1** In `ui/repos/AddRepoNavHost.kt`, gate the entry on
  `ParentLocationGate.Confirmed`. If not confirmed, render a
  small `StorageStepInline` composable (reuses the wizard's
  composable) instead of the form. After the user picks, the form
  appears.
- [ ] **E.2** Add a Settings category `StorageCategory` under the
  existing settings host (find via grep for `BackupLocationCategory`
  — that's the 2.7.D surface to replace). New rows:
  - "Storage folder" — current label (`label` for External,
    "Inside the app" for Internal), tap to open
    `StorageFolderScreen`.
  - "Adopt existing folder" — sub-action below.
  - "Backup / Restore" — opens `BackupRestoreScreen` (Phase F+G).
- [ ] **E.3** Build `StorageFolderScreen`: shows current
  parent + path + repo count, button "Change folder" (fires
  `parentPickerHandle`), button "Switch to internal".
- [ ] **E.4** "Adopt existing folder" entry: fires the parent
  picker, but on grant DOES NOT change the parent — instead runs
  `ParentReconciler.reconcileExternal(uri)` against that one
  folder, presents a confirmation list of discovered repos, and
  on confirm switches the parent to that folder + registers the
  repos. Marker must be present for the action to enable.
- [ ] **E.5** Move-job: when "Change folder" changes the parent
  (Internal↔External or External→External), schedule a
  `RepoMover` worker that copies (then deletes) every existing
  `<oldParent>/<repoId>/` to `<newParent>/<repoId>/`, updates
  `RepoConfig.rootDir` for each, surfaces a progress dialog with
  cancel. On cancel, revert prefs to old parent.
- [ ] **E.6** Move-job unit test
  (`RepoMoverTest`) — pre-seed two repos, move them, assert
  rootDirs updated + old paths gone + new paths exist + marker
  carried over.
- [ ] **E.7** Banner on Repos pane when SAF permission is revoked
  (per D-2.17.k) — "Strictlykeptboy lost access to its folder",
  tap to re-pick. Wired via boot-time
  `contentResolver.persistedUriPermissions` check in
  `AppGraph.init`.

## Phase F — Settings: backup export

- [ ] **F.1** Add `backup/BackupArchiver.kt`:
  `suspend fun export(parent: File, out: OutputStream): Manifest`.
  Streams a `tar.gz` via Commons Compress
  (`TarArchiveOutputStream` + `GzipCompressorOutputStream`).
  Preserves symlinks (`TarArchiveEntry.LF_SYMLINK`). Skips
  `.skb-root` (regenerated on restore) and per-repo
  `.git/objects/pack/.tmp*` lockfiles.
- [ ] **F.2** Manifest TOML schema: `skb_version`,
  `schema_version = 1`, `created_at`, `repo_count`,
  `parent_label`, `device_name`. Written as the first entry in the
  tar so a partial read can show the manifest.
- [ ] **F.3** "Export backup" Settings row → opens a
  `CreateDocument("application/gzip")` launcher (suggested name
  `strictlykeptboy-backup-<yyyy-MM-dd>.tar.gz`). On grant, write
  on `Dispatchers.IO`, Toast on completion with byte count.
- [ ] **F.4** Add the `CreateDocument` launcher to
  `MainActivity.kt` alongside the others.
- [ ] **F.5** Robolectric test `BackupArchiverTest` — round-trip
  a parent with two repos through `export` → `import`, assert
  every file (including a symlink) survives.

## Phase G — Settings: restore

- [ ] **G.1** `backup/BackupRestorer.kt`:
  `suspend fun restoreFromArchive(in: InputStream, parent: File):
  Manifest` — extracts tar.gz over a freshly-wiped parent. Refuses
  if the destination has files but no `.skb-root` (refuse to nuke
  user data we didn't put there).
- [ ] **G.2** `suspend fun rescanParent(parent: File): List<RepoConfig>`
  — walks `<parent>/<dir>/.git` (or bare equivalent), builds a
  `RepoConfig` per discovered repo (reads `repo.toml` for
  `displayName`, `defaultCalendarId`, etc.; falls back to
  directory name).
- [ ] **G.3** `ui/settings/BackupRestoreScreen.kt` — three
  sub-actions:
  - Export backup (Phase F)
  - Restore from current folder (calls `rescanParent`, replaces
    `RepoStore` contents on confirm)
  - Restore from backup archive (calls `restoreArchivePickerLauncher`)
- [ ] **G.4** Destructive-confirm dialog per D-2.17.g — typed
  `restore` field + 3-second hold on the red button. Component
  lives at `ui/components/DestructiveConfirmDialog.kt` (new) —
  reuse-able for any future destructive flow.
- [ ] **G.5** Wire `RepoStore.replaceAll(list: List<RepoConfig>)` —
  may not exist yet (grep `RepoStore.kt`). If absent, add it;
  emits a single `state` update.
- [ ] **G.6** After restore, invalidate the cache DB and trigger a
  full re-index via the existing `CacheRebuilder` (grep for
  `CacheRebuilder` or `rebuildAll`).
- [ ] **G.7** Robolectric test `BackupRestorerTest` — export
  parent A, restore into empty parent B, assert
  `rescanParent(B)` returns the same repo configs.
- [ ] **G.8** Robolectric test `RestoreSafetyTest` — restore
  refuses to wipe a directory containing unrelated files
  (no `.skb-root`).

## Phase H — Tests + AVD smoke

- [ ] **H.1** New Robolectric tests added per phase land green
  (A.6, A.7, D.5, E.6, F.5, G.7, G.8).
- [ ] **H.2** Update existing wizard tests to expect the new
  Storage step.
- [ ] **H.3** Existing `MirrorReconcilerTest` (if present) →
  rewrite as `ParentReconcilerTest`. Cover (a) reconcile on
  Internal parent, (b) reconcile on External parent, (c)
  stale-`mirror`-remote pruning on first run after migration.
- [ ] **H.4** AVD smoke — fresh install (`adb uninstall` first):
  1. Launch → wizard → Welcome → Storage step appears.
  2. Pick "Keep inside the app" → marker created at
     `filesDir/strictlykeptboy/.skb-root` (verify via `adb shell
     run-as`).
  3. Finish wizard → first repo lands at
     `filesDir/strictlykeptboy/<repoId>/`.
- [ ] **H.5** AVD smoke — external picker happy path:
  1. Fresh install → wizard → Storage step → "Save on your phone"
     → SAF picker → pick `/Documents/`.
  2. Assert `/storage/emulated/0/Documents/strictlykeptboy/` was
     created and contains `.skb-root`.
  3. Finish wizard → repo lands under that folder.
- [ ] **H.6** AVD smoke — external picker rejects SD card / cloud
  (`null` real path) with the documented Toast.
- [ ] **H.7** AVD smoke — Settings → Change folder → switch from
  External back to Internal. Repos move; previous external folder
  contents persist (we don't nuke them on switch, only on Restore).
- [ ] **H.8** AVD smoke — Settings → Backup → tar.gz produced →
  `adb pull` → `tar tzf` lists every repo's files including the
  `CLAUDE.md → AGENTS.md` symlink.
- [ ] **H.9** AVD smoke — Settings → Restore from current folder.
  Manually edit a file under the parent (rename a repo dir),
  Restore, repo list reflects the rename.
- [ ] **H.10** AVD smoke — Settings → Restore from archive. Wipe
  the parent (`adb shell run-as ... rm -rf`), Restore from the
  earlier export, repos return.
- [ ] **H.11** AVD smoke — revoke SAF permission via system
  settings, relaunch app, banner appears, write paths refuse,
  re-pick restores normal operation.
- [ ] **H.12** AVD smoke — migrate-from-2.7.b: install a prior
  build with a repo at `filesDir/repos/foo/`, `adb shell pm
  install -r` the new build, verify the repo moved to
  `filesDir/strictlykeptboy/foo/`.

## Phase I — Plan close-out

- [ ] **I.1** Edit `docs/plans/round-2-7.md`: mark D-2.7.b
  "SUPERSEDED by D-2.17.a" and D-2.7.c "SUPERSEDED by D-2.17.b".
  Don't delete the old plan — leave the history.
- [ ] **I.2** Edit `docs/plans/decisions.md`: append D-2.17.a
  through D-2.17.l to the locked-decisions list.
- [ ] **I.3** Tick every phase header in this file with its jj
  change ID as the work lands.
- [ ] **I.4** Add `## Status: ✅ DONE` to the top of this file
  when every phase is ticked.

## Verification

### Robolectric tests (concrete names)

- `ParentLocationMigratorTest.migrates_filesDir_repos_to_parent_once`
- `ParentLocationMigratorTest.is_idempotent_when_run_twice`
- `RepoStoragePrefsV2Test.round_trips_internal`
- `RepoStoragePrefsV2Test.round_trips_external_with_cached_path`
- `RepoStoragePrefsV2Test.reads_v1_prefs_and_upgrades`
- `SkbRootMarkerTest.write_then_read_round_trip`
- `SkbRootMarkerTest.isSkbRoot_returns_false_for_empty_dir`
- `ParentReconcilerTest.reconcile_adopts_unregistered_repo_dirs`
- `ParentReconcilerTest.prune_stale_mirror_remote_drops_legacy_remote`
- `RepoMoverTest.moves_two_repos_and_updates_rootDirs`
- `WizardStorageStepTest.renders_two_cards`
- `WizardStorageStepTest.external_card_fires_picker_callback`
- `BackupArchiverTest.round_trip_preserves_symlinks`
- `BackupRestorerTest.restores_into_empty_parent`
- `RestoreSafetyTest.refuses_to_overwrite_unmarked_directory`

### AVD smoke scenarios

See H.4 — H.12 above (each is its own scenario with steps + expected
outcome).

## What is intentionally NOT in scope

- `MANAGE_EXTERNAL_STORAGE` escape hatch for SD cards / cloud
  folders. Tracked as a future round if users ask.
- Per-repo parent overrides (one repo on SD, another internal). Per
  D-2.17.e — strictly one parent at a time.
- Cloud sync via the parent folder (Drive, Dropbox). The parent
  folder being on a syncthing-watched directory works incidentally
  but is not promoted in copy.
- Encryption-at-rest for the external parent. The user picked
  external storage — they accepted whatever the OS / filesystem
  offers.
- Restore-then-merge (i.e. selectively restore some repos from an
  archive into a non-empty parent). Restore is replace-all.
- A `skb` CLI subcommand for the same export/import. CLI scope
  lives in `docs/plans/cli-tooling.md`; revisit when that round
  is active.
- Automatic backup scheduling (daily tarball into a Downloads
  subfolder). Manual-only in this round; cron-style scheduling is
  a later round if the user requests it.
