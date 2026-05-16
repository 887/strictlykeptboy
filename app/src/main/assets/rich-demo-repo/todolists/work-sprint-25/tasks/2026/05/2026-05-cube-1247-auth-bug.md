+++
schema_version = 1
id = "0190d047-7fab-7c50-9c1e-4b0000000047"
kind = "task"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "CUBE-1247 — token-refresh race (auth bug)"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000002"
due = 2026-05-22T17:00:00+01:00
tags = ["sprint-25", "in-progress"]
+++
## Status — **in progress**

**Pairing with Priya 2026-05-18 11:00.** Reproduced locally. Logging
interceptor in place. Need to write the fix in `AuthInterceptor.kt`
once we confirm the swallow path.

### Acceptance

- [ ] 401 retry path triggers within 200ms
- [ ] no auth loop on permanent-failure (4xx persistent)
- [ ] unit test + integration test green
- [ ] manual smoke pass on staging
