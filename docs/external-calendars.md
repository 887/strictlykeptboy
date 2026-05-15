# External calendars

strictlykeptboy reads and (optionally) writes events from Android's
own calendar database (`CalendarContract`). Anything that lands in
that database — Google, iCloud, Nextcloud, Fastmail, Microsoft 365,
Exchange, work CalDAV, local-only on-device calendars — shows up
inside skb alongside the events stored in your skb Git repos.

This doc mirrors the content of **Settings → External calendars →
Connect accounts** for users who'd rather read it in the repo.

## Why skb doesn't sync directly

There is no skb-side CalDAV client, no skb-side Exchange ActiveSync
implementation, and no skb-side OAuth dance for Google's private
Calendar API. We deliberately don't ship any of those because:

- Android already has well-maintained, open-source bridges (DAVx⁵,
  the system Exchange account adder) that put events into
  `CalendarContract` for *every* app to read.
- Each protocol's quirks (CalDAV CTag/ETag dance, Exchange's session
  cookies, Google's per-app OAuth client IDs) is a maintenance tax
  skb shouldn't be paying — once events are in `CalendarContract`,
  skb's job is the same regardless of which bridge put them there.
- Storing third-party credentials inside skb would put us on the
  hook for security reviews (Apple-ID-with-app-specific-password,
  Microsoft-conditional-access, Google-OAuth-app-verification) that
  a calendar app one solo developer ships should not be on the hook
  for.

So instead: install the right bridge once, sign in once, and skb
picks the events up.

## DAVx⁵ for CalDAV (Google / iCloud / Nextcloud / Fastmail)

**[DAVx⁵](https://www.davx5.com/)** is a free, open-source (GPLv3)
Android app that adds CalDAV and CardDAV accounts to your phone.
It's the standard answer for any provider that speaks CalDAV — which
is most of them:

- **Google Calendar** — sign in with your Google account (DAVx⁵
  handles the OAuth flow); enable Calendar in DAVx⁵'s per-account
  settings.
- **iCloud** — generate an *app-specific password* at
  <https://appleid.apple.com/account/manage>, then add an account in
  DAVx⁵ with server URL `https://caldav.icloud.com` and your
  app-specific password.
- **Nextcloud** — DAVx⁵ has a one-tap login flow; pick "Nextcloud"
  from the new-account screen.
- **Fastmail** — server URL `https://caldav.fastmail.com`, your
  Fastmail email, and a Fastmail app password.

Install path: **Settings → External calendars → Connect accounts →
Install DAVx⁵**. If you have F-Droid (or Aurora Droid) installed,
the button opens the F-Droid listing; otherwise it opens the Play
Store. The F-Droid build is preferred — it's signed by DAVx⁵'s
developers, doesn't include the Play Store's licensing check, and
updates without needing a Google account.

Full DAVx⁵ docs: <https://manual.davx5.com/>

## Exchange for MS 365

Most Samsung / OEM-vendored Android builds ship the Exchange
account adder out of the box. **Settings → External calendars →
Connect accounts → Add Exchange account** launches the system
"Add account" picker filtered to `com.android.exchange`.

If your phone *doesn't* have a built-in Exchange adder (most
non-Samsung devices), install one of:

- **Microsoft Outlook** (Play Store) — adds an Outlook account
  type that publishes events into `CalendarContract`.
- **Nine Mail** (paid) — the cleanest pure-Exchange-ActiveSync
  option on Android.
- **DAVx⁵ + EWS/ICS** — DAVx⁵ doesn't speak Exchange, but if
  your Exchange server exposes ICS feeds (most do, under
  Outlook's "Publish to internet"), DAVx⁵ can subscribe to those
  as read-only calendars.

## Verifying it worked

1. **Settings → External calendars → "Show system calendars in
   strictlykeptboy"** — flip this on. Grant the Calendar permission
   when Android asks.
2. The **Visible calendars** list below the toggle should now show
   one section per account (Google · com.google · `you@example.com`,
   iCloud · com.apple.icloud · `you@icloud.com`, etc.) with a
   per-calendar switch.
3. If a section is missing: **Connect accounts → Open sync
   settings** and confirm Calendar sync is enabled for that account.
   Pull-to-refresh in DAVx⁵ / Outlook also forces a sync that
   re-publishes the calendar list.
4. Events show up in skb's Schedule view tagged with their source
   (e.g. "From Google Calendar (`you@example.com`)").

## What skb does *not* do

- No bundled CalDAV / CardDAV / Exchange code.
- No auto-installation of bridges — the install buttons are
  outbound `ACTION_VIEW` intents to the F-Droid / Play Store
  listings; you pick when to install.
- No reading of third-party app data outside `CalendarContract` —
  if DAVx⁵ has a calendar but hasn't synced it yet, skb won't see
  it until DAVx⁵ writes it through.

If a provider you care about isn't covered by either bridge above,
the answer is almost certainly to file a feature request against
DAVx⁵, not skb.
