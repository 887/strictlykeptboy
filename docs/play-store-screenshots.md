# strictlykeptboy — Play Store screenshots spec

Phase W.5 deliverable. Locked decisions: K-4 (show both neutral-mode
and kink-mode versions as peer examples — 4 of each), D.59.

## Target directory

Google Play's per-locale screenshot directory convention:

```
app/src/main/play/screenshots/en-US/
app/src/main/play/screenshots/en-GB/
```

Capture path on the AVD:

```bash
adb -s emulator-5554 exec-out screencap -p > app/src/main/play/screenshots/en-US/<n>.png
```

Native AVD resolution is 1080×2400 — already in Google Play's phone
screenshot range (min 320, max 3840 on long edge; 16:9 to 9:16 aspect).
No resize needed for upload.

## Eight-screenshot set

### Neutral-mode (4)

| # | File | Surface | What to capture |
| - | ---- | ------- | --------------- |
| 1 | `01-neutral-schedule-day.png` | Schedule → Day | Today's day timeline with 3–5 events across morning + afternoon, current-time indicator visible. |
| 2 | `02-neutral-schedule-week.png` | Schedule → Week | Full week with overlap bands, lane assignment visible. |
| 3 | `03-neutral-schedule-month.png` | Schedule → Month | Month grid with event chips, mid-week density visible. |
| 4 | `04-neutral-tasks-combined.png` | Tasks → Combined | Combined view, priority-ordered, mix of dated + standing tasks. |

### Kink-mode (4)

| # | File | Surface | What to capture |
| - | ---- | ------- | --------------- |
| 5 | `05-kink-wizard-alignment.png` | Wizard → Alignment | Alignment screen with "Submissive" selected, bat mascot present. |
| 6 | `06-kink-wizard-identity.png` | Wizard → Identity | Identity screen: praise = "good boy", pronouns = "he/him", honorific = "Sir". |
| 7 | `07-kink-now-card.png` | Schedule → Day | Now-card mid-day surfacing a cage-check activity (or other lifestyle-coded atom). Bat sticker placeholder. |
| 8 | `08-kink-share-sheet.png` | Repo → Share | Share-link sheet with multi-partner targets visible. |

## Stickers

The WW (avatar + sticker presence-indicator) sticker pack ships later.
For the v0.1.0 release screenshots, the bat-mascot placeholder
(`R.drawable.about_bat`) is used everywhere a species-specific sticker
would normally render. This is consistent with the K.11 LW-K
deliverable note.

## Capture workflow

1. Start the AVD: `scripts/start-avd.sh --no-mirror`
2. Install: `adb -s emulator-5554 install -r release/latest.apk`
3. For each screenshot:
   - Drive the app to the target surface (mobile-mcp or manual).
   - `adb -s emulator-5554 exec-out screencap -p > app/src/main/play/screenshots/en-US/<n>.png`
4. Copy `en-US/` to `en-GB/` (same artwork for now).

## Re-shooting

When the WW sticker pack lands, re-shoot screenshots 5–8 with the
species-resolved stickers in place of the bat placeholder. Re-shoot
1–4 only if the app chrome changes materially (top-bar / rail /
theming).
