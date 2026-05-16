+++
schema_version = 1
id = "0190d001-7fab-7c50-9c1e-7b0000000001"
kind = "event"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "Pair programming with Priya — CUBE-1247 auth bug"
start = 2026-05-18T11:00:00+01:00
end = 2026-05-18T12:00:00+01:00
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000001"
tags = ["work", "pairing", "bug"]
emoji = "🔧"
+++
Pairing with Priya on `CUBE-1247` — token-refresh race. She thinks it's
the `OkHttp` interceptor swallowing the 401 before our retry hook
fires.

Plan:

1. Reproduce locally (test user `qa-37`).
2. Add a logging interceptor between OkHttp and our retry hook.
3. If we see the 401, fix in `AuthInterceptor.kt`.

```kotlin
// suspicious bit
override fun intercept(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    if (response.code == 401) {
        // … this branch swallows the 401 without re-throw
    }
    return response
}
```

Notes: Priya's senior, kind, patient. Don't over-explain my reasoning,
just describe what I see.
