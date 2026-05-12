# draft-kink-positive-identity — kink-positive identity reframe

## Status: ✅ INTEGRATED — see `decisions.md` D.55 / D.57 / D.58 / D.59 / D.60 / D.61 / D.62 (locks K-1..K-7). SP-1..SP-9 retraction-status applied inline in `templates-demo-wizard.md`. VV.12 rewritten + VV.13 added in `main.md`. Age gate + neutral-mode toggle in `main.md` Phase K.14 and `ui-spec.md` Phase UI-NN. CLI `skb mode neutral` at `cli-tooling.md` CLI-L.26. No new phase letter — decisions-only integration. Original draft content preserved below for reference.

## Framing

strictlykeptboy is being repositioned from a "SFW-surface, kinky-body"
two-layer app to an openly kink-positive lifestyle organizer with
first-class peer support for neutral lifestyles. The previous
register-discipline (hint-not-blatant, charge-in-context-only) was
born of caution about Play Store distribution. That caution is now
demonstrably over-calibrated: the Play Store hosts an established
category of mature-themed lifestyle/dating/D/s apps (KinkD, FET,
Feeld, Obedience, etc.) under standard Mature 17+ content ratings.
strictlykeptboy joins that category — it does not pioneer it.

This draft enumerates the **retractions** of the previous SFW-surface
locks (SP-1..SP-9, VV.12) and the **new locked decisions** (K-1..K-7)
that replace them. All decisions below are LOCKED defaults per user
intent ("don't push decisions on me"). The integration agent applies
the edits listed in the Integration-notes section.

Two principles run through every decision:

1. **Kink-positive default surface; neutral-mode is a peer toggle.**
   The app does not pretend to be neutral and then "reveal" kink. Kink
   is on the surface. Users who want SFW chrome flip neutral-mode and
   get a peer-equal neutral surface (no apologetic copy, no
   "deactivated kink-mode" wording).
2. **Privacy is orthogonal to SFW.** Lockscreen-discreet behavior is
   driven by a per-event `private = true` flag that works identically
   for medical appointments, surprise parties, and kink-coded events.
   It is not a SFW mechanism.

---

## Phase KI-A — retract SP-1..SP-9 from `templates-demo-wizard.md`

Each existing SP-* decision is listed with the original framing, then
the retraction or replacement. Wizard output is now actual user-life
content, not "tastefully phrased demo data".

- [ ] **KI-A.1** **SP-1 — Dom display name "Sir" (charged-in-context,
      recruiter-safe).** *Replace, not retract.* Demo + wizard still
      default to **"Sir"** as the display name because it remains a
      good default for the target audience (one-syllable, traditional
      D/s address). The *justification* changes: it's the default
      because it fits the lifestyle, not because it's plausibly
      deniable. Users can pick any name — "Master", "Owner",
      "Daddy", "Mistress", "Goddess", "Coach", or a regular name —
      with no euphemism nudging from the UI.

- [ ] **KI-A.2** **SP-2 — Sub display name "Bat" (mascot fit, reads as
      nickname).** *Keep as-is.* The mascot tie-in stands. No SFW
      justification needed; it's just a cute default. Users can pick
      any sub name — "boy", "pet", "kitten", "pup", "slave", "toy" —
      directly from the wizard with no euphemism.

- [ ] **KI-A.3** **SP-3 — Couple events titled "Check-in" / "Weekly
      review" / "Date night" / "Monthly call"; calendar named
      "Co-schedule (with Sir)".** *RETRACTED.* The wizard now offers
      direct kink-coded titles as defaults when the user picks a
      D/s-coded alignment: "Inspection", "Cage check", "Protocol
      review", "Collar maintenance", "Service report", etc., alongside
      the neutral variants. The calendar can be called "Sir's
      schedule for boy", "Master's protocols", or anything the user
      picks. No locked SFW-readable title list. The neutral-mode
      toggle re-suggests the neutral variants ("Check-in", etc.) as
      defaults; user-authored titles already in the repo are never
      rewritten.

