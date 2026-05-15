# Round 2.18 Phase E.9 — default-handler manual test

One-page checklist. Run on a fresh `medium_phone` AVD or a wifi-adb
phone after `scripts/build-release-apk.sh --install`. Confirms the
new manifest filters land skb in the right system slots.

## Setup

1. Fresh install: `adb install -r app-debug.apk`.
2. Launch once so the wizard / age gate completes and at least one
   repo exists.
3. Grant `READ_CALENDAR` + `WRITE_CALENDAR` so External Calendars
   pickups don't disappear from the destination dropdown.

## APP_CALENDAR default-app slot (E.1)

- [ ] Settings → Apps → Default apps → Calendar app: skb appears
      and can be selected.
- [ ] Long-press a date in Gmail snippet ("Tomorrow 3pm") → "View in
      Calendar" → chooser includes skb.

## `.ics` default handler (E.5 + E.9)

- [ ] In Gmail, attach a `.ics` to a draft to yourself, send + open.
- [ ] Tap the `.ics` attachment → chooser includes skb, pick skb,
      tick "Always use this app".
- [ ] After "Always": tapping subsequent `.ics` attachments opens
      skb directly without a chooser.
- [ ] In skb: `IcsImportScreen` opens; event preview shows
      SUMMARY / DTSTART / LOCATION; destination dropdown lists both
      the active skb repo's primary calendar AND the user's external
      calendars (when WRITE_CALENDAR is granted).
- [ ] Save lands the event in the picked destination. For skb
      destinations: `EntityWriter` commits a new `.md` under
      `calendars/<id>/events/<yyyy>/<mm>/`. For external: row appears
      in the system calendar app.

## "Go to date" intent (E.2)

- [ ] `adb shell am start -a android.intent.action.VIEW -d \
        content://com.android.calendar/time/1747353600`
- [ ] skb opens (no chooser if APP_CALENDAR default), Schedule pane
      pins to 2025-05-16. Switches to Day tab.

## Event detail intent (E.3)

- [ ] From the system calendar app, tap an event → "Open with" →
      skb appears (or routes through skb automatically once it's
      the default). For E.3 the toast `Open event <id>` confirms
      the route was classified; deeper cross-row mapping is Phase F.

## Backwards compat (E.10)

- [ ] `adb shell am start -a android.intent.action.VIEW -d \
        strictlykeptboy://event/abc:def com.eight87.strictlykeptboy`
- [ ] Existing Phase MM deep link still resolves (Toast / open path
      unchanged). No new filter collision.

## Recovery

- [ ] Settings → Apps → strictlykeptboy → Open by default → Clear
      defaults resets the chooser state.
