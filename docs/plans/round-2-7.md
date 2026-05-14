# Round 2.7 — Sticker pack bake-into-repo + external storage mirror

## Status: PENDING — planning complete, implementation not started

## Why this round exists

1. **Sticker pack follow-up from Round 2.6.** The wizard now correctly captures the species choice and frames `Customize later` as "clone the repo + edit `stickers/<species>/` afterward". For that promise to mean anything, the bundled pack files must actually exist in `<repoRoot>/stickers/<species>/` after scaffolding. Today `WizardScaffolder.materialize()` doesn't copy them. Fix: copy the bundled pack into the repo during scaffolding so it's part of git history.

2. **Repo storage location choice.** User feedback: "make it so you can choose the location of the repos.. either inside the app or a folder you select in 'internal storage'.. I kinda want the user to do this during the initial setup or skip it but let it be a one time export later from the internal app data."

   **Design call:** repos can't literally live outside `filesDir/` because JGit needs `java.io.File` access and modern Android restricts that outside app-private storage. The cleanest way to give the user what they want — *data outside the app, survives uninstall, accessible to external file managers* — is to keep repos in `filesDir/repos/` and add an auto-mirror `file://` remote pointing at a SAF-picked folder. Every repo gets the mirror; the sync runtime auto-pushes to it alongside other remotes. User-perceived UX is "my data is in that folder" with much simpler implementation.

## Locked design decisions

- **D-2.7.a — Sticker pack bakes into the repo at scaffold time.** `WizardScaffolder` calls `assetPackLoader.copyPackInto(species, destDir = <repoRoot>/stickers/<species>/)` after `RepoBootstrap.scaffold`. The pack files end up tracked by git on the initial commit.
- **D-2.7.b — Storage choice is a single app-wide setting**, not per-repo. New `RepoStoragePrefs`:
  - `MirrorLocation.None` (default) — no external mirror
  - `MirrorLocation.External(treeUri, label)` — SAF tree URI persisted (`takePersistableUriPermission`) + display label
- **D-2.7.c — Wizard gets a "Backup location" screen** between `Git` and `Scaffold`. Three options:
  - "Keep inside the app" (selected by default — `MirrorLocation.None`)
  - "Pick a folder" — launches `OPEN_DOCUMENT_TREE`; on grant, persist tree URI
  - "Skip — decide later" — same as default; surfaces a one-time reminder in Settings → Backup after first launch
- **D-2.7.d — When mirror location is set, every existing + new repo gets a `file://` remote** named `mirror` pointing at `<mirrorPath>/<repoId>.git`. The sync runtime auto-pushes to it. Retroactive: when the user sets the mirror later, an "Apply to existing repos" prompt asks once to bulk-add the mirror remote to all current repos.
- **D-2.7.e — SAF tree URI → java.io.File path resolution.** On Android 13+ the SAF tree URI for a directory under `/storage/emulated/0/...` is resolvable to a real path via `DocumentsContract.getTreeDocumentId` + the known volume path. We use that for the JGit mirror push. If resolution fails (e.g. SD card / cloud provider), gracefully degrade: show the user a "Couldn't write to that folder" warning and keep `MirrorLocation.None`. Document the constraint in the picker copy: "Pick a folder on your phone's internal storage."
- **D-2.7.f — No data movement.** Repos always live in `filesDir/repos/<repoId>/`. The mirror is a *mirror*, not a move. App uninstall removes the working copy but leaves the mirror intact, which is the user's actual concern.

## Phase ordering

```
2.7.A  Sticker pack bake-into-repo   (PREREQ — small)
2.7.B  Mirror prefs + SAF picker      (data model + wizard screen + Settings → Backup)
2.7.C  Sync-runtime mirror push       (auto-push to file:// mirror on every sync)
2.7.D  Retroactive apply              (set-mirror-later flow + bulk-apply prompt)
2.7.E  AVD smoke + release
```

## Phase 2.7.A — Sticker pack bake-into-repo

- [x] **2.7.A.1** Add `AssetPackLoader.copyPackInto(species: String, destDir: Path)` that copies all files under `assets/avatar-packs/<species>/` to `destDir`. Existing files in `destDir` aren't overwritten (lets future runs add new artwork without clobbering user edits). Note: actual on-disk asset layout is `avatar-packs/<species>/` (no `default-` prefix), confirmed against `app/src/main/assets/avatar-packs/`. Returns 0 on empty/missing source so early-Phase-WW state (where artwork hasn't shipped yet) doesn't fail scaffolding.
- [x] **2.7.A.2** `WizardScaffolder.materialize()` calls `copyPackInto(draft.species.id, repoRoot.resolve("stickers/${draft.species.id}/"))` after `RepoBootstrap.scaffold` and before the initial commit. The stickers land in git history. Passed `assetPackLoader` as an optional `materialize` param (not a constructor dep — `WizardScaffolder` is an `object`, and per-call injection lets the existing Robolectric test surface keep passing `null`). MainActivity wires `graph.assetPackLoader`.
- [x] **2.7.A.3** For `SpeciesChoice.ChooseYourOwn` (id `"custom"`, no bundled `custom` pack on disk), the Bat pack is copied into `stickers/bat/` as the user's editable starting point. A `stickers/README.md` is written for ChooseYourOwn explaining the customization path; non-custom species do NOT get the README.
- [x] **2.7.A.4** `StickerPackScaffoldTest` — `foxScaffoldShipsBundledPack` scaffolds a Fox repo, asserts `stickers/fox/` exists, every bundled file is byte-identical on disk, the initial commit contains them, no `stickers/README.md` for Fox. `customScaffoldShipsBatStarter` asserts `stickers/bat/` exists + `stickers/README.md` exists + both land in the initial commit + no `stickers/fox/` is created for ChooseYourOwn.

