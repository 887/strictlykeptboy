+++
schema_version = 1
id = "0190d04c-7fab-7c50-9c1e-4b000000004c"
kind = "task"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "Write unit tests for resolver overlay"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000002"
due = 2026-05-29T17:00:00+01:00
tags = ["sprint-25", "todo"]
+++
## Status — **todo**

Mock the overlay-resolver inputs and assert the supersedence
behaviour. Specifically:

- vacation pauses work, commute, gym, dom-overlay, social
- kinky-rituals + cat-care + holidays are nonSuperseable — pass through
- force-show overrides resurface specific instances
