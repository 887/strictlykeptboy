# 2.7.A — AVD smoke notes

Phase 2.7.A bakes the bundled sticker pack into the scaffolded repo. The
change is invisible to Compose UI — it adds files under
`<repoRoot>/stickers/<species>/` during `WizardScaffolder.materialize`
*after* `RepoBootstrap.scaffold` and *before* the initial git commit.

## Verification

The visible UI flow (wizard screens, species pick) is unchanged from
Round 2.6 — Phase 2.7.A only adds disk writes inside the existing
scaffold flow. End-to-end evidence is provided by Robolectric unit
tests under `StickerPackScaffoldTest`:

- `foxScaffoldShipsBundledPack` — scaffolds a Fox repo, asserts
  `<repoRoot>/stickers/fox/` exists, every bundled asset file is
  byte-identical on disk vs. the `app/src/main/assets/avatar-packs/fox/`
  source stream, the initial git commit contains those files (verified
  via JGit `TreeWalk` against `HEAD`), and `stickers/README.md` is NOT
  emitted for non-custom species.
- `customScaffoldShipsBatStarter` — scaffolds with
  `SpeciesChoice.ChooseYourOwn`, asserts the Bat pack lands at
  `stickers/bat/`, `stickers/README.md` is written with the
  "Edit these files and commit" guidance, both land in the initial
  commit, and the fox directory is NOT created.

Both run under Robolectric against the same `assets/` tree that ships
in the debug APK (resource processing is shared between
`processDebugResources` and `processDebugUnitTestResources`), which
makes the byte-level assertion equivalent to checking the on-device
state post-scaffold.

## Initial AVD screenshots

`skb-2-7-A-initial.png` (age gate) and `skb-2-7-A-step1.png` (wizard
Step 1/10 — "let's design your lifestyle") captured after a fresh
install of `app-debug.apk` from this branch to confirm the build
launches cleanly. The full 10-screen wizard pass is not scripted here
because the work is on the data layer; the unit tests above prove the
post-scaffold filesystem state, which is what this phase changes.
