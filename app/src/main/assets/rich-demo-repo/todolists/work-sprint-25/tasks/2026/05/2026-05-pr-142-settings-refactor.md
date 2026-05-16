+++
schema_version = 1
id = "0190d049-7fab-7c50-9c1e-4b0000000049"
kind = "task"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "PR #142 — settings refactor"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000002"
due = 2026-05-19T17:00:00+01:00
tags = ["sprint-25", "needs-review"]
+++
## Status — **needs review**

Opened 2026-05-14. Splits the monster `SettingsViewModel` into three
narrow ones. Anna assigned Sam as reviewer. Sam usually takes ~48h.

### Notes for reviewer
- behaviour preserved (Robolectric test suite green)
- naming: `AppearanceSettingsVM`, `AccountSettingsVM`,
  `NotificationSettingsVM`
- the three VMs share a single `SettingsStore` interface
