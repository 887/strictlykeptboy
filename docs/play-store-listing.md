# strictlykeptboy — Play Store listing copy

Phase W.4 deliverable. Locked decisions: K-4 (Mature 17+ Lifestyle
category, lead with git-backed value prop, lifestyle / D/s positioning
mentioned in paragraph 2-3 not paragraph 1), D.59.

## Category and rating

- **Category:** Lifestyle
- **Content rating:** Mature 17+
- **Tags:** `calendar`, `todolist`, `git`, `agent`, `lifestyle`, `timeboxing`

## Title (50 chars max — counts spaces)

```
strictlykeptboy — agent-native calendar
```

(39 chars)

## Short description (80 chars max)

```
Git-backed timeboxing & todolists. AI-readable. Multi-repo. Common-time finder.
```

(79 chars)

## Full description (4000 chars max)

```
strictlykeptboy is a calendar, timeboxing tool, and todolist that stores everything
as plain Markdown files in a git repo. Every event, every task, every recurrence,
every cancellation is a single hand-editable file you (and your AI assistants) can
read directly. No proprietary database. No cloud lock-in. No surprise telemetry.

— What makes it different —

Most calendar apps treat your schedule as opaque rows in their server's database.
strictlykeptboy treats your schedule as a git repository you own. Push it to GitHub,
to Forgejo, to Codeberg, or to nowhere — phone-only repos are first-class. Share
the repo with a partner, a family member, or a team and you get a shared calendar
that's diffable, auditable, and offline-first. Hand the repo to Claude or your
agent of choice and it can read your week the same way you do.

— Features —

• Timeboxing day view — see your day as edge-to-edge blocks, big tap targets,
  current activity emphasised
• Atomic activities — one event per file, never an opaque batch update
• Inverted habits — default state is "completed by schedule"; you only write
  a file when you deviate, so the repo stays quiet when life goes to plan
• Multi-calendar overlays — layer N calendars (work, partner, routines,
  vacation) with priority + supersedence; one calendar can pause another
• Common-time finder — sweep-line scheduler finds slots across N participants,
  ranked by length, proximity to ideal time, and earliest fit
• Share-this-repo deep links — one tap shares a calendar repo's read or
  read-write access
• iCal import + export, CalDAV mirroring (one-way and two-way)
• Material 3 Expressive theme, edge-to-edge, dynamic color
• Android Auto template surface for hands-free schedule glance

— Lifestyle positioning —

strictlykeptboy is built with lifestyle organisation in mind, including support
for D/s dynamics — opt-in, configurable, with a neutral-mode toggle for users
who prefer SFW defaults. The setup wizard lets you pick an alignment (dominant,
submissive, switch, or unaligned-private), praise terms, pronouns, honorific,
and tone register; the app respects those choices throughout. Everything kink-
related lives behind the neutral-mode switch and an age gate. This is a Mature
17+ app.

— Privacy + open source —

We don't have servers, so there's nothing for us to collect. Your data lives in
your git repos. The only network traffic is the pushes and pulls you configure
to your chosen git providers. No analytics, no crash reporting, no ads, no
third-party SDKs. Source code, full dependency license inventory, and release
verification (commit hash + APK SHA-256) are published with every release.
Distributed via Obtainium for automatic updates outside the Play Store.
```

(approx 2150 chars — well under 4000)

## Promotional text (170 chars max)

```
A calendar your AI agent can actually read. Markdown files in git instead of
a black-box database. Push to GitHub, share with partners, schedule together.
```

## Image assets

See `docs/play-store-screenshots.md` for the 8-screenshot spec.

## Translation strategy

English (`en-US`, `en-GB`) ships for v0.1.0. Locale variants follow the
same pattern as the in-app strings: user + Claude per-language session.
