# QA iteration 1 — full-app walkthrough, defect log, fixes

Date: 2026-06-07. Target: headless `medium_phone` AVD (Android 16 / API 36),
mirrored via scrcpy. Build: `:app:assembleDebug` off `main` @ `1a97a32`.

Method: install debug APK, drive the UI by screencap + tap, inspect each
surface, log defects / improvements / ideas, fix in the same iteration,
rebuild, re-verify on the AVD.

## Phase A — Walkthrough (surfaces visited)

- [x] **A.1** Launch + splash + Schedule (agenda) — demo content loads, SFW-but-charged paired calendar renders correctly.
- [x] **A.2** Reviews feed (Boy Keeper review-feed) — renders, sub-filter rail (All / Unread / Reactions / Threads) present.
- [x] **A.3** Settings shell — full category tree mapped (Appearance, Library, Behaviour, Lifestyle, About).
- [x] **A.4** CalDAV settings screen — placeholder surface.
- [x] **A.5** Event detail screen — title, time+tz, repo, kind, priority, calendar chip, status, Markdown body, mute toggle, Edit.
- [x] **A.6** Tasks surface — checkbox rows, list chips, start ▶, priority dots, left rail (Today / Upcoming / All / By repo / Done).
- [x] **A.7** Look and Feel (Appearance) — theme, base theme, tint-by-avatar, custom tint, density picker, font scale, neutral-mode pointer.

## Phase B — Defects found

- [x] **B.1 — Internal plan-phase labels leak into user-facing copy.** 12 strings
  cited internal phase letters ("Phase Y", "Phase GG", "Phase W", "Phase WW",
  "Phase F", "Phase G", "later phase") and/or the word "placeholder" directly in
  user-visible settings copy (CalDAV subtitle + intro + chip, signed-commits
  blurbs, GPG/SSH import buttons, licenses label, templates custom-URL blurb,
  category-placeholder, backup/restore placeholders). Users should never see
  internal nomenclature. **Severity: medium (polish / professionalism).**
- [x] **B.2 — Task row secondary line collides for long list names.** In
  `TaskRow.kt` the list-name `Text` had no `maxLines`/`weight`/ellipsis, so a long
  todolist name (e.g. "Sprint 25 board", "Activity log for Sir") consumed the row
  and forced the `"  ·  <date>"` `Text` to wrap internally — the `·` detached and
  floated, the date wrapped onto a second misaligned line, and the visual gap was
  lost, rendering as "Sprint 25 board2026-05-17" / "Sir2026-05-16". Short names
  ("Groceries · 2026-05-16") rendered fine. **Severity: medium (visible layout bug).**
- [x] **B.3 — Density segmented control wraps "Spacious".** In
  `AppearanceCategory.kt` the three density `FilterChip`s were laid left-to-right in
  a width-constrained Row; the last chip got squeezed and its label wrapped to
  "Spacio / us". **Severity: low-medium (visible layout bug).**
- [ ] **B.4 — Event-detail timezone shown redundantly.** Detail sheet shows
  "(Arctic/Longyearbyen · Longyearbyen)" — the IANA zone path and the friendly
  city duplicate each other. **Severity: low (cosmetic). Carried to iter-2 review
  (needs decision on intended multi-tz display format).**
- [x] **B.5 — Neutral-mode pointer row cramped in Appearance.** The row's trailing
  control was a long `TextButton` ("Open Lifestyle → Neutral mode") that ate ~half
  the width, squeezing the headline to "Neutral / mode" and the blurb to one word
  per line. **Severity: medium (visible layout bug). Fixed this iteration (C.4).**
- [ ] **B.6 — Back-stack candidate: back from a Settings sub-screen (Look and
  Feel) appeared to return to Schedule, skipping the Settings category list.**
  Observed once; could be a stale-instance artifact of `am start` during testing.
  **Carried to iter-2 for deliberate back-stack verification.**

## Phase B-gap — Feature-gap discrepancies (logged, not "defects")

- [ ] **G.1 — CalDAV is a placeholder but the README sells it as a v1 feature.**
  README: "Bidirectional CalDAV *client* sync IS in v1." Reality: settings screen
  is an empty "coming soon" surface. The UK-nurse walk depends on it. This is a
  scope/claim discrepancy to raise with the owner — not fixable as a copy tweak.
  Copy leak fixed in B.1; the engine gap remains open. **Flag to user.**

## Phase C — Fixes applied (this iteration)

- [x] **C.1** Rewrote all 12 leak strings → plain user-facing copy ("coming soon",
  feature description) with zero phase letters / "placeholder" wording. Verified
  0 phase-leaks remain across `values/` and all `values-*/` locale files.
- [x] **C.2** `TaskRow.kt`: list-name `Text` → `weight(1f, fill = false)` +
  `maxLines = 1` + `Ellipsis`; due/done-ago `Text` → `maxLines = 1` +
  `softWrap = false`. Long names now ellipsize and the `· date` stays inline.
- [x] **C.3** `AppearanceCategory.kt`: `PickerRow` content lambda → `RowScope`;
  inner Row gains `spacedBy(6.dp)`; density chips → `FlowRow` of natural-width
  chips (first tried equal `weight(1f)`, but that truncated the longest label
  "Comfortable" → "Comfor…"; FlowRow keeps full labels and wraps whole chips).
- [x] **C.4** `AppearanceCategory.kt`: Neutral-mode pointer row → whole-row
  `clickable` + compact `ArrowForward` chevron (string reused as the icon
  `contentDescription`). Headline + blurb now use full width.

## Phase D — Re-verify on AVD

- [x] **D.1** Rebuilt + reinstalled; task rows render long names ellipsized with
  the `· date` inline ("Sprint 25 b… · 2026-05-17"). Verified on device.
- [x] **D.2** Density chips: "Compact / Comfortable" on line 1, "Spacious" wraps
  as a whole chip to line 2 — every label fully readable. Verified on device.
- [x] **D.3** CalDAV row now reads "Two-way calendar sync." (no phase letter);
  grep confirms 0 phase-leaks across `values/` + all locale files. Verified.
- [x] **D.4** Neutral-mode row: full-width headline + blurb + "→" chevron. Verified.

## Status: ✅ DONE (iter-1 defects B.1/B.2/B.3/B.5 fixed + verified; B.4/B.6 carried to iter-2; G.1 flagged to owner)