- [ ] **KI-A.4** **SP-4 — `kinky-chores` titles use grooming /
      health / posture language only; bodies may be candid.**
      *RETRACTED.* The titles can be candid too. "Edge denial log",
      "Cage check Mon/Wed/Fri", "Posture drill 18:00", "Service
      kneel before bed", "Protocol violation tracker" — all valid
      title-surface content. The body remains free-form Markdown as
      always.

- [ ] **KI-A.5** **SP-5 — `sub-scheduled` says "your partner" when no
      Dom configured.** *Replace.* When no Dom-identity is configured,
      `sub-scheduled` references "your Dom" by default (the most
      common case for the template); user can change to "your
      partner", "your Owner", "your Master", "your Mistress", "your
      Daddy", etc. in the template-apply screen. Neutral-mode users
      get "your partner" as the default.

- [ ] **KI-A.6** **SP-6 — `work-shift` ships three concurrent
      DAILY-INTERVAL=8 RRULEs.** *Keep as-is.* This is an RFC5545
      mechanics decision, not a surface-phrasing decision. Carries
      forward unchanged.

- [ ] **KI-A.7** **SP-7 — `master-scheduled` writes its calendar in
      the applying repo only.** *Keep as-is.* Architectural, not
      surface. Carries forward unchanged.