## Phase 2.7.B — Mirror prefs + SAF picker + wizard screen

- [ ] **2.7.B.1** New `RepoStoragePrefs.kt` (`prefs/` or `git/` package, your call — defend in commit). EncryptedSharedPreferences-backed. Fields:
  - `MirrorLocation.None`
  - `MirrorLocation.External(treeUri: String, label: String)`
- [x] **2.7.B.2-UI** SAF tree picker wired in `MainActivity` + parked on `AppGraph.backupPickerHandle`. Settings + banner surfaces drive the picker; wizard insertion deferred (out of scope for this dispatch — Settings + reminder banner are the access points now). *Shipped via UI follow-up commit.*
- [ ] **2.7.B.3** New `SafTreeUriResolver.resolveRealPath(uri: Uri, context: Context): String?` helper that uses `DocumentsContract.getTreeDocumentId(uri)` to map `primary:<sub-path>` → `/storage/emulated/0/<sub-path>`. Returns null for non-primary volumes / cloud providers.
- [x] **2.7.B.4-UI** Settings → Behaviour → "Backup location" category shipped (`BackupLocationCategory.kt`). Renders blurb + Pick / Change / Remove. After picking, `MirrorReconciler.applyToAll()` fires + Toast surfaces the count. *Shipped via UI follow-up commit.*
- [ ] **2.7.B.5** Tests:
  - `RepoStoragePrefsTest` — round-trip None / External; persistable-URI-permission stays granted across app restart.
  - `SafTreeUriResolverTest` — primary volume maps correctly; SD card returns null; tree-URI-without-DOCUMENT_ID returns null.

## Phase 2.7.C — Sync-runtime mirror push

- [ ] **2.7.C.1** `SyncScheduler` (or per-repo sync dispatch) consults `RepoStoragePrefs.location`. If `External`, ensures a `RemoteBinding(name = "mirror", url = "file://<resolvedPath>/<repoId>.git", transport = File, authMethod = None)` exists on the repo; adds it via `RepoStore.update(...)` if missing. Push to mirror runs alongside the primary remote push.
- [ ] **2.7.C.2** First sync after the mirror is set up creates the bare repo at `<resolvedPath>/<repoId>.git` via `Git.init().setBare(true).setDirectory(...)` if it doesn't exist.
- [ ] **2.7.C.3** `MirrorPushFailure` event posted to `SyncStatusStore` when the mirror push fails (folder unreachable, SD card unmounted, etc.) — doesn't block primary-remote success.
- [ ] **2.7.C.4** Test: `MirrorPushIntegrationTest` — given a real `tmp` directory as SAF-picked location, scaffold a repo, run sync, assert the bare mirror has the same `HEAD` commit. Use `GitRepoBareFixtureTest` as the existing template (Phase B.9).

## Phase 2.7.D — Retroactive apply

- [x] **2.7.D.1-UI** *Design call:* the separate "Apply?" dialog was dropped in favour of automatic `applyToAll()` + Toast. Picking the folder IS the consent — the dialog would have been redundant friction. Documented in commit message. *Shipped via UI follow-up commit.*
- [x] **2.7.D.2-UI** Dismissable Material3 primary-container banner in `ReposPane` above the repos list. Shown when `MirrorLocation.None` AND `skippedDuringWizard == true` AND `"backup-folder-reminder"` not in `NotificationPrefs.dismissedReminders`. *Shipped via UI follow-up commit.*
- [ ] **2.7.D.3** Test: `RetroactiveMirrorApplyTest` — start with 3 existing repos with no mirror, set mirror, accept apply prompt, assert each repo's `RepoConfig.remotes` now contains a `mirror` binding with correct URL.

## Phase 2.7.E — AVD smoke + release

- [ ] **2.7.E.1** AVD walkthrough:
  - Wipe data → wizard → Species → finish → check `<filesDir>/repos/<id>/stickers/bat/*.png` exist
  - Re-run wizard with mirror screen → pick folder → finish → check bare mirror at `<picked>/<repoId>.git`
  - Edit a sticker file in the cloned mirror (use `adb shell` to write a PNG); next launch shows updated avatar (sticker re-resolved from disk).
  - Add a second repo → verify the mirror remote is auto-added to it
  - Screenshots to `docs/qa/2-7/`
- [ ] **2.7.E.2** `scripts/build-release-apk.sh --gh-release`
- [ ] **2.7.E.3** Status flip in `round-2-7.md` + `main.md`

---

## Total scope

**~17 sub-steps across 5 phases.** Reuses existing `RemoteBinding` + sync runtime + `AssetPackLoader` machinery. New ground: SAF tree URI flow + URI→real-path resolution + the wizard mirror-location screen. No new TOML schema. App-wide storage prefs, not per-repo (per user direction).
