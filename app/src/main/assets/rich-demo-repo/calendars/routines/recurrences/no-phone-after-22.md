+++
schema_version = 1
id = "0190d4ab-2b7a-7c50-9c1e-900000000001"
kind = "recurrence"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "No phone after 22:00"
group = "evening"
dtstart = 2026-05-15T22:00:00
duration = "PT5M"
tz_id = "Europe/London"
rrule = "FREQ=DAILY"
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000003"
tags = ["routine", "sleep", "passive"]
emoji = "📵"
passive = true
+++
Passive habit — phone goes in the kitchen at 22:00 every night.
Default state: completed-by-schedule. Deviation requires an explicit
file at `routines/deviations/no-phone-after-22/<yyyy-mm-dd>.md` with a
reason and the time the phone went down.

Boy Keeper's standard: a late check-in from BK does NOT count as
deviation (the overlay-event will license it).
