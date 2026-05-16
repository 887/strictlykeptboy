+++
schema_version = 1
id = "0190d04b-7fab-7c50-9c1e-4b000000004b"
kind = "task"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "Staging DB migration — blocked"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000002"
tags = ["sprint-25", "blocked"]
+++
## Status — **blocked**

Waiting on DevOps to spin the new staging Postgres 16 instance. I've
pinged Marco, he'll chase. Once unblocked I'll run the
`init_v2.sql` migration script and verify on the new instance.