- [ ] **KI-A.8** **SP-8 — `sub-scheduled` in demo lays a thin
      "Co-schedule (with Sir)" calendar with one-off cross-author
      events.** *Replace.* Same mechanic, but the calendar name and
      seeded titles are now kink-coded by default ("Sir's schedule
      for Bat", "Inspection", "Cage check", "Service report"). Demo
      doubles as a "this is what your repo could look like" preview.
      Neutral-mode demo path uses the older "Co-schedule" /
      "Check-in" wording.

- [ ] **KI-A.9** **SP-9 — Demo "Sir's visit" event seeds the
      countdown widget; widget renders the title verbatim under SFW
      guarantee.** *Replace.* The demo seed event is "Sir's visit"
      (or in kink-default mode, can be seeded as "Sir's inspection
      visit", "Collar ceremony", etc.). The SFW guarantee on widget
      copy is RETRACTED (see KI-B). The widget renders whatever the
      user authored, verbatim.

---

## Phase KI-B — retract `main.md` Phase VV.12 (countdown widget SFW guarantee)

- [ ] **KI-B.1** **Original VV.12:** *"SFW phrasing guarantee: widget
      renders only user-authored event titles + locale-formatted day
      count. No editorial copy, no decorative text, nothing the app
      generates that could surface non-SFW phrasing on a lockscreen
      preview."*

- [ ] **KI-B.2** **Replacement VV.12:** *"User-authored event titles
      surface on the widget verbatim. The widget never adds editorial
      copy, decorative text, or app-generated phrasing — what the
      user typed is what shows. Lockscreen-discreet behavior is
      driven by the per-event `private = true` flag (see K-2): when
      true, the widget shows '—' instead of the title and the
      notification body is suppressed. This privacy mechanism works
      identically for any event the user marks private and is not
      tied to content register."*

- [ ] **KI-B.3** Phase VV.3 ("Lockscreen preview shows numerals only —
      privacy default") is preserved verbatim — that's a separate,
      narrower discretion default (numerals-only on lockscreen) and
      it composes correctly with K-2.

---

## Phase KI-C — add new locked decisions K-1..K-7 to `decisions.md`

- [ ] **KI-C.1** **K-1 — Kink-positive default surface (locked).** The
      app's default content (templates, sticker tags, alignment
      copy, wizard prose, suggested event titles) is openly
      kink-positive. Justification: the target audience is furries,
      kinksters, and people in D/s relationships; defaulting to
      neutral would underserve them. The wizard alignment screen
      offers "unaligned-private" as the neutral-mode toggle for
      users who want SFW defaults — neutral is a peer option, not
      the floor.

- [ ] **KI-C.2** **K-2 — Per-event privacy flag (locked).** Every
      event/task frontmatter accepts optional `private = true`
      (default `false`). When true:
      - Lockscreen notification shows generic "Scheduled event"
        label only; no title, no body.
      - Countdown / agenda widgets show "—" instead of the title.
      - Notification body is suppressed.
      - In-app rendering is unaffected (user sees their own data
        normally on an unlocked device).
      This is a **privacy mechanism**, not a SFW mechanism — it
      works identically for medical appointments, surprises, and
      kink-coded events. Per-calendar default supported via
      `calendar.toml` `default_private = true`; per-event flag wins.

- [ ] **KI-C.3** **K-3 — Neutral-mode scope (locked).** A
      Settings → Appearance → "Neutral mode" toggle (also set by
      wizard "unaligned-private" alignment). When ON:
      - Kink-tagged templates hidden from the template picker and
        wizard role-toggle list (tag: `kink-coded = true` in
        manifest).
      - Kink-tagged stickers replaced with their neutral counterparts
        in the active sticker pack (sticker `tags` field; see
        draft-avatar-stickers.md).
      - Kink-coded copy in wizard, settings, and onboarding replaced
        with neutral phrasing.
      - Alignment options reduce to "private organizing" with no
        dom/sub/switch axis.
      When OFF: kink surface restored. **User-authored content is
      NEVER censored** by either transition — only suggestions and
      chrome change. Existing events titled "Cage check" stay
      "Cage check" when neutral-mode flips on; they just don't get
      *suggested* on next wizard run.

- [ ] **KI-C.4** **K-4 — Play Store positioning (locked).**
      - Content rating: **Mature 17+** (covers "moderately
        suggestive themes and references"; we avoid explicit
        imagery to stay below the 18+ adults-only bar).
      - Category: **Lifestyle**.
      - Listing copy leads with the unique value prop: git-backed,
        atomic, AI-native, multi-repo, common-time finder. Lifestyle
        / D/s positioning is **mentioned, not lead-with**.
      - Screenshots: show both neutral-mode AND kink-mode versions
        as peer examples (4 of each).
      - First-launch age gate (K-6).
      - IARC questionnaire: declare "infrequent/mild references to
        adult themes" + "user-generated content" + no nudity, no
        explicit sexual content, no graphic violence.

- [ ] **KI-C.5** **K-5 — Content guidelines for shipped defaults
      (locked).** What the app CAN ship in default content:
      - Kink-coded task names and descriptions (text only).
      - Kink-coded sticker artwork in SFW-rendered chibi-style:
        collars, harnesses, cages, leashes, paw prints, locks,
        bunny ears, fox tails — depicted as accessory / clothing
        style only.
      - Bat-mascot guideline: collar / harness / cage suggestion via
        clothing-style accessories is fine; explicit anatomy,
        nudity, or graphic sexual depiction is not.
      - Reaction emoji set with kink-positive entries (K-7).

      What the app does NOT ship:
      - Pornography or explicit imagery.
      - Nudity (including stylized).
      - Graphic depictions of sexual acts.
      - Anything that pushes past Mature 17+ into 18+ adults-only.

      User-authored content in user-owned repos is unconstrained by
      this rule — it constrains only what *ships* with the app.

- [ ] **KI-C.6** **K-6 — Age verification (locked).** First-launch
      one-time age gate:
      - Modal: "This app contains references to adult lifestyle
        dynamics. You must be 17 or older to use it."
      - Buttons: "I am 17 or older — continue" / "Exit".
      - Decline → app finishes gracefully (returns to launcher).
      - Confirmation stored in encrypted app prefs
        (`age_confirmed_at = <ISO ts>`).
      - NO ID upload, NO email verification, NO third-party
        verification SDK — Play Store's IARC rating + the modal
        constitute the regulatory surface.
      - Re-shown only if app data is cleared.

- [ ] **KI-C.7** **K-7 — Reaction set kink-positivity (locked).**
      Phase FB (cross-repo feedback / reactions) ships a default
      reaction set including: 🔒 locked, 🐾 paw, 💍 collar (rendered
      as collar variant of the ring), 👍 good-boy/good-girl/good-pet
      (label varies by mode-label), 🦴 bone, 🥄 spoon (aftercare).
      Neutral-mode behavior:
      - These reactions are **filtered out of the reaction picker**
        when neutral-mode is on (user can't pick them).
      - But reactions **received from a non-neutral-mode partner**
        render in the user's view with their generic counterpart:
        🔒 locked → 🔒 (same), 🐾 paw → ✋ hand, 💍 collar → 💍 ring,
        good-boy → 👍 thumbs-up, etc.
      - LOCK: visually downgrade, never suppress. The neutral-mode
        user sees that the partner reacted; just with a neutral
        visual.

---

## Phase KI-D — Play Store precedent research (for App Store submission)

- [ ] **KI-D.1** **KinkD (com.kinky.fetlifestyle)** — live on Google
      Play. BDSM/fetish dating. Mature 17+. Category: Dating.
      Notable: robust identity verification, privacy-first chrome,
      uses "alternative lifestyle" framing in store listing.

- [ ] **KI-D.2** **FET: Kinky BDSM Dating & Chat (de.ideawise.fet)** —
      live on Google Play. Direct "Kinky BDSM" in the title. Mature
      17+. Category: Dating. Demonstrates that explicit kink-coded
      titles pass review.

- [ ] **KI-D.3** **Obedience: BDSM habit tracker (com.obedience)** —
      live on Google Play. **Direct precedent** for our category:
      not dating, not social — a habit/task tracker with explicit
      BDSM framing. Mature 17+. Category: Lifestyle. This is the
      closest existing app to strictlykeptboy's positioning.

- [ ] **KI-D.4** **Feeld** — live on Google Play. Polyamory / kink-
      friendly dating. Mature 17+. Category: Dating. 2M+ users.
      Mainstream-visible kink-positive precedent.

- [ ] **KI-D.5** **Whiplr** — currently in update-pending state on
      Google Play (per recent search). Hardcore-kink-oriented;
      historically Mature 17+. Cautionary precedent: apps that lean
      *explicit* face listing churn. We stay clear of that bar with
      K-5.

- [ ] **KI-D.6** **Mister** — gay men's dating/kink app. Live on
      Google Play. Mature 17+. Category: Dating.

- [ ] **KI-D.7** **Grindr** — gay dating with substantial kink
      visibility (tribes, profile fields). Mature 17+. Category:
      Dating. Mainstream-distribution precedent for explicit kink
      vocabulary in profile UI.

- [ ] **KI-D.8** **Fetlife** — *not* on Google Play (web-only).
      Their absence is informative: Fetlife hosts user-generated
      explicit imagery, which is the bar we don't cross. As long as
      strictlykeptboy stays text-and-stylized-stickers, we're in the
      KinkD/Obedience class, not the Fetlife class.

- [ ] **KI-D.9** **2026 regulatory context:** Texas SB 2420 (under
      preliminary injunction as of May 2026), Utah law effective
      May 7 2026. These affect age-verification flow for minors;
      since our app is 17+ with an age gate (K-6) and no minor
      onboarding path, the regulatory surface is the same as KinkD,
      FET, Obedience. Monitor; no architectural change needed.

---

## Phase KI-E — wizard copy review checklist (kink-positive direct phrasing)

Confirm each wizard screen (per Agent 4's `draft-lifestyle-wizard.md`)
can use direct kink phrasing without euphemism. One-line note per
screen.

- [ ] **KI-E.1** **Welcome screen.** Mascot + "Your schedule, your
      lifestyle, your repo." → Lifestyle-broad framing, no kink-lead,
      no euphemism. OK.
- [ ] **KI-E.2** **Path picker (demo / templates / empty).** → No
      surface text to retract. OK.
- [ ] **KI-E.3** **Alignment screen.** Options: "Dominant",
      "submissive", "switch", "unaligned-private". → Direct phrasing.
      "unaligned-private" doubles as neutral-mode toggle. OK.
- [ ] **KI-E.4** **Role-toggle screen (morning-bird, kinky-chores,
      master-scheduled, sub-scheduled, etc.).** → Template display
      names can be direct: "Kinky chores", "Master-scheduled",
      "Sub-scheduled" — no euphemism. OK.
- [ ] **KI-E.5** **Identity-name screen.** → Free-text + suggestion
      list including "Sir", "Master", "Daddy", "Mistress", "Goddess",
      "Coach", "boy", "pet", "kitten", "pup", and regular names. OK.
- [ ] **KI-E.6** **Repo-name screen.** → Suggestion list can include
      "kept-schedule", "sirs-boy", "masters-protocols" alongside
      neutral options. OK.
- [ ] **KI-E.7** **Auth screen.** → No content register. OK.
- [ ] **KI-E.8** **Done screen.** → "Your schedule is ready. <Sir>
      can read it at <URL>." Direct phrasing with the user's chosen
      Dom-name interpolated. OK.

---

## Phase KI-F — onboarding copy / first-launch UX

- [ ] **KI-F.1** **Play Store listing copy** leads with: "Git-backed
      calendar and timeboxing for people who want their schedule
      under their own control. AI-native (Claude reads and writes
      your repo). Atomic-activity tracking. Multi-repo overlay.
      Common-time finder." Lifestyle / kink-positive surface is
      mentioned in paragraph 2-3, not paragraph 1.

- [ ] **KI-F.2** **First-launch sequence:**
      1. Age gate (K-6).
      2. Mode-label picker (D.45 — "Simplified" / "Focused" /
         "Good Boy Mode" / etc.) — direct phrasing in the picker,
         no euphemism.
      3. Path picker (demo / templates / empty / receive-link).
      4. Wizard alignment screen — first place kink-positive copy
         appears explicitly (Dominant / submissive / switch /
         unaligned-private).
      5. Rest of wizard per Agent 4's draft.

- [ ] **KI-F.3** **Rationale for the staging:** the Play Store
      listing meets users where they are — not everyone discovering
      this app is shopping for a kink-app; some are git-curious
      people who like the architecture. The wizard graciously
      routes neutral users to neutral-mode without making them feel
      out of place (just pick "unaligned-private" — no extra clicks,
      no apology screen). Kink-positive users flow through the
      same wizard and see the direct phrasing they came for.

---

## Integration notes (for the integration agent)

Apply these edits in order. All edits are mechanical — every locked
decision in this draft replaces specific prior text.

### `docs/plans/decisions.md`

Append new section **`# Round 4 — kink-positive identity (D.54
onward)`** with sub-decisions **K-1..K-7** (copy phase KI-C bodies
verbatim, renumbered D.54..D.60 if the integration agent prefers a
numeric register — but keep `K-N` as locked-decision short-IDs in
cross-references). The doc should also add a top-of-file note
retracting D.1's "Play Store branding stays hinting-not-blatant" —
that line conflicts with K-4 and gets replaced with "Play Store
branding is openly lifestyle-positive at Mature 17+; see K-4".

### `docs/plans/templates-demo-wizard.md`

For each of SP-1..SP-9, append a "**RETRACTED / REPLACED in Round 4
— see K-1..K-7 and draft-kink-positive-identity.md KI-A.N**" note,
**inline at the SP-N decision**. Update the SP summary table at
~line 1620 with retraction-status column. Specific edits:

- SP-1 line 37: append "(*replaced KI-A.1 — same default, different
  justification*)".
- SP-2 line 42: append "(*kept KI-A.2*)".
- SP-3 line 44: append "(*RETRACTED KI-A.3 — wizard now offers
  direct kink-coded titles as defaults*)".
- SP-4 line 50: append "(*RETRACTED KI-A.4 — titles can be candid
  too*)".
- SP-5 line 53: append "(*replaced KI-A.5 — defaults to 'your Dom'
  with user-pick override*)".
