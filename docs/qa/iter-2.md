# QA iteration 2 — deeper sweep, defect log, fixes

Date: 2026-06-07. Target: `medium_phone` AVD (API 36) via scrcpy. Built on
iter-1 (`6e02d2c`). Focus: surfaces not covered in iter-1 — Lifestyle,
Identity, Mode, Sync, event-create, plus deliberate back-stack testing.

## Phase A — Walkthrough (surfaces visited)

- [x] **A.1** Lifestyle settings — roles/trips/neutral-mode.
- [x] **A.2** Identity settings — praise/pronoun/honorific fields, tone register, emoji density, live preview, signing section.
- [x] **A.3** Mode settings — "How do you live?" 6-role radio + Dom persona chips + cadence.
- [x] **A.4** Sync settings — interval / Wi-Fi-only / push policy / conflict policy.
- [x] **A.5** Event-create sheet (Free-form) — title, start/end, repeat, calendar, notes, private.
- [x] **A.6** Settings back-stack behaviour (deliberate test).

## Phase B — Defects found + fixed

- [x] **B2.1 — Internal decision ids leak into user copy.** Missed by iter-1's
  "Phase X" grep: `(K-3)`, `(K-3 / KI)`, `(D.81)` appeared in neutral-mode +
  briefings-calendar strings. Stripped the parentheticals (meaning preserved).
  0 id-leaks remain.
- [x] **B2.2 — Identity chip groups wrap mid-word.** Tone register "Playful" →
  "Playfu / l"; same latent risk on emoji density. `ToneRow`/`EmojiRow` used a
  plain `Row`. → `FlowRow` (full labels, whole-chip wrap).
- [x] **B2.3 — Sync chip groups overflow.** `IntervalRow` had **6** chips in a
  plain `Row` (guaranteed phone overflow); push/conflict policy rows likewise.
  → all three `FlowRow`.
- [x] **B2.4 — Mode chip groups overflow.** Dom-persona (6 chips) + cadence rows
  used plain `Row`. → `FlowRow`.
- [x] **B2.5 — System/gesture back skips the Settings category list (confirmed
  B.6).** The toolbar back-arrow correctly did `popToCategoryList()` in a
  subpage, but there was no `BackHandler`, so hardware/gesture back over-popped
  the whole overlay to Schedule. Added `BackHandler(enabled = inSubpage) {
  state.popToCategoryList() }` mirroring the toolbar logic.
- [x] **B2.6 — Event-create calendar chips render the repo name twice.**
  `MainActivity` set both `displayName` and `repoDisplayName` to `cfg.displayName`,
  so `CalendarOption.label()` produced "my calendar · my calendar" /
  "demo · kept-life · demo · kept-life". Set `repoDisplayName = null` (the option
  is a repo's default calendar; show the repo name once).

## Phase B-carry — Carried to iteration 3

- [ ] **B2.7 (= F) — Event-create Start/End show raw ISO-8601**
  (`2026-06-07T12:00+02:00`) as editable text. Explicitly v1-deferred in code,
  but it is the primary create flow and reads as developer-grade. **Iter-3
  headline: wire Material3 date + time pickers.**
- [ ] **B.4 — Event-detail timezone shown redundantly** ("Arctic/Longyearbyen ·
  Longyearbyen"). Carried from iter-1; assess in iter-3.

## Phase C — Systemic note

The chip-wrap bug recurred in 5 files (density, tone, emoji, sync ×3, mode ×2).
All now use inline `FlowRow`. The wizard already had a private `WrappingChipRow`
helper. **SOLID/DRY follow-up (logged in `refactor-solid.md`): extract one shared
`WrappingChipRow` into `ui/components/` and route every chip group through it,
removing the wizard's private copy and the inline duplications.**

## Phase D — Re-verify on AVD

- [x] **D.1** Identity: tone register shows all 5 chips on one line; emoji density clean. Verified.
- [x] **D.2** Sync: 6 interval chips wrap to 2 lines, all readable; push/conflict clean. Verified.
- [x] **D.3** Mode: persona chips wrap across 3 lines, full labels. Verified.
- [x] **D.4** Back-stack: system back from Identity subpage → Settings category list (not Schedule). Verified.
- [x] **D.5** Event-create: calendar chips read "my calendar" / "demo · kept-life" (no duplication). Verified.
- [x] **D.6** Identity signing copy reads "(coming soon)" (no "Phase GG"). Verified.

## Status: ✅ DONE (B2.1–B2.6 fixed + verified; B2.7/B.4 carried to iter-3; DRY follow-up logged)
