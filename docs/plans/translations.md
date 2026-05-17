# Translations — workflow + canonical source-of-truth

## Status: ✅ DECIDED — ready for implementation.

> Phase U.5 (closes F11 + ships the locale scaffolding). Mirrors the
> `shutterboy` / `tonearmboy` / `whisperboy` pattern: every sister app uses
> the same workflow so a translator coming from one is at home in the next.

## Per-locale AVD-smoke ritual (TR-A.4)

Every locale that ships — `en-rGB` today, future `de` / `fr` / `ja` /
… — must pass this verbatim sequence on the headless `medium_phone`
AVD before the row in the "Tested locales" table flips to `shipped`.
The viewer locale is OS-level (T-10): no in-app picker, so we drive it
through `adb`.

```bash
# 1. Boot the AVD (or attach to a running one — emulator-5554).
scripts/start-avd.sh
~/Android/Sdk/platform-tools/adb -s emulator-5554 wait-for-device

# 2. Flip the system locale. Use BCP-47 (Android accepts e.g. en-US,
#    en-GB, de-DE, fr-FR, ja-JP). The `persist.sys.locale` prop
#    survives the restart triggered next.
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell setprop persist.sys.locale <bcp47>
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell stop
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell start

# 3. Re-install the debug APK to pick up any changed string resources,
#    relaunch, walk Schedule / Tasks / Settings / Wizard, screencap.
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk \
    ./gradlew :app:assembleDebug
~/Android/Sdk/platform-tools/adb -s emulator-5554 install -r \
    app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start \
    -n com.eight87.strictlykeptboy/.MainActivity
~/Android/Sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p | \
    magick - -resize 50% /tmp/skb-<bcp47>.png

# 4. Reset to en-US so the next session starts from a known baseline.
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell setprop persist.sys.locale en-US
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell stop
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell start
```

Pass criteria: the translated keys appear; the untranslated keys fall
back to English silently (T-9 — no `???` markers, no logcat warnings).

## Tooling guardrails (TR-A.1 / TR-A.2)

- **`scripts/check-hardcoded-strings.sh`** — greps `app/.../ui/**.kt`
  for `Text("...")` / `text = "..."` style literals outside the
  allowlist (test tags / log strings / Compose previews / wire-format
  `.label` references / strings already routed via `stringResource(...)`
  or `R.string.*`). Exits non-zero on any finding. Run before opening
  a translation PR; if your `release-preflight.sh` exists, wire it in
  there. Invocation:

  ```bash
  scripts/check-hardcoded-strings.sh
  ```

- **`./gradlew :app:translationsAudit`** — diffs each
  `values-<bcp47>/strings.xml` against canonical and reports missing
  keys (fall back to English by design) + orphan keys (in the locale
  but not in canonical → stale rename). Run before adding a row to the
  "Tested locales" table:

  ```bash
  JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk \
      ./gradlew :app:translationsAudit
  ```

## Canonical source of truth

`app/src/main/res/values/strings.xml` is the **canonical English file**.
Every user-facing literal in the app originates here. Adding a new
string means:

1. Add the `<string name="...">English copy</string>` entry to
   `values/strings.xml`.
2. Reference it from Compose via `stringResource(R.string.name)` (or
   `Context.getString(R.string.name)` outside composition).
3. Locale variants pick it up automatically — see below.

Hardcoded user-facing literals in Kotlin code are a regression and
should be caught in code review. The standing rule is: if a human will
ever read it on-screen, it lives in `strings.xml`.

**Wire-format exception:** a small number of enum `.label: String`
fields (e.g. `RoleId.label`, `Honorific.label`, `Alignment.label`)
remain as English literals in Kotlin — but these are **persisted to
disk** as part of the repo file format (`calendar.toml`,
`identity.toml`) and must NOT be localised. The UI never reads those
fields directly; it routes through `labelString()` / `labelRes`
extensions in `ui/a11y/EnumLabels.kt` which DO go through
`strings.xml`. The split is documented inline on each enum.

## Locale-variant files (partial overrides)

