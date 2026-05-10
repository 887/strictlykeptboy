# strictlykeptboy

Git-backed calendar + timeboxing + todolist app for Android. Modern Android stack (Kotlin + Jetpack Compose + Material3 Expressive + Room cache over a Git-of-truth file store). AI-friendly: every event, task, and recurrence rule is a single hand-editable Markdown-with-TOML-frontmatter file in a Git repo, so Claude (or any other agent) can read and write your schedule the same way you can. Multi-repo support — your own repos, shared repos you have read or write access to, common-time finder across repos. Built entirely from the CLI, no Android Studio required.

The name is the brand: a strict, kept schedule, kept boy. Cute bat-in-hoodie mascot with the calendar on the cover. Material3 Expressive throughout. Demo content ships a SFW-but-charged sub-and-Dom paired calendar so you can explore the intended use without a fresh setup.

## Status

🚧 **Planning phase.** See [`docs/plans/main.md`](docs/plans/main.md) for the phased build plan. Decisions doc at [`docs/plans/decisions.md`](docs/plans/decisions.md). Deep-dive specs under [`docs/plans/`](docs/plans/) — data model, sync engine, UI, resolver, templates+demo+wizard, notifications+sharing+import.

## Goals

- **Git-backed** scheduling. Every event, task, recurrence rule is a file. Source of truth lives in Git. Room is a read-through cache, never authoritative.
- **AI-native**. CLAUDE.md / AGENTS.md in every repo. Schema designed so Claude can author events safely without breaking the index.
- **Multi-repo**. Switch between your repos and shared repos. Filterable repo picker with circular icons. Per-repo identity. Author attribution chips on every event.
- **Overlay scheduling**. Run a morning-routine, work, gym, soccer, and master-scheduled calendar simultaneously — toggle each on/off, set active windows, set priorities. The resolver builds the unified day view from whatever's currently active.
- **Timeboxing as first-class**. Calendars come in two kinds: regular (events with location/context) and timebox (focus blocks). Resolver renders both layered.
- **Multi-list todos with overlays**. Same model as calendars — lists have priority, active-windows, and toggles. Shopping/chores/standing tasks share a roof.
- **Common-time finder**. Pick N repos, get free slots ranked.
- **Recurrence**. RFC5545 RRULE inside recurrence files. Exceptions as real files. No materialization until view-time.
- **Conflict-free sync by design**. One file per entity. No index files. Auto background sync + manual sync button. Airplane-mode tolerant.
- **GitHub + Forgejo, SSH + HTTPS.** OAuth device flow for HTTPS; on-device ed25519 generation for SSH.
- **Material3 Expressive**. Left nav rail (like tonearmboy). Top bar with repo switcher + sync button. Tablet + Android Auto support.

## Non-goals (v1)

- CalDAV/CardDAV server-side sync. Scripts in `tools/` for iCal export/import to Thunderbird/Outlook; no real-time CalDAV in v1.
- Web app or desktop app. Android only in v1.
- End-to-end encrypted repo contents (Git repo can be private; that's the encryption story for now).
- Time-zone-aware multi-timezone display (single device timezone in v1; recurrence rules stored in repo timezone).

## Stack (locked)

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3 Expressive
- **Data:** Room (read-cache over the Git-of-truth file store)
- **Git:** JGit + apache-sshd-osgi for SSH transport, OkHttp for HTTPS+OAuth
- **Serialization:** ktoml (frontmatter) + kotlinx.serialization
- **Recurrence:** lib-recur (RFC5545 RRULE)
- **Markdown:** intellij-markdown or commonmark for parsing free-form bodies
- **Background:** Foreground service (dataSync type) + WorkManager
- **Build front-end:** Google's [Android CLI](https://developer.android.com/tools/agents/android-cli)
- **Unit tests:** Robolectric
- **UI tests:** mobile-mcp over ADB
- **App ID:** `com.eight87.strictlykeptboy`
- **minSdk:** 26  /  **targetSdk:** 36

See [`docs/plans/decisions.md`](docs/plans/decisions.md) for every locked decision with rationale.

## Install on Android via Obtainium (after v1 release)

```
obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Fstrictlykeptboy
```

(Not yet released — planning phase.)
