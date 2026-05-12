# Translations — workflow + canonical source-of-truth

> Phase U.5 (closes F11 + ships the locale scaffolding). Mirrors the
> `shutterboy` / `tonearmboy` / `whisperboy` pattern: every sister app uses
> the same workflow so a translator coming from one is at home in the next.

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
