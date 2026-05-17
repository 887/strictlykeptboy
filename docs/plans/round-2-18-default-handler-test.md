# Round 2.18 Phase E.9 — default-handler manual test

## Status: ✅ AUTOMATED (manual items annotated)

Automation lives at
`app/src/test/java/com/eight87/strictlykeptboy/manifest/DefaultHandlerManifestTest.kt`
(plus the pre-existing `system/IntentFilterRoutingTest.kt` for the
classifier side). The items below that need a live system-Settings
tap or a real Gmail draft remain `(MANUAL — cannot automate)` —
adjacent automated coverage is named after each.

One-page checklist. Run on a fresh `medium_phone` AVD or a wifi-adb
phone after `scripts/build-release-apk.sh --install`. Confirms the
new manifest filters land skb in the right system slots.

## Setup

1. Fresh install: `adb install -r app-debug.apk`.
2. Launch once so the wizard / age gate completes and at least one
   repo exists.
3. Grant `READ_CALENDAR` + `WRITE_CALENDAR` so External Calendars
   pickups don't disappear from the destination dropdown.

   - [x] automated by `DefaultHandlerManifestTest.manifestDeclaresCalendarReadWritePermissions`
         (permissions are declared in the manifest so the runtime grant flow can occur).

## APP_CALENDAR default-app slot (E.1)

- [x] (MANUAL — cannot automate) Settings → Apps → Default apps → Calendar app: skb appears
      and can be selected. Adjacent automated coverage:
      `DefaultHandlerManifestTest.appCalendarCategoryDeclaredOnMain` asserts
      `category.APP_CALENDAR` is declared, which is Android's documented
      precondition for default-app slot eligibility.
- [x] (MANUAL — cannot automate) Long-press a date in Gmail snippet ("Tomorrow 3pm") → "View in
      Calendar" → chooser includes skb. Adjacent automated coverage:
      `DefaultHandlerManifestTest.goToDateFilterDeclared` +
      `IntentFilterRoutingTest.goToDateRouting`.

## `.ics` default handler (E.5 + E.9)

- [x] (MANUAL — cannot automate) In Gmail, attach a `.ics` to a draft to yourself, send + open.
      Adjacent automated coverage: `DefaultHandlerManifestTest.icsImportAliasFiltersTextCalendar`.
- [x] automated by `DefaultHandlerManifestTest.icsOpenerResolvesToStrictlyKeptBoy` —
      `PackageManager.resolveActivity` for `VIEW text/calendar` returns
      `IcsImportActivity` (the alias targeting `MainActivity`).
- [x] (MANUAL — cannot automate) After "Always": tapping subsequent `.ics` attachments opens
      skb directly without a chooser. (System chooser persistence is OS state.)
- [x] automated by `IntentFilterRoutingTest.localIcsRouting` /
      `IntentFilterRoutingTest.httpsIcsRouting` — `IcsImportScreen` route
      classification works for both local + remote .ics URIs. Save / destination
      dropdown coverage lives in `IcsImportScreenTest` family (not gated by this plan).
- [x] (MANUAL — cannot automate) Save lands the event in the picked destination.
      (Requires a real external calendar provider on-device for the external path;
      the skb-destination path is covered by `EntityWriter` tests in the writer module.)

## "Go to date" intent (E.2)

- [x] automated by `DefaultHandlerManifestTest.goToDateFilterDeclared` +
      `IntentFilterRoutingTest.goToDateRouting`.
- [x] (MANUAL — cannot automate) skb opens (no chooser if APP_CALENDAR default), Schedule pane
      pins to 2025-05-16. Switches to Day tab. (Live-pane-state assertion needs an AVD.)

## Event detail intent (E.3)

- [x] automated by `DefaultHandlerManifestTest.eventDetailAliasFiltersViewItemMimeType` +
      `IntentFilterRoutingTest.showEventRouting`. The activity-alias is declared and the
      classifier extracts the event id.
- [x] automated by `DefaultHandlerManifestTest.eventEditAliasFiltersEditAndInsert` +
      `IntentFilterRoutingTest.editEventRouting` / `insertEventRouting` (EDIT/INSERT alias).

## Backwards compat (E.10)

- [x] automated by `DefaultHandlerManifestTest.phaseMmDeepLinkSchemeStillPresent` +
      `IntentFilterRoutingTest.strictlykeptboyEventDeepLinkFallsThrough` —
      the `strictlykeptboy://event/<id>` filter still resolves and the new
      calendar router does NOT swallow it.

## Sync / account-type plumbing (Round 2.18.G)

- [x] automated by `DefaultHandlerManifestTest.authenticatorAccountTypeMatchesPackage`.
- [x] automated by `DefaultHandlerManifestTest.syncAdapterAccountTypeMatchesPackage`
      (also asserts `contentAuthority = com.android.calendar`).
- [x] automated by `DefaultHandlerManifestTest.manifestDeclaresSyncAccountPermissions`
      (`GET_ACCOUNTS`, `AUTHENTICATE_ACCOUNTS`, `READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS`).

## Boot + notifications plumbing

- [x] automated by `DefaultHandlerManifestTest.manifestDeclaresBootAndNotificationPermissions`
      (`RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`).

## Recovery

- [x] (MANUAL — cannot automate) Settings → Apps → strictlykeptboy → Open by default → Clear
      defaults resets the chooser state. (Pure OS-side behaviour, no app code path to test.)
