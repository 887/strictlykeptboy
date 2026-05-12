# strictlykeptboy — privacy policy

Last updated: 2026-05-13.
App version: 0.1.0.
Package: `com.eight87.strictlykeptboy`.
Source: https://github.com/887/strictlykeptboy

## Summary

We don't have servers, so there's nothing for us to collect. Your
calendar, todolist, and identity data live in **git repositories you
control**. The app only talks to the network when you explicitly
configure a remote (GitHub, Forgejo, Codeberg, a self-hosted Gitea,
etc.) and tell the app to sync.

## What stays on your device

- The on-disk repository contents (events, tasks, recurrences, exceptions,
  deviations, journal entries, `identity.toml`, `mode.toml`, `AGENTS.md` /
  `CLAUDE.md`) under the app's private storage and any locations you point
  the app at.
- `EncryptedSharedPreferences` (Android Keystore-backed) holding:
  - OAuth tokens (per `(repoId, remoteName)`)
  - SSH private keys (per `(repoId, remoteName)`)
  - Personal Access Tokens, if you choose the PAT auth path
  - App preferences (theme, density, neutral-mode toggle, age-confirmed flag,
    notification channel + per-calendar settings, last-used view modes)
  - The Room cache database (rebuildable from disk; nothing not derivable
    from your repo files is stored there)

These never leave your device unless you push the underlying git repo
to a remote you configured.

## What goes over the network

Exactly and only what you ask the app to do:

- **Git over HTTPS** — pushes, pulls, fetches against the remote(s) you
  configure. Default transport is OkHttp; OAuth-bound for GitHub and
  Forgejo, basic-auth for PAT users.
- **Git over SSH** — pulls and pushes via apache-sshd; the app generates
  the keypair for you, you paste the public key into the provider.
- **OAuth Device Flow** — only when you explicitly authenticate a new
  remote. The flow targets the provider you chose (GitHub, Forgejo).
- **CalDAV** (Phase Y, opt-in) — only against endpoints you configure.

No background telemetry. No analytics. No crash reporting (a future
opt-in crash reporter would be opt-in, off by default, and disclosed
here before it ships). No ads. No third-party SDKs beyond the open-
source libraries inventoried in the in-app **Licenses** screen.

## Permissions explained

| Permission | Why |
| --- | --- |
| `INTERNET` | Git push/pull against the remotes you configure. |
| `ACCESS_NETWORK_STATE` | Pause sync when offline; resume on reconnect. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Long-running sync without being killed mid-pull. |
| `POST_NOTIFICATIONS` | Event reminders, sync status, foreground-service notification. |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Fire event reminders at the right second; no exact-alarm = late notifications. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm scheduled reminders after a reboot. |

## Children

This app is rated **Mature 17+** and gated by an in-app age confirmation
on first launch (per K-6). It is not intended for, and not directed at,
children under 17.

## Provider-specific privacy

When you sync to a hosted git provider, that provider's privacy policy
applies to whatever you push:

- GitHub: https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement
- Forgejo / Codeberg: https://codeberg.org/Codeberg/org/src/branch/main/PrivacyPolicy.md
- Gitea (self-hosted): governed by the operator of your instance
- A CalDAV endpoint you mirror (Phase Y): governed by that provider

The app passes no extra metadata to providers beyond what git and the
chosen auth method require.

## Your data, your control

- Stop syncing any repo: delete or pause the remote in
  Settings → Repos → `<repo>` → Remotes.
- Delete all local data: uninstall the app, or use Settings → Repos →
  `<repo>` → Remove for a single repo. Both clear the on-disk repo and
  the cached prefs.
- Inspect everything: every file is plain Markdown + TOML; open the
  repo on your desktop with any editor.

## Contact

Issues and questions: https://github.com/887/strictlykeptboy/issues

## Changes to this policy

Material changes ship in a release alongside an in-app notification
prompt. The current policy is whatever is checked into the release tag
for the version installed on your device.