- SP-6 line 369: append "(*kept KI-A.6 — architectural*)".
- SP-7 line 680: append "(*kept KI-A.7 — architectural*)".
- SP-8 line 992: append "(*replaced KI-A.8 — calendar name +
  seeded titles now kink-coded by default*)".
- SP-9 line 985: append "(*replaced KI-A.9 — seed kept; SFW
  guarantee retracted, see KI-B*)".

Also: surface-phrasing-decisions framing block at lines 35–56 gets a
prepended note: *"Round 4: SP-1..SP-9 retracted/replaced per K-1..K-7
in `decisions.md` and `draft-kink-positive-identity.md`. The
surface-phrasing register is now kink-positive direct, not
SFW-readable. The neutral-mode toggle (K-3) preserves a peer-equal
SFW path."*

### `docs/plans/main.md`

Phase VV.12 — replace the bullet text with KI-B.2's replacement
language verbatim. VV.3 stays as-is. Add a new Phase VV.13 bullet:
**"Per-event privacy flag (K-2): when `private = true`, widget
renders '—' instead of title and suppresses any subtitle. Composes
with VV.3 (lockscreen-numerals-only) — when both apply, the widget
on the lockscreen shows only the numeral count."**

Add a new top-level Phase **AG — age gate + neutral-mode toggle**
covering K-6 age-gate flow and K-3 neutral-mode Settings toggle.
Insert before Phase L (template apply) so first-run sees the gate
first.