Locale-specific overrides live under `app/src/main/res/values-<bcp47>/`,
matching the Android resource qualifier convention:

- `values-en-rGB/` — English (United Kingdom). Ships in v1 as a
  *proof-of-concept partial override* for one ceremonial difference
  ("Colour seed" vs "Color seed"). Verifies the fallback chain works.
- (future) `values-de/`, `values-fr/`, `values-ja/`, etc.

### Partial-override is the design

Locale files are **intentionally partial**. They override only the keys
that differ from the canonical English. Every other key falls through
to `values/strings.xml` automatically — this is Android's built-in
behaviour and is the correct outcome.

Do NOT mirror the entire canonical file into every locale variant.
That:

- creates a massive maintenance burden,
- creates a window where a locale file silently diverges from
  canonical English on a key the translator never intended to touch
  (e.g. someone adds a new `cd_*` string in English, and the German
  file inherits the literal English copy rather than carrying a stale
  half-translated copy that's worse than English),
- defeats the merge story for translation PRs (one canonical PR vs
  N locale-mirror PRs).

The fallback rule reads: "English is the floor — every locale rises
above it on the keys the translator chose to translate." A locale at
30% coverage is shippable and useful; the remaining 70% reads as
English. This is acceptable. A user who configured their phone in
German *expects* an app to be partially translated; what they do NOT
expect is for a single random string to be in some other random
language because a translator missed a sync.

## Adding a new language

The same pattern across all 887boy apps:

1. The user and Claude pair in a **dedicated session**. No community
   PRs assumed for translations — the user owns final wording for
   every locale they ship.
2. Open `values/strings.xml` side-by-side with the empty
   `values-<bcp47>/strings.xml`.
3. Translate string-by-string, only the ones the user wants to ship.
   It's fine to skip strings — they fall back.
4. Run `:app:testDebugUnitTest` — `LocaleFallbackTest` proves the
   fallback chain still works.
5. AVD-smoke: `adb shell setprop persist.sys.locale <lang>-<region> &&
   adb shell stop && adb shell start`, launch app, walk Schedule /
   Tasks / Settings / Wizard, verify the translated keys appear AND
   the un-translated keys fall back to English cleanly (no `???` or
   raw resource names).
6. Reset locale: `adb shell setprop persist.sys.locale en-US && adb
   shell stop && adb shell start`.

## Tested locales

| BCP-47 | File | Coverage | Status |
| --- | --- | --- | --- |
| `en` (canonical) | `values/strings.xml` | 100% | shipped — Phase U.5 |
| `en-rGB` | `values-en-rGB/strings.xml` | partial — colour spellings | shipped — Phase U.5 (smoke for fallback) |

## Out-of-scope copy

- **Identity-driven copy** (praise terms, pronouns, honorifics, dom
  persona register) lives in the user's per-repo `identity.toml` and
  is set by the user via the wizard. This is NOT translated chrome —
  it's user-supplied content that flows back into the UI verbatim.
- **User-supplied content** (event titles, task names, repo display
  names, calendar names) is also not translated — translation is for
  app chrome, not user data.
- **Wire-format identifiers** (TOML keys, enum `.label` fields used
  by `WizardScaffolder` for disk persistence, `RoleId.id`,
  `Lifestyle.id`, `SpeciesChoice.id`) are stable English by design
  and must not be touched.

## Verification

- `EnumLabelLocalizationTest` walks every enum that was previously
  hardcoded and asserts every variant resolves to a non-empty English
  string via `labelRes`.
- `LocaleFallbackTest` exercises the en-rGB partial-override and
  proves canonical-English fallback for un-overridden keys.
- AVD smoke at end-of-Phase-U: `adb shell setprop persist.sys.locale
  en-GB`, launch app, confirm "Colour seed" appears on the repo
  settings appearance section.

## Locked decisions (Round 2 deep-dive)

These resolve every open thread that was implicit in the prose above
so subagents can ship without further user input.

- **T-1 Canonical locale is `values/strings.xml` (English).** Every
  new string lands here first. **Rationale:** matches Android's
  built-in fallback floor; mirrors tonearmboy / shutterboy /
  whisperboy convention.
- **T-2 Locale variants are partial overrides only.** Mirroring the
  canonical file into a locale variant is a regression and must be
  reverted on sight. **Rationale:** see "Partial-override is the
  design" above — maintenance-cost + silent-divergence + merge-story
  reasons.
- **T-3 v1 proof-of-concept locale is `en-rGB`.** One ceremonial diff
  ("Colour seed" vs "Color seed") is enough to prove the fallback
  chain end-to-end. **Rationale:** smallest possible diff that
  exercises the qualifier resolver without bringing a translator into
  the loop on day 1.
- **T-4 Wire-format enum `.label` stays English in Kotlin.** Persisted
  to `calendar.toml` / `identity.toml`; UI reads via `labelString()` /
  `labelRes` in `ui/a11y/EnumLabels.kt`. **Rationale:** repo files are
  the source of truth and must round-trip across locales — a German
  user's `identity.toml` opened on an English phone must read
  identically.
- **T-5 No community translation PRs in v1.** User + Claude pair in a
  dedicated session per locale; user owns final wording. **Rationale:**
  per CLAUDE.md "Editorial — user-facing copy"; identity-driven copy
  is too kink-positive / register-sensitive to crowd-source.
- **T-6 No translation-management tooling (Weblate / Crowdin / Lokalise)
  in v1.** Plain `strings.xml` files only, edited in the repo.
  **Rationale:** agent-native + file-first; an external SaaS would
  break the "Claude can read and write the schedule the same way the
  user can" invariant.
- **T-7 Plurals use `<plurals>` from day one, even on English-only
  strings that have a count.** **Rationale:** retrofitting plurals
  after a locale ships is a breaking change for that locale's
  translator; cheap to do up front.
- **T-8 No RTL-specific layout work in v1.** Compose handles BiDi
  automatically; no Hebrew / Arabic locale is in scope for Round 2.
  **Rationale:** scope discipline — first non-English locale will be
  German or French (LTR).
- **T-9 Untranslated keys fall back to English silently. No
  `???PLACEHOLDER???` markers, no logcat warnings, no Crashlytics
  events.** **Rationale:** partial coverage is shippable by design
  (see prose above); noise would train users + agents to ignore the
  channel.
- **T-10 Locale switching is OS-level only.** No in-app language
  picker in v1. **Rationale:** Android 13+ per-app language is
  available system-side; building a duplicate UI is wasted surface
  for a 1-locale-and-a-half app.
- **T-11 Locale qualifier convention is BCP-47 with Android's
  `-r<REGION>` form (e.g. `values-en-rGB/`, `values-pt-rBR/`).**
  **Rationale:** required by AGP resource resolver; the
  `BCP47:values-<tag>/` style is for `valuesB+...` which we are not
  using.
- **T-12 String key naming: `snake_case`, prefixed by surface
  (`wizard_`, `schedule_`, `cd_` for content-description, `err_` for
  errors).** **Rationale:** matches existing keys in the codebase and
  keeps the wizard / schedule diffs reviewable in isolation.
- **T-13 Each new locale ships its own `LocaleFallbackTest`
  variant + an AVD smoke commit.** **Rationale:** locale regressions
  are silent on unit tests alone; the AVD-smoke discipline applies
  to translation work the same way it applies to Compose work.

## Phase U.5 — locale scaffolding (shipped)

- [x] **U.5.1** Migrate every user-facing literal in Kotlin to
  `R.string.*` via `stringResource` / `getString`.
- [x] **U.5.2** Stand up `values/strings.xml` as the canonical English
  source-of-truth.
- [x] **U.5.3** Stand up `values-en-rGB/strings.xml` as the partial-
  override proof-of-concept (one ceremonial spelling diff).
- [x] **U.5.4** Add `ui/a11y/EnumLabels.kt` with `labelString()` /
  `labelRes` extensions so wire-format enum labels never leak into
  the UI directly.
- [x] **U.5.5** Add `EnumLabelLocalizationTest` covering every enum
  variant previously hardcoded.
- [x] **U.5.6** Add `LocaleFallbackTest` proving canonical-English
  fallback for un-overridden keys.
- [x] **U.5.7** AVD-smoke `en-GB`: confirm "Colour seed" appears on
  repo settings appearance section; confirm un-overridden keys fall
  back to English with no `???` markers.

## Phase TR-A — translation tooling hygiene (shipped Round 2.x)

- [x] **TR-A.1** Add a lint rule (or CI grep) that flags hardcoded
  user-facing literals in `app/src/main/java/**/*.kt` outside the
  whitelisted wire-format enum files. Rationale: prevents
  regressions on the canonical-English invariant. Shipped as
  `scripts/check-hardcoded-strings.sh`; allowlist covers test tags
  / Compose previews / log strings / wire-format `.label`
  references / strings already routed via `stringResource` /
  `R.string.*`. Documented invocation in "Tooling guardrails" above
  (no `release-preflight.sh` to wire into today).
- [x] **TR-A.2** Add a `translations:audit` Gradle task that diffs
  each `values-<bcp47>/strings.xml` against canonical and prints
  coverage % per locale. Rationale: makes the "30% is shippable"
  call observable without manual key counting. Shipped as
  `:app:translationsAudit` (depends on `:app:preBuild`), reports
  missing keys + orphan keys + coverage % per BCP-47 locale dir.
- [x] **TR-A.3** Convert every count-bearing English string to a
  `<plurals>` resource (T-7). Audit `schedule_*`, `tasks_*`,
  `wizard_*` namespaces. 25 keys converted (notif / sync / together
  / import / tasks / widget / lifestyle / adopt / backuprestore /
  trip / event-detail namespaces); call-sites moved to
  `pluralStringResource(...)` or `resources.getQuantityString(...)`.
- [x] **TR-A.4** Document the per-locale AVD-smoke ritual in
  `docs/plans/translations.md` (this file) as locales are added,
  appending rows to the "Tested locales" table. See "Per-locale
  AVD-smoke ritual" section above for the verbatim command
  sequence.

## Phase TR-B — first non-English locale (deferred to user-pick session)

- [ ] **TR-B.1** User + Claude pair in a dedicated session, picking
  the first non-English locale (default: `de` — German — unless the
  user picks otherwise at session start).
- [ ] **TR-B.2** Create `values-<bcp47>/strings.xml` empty-shell.
- [ ] **TR-B.3** Translate string-by-string, only the keys the user
  wants to ship. Skipping is fine (T-9).
- [ ] **TR-B.4** Add a locale-specific `LocaleFallbackTest` variant.
- [ ] **TR-B.5** AVD-smoke: `adb shell setprop persist.sys.locale
  <lang>-<region>`, walk Schedule / Tasks / Settings / Wizard,
  screencap the wizard alignment + lifestyle pages (identity-driven
  copy from `identity.toml` flows through verbatim — confirm).
- [ ] **TR-B.6** Append a row to the "Tested locales" table with
  coverage %.
- [ ] **TR-B.7** Reset locale to `en-US` before closing the session.

## Phase TR-C — identity-driven copy boundary (locked)

- [x] **TR-C.1** Confirm via test that no key under
  `values/strings.xml` references praise terms, pronouns,
  honorifics, or dom persona register — those are user-supplied via
  `identity.toml` and must never appear as resource keys.
  Rationale: locks the boundary defined in "Out-of-scope copy" above
  so a future translator cannot accidentally translate a user's
  personal vocabulary. Shipped as
  `app/src/test/java/.../EnumLabelStabilityTest.kt` — asserts every
  wire-format enum `.label` (SpeciesChoice / Alignment / RoleId /
  Honorific / ToneRegister / EmojiDensity) plus `Lifestyle.id`
  stays English; if a future PR routes any of them through
  `R.string.*`, this test fails before the on-disk repo schema
  (D.3 / T-4) breaks for users worldwide.
- [ ] **TR-C.2** Document the boundary inline at the top of
  `values/strings.xml` as a `<!-- comment -->` so the next
  translator sees it before touching the file.
