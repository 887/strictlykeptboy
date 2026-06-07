# Persona — Tobias Reichenbach, the privacy engineer

> One realistic user persona for strictlykeptboy. Voice-led so QA can
> hear him grumble. Everything below maps to features that actually
> exist per `docs/plans/` — no invented capabilities.

## Snapshot

- **Name:** Tobias Reichenbach (invented, neutral German name — not a
  real public figure)
- **Age:** 45
- **Location:** Leipzig, Germany
- **Job:** Senior backend / platform engineer at a mid-size German
  logistics-software shop. Writes Go and Rust by day, runs a homelab
  by night.
- **Languages:** German native; reads English fluently (all his docs,
  RFCs, and most software are English anyway).

## Devices

- **Phone:** Pixel 8, GrapheneOS, system locale set to **Deutsch
  (de-DE)**. No Google Play Services on the personal profile. Installs
  APKs via **Obtainium** straight from GitHub Releases.
- **Homelab:** an Intel NUC + a small Hetzner box running a
  **self-hosted Forgejo** instance behind his own domain, reachable
  over SSH on port 22. He terminates TLS himself; he holds every key.
- **Laptop:** Arch Linux. `git`, `ssh`, `gpg`, the works.

## Tech comfort

Maxed out. He knows what an ed25519 key is, knows the difference
between the OpenSSH private-key format and PKCS#8, and will read the
host fingerprint before he trusts it. He does **not** need hand-holding
— but he is exactly the user who will notice if the app does something
sloppy with his secret material, and he will uninstall on the spot if
it does.

## The hard privacy stance (in his voice)

> "I don't put my life on someone else's computer. Google Calendar is
> a behavioural-profiling product with a calendar bolted on. Outlook is
> the same thing in a corporate trench coat. Every 'free' calendar is
> selling when I sleep, who I meet, and how often. No thank you.
>
> My calendar is **files**. Markdown with TOML frontmatter, in a Git
> repo, on a server *I* run. If the app vanishes tomorrow, I still have
> my schedule in plain text. If the company behind it gets bought, my
> data doesn't go with it. That's the only deal I'll take."

Non-negotiables:

- **No cloud calendar. No telemetry. No account on anyone's server but
  his own.** strictlykeptboy has no app-level account — a repo *is* the
  identity — which is exactly why he tolerates it.
- **Key material never leaves the device.** The on-device ed25519
  generator (`SE-C`, BouncyCastle, stored in
  `EncryptedSharedPreferences` behind an Android-Keystore master key)
  is the feature that sold him. The *public* key he copy-pastes into
  Forgejo; the *private* key never moves.
- **He verifies host keys.** TOFU-with-pin is fine by him — he wants to
  see the fingerprint on first connect and be screamed at if it ever
  changes.
- **Files-of-truth, not a sync black box.** Room is a rebuildable cache;
  Git is authoritative. He can `git clone` the same repo on his laptop
  and read it with `cat`. That's the whole point.

## Why strictlykeptboy specifically

He tried DAVx⁵ + a Nextcloud CalDAV calendar and it was *fine*, but it
was still a database he couldn't `grep`, and recurrence exceptions were
opaque. strictlykeptboy gives him:

- A repo he already understands (it's just Git).
- **On-device SSH keygen** pointed at **his own Forgejo** — no third
  party in the auth path at all.
- **Recurrence as RFC5545 RRULE in a file**, with **cancellations and
  overrides as real files** (`exceptions/<rule-id>/<date>.md`) he can
  read and diff.
- **Multi-repo** so his private life and his chess club live in
  separate repos with separate access control.
- **Strict timeboxing** as a first-class calendar kind — he runs his
  deep-work blocks like a build pipeline.
- **German UI** where it's been translated, clean English fallback
  where it hasn't. He prefers that to a half-machine-translated mess.

## His repos

1. **`reichenbach/leben`** ("life") — his personal repo on his Forgejo.
   Private. SSH remote. Calendars: a **work** regular-calendar, a
   **focus** timebox-calendar, a **chores** todolist. He's the only
   author.

2. **`schachverein/termine`** ("chess club / Schachverein schedule") —
   a **shared** repo on the same Forgejo. Three committee members have
   **write** access; ~20 club members have **read**. This is where club
   nights, tournaments, and the Vereinsabend recurrence live. He sees
   **author-attribution chips** from the other committee members on
   events they added.

   > Schema note: the *concept* is a chess club / Schachverein, but
   > nothing German leaks into identifiers. The repo's calendars use
   > English ids; the German is only in display names, event titles,
   > and the de-DE UI strings.

## Identity & mode choices

- **Per-repo identity** (`identity.toml`). In `leben` his display name
  is "Tobias" with a plain avatar; in `termine` his display name is
  "Tobias R. (Vorstand)" so club members can tell committee posts
  apart in the author chips.
- **Mode:** `free` everywhere. He is emphatically **not** here for the
  kink-positive / strictly-kept persona surfaces — he toggles those off
  and uses the neutral calendar/timebox/todo core. (Good QA signal: the
  app must be completely usable and not awkward for a free-mode,
  no-identity-flair user.)
- **Author attribution:** on. He wants to know who moved the
  Vereinsabend.

## German-locale preference

System locale is **de-DE**. He expects:

- App chrome in German where `values-de/` overrides exist.
- Clean fallback to canonical English for un-translated keys (he
  explicitly prefers this to a fully-but-badly-translated app — see
  `docs/plans/translations.md`, the "partial-override is the design"
  section).
- **No German in schema identifiers** — and he'd actually be annoyed if
  the app wrote `Mannschaftsaufstellung` as a TOML key. Event *titles*
  in German: fine. TOML *keys* in German: that's a bug to him.

## Frustrations he's bringing with him

- Google/Outlook recurrence exceptions are a black box — you can't see
  *why* a date is missing.
- Cloud calendars phone home; he can't audit what leaves.
- CalDAV sync conflicts produce mystery duplicate events.
- Most "self-hosted-friendly" apps still assume a vendor account.

## What success looks like (his words)

> "I open the app on my phone, it's in German, I generate a key right
> there, paste the public half into my Forgejo, and my `leben` repo
> syncs over SSH without a single byte touching anyone else's server.
> My chess club has its own repo where I can see who added what. My
> focus blocks sit on top of work and club like layers I can toggle.
> And when I take a tournament weekend, one vacation overlay quietly
> supersedes my weekday routine without me deleting anything. Then I
> close the app and forget about it — because it just kept my schedule,
> like it said on the tin."