### `docs/plans/draft-lifestyle-wizard.md`

(May exist by integration time as Agent 4's parallel draft.) The
wizard ASKS the user for alignment / neutral-mode choice; this draft
defines what the toggle DOES (K-3). Cross-reference: integration
agent confirms Agent 4's wizard alignment screen offers the four
options listed in KI-E.3 and that "unaligned-private" wires through
to the K-3 neutral-mode flag. No edit needed if Agent 4 already
landed this.

### `docs/plans/draft-avatar-stickers.md`

The sticker `tags` field works with neutral-mode toggle (K-3). The
integration agent confirms each sticker manifest entry has a
`tags = ["kink-coded"]` or `tags = ["neutral"]` (or both) marker. In
neutral-mode, kink-coded stickers are hidden from the picker;
neutral counterparts in the same pack remain. No new sticker
artwork required for this reframe — existing stickers per
`draft-avatar-stickers.md` are already designed under the K-5
SFW-stylized-chibi guideline.

### `docs/plans/notifications-sharing-import.md`

Add to the notifications-channels section: `private` flag (K-2)
suppresses notification title + body, leaving channel name as the
only visible string. Add to the cross-repo feedback / reactions
section (Phase FB): reaction-set per K-7, with neutral-mode
downgrade behavior.

### `docs/plans/data-model.md`

Add `private: bool = false` to the event / task / recurrence
frontmatter schema (K-2). Add `default_private: bool = false` to
`calendar.toml` schema. Add `tags: list<string>` to sticker pack
manifest if not already present (K-3 / K-7 coordination).

### `docs/plans/cli-tooling.md`

Add `--private` flag to `skb event add` / `skb task add` /
`skb recurrence add`. Add `skb mode neutral on|off|toggle` aliasing
the K-3 setting (parallel to `skb mode simplified|full|toggle` from
D.52).

---

## Report-back summary

This draft locks K-1..K-7 (kink-positive default surface, per-event
privacy flag, neutral-mode toggle scope, Mature-17+ Play Store
positioning, shipped-content guidelines, age gate, reaction-set
kink-positivity) and retracts the SFW-surface register: SP-3, SP-4,
SP-8, SP-9 fully retracted/replaced with direct kink phrasing; SP-1,
SP-2, SP-5 kept with revised justification; SP-6, SP-7 untouched
(architectural). VV.12's SFW phrasing guarantee is replaced by
verbatim user-content rendering + K-2 privacy flag. Play Store
precedent (KinkD, FET, Obedience habit tracker, Feeld, Mister) shows
we're joining an established Mature-17+ Lifestyle category, not
pioneering. Neutral-mode is a peer-equal toggle, not a fig-leaf.
User-authored content is never censored across mode transitions.
