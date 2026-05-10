# strictlykeptboy — sync engine deep-dive

## Status: 🚧 IN-PLANNING

This document is the exhaustive specification for the Git sync engine of
`strictlykeptboy`. It owns:

- JGit 6.x integration on Android (vendor, init, `Repository`/`Git`
  abstractions, working-tree quirks).
- The `GitRepo` abstraction that wraps JGit for the rest of the app.
- Transport: SSH via `apache-sshd-osgi` + `SshdSessionFactory`; HTTPS via
  OkHttp + `UsernamePasswordCredentialsProvider`.
- Auth: ed25519 keygen + storage; GitHub OAuth Device Flow; Forgejo
  OAuth2 Device Flow; manual PAT entry.
- The `SyncService` foreground service + WorkManager retry layer.
- Per-repo sync orchestration, scheduling, jitter, triggers.
- Conflict detection + 3-way diff data side (UI is in `ui-spec.md`).
- Airplane-mode/offline handling, multi-repo coordination, retry/backoff,
  error taxonomy.

Cross-references back to `main.md`:

| `main.md` phase | This doc's phases |
|---|---|
| Phase B (B.1–B.9, Git layer foundations) | SE-A, SE-B, SE-C, SE-D, SE-E, SE-F |
| Phase J (J.1–J.8, sync orchestration) | SE-G, SE-H, SE-I, SE-J, SE-K, SE-L |

All architectural choices below are derived from `decisions.md` D.7, D.8,
D.9, D.22. Where this doc resolves an unforeseen tradeoff inline, the
resolution is marked **Decision:** with rationale.

---

## Phase SE-A — JGit on Android: vendor + initialization

Corresponds to `main.md` **B.1**.

### What works, what doesn't

JGit 6.x is pure Java + a small NIO surface. On Android API 26+ (our
`minSdk`) we get:

- `java.nio.file.Path` and `Files` — fully available since API 26
  (`Build.VERSION_CODES.O`). Required for JGit 6's working-tree IO.
- `java.security.MessageDigest`, `java.util.zip` — fine.
- `sun.misc.Unsafe` — JGit avoids it; not needed.
- `java.lang.management` — JGit references it for diagnostics; calls are
  guarded. We will NOT pull in any JGit `pgm` (CLI tools) artifact since
  it drags in `org.eclipse.jgit.pgm` which assumes a real JVM
  `java.lang.management` surface.

What historically broke and how we handle it:

- `org.eclipse.jgit.util.FS_POSIX` calls `Files.setPosixFilePermissions`
  which throws `UnsupportedOperationException` on some Android FS
  variants. JGit catches and degrades to a no-op when the FS reports no
  POSIX-perm support — Android's `EmulatedFileSystem` reports correctly.
  We test on API 26 + 36 in instrumented tests.
- `java.util.ServiceLoader` discovery of providers — JGit relies on this
  for `RefStorage`, hooks, etc. Android handles `ServiceLoader` since
  forever; the only gotcha is `META-INF/services` files must survive
  R8/ProGuard. We add explicit keep rules in `proguard-rules.pro`
  (Phase W.6).
- `java.io.tmpdir` — JGit writes pack temp files. On Android we set
  `System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)`
  during `App.onCreate` *before* any JGit class is loaded (else JGit
  caches the wrong tmpdir).

### Vendor instructions

`libs.versions.toml` entries (Phase A.3):

```toml
[versions]
jgit = "6.10.0.202406032230-r"          # latest 6.x at planning time
sshd = "2.13.2"                          # apache-sshd-osgi pinned
bouncycastle = "1.78.1"                  # BC + bcpkix for ed25519
okhttp = "4.12.0"
security-crypto = "1.1.0-alpha06"        # EncryptedSharedPreferences
work = "2.10.0"

[libraries]
jgit-core = { module = "org.eclipse.jgit:org.eclipse.jgit", version.ref = "jgit" }
jgit-ssh-apache = { module = "org.eclipse.jgit:org.eclipse.jgit.ssh.apache", version.ref = "jgit" }
jgit-ssh-apache-agent = { module = "org.eclipse.jgit:org.eclipse.jgit.ssh.apache.agent", version.ref = "jgit" }  # excluded later
sshd-osgi = { module = "org.apache.sshd:apache-sshd", version.ref = "sshd" }
sshd-common = { module = "org.apache.sshd:sshd-common", version.ref = "sshd" }
sshd-core = { module = "org.apache.sshd:sshd-core", version.ref = "sshd" }
bouncycastle-prov = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncycastle" }
bouncycastle-pkix = { module = "org.bouncycastle:bcpkix-jdk18on", version.ref = "bouncycastle" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
```

Gradle exclusions:

- Exclude `jgit-ssh-apache-agent` transitive — we don't use a key agent
  on Android (no ssh-agent socket).
- Exclude `org.slf4j:slf4j-api` from JGit; replace with `slf4j-android`
  bridge so JGit logs land in `logcat` and our in-app debug log.

Licensee allowlist additions: `EDL-1.0` (JGit's primary license), `EPL-2.0`
(JGit secondary), `BSD-3-Clause` (apache-sshd has BSD-licensed bits).

### Phase tasks

- [ ] **SE-A.1** Add JGit, sshd, BouncyCastle, OkHttp, security-crypto,
  WorkManager versions to `libs.versions.toml`
- [ ] **SE-A.2** Add the modules listed above to `app/build.gradle.kts`
  with the agent exclusion
- [ ] **SE-A.3** Add Licensee allowlist entries for `EDL-1.0`, `EPL-2.0`
- [ ] **SE-A.4** R8 keep rules: `-keep class org.eclipse.jgit.** { *; }`
  (narrow later in V.5), keep `META-INF/services/**`
- [ ] **SE-A.5** `App.onCreate`: set `java.io.tmpdir` to `cacheDir`,
  install BouncyCastle provider (`Security.insertProviderAt(BouncyCastleProvider(), 1)`),
  install `slf4j-android` bridge
- [ ] **SE-A.6** Smoke test: open an in-memory `Repository` via
  `InMemoryRepository.Builder().build()`, write a single blob, read it
  back. Asserts the JGit core paths load on API 26 and API 36.
- [ ] **SE-A.7** Document the `java.io.tmpdir` ordering hazard in code
  comments next to the `App.onCreate` call

---

## Phase SE-B — `GitRepo` abstraction

Corresponds to `main.md` **B.2**.

### Surface

```kotlin
class GitRepo internal constructor(
    val rootDir: File,
    val remoteUrl: String,
    private val credentialBinding: CredentialBinding,
    private val authorIdentity: AuthorIdentity,
) {
    suspend fun status(): GitStatus
    suspend fun fetch(): FetchResult
    suspend fun pullRebase(): PullResult                  // fetch + rebase onto upstream
    suspend fun push(): PushResult
    suspend fun commitAll(message: String): CommitResult  // stage every change in working tree, commit
    suspend fun commitPaths(paths: List<String>, message: String): CommitResult
    suspend fun diffSinceLastIndexed(lastIndexedHead: ObjectId?): Set<ChangedPath>
    suspend fun log(maxCount: Int = 100): List<LogEntry>
    suspend fun headSha(): ObjectId
    suspend fun close()

    companion object {
        suspend fun open(rootDir: File, remoteUrl: String, ...): GitRepo
        suspend fun init(rootDir: File, remoteUrl: String, ...): GitRepo
        suspend fun clone(rootDir: File, remoteUrl: String, credentialBinding: CredentialBinding, ...): GitRepo
    }
}

sealed interface PullResult {
    object UpToDate : PullResult
    data class FastForwarded(val fromSha: ObjectId, val toSha: ObjectId, val changedPaths: Set<ChangedPath>) : PullResult
    data class Rebased(val fromSha: ObjectId, val toSha: ObjectId, val changedPaths: Set<ChangedPath>) : PullResult
    data class Conflicted(val conflictedPaths: List<String>, val rebaseHandle: RebaseHandle) : PullResult
    data class Failed(val error: SyncError) : PullResult
}

sealed interface PushResult {
    object Success : PushResult
    object NothingToPush : PushResult
    data class Rejected(val reason: PushRejection) : PushResult
    data class Failed(val error: SyncError) : PushResult
}

enum class PushRejection { NonFastForward, NoPermission, BranchProtected, Unknown }
```

### Threading

- Every public function is `suspend` and switches to `Dispatchers.IO`
  internally via `withContext(Dispatchers.IO)`. Callers never block the
  main thread.
- Concurrent calls to the same `GitRepo` instance are serialized via a
  per-repo `Mutex` held inside the class. JGit's `Repository` is
  thread-safe for reads but writes (commit, rebase, checkout) MUST be
  serialized. Our mutex covers all writes; reads (`log`, `status`,
  `headSha`) acquire a `readLock` (we use a single `Mutex` for v1 —
  performance is fine, file-count per repo is small).

### Implementation notes

- `Git` and `Repository` instances are held for the lifetime of the
  `GitRepo` (avoid the per-call `Repository.Builder` overhead).
- `close()` must be called when the repo is removed from `RepoStore`.
  We hold a `WeakReference` cache of opened repos in `GitRepoRegistry`
  so listing 5 repos doesn't open 5 times unnecessarily.
- All file IO through `java.nio.file.Files` (not `java.io.File`) for
  symlink correctness — the `CLAUDE.md → AGENTS.md` symlink in produced
  repos must round-trip.

### Phase tasks

- [ ] **SE-B.1** Define `GitRepo`, `GitStatus`, `PullResult`, `PushResult`,
  `CommitResult`, `ChangedPath`, `SyncError` in `:app:git` module
- [ ] **SE-B.2** Implement `open` / `init` / `clone` constructors
  wrapping `Git.open` / `Git.init` / `Git.cloneRepository`
- [ ] **SE-B.3** Implement `status` via `git.status().call()`, mapping
  `Status` to typed `GitStatus` (untracked, modified, missing, conflicted)
- [ ] **SE-B.4** Implement `fetch` via `git.fetch().setRemote("origin")
  .setTransportConfigCallback(transportFor(credentialBinding))`
- [ ] **SE-B.5** Implement `pullRebase`: call `fetch`, then
  `git.rebase().setUpstream("origin/main").call()`. Map `RebaseResult` →
  `PullResult`. On `STOPPED`, package the rebase handle so the conflict
  UI can drive `continue`/`abort`.
- [ ] **SE-B.6** Implement `push` via `git.push().setRemote("origin")
  .call()`, parsing `PushResult.getRemoteUpdates()` for rejection codes
- [ ] **SE-B.7** Implement `commitAll` / `commitPaths`: `git.add()` then
  `git.commit().setAuthor(authorIdentity.name, authorIdentity.email)
  .setCommitter(...).setMessage(...).call()`
- [ ] **SE-B.8** Implement `diffSinceLastIndexed` via JGit's `DiffFormatter`
  comparing `lastIndexedHead` ↔ current HEAD using `TreeWalk` (faster
  than shelling out; pure JGit)
- [ ] **SE-B.9** Implement `log` via `git.log().setMaxCount(n).call()`
- [ ] **SE-B.10** Per-repo `Mutex`; `GitRepoRegistry` keyed by repo URL
- [ ] **SE-B.11** Robolectric tests for each entry point against a temp
  `git init --bare` filesystem (next phase covers test infra)

---

## Phase SE-C — Auth: SSH transport

Corresponds to `main.md` **B.4**.

### ed25519 keygen

We use BouncyCastle (`bcpkix-jdk18on`) for ed25519 keypair generation
because Android API 26 ships an ed25519 KeyPairGenerator only at API 33+.
BC is available everywhere we run.

```kotlin
fun generateEd25519Keypair(): Ed25519Keypair {
    val gen = KeyPairGenerator.getInstance("Ed25519", "BC")
    val kp = gen.generateKeyPair()
    val sshPub = OpenSshPublicKeyUtil.encodePublicKey(kp.public)  // BC helper
    val openSshPriv = OpenSshPrivateKeyUtil.encodePrivateKey(kp.private)  // BC OpenSSH-format
    return Ed25519Keypair(
        publicOpenSsh = "ssh-ed25519 ${Base64.encodeToString(sshPub, NO_WRAP)} strictlykeptboy-<repo-id>",
        privateOpenSsh = String(openSshPriv, UTF_8),  // PEM-armored OpenSSH private key
    )
}
```

The OpenSSH private key format (not PKCS#8) is what
`SshdSessionFactory` reads out of the box — match that.

### Storage

`EncryptedSharedPreferences` with a `MasterKey` from the Android
Keystore (AES256 GCM, hardware-backed when available, software fallback
otherwise). One file: `secrets_v1.xml`. Keys:

- `ssh.priv.<repo-id>` — armored OpenSSH private key
- `ssh.pub.<repo-id>` — `ssh-ed25519 <base64> <comment>` line
- `oauth.token.<repo-id>` — bearer token
- `oauth.refresh.<repo-id>` — refresh token if provider issues one
- `oauth.expiry.<repo-id>` — epoch millis
- `pat.token.<repo-id>` — manual PAT
- `https.username.<repo-id>` — username for HTTPS basic (typically
  `git`, `oauth2`, or PAT username)

`<repo-id>` is `sha256(remoteUrl).take(12).hex` — stable, opaque,
collision-resistant for our scale.

The `MasterKey` itself is keyed by `"strictlykeptboy_master_v1"` in
Keystore. On factory reset / app uninstall the keystore + prefs are wiped
together — secrets do not survive reinstall (acceptable; user
re-authenticates).

### Wiring SSH transport into JGit

```kotlin
class StrictlyKeptBoySshSessionFactory(
    private val keypairProvider: (RepoId) -> Ed25519Keypair,
    private val knownHostsFile: File,
) : SshdSessionFactory(
    /*proxyDb*/ null,
    /*hostKeyVerifierFactory*/ TofuHostKeyVerifierFactory(knownHostsFile),
) {
    override fun getDefaultIdentities(sshDir: File): List<Path> = emptyList()
    override fun getDefaultKeys(sshDir: File): KeyIdentityProvider {
        // Provide repo-specific identity at session-build time; resolve from
        // a thread-local that the GitRepo set before invoking transport.
        return KeyIdentityProvider { _ -> resolveCurrentRepoKeys() }
    }
}
```

We override `getDefaultKeys` because JGit's stock factory walks
`~/.ssh/` which doesn't exist on Android. We inject the per-repo key by
having `GitRepo` set a `ThreadLocal<RepoId>` before each transport call;
the factory reads it.

### Known-hosts: TOFU + pin

**Decision: Trust-On-First-Use, with per-repo pin.** The first time a
repo connects, we capture the host key and pin it in `known_hosts.json`
(JSON, our own format — JGit's `known_hosts` parser is brittle on
Android paths). Subsequent connections require an exact match. If the
host key changes, we throw `HostKeyChangedException` and surface a
red-banner error: "the SSH host key for github.com changed — possible
MITM, contact provider before continuing." User can manually re-pin from
Settings → Repos → \<repo\> → SSH → Reset host pin.

For first-party hosts (`github.com`, `gitlab.com`, `codeberg.org`,
`gitea.com`) we ship a baked-in known-hosts seed of their published
keys, so TOFU is bypassed for these — first connection still verifies
against the seed. The seed lives in `assets/known_hosts_seed.txt`.

### Public-key export UX

After keygen the public key (`ssh-ed25519 AAAA... comment`) is shown to
the user with three actions:

1. **Copy** → clipboard (`ClipboardManager.setPrimaryClip`).
2. **Share** → `ACTION_SEND` text intent.
3. **Upload to provider** → only if OAuth token with `admin:public_key`
   (GitHub) or `write:user` (Forgejo) is present. POSTs to the provider
   API. Surfaces success/failure inline.

**Decision: no QR code in v1.** The user's import path is
"copy-paste into the provider's web UI on a desktop", which works fine
via a clipboard sync (KDE Connect, etc.) or by emailing themselves. QR
adds a code dependency (zxing) for marginal value.

### Phase tasks

- [ ] **SE-C.1** `Ed25519Keypair` data class + BC-based generator
- [ ] **SE-C.2** `SecretsStore` wrapper around `EncryptedSharedPreferences`
  with the key naming above
- [ ] **SE-C.3** `StrictlyKeptBoySshSessionFactory` with per-repo
  identity injection
- [ ] **SE-C.4** `TofuHostKeyVerifierFactory` + JSON known-hosts file
- [ ] **SE-C.5** Bake `assets/known_hosts_seed.txt` with github.com,
  gitlab.com, codeberg.org, gitea.com keys; load on first run into
  `known_hosts.json`
- [ ] **SE-C.6** `transportConfigCallbackForSsh(repoId)` returning a
  callback that sets the thread-local RepoId and installs the session
  factory
- [ ] **SE-C.7** Public-key export UI hooks (copy/share/upload-to-provider).
  Provider upload bodies:
  - GitHub: `POST https://api.github.com/user/keys` with `{"title":"strictlykeptboy <repo-name>","key":"<sshPub>"}`
  - Forgejo: `POST <base>/api/v1/user/keys` with same body
- [ ] **SE-C.8** Robolectric test: round-trip keygen → store → retrieve
  → verify public-key string matches `ssh-ed25519 ...` regex
- [ ] **SE-C.9** Instrumented test (later): clone a real public test repo
  over SSH using a generated key uploaded via OAuth-bearing fixture

---

## Phase SE-D — Auth: HTTPS transport

Corresponds to `main.md` **B.5–B.7**.

### Wiring

JGit ships `HttpConnection` SPI. We provide an OkHttp-backed
`HttpConnectionFactory`:

```kotlin
class OkHttpConnectionFactory(private val client: OkHttpClient) : HttpConnectionFactory {
    override fun create(url: URL): HttpConnection = OkHttpConnection(client, url)
    override fun create(url: URL, proxy: Proxy?): HttpConnection = OkHttpConnection(client, url, proxy)
}
```

Installed once at app start:
`HttpTransport.setConnectionFactory(OkHttpConnectionFactory(appOkHttpClient))`.

OkHttp gives us:

- TLS 1.3 on API 26+ via Conscrypt (we add `conscrypt-android` if we want
  modern cipher suites on older devices). **Decision: skip Conscrypt for v1**
  — minSdk 26 has TLS 1.2 by default and modern OkHttp negotiates 1.3 on
  newer Android. We revisit if we see handshake failures in the field.
- Connection pooling + HTTP/2.
- A common timeout/retry surface across our OAuth client and Git client.

### Credentials

`UsernamePasswordCredentialsProvider(username, token)`:

| Provider | username | password |
|---|---|---|
| GitHub OAuth (Device Flow) | `oauth2` | access token |
| GitHub PAT (manual) | PAT username (often `x-access-token` or the PAT value with empty username) | PAT |
| Forgejo OAuth | `oauth2` | access token |
| Forgejo PAT (manual) | the username | PAT |

For GitHub the canonical pattern is `("oauth2", "<token>")` — works for
both PATs and OAuth tokens.

### Phase tasks

- [ ] **SE-D.1** `OkHttpConnection` and `OkHttpConnectionFactory`
  implementing JGit's `HttpConnection` SPI (read body, write body, set
  headers, follow redirects). Reference impl exists in JGit ecosystem;
  we adapt for our OkHttp version.
- [ ] **SE-D.2** Install factory in `App.onCreate`
- [ ] **SE-D.3** `transportConfigCallbackForHttps(repoId)` returning
  `setCredentialsProvider(UsernamePasswordCredentialsProvider(...))`
  populated from `SecretsStore`
- [ ] **SE-D.4** Token-refresh-on-401: wrap the credentials provider so
  a 401 triggers an OAuth refresh attempt (Phase SE-E.5), retries once
- [ ] **SE-D.5** Robolectric test: clone an HTTPS URL against a local
  `git http-backend` fixture (or a `JettyDaemon` running JGit's
  `org.eclipse.jgit.http.server`); validates the OkHttp factory works

---

## Phase SE-E — OAuth: GitHub + Forgejo Device Flow

Corresponds to `main.md` **B.5**, **B.6**.

### GitHub OAuth Device Flow

Endpoints:

- `POST https://github.com/login/device/code` — start, returns
  `device_code`, `user_code`, `verification_uri`, `expires_in`,
  `interval`.
- `POST https://github.com/login/oauth/access_token` — poll, with
  `grant_type=urn:ietf:params:oauth:grant-type:device_code`.

Scopes we request: `repo admin:public_key`. The `repo` scope is needed
to read+write private repos; `admin:public_key` lets us auto-upload the
SSH public key. We do NOT request `workflow`, `delete_repo`, or `gist`.

**Client ID strategy:** **Decision: ship one public client ID per build
flavor (debug, release).** Release client ID is registered in the
strictlykeptboy GitHub app (created once by the user). Device Flow does
not require a client secret. The client ID is in `BuildConfig` so it can
be overridden for forks. Debug builds use a separate ID so research
forks can develop without touching prod.

Token persistence: GitHub Device Flow tokens *do not expire by default*
unless the GitHub app config requires expiry. We treat absence of
`expires_in` in the response as "non-expiring; refresh on 401 by
re-running Device Flow". If `expires_in` and `refresh_token` are
present, we use them.

```kotlin
suspend fun startGitHubDeviceFlow(): DeviceCodeResponse =
    okhttp.post("https://github.com/login/device/code")
        .form("client_id" to BuildConfig.GITHUB_CLIENT_ID,
              "scope" to "repo admin:public_key")
        .header("Accept", "application/json")
        .toJson()

suspend fun pollGitHubToken(deviceCode: String, intervalSec: Int): TokenResponse {
    while (true) {
        delay(intervalSec.seconds)
        val res = okhttp.post("https://github.com/login/oauth/access_token")
            .form("client_id" to BuildConfig.GITHUB_CLIENT_ID,
                  "device_code" to deviceCode,
                  "grant_type" to "urn:ietf:params:oauth:grant-type:device_code")
            .header("Accept", "application/json")
            .toJsonOrError()
        when (res.error) {
            null -> return res
            "authorization_pending" -> continue
            "slow_down" -> delay(5.seconds)            // bump interval
            "expired_token" -> throw DeviceFlowExpired
            "access_denied" -> throw DeviceFlowDenied
            else -> throw DeviceFlowError(res.error)
        }
    }
}
```

### Forgejo OAuth2 Device Flow

Forgejo (1.21+) supports the `urn:ietf:params:oauth:grant-type:device_code`
grant. Endpoints are instance-specific (self-hosted), discovered via:

1. **Try OIDC discovery:** `GET <base>/.well-known/openid-configuration`
   — extract `device_authorization_endpoint` and `token_endpoint`.
2. **Fallback to convention:** `<base>/login/oauth/authorize/device` and
   `<base>/login/oauth/access_token` (Forgejo's documented paths).

Scopes for Forgejo: `read:user write:user_keys read:repository write:repository`.
(Forgejo's scope grammar; tests against codeberg.org canonical.)

**Client registration:** **Decision: ship a generic "strictlykeptboy
device flow" registration per known instance is impractical.** Instead,
v1 ships a *user-supplied* client ID. The user creates an OAuth app on
their Forgejo instance and pastes the client ID into the add-repo flow.
This is a small UX cost in exchange for not requiring us to register
clients on every Forgejo instance in the world. `codeberg.org` and a
short list of known instances ship with a baked-in client ID we register
ourselves.

If the user can't or won't create an OAuth app, the manual PAT path is
the fallback (Phase SE-F).

### Manual PAT entry (Phase SE-F)

Three fields: provider URL (HTTPS), username (for Forgejo) or `oauth2`
(for GitHub), token. Validation:

1. Hit the provider's "current user" endpoint (`/user` for GitHub,
   `/api/v1/user` for Forgejo) with the token. Expect 200 + JSON.
2. If it's for a specific repo, attempt `git ls-remote <url>` — fails
   fast on missing read permission.

Stored as `pat.token.<repo-id>` + `https.username.<repo-id>`.

### Token storage + refresh

- Tokens go into `SecretsStore` (Phase SE-C.2).
- On any HTTPS-transport 401 from JGit, the `transportConfigCallback`
  retries once after refreshing:
  - If the provider issued a `refresh_token`, POST to the token endpoint
    with `grant_type=refresh_token`.
  - Else (GitHub no-refresh case), surface a "session expired,
    re-authenticate" notification + flag the repo `auth_failed`.
- `auth_failed` repos are skipped by background sync until the user
  re-authenticates from Settings.

### Phase tasks

- [ ] **SE-E.1** `OAuthClient` interface; `GitHubOAuthClient` and
  `ForgejoOAuthClient` implementations
- [ ] **SE-E.2** Device Flow polling loop with `slow_down` handling
- [ ] **SE-E.3** `OidcDiscoveryClient` for Forgejo; fallback to
  convention paths
- [ ] **SE-E.4** Add-repo wizard step: pick provider → start Device Flow
  → show user code + verification URL + open-browser button → poll →
  success
- [ ] **SE-E.5** `RefreshingCredentialsProvider` decorator: catches 401,
  attempts refresh, retries once
- [ ] **SE-E.6** Auto-upload public key flow after first successful auth
  (per Phase SE-C.7)
- [ ] **SE-E.7** Manual PAT entry screen + `validateToken` smoke test
  hitting `/user` endpoint
- [ ] **SE-E.8** `BuildConfig.GITHUB_CLIENT_ID` + `BuildConfig.FORGEJO_KNOWN_INSTANCES`
  baked-in map
- [ ] **SE-E.9** MockWebServer-driven tests for the polling state
  machine: pending → success, pending → slow_down → success, pending →
  expired

---

## Phase SE-F — Per-repo credential binding + RepoStore

Corresponds to `main.md` **B.3**, **B.7**.

### Data shape

```kotlin
@Serializable
data class RepoConfig(
    val repoId: String,           // sha256(remoteUrl).take(12)
    val displayName: String,
    val remoteUrl: String,
    val transport: Transport,     // SSH | HTTPS
    val authMethod: AuthMethod,   // OAuthGitHub | OAuthForgejo | ManualPat | SshKey
    val activeIdentityPersonId: String,
    val autoSyncEnabled: Boolean,
    val syncIntervalMinutes: Int, // 5, 15, 30, 60, 240, -1 (manual-only)
    val wifiOnly: Boolean,
    val defaultCalendarId: String?,
    val defaultTodolistId: String?,
    val colorSeed: Int?,
    val iconSpec: IconSpec,       // Emoji | Photo(uri) | Initials
    val readOnlyDetected: Boolean,
    val lastError: SyncError?,
    val lastSyncedAt: Long?,      // epoch ms
    val commitsAhead: Int,
    val commitsBehind: Int,
)
```

`RepoStore` persists a `List<RepoConfig>` as JSON in
`EncryptedSharedPreferences` (alongside secrets, but in a separate file
`repos_v1.xml` for clean uninstall reset). The store exposes a
`StateFlow<List<RepoConfig>>` that the UI observes.

Mapping repo URL → credentials goes by `repoId`. On lookup: read all
`*.<repoId>` keys from `SecretsStore` and assemble a `CredentialBinding`.

### Phase tasks

- [ ] **SE-F.1** `RepoConfig` data class with `kotlinx.serialization`
- [ ] **SE-F.2** `RepoStore` with `StateFlow` and add/remove/update
  operations; persists JSON-encoded list
- [ ] **SE-F.3** `CredentialBinding` resolver that maps a `RepoConfig`
  to a `transportConfigCallback`
- [ ] **SE-F.4** Migration shim: if `repos_v0.xml` exists (none does at
  v1, scaffold the hook for v2)
- [ ] **SE-F.5** Robolectric test: write 3 repos, restart-emulated
  process, read 3 repos back

---

## Phase SE-G — `SyncService` foreground service

Corresponds to `main.md` **J.1**.

### Manifest

```xml
<service
    android:name=".sync.SyncService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Lifecycle

```kotlin
class SyncService : LifecycleService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_SYNC, buildSyncNotification(content = "ready"))
        when (intent?.action) {
            ACTION_SYNC_ALL -> lifecycleScope.launch { syncOrchestrator.syncAllFlagged() }
            ACTION_SYNC_REPO -> lifecycleScope.launch { syncOrchestrator.syncOne(intent.repoId) }
            ACTION_TICK -> lifecycleScope.launch { syncOrchestrator.runScheduledTick() }
        }
        return START_STICKY
    }
}
```

`startForeground` runs within 5 seconds of `onStartCommand` (Android
mandate) — we always call it first, then dispatch.

### Notification

- Channel id: `sync` (LOW importance; `IMPORTANCE_LOW` so no sound or
  heads-up).
- Title: `strictlykeptboy is syncing`.
- Body (live-updated): `idle` / `syncing <repo-name>` / `last synced <time>`.
- Ongoing flag set; user cannot dismiss while service is foreground.
- Tap → opens app to Settings → Sync.
- Action: "Sync now" — pending intent that broadcasts `ACTION_SYNC_ALL`.

### Restart-on-boot

**Decision: yes, restart on boot, but only if any repo has
`autoSyncEnabled = true` AND `syncIntervalMinutes > 0`.** A
`BootCompletedReceiver` checks the `RepoStore` and, if at least one repo
qualifies, schedules the next WorkManager tick (Phase SE-H). It does
NOT immediately start `SyncService` on boot — first tick is scheduled
for `now + jitter(0, 5min)` to avoid the boot-storm.

### WorkManager interaction

`SyncService` handles the *currently-running* sync. WorkManager handles:

- **Periodic ticks:** `PeriodicWorkRequest` with the smallest configured
  per-repo interval (clamped ≥ 15min — Android's WM minimum). The tick
  worker enqueues `ACTION_TICK` to `SyncService`.
- **Retry-on-transient-failure:** when `SyncService` reports a
  retryable error (network, 5xx) for a repo, it enqueues a
  `OneTimeWorkRequest` with exponential backoff
  (`setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, SECONDS)`).
  Worker re-invokes `SyncService` for that repo.
- **Connectivity-restored trigger:** a `OneTimeWorkRequest` with
  `Constraints.Builder().setRequiredNetworkType(CONNECTED).build()`
  fires when network returns. (Replaces a manual `ConnectivityManager`
  callback for the offline-flush path.)

For sub-15min intervals (5min) we drive ticks from `SyncService`'s own
`Handler`/coroutine — WM can't go below 15min. Service-driven ticks
require the service to stay alive, which the foreground notification
permits.

### Phase tasks

- [ ] **SE-G.1** Manifest entries + `RECEIVE_BOOT_COMPLETED`
- [ ] **SE-G.2** `SyncService` class with `startForeground` discipline
- [ ] **SE-G.3** Notification channel registration in `App.onCreate`
- [ ] **SE-G.4** Notification builder; live updates via
  `NotificationManagerCompat.notify` re-fire
- [ ] **SE-G.5** `BootCompletedReceiver` + WorkManager initial enqueue
- [ ] **SE-G.6** WorkManager periodic worker + one-shot retry worker +
  connectivity-trigger worker
- [ ] **SE-G.7** Settings toggle: "show sync notification" — when off,
  we still must show it (legal/system requirement) but we use
  `IMPORTANCE_MIN` and a generic title to minimize visibility. The
  warning text in Settings says "Android requires this notification for
  background sync; disable sync entirely to remove it."

---

## Phase SE-H — Sync scheduler + triggers

Corresponds to `main.md` **J.2**, **J.3**.

### Trigger sources

1. **Periodic tick** (per-repo interval).
2. **App came to foreground** — a `LifecycleObserver` on
   `ProcessLifecycleOwner` fires `ACTION_SYNC_ALL` if any auto-sync repo
   hasn't synced in the last 60s.
3. **Connectivity restored** — WorkManager job (Phase SE-G).
4. **Manual sync** — top-bar button → `ACTION_SYNC_ALL`.
5. **Per-repo manual** — repo settings → "sync now" → `ACTION_SYNC_REPO`.
6. **Post-commit** — after every local commit by the editor, the
   `EditorViewModel` enqueues `ACTION_SYNC_REPO` for that repo (bypasses
   the per-repo auto-sync toggle in v1: a user explicitly editing
   intends a push). **Decision: post-commit always pushes when online,
   regardless of auto-sync toggle, because the alternative (silent
   queue) surprises users editing on read-write repos with auto-sync
   off for read-throttling reasons.** Read-only repos (push refused)
   queue silently per Phase SE-K.

### Jitter

When `syncAllFlagged()` runs, repos are dispatched with a random delay
of `0..30s` per repo (uniform). Prevents 5 repos from all hitting GitHub
at the same instant after wake-up.

### Per-repo configurable interval

| Setting | Effective period |
|---|---|
| `manual-only` | -1 (no scheduled ticks; manual + post-commit only) |
| `5m` | 5 min (service-driven; service must be running) |
| `15m` | 15 min (WorkManager) |
| `30m` | 30 min (WorkManager) |
| `1h` | 60 min (WorkManager) |
| `4h` | 240 min (WorkManager) |

Default `15m` per `decisions.md` D.8.

### Wi-Fi-only mode

**Decision: per-repo setting, defaulting to off (any network).** Some
users have data caps; some have no Wi-Fi. The setting lives next to the
auto-sync toggle. When on, we add
`Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)`
to the WorkManager request for that repo. For service-driven 5-min
ticks, we check `ConnectivityManager.activeNetwork`'s
`NetworkCapabilities.NET_CAPABILITY_NOT_METERED` before firing.

### Phase tasks

- [ ] **SE-H.1** `SyncOrchestrator` class with `syncAllFlagged()`,
  `syncOne(repoId)`, `runScheduledTick()`
- [ ] **SE-H.2** `ProcessLifecycleOwner` observer hook
- [ ] **SE-H.3** Connectivity callback via
  `ConnectivityManager.registerDefaultNetworkCallback` for
  service-driven path (non-WM)
- [ ] **SE-H.4** Jitter applied at orchestrator level
- [ ] **SE-H.5** Manual-sync button wires through `SyncService` action
- [ ] **SE-H.6** Per-repo "sync now" button in repo settings
- [ ] **SE-H.7** Post-commit hook in editor flow

---

## Phase SE-I — Pull strategy: fetch + rebase + auto-resolve

Corresponds to `main.md` **J.4**, **J.5** (detection part).

### Algorithm

Per repo:

1. `fetch` → updates `origin/<branch>`.
2. Compare local `HEAD` with `origin/<branch>`:
   - Equal: emit `UpToDate`. Update `lastSyncedAt`. Done.
   - Local ahead, remote unchanged: skip rebase, jump to push.
   - Remote ahead, local unchanged: fast-forward via
     `git.rebase().setUpstream(...)` (which fast-forwards when no local
     commits). Emit `FastForwarded`. Compute `changedPaths` via diff
     between old and new HEAD. Done with pull (still maybe push).
   - Both diverged: rebase local commits onto `origin/<branch>`.
     - JGit `RebaseResult.Status` cases:
       - `OK` / `FAST_FORWARD` / `UP_TO_DATE` — emit `Rebased`; compute
         changed paths.
       - `STOPPED` — conflicts. Emit `Conflicted` with file list and
         `RebaseHandle`. Proceed to conflict-resolution UI (Phase SE-J).
       - `CONFLICTS` (rebase aborted before any commit applied) — same
         treatment.
       - `EDIT`, `NOTHING_TO_COMMIT`, `INTERACTIVE_PREPARED` — should
         not happen (we don't use interactive mode); treated as `Failed`.
       - `STASH_APPLY_CONFLICTS` — should not happen (we don't autostash).
       - `FAILED` — emit `Failed` with the underlying exception.

### Auto-resolvable cases

We do NOT attempt automatic content-level merge in v1. Reasoning:

- Files are tiny (one entity) and conflicts are meaningful — silent
  auto-merge is dangerous on user data.
- The "trivial" auto-merges JGit could do (different files touched on
  each side) are already handled by `git rebase`'s own merger when there
  is no overlap on the same path. JGit's default merger is `RECURSIVE`
  / `RESOLVE`; we accept its judgment for non-overlapping changes and
  surface UI only when the same file diverges.

**Decision: zero custom auto-merge logic in v1.** Future v2 may
recognize TOML-key-disjoint changes and auto-merge frontmatter when
local touched key X and remote touched key Y; deferred because it
requires careful semantic-merge UX.

### Updating sync status

After every pull (success or fail) we update `RepoConfig`:

- `lastSyncedAt` (only on success / up-to-date)
- `commitsBehind` (always recomputed; 0 after success)
- `lastError` (set on Failed; cleared on success)
- `commitsAhead` (recomputed via `revWalk` against `origin/<branch>`)

These flow into the UI's per-repo sync badge (Phase J.8).

### Phase tasks

- [ ] **SE-I.1** `pullRebase` returning typed `PullResult`
- [ ] **SE-I.2** `changedPaths` computation between old and new HEAD
  (using `DiffFormatter` and ignoring whitespace)
- [ ] **SE-I.3** Emit changed paths to `IndexerBus` (a SharedFlow the
  Room indexer subscribes to)
- [ ] **SE-I.4** `commitsAhead` / `commitsBehind` recompute helpers
- [ ] **SE-I.5** Robolectric tests: simulate diverged history with two
  branches; assert auto-rebase succeeds for non-overlapping file
  changes, emits `Conflicted` for overlapping changes

---

## Phase SE-J — Conflict detection + resolution data path

Corresponds to `main.md` **J.5**, **J.6** (data-side; UI side is
`ui-spec.md`).

### Conflict surfacing

When `pullRebase` returns `Conflicted(paths, handle)`:

1. The orchestrator sets `RepoConfig.lastError = SyncError.Conflict(paths)`.
2. The orchestrator does NOT abort the rebase — it leaves the rebase in
   `STOPPED` state with the `RebaseHandle` cached in
   `ConflictResolutionRegistry`.
3. The UI shows a banner on the repo: "N files conflicted — resolve".
4. Tapping the banner opens the conflict-resolution screen which iterates
   the path list one file at a time.

### Three-way file model

For each conflicted file we expose `ConflictedFile`:

```kotlin
data class ConflictedFile(
    val path: String,
    val baseContent: String?,    // common ancestor; null if the file was added on both sides
    val oursContent: String?,    // local side (the side being rebased onto remote, so "theirs" in JGit terms — but we present from the user's viewpoint as "ours")
    val theirsContent: String?,  // remote side
    val parsedBase: ParsedFile?, // null if base unparseable
    val parsedOurs: ParsedFile?,
    val parsedTheirs: ParsedFile?,
)

data class ParsedFile(
    val frontmatter: Map<String, TomlValue>,  // structured, key-ordered
    val body: String,
)
```

Note on git terminology: during `git rebase`, JGit's "ours" is the
upstream side (`origin/main`) and "theirs" is your local side (because
rebase replays your commits *on top of* upstream). We re-label
consistently from the user's perspective — `oursContent` in our model
is the user's local change, `theirsContent` is the remote change.

The data layer extracts these by reading:

- `baseContent`: blob at the merge base (`git merge-base`).
- `oursContent`: blob at `HEAD` (during rebase, the originally-local
  side is the one being applied; we materialize from the in-progress
  rebase metadata).
- `theirsContent`: blob at `origin/<branch>`.

We use JGit's `IndexDiff` and the rebase metadata files
(`.git/rebase-merge/orig-head`, `.git/rebase-merge/onto`, etc.) to
identify these SHAs reliably. JGit exposes them via
`Repository.readMergeHeads()` and `Repository.readOrigHead()` during a
rebase-stopped state.

### TOML-aware merge presentation

The data layer parses each side via the `ktoml` parser (Phase C.1) to
produce `ParsedFile`. This enables the UI to show a structured 3-way
diff per TOML key (the visual side is in `ui-spec.md`). If parsing fails
(corrupt frontmatter), the UI falls back to raw text 3-way diff.

For the body section (Markdown, after the `+++` close fence): we present
as plain text with conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`)
preserved if JGit wrote them. The user edits the text directly.

### Resolution flow

For each conflicted file, the user produces a `ResolvedFile`:

```kotlin
data class ResolvedFile(
    val path: String,
    val finalContent: String,    // serialized TOML+body, ready to write
)
```

The data layer:

1. Writes `finalContent` to the working tree at `path`.
2. Invokes `git.add().addFilepattern(path).call()` to stage it.
3. Marks the conflict resolved in JGit's index.

When all conflicts in the rebase step are resolved, the user presses
"continue rebase":

```kotlin
git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call()
```

If more conflicts arise on the next replayed commit, the cycle repeats.

If the user gives up: `git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()`
restores the pre-rebase HEAD. The repo is back to a healthy "ahead of
remote" state; sync can be retried later.

### Keep-mine / keep-theirs fast paths

The UI exposes:

- **Keep mine**: `finalContent = oursContent`, then add + continue.
- **Keep theirs**: `finalContent = theirsContent`, then add + continue.
- **Manual merge**: present the 3-way editor.

These collapse to a single helper on the data side:
`resolveAs(file, choice: Choice)`.

### Phase tasks

- [ ] **SE-J.1** `ConflictedFile` extraction: read base/ours/theirs
  blobs via JGit, parse with ktoml, build the structured object
- [ ] **SE-J.2** `ConflictResolutionRegistry`: maps `repoId` → list of
  `ConflictedFile` + `RebaseHandle`
- [ ] **SE-J.3** `RebaseHandle` wrapping `Git` + the active rebase op
  (continue/abort/skip)
- [ ] **SE-J.4** `resolve(file, finalContent)` writes file + adds + marks
  resolved in JGit index
- [ ] **SE-J.5** `continueRebase()`: invokes JGit continue; returns a
  new `PullResult` (continued OK / new conflicts / done)
- [ ] **SE-J.6** `abortRebase()`: invokes JGit abort
- [ ] **SE-J.7** Integration test: produce a diverged repo with one
  conflicting TOML edit, drive resolution programmatically, assert
  final HEAD is correct + working tree clean

---

## Phase SE-K — Push strategy + read-only handling

Corresponds to `main.md` **J.7**.

### Push policy

- **Online + repo not flagged read-only:** push immediately after every
  local commit.
- **Online + repo flagged read-only:** skip push; the local commit stays
  in the local branch only. UI shows "your changes are local-only on
  this read-only repo" banner. (User can still edit and view —
  effectively a forked private workspace.)
- **Offline:** local commit succeeds; push is queued. WorkManager
  connectivity-trigger fires on reconnect → orchestrator retries push.

### Detecting read-only

After a push attempt:

- `RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD`: not necessarily
  read-only — could be a race. We pull-rebase and retry once; if it
  rejects again, surface "non-fast-forward push rejected" error.
- `RemoteRefUpdate.Status.REJECTED_OTHER_REASON` with message containing
  `permission`/`forbidden`/`denied` (case-insensitive substring match):
  flag `readOnlyDetected = true` on the `RepoConfig`.
- `RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED`: same as
  non-fast-forward; pull and retry.
- TransportException with HTTP 403 / 401: treat as auth failure
  (Phase SE-E.5).
- Branch-protection (GitHub): manifests as `REJECTED_OTHER_REASON` with
  message `protected branch`. We surface a distinct error
  `BranchProtected` so the UI can advise opening a PR (out of v1 scope;
  for v1 we just block).

When `readOnlyDetected = true`, the orchestrator skips push for that
repo on subsequent syncs. The user can manually clear the flag from
repo settings ("retry push" button).

### Phase tasks

- [ ] **SE-K.1** Map `RemoteRefUpdate.Status` to `PushRejection`
- [ ] **SE-K.2** Push retry-once logic for `REJECTED_NONFASTFORWARD`
  (interleaves pull-rebase between attempts)
- [ ] **SE-K.3** `readOnlyDetected` flag toggling + UI banner wiring
- [ ] **SE-K.4** "Retry push" button clears the flag and forces a sync
- [ ] **SE-K.5** Robolectric test: bare-repo with hooks rejecting push,
  assert correct flag transitions

---

## Phase SE-L — Network monitoring, retry/backoff, error taxonomy

Corresponds to `main.md` **J.4** (status persistence) and **B.8**
(network monitor).

### Network monitor

`ConnectivityManager.registerDefaultNetworkCallback` provides:

- `onAvailable(network)` → trigger flush
- `onLost(network)` → mark "offline"; orchestrator skips network ops
- `onCapabilitiesChanged(network, caps)` → update `metered` flag for
  Wi-Fi-only repos

We expose a `StateFlow<NetworkState>` consumed by the orchestrator.
Network state: `Offline`, `OnlineMetered`, `OnlineUnmetered`.

### Retry + backoff

Transient errors (`UnknownHostException`, `ConnectException`,
`SocketTimeoutException`, HTTP 502/503/504, JGit `TransportException`
with cause matching the above):

| Attempt | Delay before retry |
|---|---|
| 1 | 30s |
| 2 | 2min |
| 3 | 8min |
| 4 | 30min |
| 5 | 2h |
| 6+ | give up; surface persistent failure |

Implemented via WorkManager exponential backoff
(`setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, SECONDS)`) capped
at 6 attempts via `runAttemptCount`.

Non-transient errors (auth failure, conflict, push rejected for
permissions, repo corrupted, schema mismatch): no retry; immediate
surface to UI; user-driven recovery.

### Error taxonomy

```kotlin
sealed interface SyncError {
    object Offline : SyncError
    data class Auth(val provider: ProviderKind) : SyncError
    data class Network(val cause: String, val transient: Boolean) : SyncError
    data class PushRejected(val reason: PushRejection) : SyncError
    data class Conflict(val paths: List<String>) : SyncError
    object RepoCorrupted : SyncError                 // .git invariant violated
    data class SchemaMismatch(val repoVersion: Int, val appLatest: Int) : SyncError
    data class Unknown(val message: String) : SyncError
}
```

User-facing messaging matrix (each maps to a recovery action):

| Error | Banner text | Recovery action |
|---|---|---|
| `Offline` | "No network — local changes queued" | (auto on reconnect) |
| `Auth(github)` | "GitHub session expired — sign in again" | Re-run Device Flow |
| `Auth(forgejo)` | "Forgejo authentication failed" | Re-run Device Flow / re-enter PAT |
| `Network(transient)` | "Network glitch — retrying" | (auto retry) |
| `Network(persistent)` | "Sync failed: \<cause\>. Tap to retry" | Manual retry |
| `PushRejected(NonFastForward)` | "Remote moved while pushing — re-syncing" | (auto pull+push) |
| `PushRejected(NoPermission)` | "Read-only repo — local changes only" | Optional: re-auth with write scope |
| `PushRejected(BranchProtected)` | "Branch is protected; cannot push" | Open PR manually |
| `Conflict` | "N files conflicted — resolve" | Open conflict UI |
| `RepoCorrupted` | "Repo data damaged — re-clone?" | Re-clone destructively (kept the EncryptedSharedPreferences creds) |
| `SchemaMismatch` | "This repo was written by a newer app version" | Update strictlykeptboy |

### Telemetry: in-app debug log only

No analytics. We keep a circular in-memory log (last 1000 entries) and
optionally mirror to `cacheDir/sync.log` (rotated at 1MB). Settings →
About → "Copy debug log" gives the user a clipboard dump for issue
reports. Per `decisions.md` D.22.

### Multi-repo coordination

- Up to **3 concurrent syncs**. Bounded by a `Semaphore(3)` inside the
  orchestrator. Avoids running 10 parallel rebases on a phone.
- Per-repo error isolation: one repo's failure never aborts another
  repo's sync. The orchestrator runs each repo's sync inside its own
  `try/catch`; failures update only that repo's `RepoConfig.lastError`.
- UI updates: each repo emits status to its own `StateFlow`; the repo
  list view collects all and shows badges per repo without serialization.

### Phase tasks

- [ ] **SE-L.1** `NetworkMonitor` with `StateFlow<NetworkState>`
- [ ] **SE-L.2** `SyncError` sealed hierarchy
- [ ] **SE-L.3** Error→banner-text mapping in a single `SyncErrorMessages`
  object with `stringResource` keys (per Phase U.4 strings discipline)
- [ ] **SE-L.4** Retry classification: helper `SyncError.isTransient`
- [ ] **SE-L.5** WorkManager retry worker honors
  `runAttemptCount` cap; gives up after 6
- [ ] **SE-L.6** `Semaphore(3)` in orchestrator; per-repo isolation
- [ ] **SE-L.7** In-app circular log + Settings "copy debug log" action
- [ ] **SE-L.8** Test: 5 repos, 3 succeed and 2 fail with different
  errors; assert per-repo isolation and correct status emission

---

## Phase SE-M — Commit message generation + author identity

Corresponds to `decisions.md` D.8 + D.15.

### Auto-template

Format: `<verb> <entity-kind> "<title>" in <calendar-name>`

Verbs:

- New file: `add`
- Existing file modified: `update`
- File deleted: `delete`

Entity kinds: `event`, `task`, `recurrence`, `exception`, `identity`,
`calendar`, `todolist`, `repo-meta`.

`<title>` is the entity's `title` frontmatter field, truncated at 60
chars with ellipsis if longer.

`<calendar-name>` is the parent calendar's `name` field; for tasks it's
the todolist's `name`.

### Bulk commits

When the editor flushes >1 change at once (rare; mostly from import
flows or template re-application), commit messages collapse:

- Same kind, same calendar, ≥2 entities: `update 5 events in Personal`
- Mixed kinds, same calendar: `update 7 entries in Personal`
- Mixed calendars: `update 7 entries across 3 calendars`

### User override

Settings → Sync → "Commit message style":

- `Auto` (default)
- `Auto + manual confirmation` — show a dialog before commit with the
  generated message editable
- `Always prompt` — empty default, user types every commit message

### Author identity

`identities/<person-id>.md` frontmatter:

```toml
display_name = "Alex"
email = "alex@example.com"           # optional
default_author = true
```

Mapping at commit time:

- `user.name` = `display_name`
- `user.email` = `email` if present, else
  `<person-id>@strictlykeptboy.local`

The active identity per repo is `RepoConfig.activeIdentityPersonId`,
configurable in Settings → Identities.

### Phase tasks

- [ ] **SE-M.1** `CommitMessageGenerator` taking `List<EntityChange>`
  → `String`
- [ ] **SE-M.2** Bulk-collapse heuristics
- [ ] **SE-M.3** Settings preference for style; UI for "manual
  confirmation" dialog
- [ ] **SE-M.4** `AuthorIdentity` resolver from `RepoConfig` +
  `identities/<id>.md`
- [ ] **SE-M.5** Fallback email synthesis: `<person-id>@strictlykeptboy.local`
- [ ] **SE-M.6** Test: 3 events added in 1 batch → `add 3 events in Foo`

---

## Phase SE-N — Indexer integration

Corresponds to `main.md` **D.3**.

### Contract

After every successful pull/rebase or local commit:

1. The `GitRepo` computes
   `diffSinceLastIndexed(lastIndexedHead = repoConfig.lastIndexedSha)`.
2. The result is a `Set<ChangedPath>` where `ChangedPath` is:
   ```kotlin
   sealed interface ChangedPath {
       data class Added(val path: String) : ChangedPath
       data class Modified(val path: String) : ChangedPath
       data class Deleted(val path: String) : ChangedPath
       data class Renamed(val from: String, val to: String) : ChangedPath
   }
   ```
3. The set is published to `IndexerBus` (a `MutableSharedFlow<IndexUpdate>`).
4. Room indexer (Phase D) subscribes; processes each path:
   - Added/Modified → re-read file → upsert into Room
   - Deleted → delete from Room by path
   - Renamed → delete old + insert new
5. After processing, the indexer updates
   `repoConfig.lastIndexedSha = currentHead`.

If the SHA is missing or unreachable (e.g. shallow fetch garbage-collected
the merge base), the indexer falls back to a full rescan.

### Phase tasks

- [ ] **SE-N.1** `IndexerBus` SharedFlow keyed by repoId
- [ ] **SE-N.2** `diffSinceLastIndexed` implementation (already in
  Phase SE-B.8; this phase wires it to the bus)
- [ ] **SE-N.3** `lastIndexedSha` field added to `RepoConfig`
- [ ] **SE-N.4** Fallback to full scan when last sha unreachable
- [ ] **SE-N.5** Test: simulate a 3-commit divergence pull → assert
  exactly 3 ChangedPath events emitted

---

## Phase SE-O — Testing strategy

Corresponds to `main.md` **B.9**.

### Robolectric layer

- `git init --bare` in a temp dir as the "remote".
- Two clones: `clientA`, `clientB`. Drive divergent edits via JGit, then
  exercise our `GitRepo.pullRebase` / `push` paths.
- All conflict scenarios scriptable as fixtures.
- Auth path: skip transport entirely; use `file://` URLs (no network).

### MockWebServer layer

- OAuth Device Flow: full state machine (pending → slow_down → success;
  pending → expired; pending → access_denied).
- Refresh-on-401 retry path.
- Forgejo OIDC discovery.
- HTTPS clone retries on 5xx.

### Instrumented layer (later, post-v1 stabilization)

- Real SSH against a throwaway test server (a single `sshd-osgi`
  instance bundled in the test apk that hosts a tiny git repo on
  loopback).
- Real HTTPS via JGit's `JettyDaemon` test server.

### Test data

- Three canonical fixture repos: `tiny` (3 entries), `medium` (300),
  `large` (3000). Generated once, snapshotted in
  `app/src/test/fixtures/`.

### Phase tasks

- [ ] **SE-O.1** `GitTestFixtures` helper to spin up bare + clones
- [ ] **SE-O.2** `MockOAuthServer` Robolectric helper
- [ ] **SE-O.3** Fixture generators for tiny/medium/large repos
- [ ] **SE-O.4** Unit tests covering each Phase SE-A through SE-N's
  test-tagged tasks
- [ ] **SE-O.5** Coverage target: ≥80% line coverage on `:app:git`
  module by end of Phase B (per `main.md` Phase V budgets)

---

## Phase SE-P — Edge cases + invariants

Tactical edge cases discovered during planning. Resolved inline.

### Repo cloned but never synced (initial state)

- `lastSyncedAt = null`, `commitsAhead = 0`, `commitsBehind = 0`.
- First sync: full diff against null → indexer runs full scan.

### User changes active identity mid-edit

- The identity at *commit time* wins. Edits prior to the switch are
  uncommitted (`status` shows them dirty); the next `commitAll` uses the
  new identity for `user.name`/`user.email` and stamps the new
  `author = "<new-person-id>"` in any *new* file. Existing files'
  `author` field is unchanged unless the user explicitly edits.

### User deletes the active identity file

- Editor refuses new entries until a new active identity is selected.
- Existing entries with `author = "<deleted-id>"` continue to render via
  `git blame` fallback (per `decisions.md` D.4).

### Repo on disk corrupted (`.git/HEAD` missing, etc.)

- `GitRepo.open` throws `RepositoryNotFoundException` or
  `CorruptObjectException`.
- Wrapper maps to `SyncError.RepoCorrupted`.
- UI offers "re-clone" which deletes `rootDir` and runs `clone` again
  with the same `RepoConfig` and credential binding.

### User rotates SSH key

- New keygen → new key written to `SecretsStore`, overwriting the old.
- User must re-upload public key to provider (auto-upload if OAuth
  token has the scope; manual otherwise).
- Old `known_hosts.json` entry untouched (host key didn't change).

### Provider's OAuth client revoked

- 401 on next call → token refresh fails (or no refresh token) → flag
  `auth_failed` → user re-runs Device Flow which gets fresh tokens.

### Repo URL changed (rename or transfer)

- `repoId` is `sha256(remoteUrl)` → URL change = new repoId = orphaned
  secrets.
- Add an "Edit URL" button in repo settings that re-keys
  `SecretsStore` entries from old repoId to new repoId atomically. The
  on-disk `.git/config` is updated via JGit's `StoredConfig`.

### Disk full mid-rebase

- JGit throws `IOException` mid-checkout. We treat as `Unknown` error
  and refuse further sync until the user clears space; the rebase is
  left in `STOPPED` state, recoverable via abort once space is free.

### Two repos with the same URL

- Disallowed at `RepoStore.addRepo` time — we enforce `repoId`
  uniqueness.

### Phase tasks

- [ ] **SE-P.1** `RepoCorrupted` → re-clone path; ensure secrets are
  preserved across re-clone
- [ ] **SE-P.2** "Edit URL" repo-setting flow with secret re-keying
- [ ] **SE-P.3** Disk-full detection + user message
- [ ] **SE-P.4** RepoStore uniqueness enforcement
- [ ] **SE-P.5** Identity-deleted fallback render (handoff to UI spec)
- [ ] **SE-P.6** Audit each edge case has a test in Phase SE-O

---

# Round 2 — extensions (Phases SE-Q through SE-W)

The phases below extend the sync engine with Round 2 v1-scope additions
locked in `decisions.md` D.23–D.26, D.35, D.36. These are appended to
the Round 1 phases (SE-A through SE-P) above; they do NOT supersede or
rewrite any prior decision. Each phase resolves its own internal
tradeoffs inline (marked **Decision:**) per the global "elaborate, don't
punt" rule.

Cross-references back to `main.md` (Round 2 phases):

| `main.md` phase | This doc's Round 2 phases |
|---|---|
| **Y** (Round-2 CalDAV bridge) | SE-Q |
| **Z** (Round-2 Git LFS) | SE-R |
| **GG** (Round-2 signed commits + ssh-agent) | SE-S, SE-T |
| **JJ** (Round-2 multi-branch) | SE-U |

---

## Phase SE-Q — Bidirectional CalDAV bridge

Corresponds to `main.md` **Y** (Round 2 CalDAV bridge) and locks in
`decisions.md` **D.25**.

### Goal

Two-way sync between a calendar in the user's git-backed repo and a
CalDAV server (Google Calendar, Microsoft 365/Exchange, Apple iCloud,
Nextcloud, DAVx⁵-bridged endpoints, generic RFC4791 servers). The
bridge is **co-equal with git sync**, not nested under it: each
configured CalDAV mirror runs its own worker in `SyncService` and
fails/recovers independently.

### Libraries + licensing

| Library | License | Distribution status |
|---|---|---|
| `ical4j` (`net.fortuna.ical4j:ical4j:4.0.x`) | MPL-2.0 (file-level copyleft) | Link-clean. We do NOT modify ical4j sources; we link as a binary dep. Per D.25, MPL-2.0 only triggers share-back on modifications to ical4j itself, not on our app linking to it. |
| `dav4jvm` (`com.github.bitfireAT:dav4jvm`) | MPL-2.0 (file-level) | Same status. The library extracted from DAVx⁵; battle-tested against real CalDAV servers. |
| `okhttp` (existing) | Apache-2.0 | Reused as `dav4jvm`'s underlying HTTP client (single OkHttp config across git HTTPS + CalDAV). |

**Decision (license-clean status):** because both libraries are MPL-2.0
and we ship them as unmodified linked binaries inside our APK, our
distribution is unaffected by MPL-2.0's copyleft. Per `decisions.md`
Round-2 preamble, MPL-2.0 is on the allowed list. `About → Open-source
licenses` screen renders the MPL-2.0 notices verbatim alongside the
existing Apache-2.0 notices.

### Data model surface

A `CalDavMirror` is a first-class object peered to `RepoConfig`:

```kotlin
data class CalDavMirror(
    val mirrorId: String,            // sha256(repoId + serverUrl + calendarPath)
    val repoId: String,              // owning repo
    val displayName: String,
    val serverUrl: String,           // CalDAV root (e.g. https://caldav.example.com)
    val calendarHomePath: String,    // discovered via PROPFIND
    val calendarPath: String,        // e.g. /caldav/user/work-calendar/
    val targetCalendarId: String,    // repo-side <calendar-id> the mirror writes to
    val mode: CalDavMode,            // PULL_ONLY | PUSH_ONLY | BIDI
    val syncIntervalMinutes: Int,    // default 30
    val credentialBindingId: String, // CalDav creds in SecretsStore
    val lastSyncedCtag: String?,     // collection-level change token
    val lastSyncedSyncToken: String?,// RFC6578 WebDAV-Sync token if supported
    val lastSyncedAt: Long?,
    val lastError: CalDavError?
)

enum class CalDavMode { PULL_ONLY, PUSH_ONLY, BIDI }
```

`CalDavMirror`s are persisted in `EncryptedSharedPreferences` keyed by
`mirrorId`, mirroring the `RepoStore` pattern. Credentials live in
`SecretsStore` keyed by `credentialBindingId` — same encrypted store as
git creds, distinct keys.

### Object ↔ file mapping

Per `notifications-sharing-import.md` NS-F (iCal import via UID):

- One CalDAV `VEVENT` ↔ one repo file under
  `calendars/<targetCalendarId>/events/<yyyy>/<mm>/<event-id>.md`.
- Repo `event-id` is UUIDv7 generated app-side on first observation.
- The CalDAV `UID` is stored in frontmatter as `imported_uid = "<uid>"`.
  This is the join key for incremental sync.
- VTODO is mapped onto a sibling `todolists/<list-id>/tasks/...`
  structure only when the mirror's target is a todolist (CalDAV servers
  that expose tasks alongside events). v1 surfaces both VEVENT and
  VTODO; VJOURNAL is read but rendered as a comment-on-the-day entry
  (kept simple for v1).
- VTIMEZONE blocks are parsed but never written verbatim to repo files;
  the resolver re-derives them from `tz_id` per Round-2 D.27.
- RRULE → repo `recurrences/<rule-id>.md` (re-uses the existing
  recurrence model from `decisions.md` D.6). Per-instance overrides
  (`RECURRENCE-ID`) → `exceptions/<rule-id>/<yyyy-mm-dd>.md`.
- VALARMs are mapped to the repo's `notifications = [...]` array per
  D.14, with lead-times converted from CalDAV TRIGGER durations.

### Discovery flow

1. User enters server URL + credentials in `Settings → Repos → <repo>
   → + Add CalDAV mirror`.
2. App does an HTTP `OPTIONS` on the server URL to check `DAV:
   calendar-access` header. If absent → reject with "this server does
   not advertise CalDAV support".
3. `.well-known/caldav` GET → expect 301/302 redirect to
   `calendarHomeSet`. If no `.well-known`, fall back to a PROPFIND on
   the supplied URL for `{DAV:}current-user-principal`.
4. PROPFIND on principal URL with depth 0 for `{C:}calendar-home-set`.
5. PROPFIND on calendar-home with depth 1 for `{D:}resourcetype`,
   `{D:}displayname`, `{C:}supported-calendar-component-set`,
   `{CS:}getctag`, `{D:}sync-token`. Filter to entries whose
   resourcetype contains `{C:}calendar`.
6. Present discovered calendars to user with checkboxes; user picks
   mode (pull/push/bidi) and the repo-side target calendar (existing
   `<calendar-id>` or new).

### Credential storage

CalDAV credentials piggyback on the existing `SecretsStore` (Phase
SE-C) with a new credential kind:

```kotlin
sealed interface CalDavCredential : Credential {
    data class BasicAuth(val username: String, val password: String) : CalDavCredential
    data class BearerToken(val accessToken: String, val refreshToken: String?, val expiresAt: Long?) : CalDavCredential
    // OAuth: Google/Microsoft both speak OAuth 2.0 to their CalDAV endpoints
}
```

OAuth for Google/Microsoft uses the Device Flow + refresh-token pattern
identical to GitHub OAuth (Phase SE-E). Apple iCloud requires
app-specific passwords (no OAuth for CalDAV) → use BasicAuth with a
clear UI note: "Apple ID requires an app-specific password from
appleid.apple.com; your main Apple password will not work".

### Incremental sync

Two paths in priority order:

1. **RFC6578 WebDAV-Sync** (preferred). Server advertises
   `{DAV:}sync-collection` in OPTIONS. We send `REPORT sync-collection`
   with our last `sync-token`; server responds with added/changed/
   deleted hrefs. Used by Google, Apple, Nextcloud, Radicale.
2. **CTag + ETag fallback**. PROPFIND `{CS:}getctag` on the collection;
   if unchanged from `lastSyncedCtag`, skip. Else PROPFIND depth 1 for
   per-resource `{D:}getetag`; compare to a stored per-resource
   `etag_<href>` map; for each changed/added href do a GET. Deletes
   detected by hrefs missing from the new PROPFIND result.

**Decision (which path):** runtime probe at first sync — if
sync-collection works, persist that capability flag on the mirror and
prefer it. If REPORT 501s, fall back to ctag. The capability flag
is re-probed every 30 days to catch server upgrades.

### Conflict resolution

Conflicts arise when both sides change the same VEVENT between syncs.
Detection:

- Pull side: we observe a remote `ETag` change AND the local repo file
  has a `lastSyncedHash` ≠ `currentHash`.
- Push side: we attempt `PUT` with `If-Match: <known-etag>` and get
  `412 Precondition Failed`.

Resolution UI: **same 3-way diff modal as git conflicts** (Phase SE-J).
The modal is parameterized by `ConflictSource.{GIT, CALDAV}` and
renders identically — TOML field-level pre-merge + Markdown body
3-way diff + Keep mine / Keep theirs / Manual merge buttons. The only
difference: "theirs" is labeled "server" for CalDAV, "remote" for git.

After resolution:
- "Keep mine" → push to CalDAV with `If-Match: *` (force-overwrite).
- "Keep theirs" → overwrite local file with server version, write a
  git commit `pull caldav: prefer server for <title>`.
- "Manual merge" → user's merged version pushed to both git and CalDAV.

Per the `decisions.md` D.8 invariant, every CalDAV-side change also
produces a git commit (the repo is canonical). The commit message
prefix is `caldav:` so history is easy to filter.

### Per-mirror sync worker

Each `CalDavMirror` gets its own coroutine job inside `SyncService`:

```kotlin
class CalDavMirrorWorker(
    private val mirror: CalDavMirror,
    private val dav: DavCollection,        // dav4jvm handle
    private val repoStore: RepoStore,
    private val gitRepo: GitRepo,          // for the git-side commit after each pull
    private val bus: SyncStatusBus
) {
    suspend fun runOnce(): SyncResult { ... }
    suspend fun runForever() { /* delay(intervalMinutes); runOnce(); repeat */ }
}
```

`SyncService` keeps a `Map<MirrorId, Job>`; on mirror add → launch; on
remove → cancel.

### Failure isolation

A CalDAV mirror crashing (auth error, network timeout, server 500)
records `lastError` on the mirror and continues its retry-with-backoff
loop. **It does NOT block the owning repo's git sync, nor any other
mirror's sync.** Conversely, a git sync failure on the owning repo
does not stop CalDAV mirrors from running — the CalDAV side has its
own working copy of the calendar state.

Cross-impact only at conflict-resolution time: if git sync produces a
conflict on the same calendar file that CalDAV is trying to push, the
CalDAV push waits until the git conflict is resolved (the file on disk
is "in conflict" state and we don't ship in-flux data upstream).

### Per-server verification matrix

v1 testing covers these servers (per D.25):

| Server | Auth | sync-collection | Notes |
|---|---|---|---|
| Google Calendar | OAuth 2.0 (Device Flow → access+refresh) | Yes | Endpoint: `apidata.googleusercontent.com/caldav/v2/<email>/events`. |
| Microsoft 365 / Exchange | OAuth 2.0 (Device Flow) | Yes (Outlook), No (older Exchange) | Endpoint: `outlook.office365.com/EWS/Exchange.asmx` for EWS-only; pure CalDAV at `outlook.office.com/<tenant>/...`. |
| Apple iCloud | BasicAuth (app-specific password) | Yes | Endpoint discovered via `caldav.icloud.com`. Requires `https://` and modern TLS. |
| Nextcloud | BasicAuth or OAuth | Yes | Endpoint: `<nc>/remote.php/dav/calendars/<user>/<cal>/`. |
| DAVx⁵-bridged (passthrough) | Whatever the underlying server uses | Inherits | Not a server; only validated indirectly. |
| Radicale (self-hosted) | BasicAuth | Yes | Common self-host target; ensures we don't accidentally rely on Google-specific extensions. |

### CLI surface (cross-ref `cli-tooling.md`)

- `skb caldav add --repo R --server URL --calendar PATH --mode bidi --target-calendar CAL`
- `skb caldav list [--repo R]`
- `skb caldav sync [--mirror M | --all]`
- `skb caldav remove --mirror M`

### Phase tasks

- [ ] **SE-Q.1** Add `ical4j` + `dav4jvm` to `libs.versions.toml`;
  R8 keep rules for ical4j's `ServiceLoader`-discovered factories
- [ ] **SE-Q.2** `CalDavMirror` data class + `CalDavMirrorStore`
  (EncryptedSharedPreferences-backed)
- [ ] **SE-Q.3** `CalDavCredential` kinds + `SecretsStore` extension
- [ ] **SE-Q.4** Discovery flow: OPTIONS + .well-known + PROPFIND chain
- [ ] **SE-Q.5** `IcalMapper`: VEVENT ↔ repo Markdown+TOML file
  (UID preserved as `imported_uid`)
- [ ] **SE-Q.6** `IcalMapper`: VTODO → repo task file
- [ ] **SE-Q.7** `IcalMapper`: RRULE + RECURRENCE-ID → repo
  recurrences + exceptions
- [ ] **SE-Q.8** `IcalMapper`: VALARM → repo `notifications` array
- [ ] **SE-Q.9** `IcalMapper`: VJOURNAL → day-comment entry
- [ ] **SE-Q.10** Sync engine: WebDAV-Sync (REPORT sync-collection)
  path with capability probe
- [ ] **SE-Q.11** Sync engine: CTag + ETag fallback path
- [ ] **SE-Q.12** Push path: `PUT` with `If-Match` + 412 detection
- [ ] **SE-Q.13** Delete path: `DELETE` with `If-Match` for local
  deletions in BIDI mode
- [ ] **SE-Q.14** Conflict modal parameterization (shared with git
  conflict UI, label-only delta) — cross-ref Phase SE-J
- [ ] **SE-Q.15** `CalDavMirrorWorker` + `SyncService` integration
  (independent worker per mirror)
- [ ] **SE-Q.16** Failure-isolation tests: git sync continues when
  one mirror is wedged; mirror continues when git sync is wedged
- [ ] **SE-Q.17** Per-server verification matrix tests against
  MockWebServer fixtures for Google / iCloud / Nextcloud / Radicale
  response shapes
- [ ] **SE-Q.18** OAuth Device Flow for Google CalDAV (re-uses Phase
  SE-E machinery; scope `https://www.googleapis.com/auth/calendar`)
- [ ] **SE-Q.19** OAuth Device Flow for Microsoft CalDAV (scope
  `Calendars.ReadWrite` + `offline_access`)
- [ ] **SE-Q.20** Apple iCloud app-specific-password UX with inline
  help link to `appleid.apple.com`
- [ ] **SE-Q.21** Per-mirror configurable sync interval (default 30m,
  options 5m/15m/30m/1h/4h/manual)
- [ ] **SE-Q.22** `caldav:` commit-message prefix for all repo-side
  writes originating from a CalDAV pull
- [ ] **SE-Q.23** CLI subcommand wiring (handoff to `cli-tooling.md`)
- [ ] **SE-Q.24** Robolectric tests: mode matrix (pull-only, push-only,
  bidi) × conflict matrix (no-conflict, repo-wins, server-wins,
  manual-merge)

---

## Phase SE-R — Git LFS for large attachments

Corresponds to `main.md` **Z** (Round 2 Git LFS) and locks in
`decisions.md` **D.26**.

### Goal

Attachments above a configurable size threshold (default 1MB) route
through Git LFS, keeping the repo's pack history bounded and clone
times fast even when users attach 50MB PDFs or screen recordings.
JGit ships LFS support since 5.x; we use 6.x's `Lfs` API.

### `.gitattributes` generation

When LFS is enabled on a repo, the app maintains a managed
`.gitattributes` file at repo root:

```
# Managed by strictlykeptboy — do not edit between the BEGIN/END markers
# BEGIN strictlykeptboy-lfs
attachments/** filter=lfs diff=lfs merge=lfs -text
# END strictlykeptboy-lfs
```

The app reads the file, finds the marker block, replaces it on every
LFS-config change. Lines outside the markers are preserved (user can
add their own entries). On first enable, if `.gitattributes` exists
without the markers, the block is appended.

**Decision (marker style):** explicit markers preserve user edits
without us needing a parser; this is the same pattern git itself
recommends for tool-managed `.gitattributes`.

### Threshold-based routing

Per-repo setting `lfsThresholdBytes` (default `1_048_576` = 1 MiB).

- On attachment add: compute size. If `size >= threshold` AND LFS is
  enabled on the repo AND the provider has LFS capability → write the
  file under `attachments/<sha-prefix>/<sha256>.<ext>` and let the
  `.gitattributes` filter route it to LFS.
- If any condition fails → in-tree storage (existing path from
  `decisions.md` D.3). User sees a one-time toast: "this attachment
  is X MB; LFS would have kept it out of the repo history but
  [reason]".

Possible reasons surfaced: "LFS is disabled for this repo", "this
provider does not support LFS", "the file is below the threshold".

### Provider LFS capability detection

Probe at repo-add time and re-probe on first LFS-touched commit:

1. `HEAD <repoUrl>/info/lfs` — LFS-enabled providers return 200 with
   `application/vnd.git-lfs+json` content type or at least a 401
   (auth required) which we treat as "endpoint exists".
2. A 404 / 405 → provider has no LFS. Record
   `RepoConfig.lfsProviderCapable = false`. Show one-time warning
   and fall back to in-tree.
3. Re-probe is cached for 7 days per repo to avoid hammering.

GitHub, GitLab, Gitea, Forgejo, Bitbucket, Codeberg, self-hosted
gitolite-with-`lfs-test-server` all support the standard endpoint.

### LFS auth

LFS uses HTTP Basic over HTTPS, separately from the repo's
git-over-SSH or git-over-HTTPS auth. **Strategy:** piggyback the
repo's existing credential:

- HTTPS repo with OAuth token → LFS gets the same `Authorization: Bearer
  <token>` (GitHub/Forgejo accept their PATs as LFS creds).
- HTTPS repo with PAT → same PAT for LFS.
- SSH repo → LFS uses SSH `git-lfs-authenticate` shim: send `ssh
  git@host git-lfs-authenticate <repo> <op>` to get a short-lived HTTPS
  token, then use that for the LFS HTTP calls. JGit's `Lfs` driver
  handles this via the configured `SshdSessionFactory`.

**Decision (no separate LFS credential UI):** keeping LFS auth invisible
to the user matches the "Git LFS is transparent when supported" goal.
Failures fall through to "provider doesn't support LFS" and in-tree
storage.

### LFS pointer files

JGit writes the pointer file to the working tree on checkout; the
actual content lives in `.git/lfs/objects/<oo>/<bb>/<sha>`. Our
attachment renderer must detect pointer files and lazy-fetch:

```
version https://git-lfs.github.com/spec/v1
oid sha256:abc123...
size 1234567
```

Detection: file starts with `version https://git-lfs.github.com/spec/`
AND is small (<1KB). On detection, the attachment loader triggers an
LFS fetch and shows a small "downloading X MB attachment..." spinner.

### Migration helper

`skb migrate --lfs --repo R` for an existing repo:

**Decision (no history rewrite):** rewriting history to move existing
large attachments to LFS is destructive (changes every commit SHA,
breaks every clone). v1 ships **forward-migration only**: from the
migration commit onward, new attachments above the threshold go to
LFS; existing in-tree large attachments stay where they are. Document
this in the CLI help text and in the migration confirmation modal.

Migration steps:
1. Probe provider LFS capability.
2. Generate `.gitattributes` with the LFS marker block.
3. Set `RepoConfig.lfsEnabled = true` and `lfsThresholdBytes`.
4. Commit `enable git-lfs (forward-only; existing attachments
   unchanged)`.
5. Push.

Optional second pass (warned, opt-in): `skb migrate --lfs
--rewrite-history --repo R` is a v1.1 addition (gated; see
deferral list update below). v1 omits the rewrite path entirely.

### LFS pointer conflicts

A pointer file conflict (rare — only when both sides upload a
different blob for the same path between syncs) appears as a
single-file 3-way diff with the pointer-text shown. Resolution:
"Keep mine" = my blob's OID wins; "Keep theirs" = their OID wins;
"Manual merge" is disabled (a pointer file is structured; manual
edits would corrupt it). UI shows pointer fields as labeled rows
rather than raw text, with thumbnails of both blobs side-by-side
when the underlying type is image.

### Phase tasks

- [ ] **SE-R.1** `RepoConfig` extensions: `lfsEnabled: Boolean`,
  `lfsThresholdBytes: Long`, `lfsProviderCapable: Boolean?`
- [ ] **SE-R.2** `.gitattributes` marker-block reader/writer
- [ ] **SE-R.3** `LfsCapabilityProbe` with 7-day cache
- [ ] **SE-R.4** Attachment router: size-check + capability-check
  before write; in-tree fallback with one-time toast
- [ ] **SE-R.5** JGit `LfsConnectionFactory` wired to share OkHttp +
  auth with the repo's existing transport
- [ ] **SE-R.6** SSH path: `git-lfs-authenticate` token exchange via
  `SshdSessionFactory`
- [ ] **SE-R.7** Pointer-file detection in attachment loader
- [ ] **SE-R.8** Lazy LFS fetch on attachment open with progress UI
- [ ] **SE-R.9** Pointer-file conflict UI (labeled rows + thumbnail
  preview when type=image)
- [ ] **SE-R.10** `skb migrate --lfs` command (forward-only)
- [ ] **SE-R.11** Per-repo setting in `Settings → Repos → <repo> →
  Storage` for `lfsEnabled` + threshold slider
- [ ] **SE-R.12** Defaults: `lfsEnabled = true` for new repos when
  capability probe passes; `false` when capability probe fails
- [ ] **SE-R.13** Test: 5MB attachment routed to LFS when enabled,
  in-tree when disabled, in-tree-with-warning when provider lacks LFS
- [ ] **SE-R.14** Test: pointer conflict → modal renders with
  thumbnails

---

## Phase SE-S — Signed commits (optional)

Corresponds to `main.md` **GG** (Round 2 signed commits) and locks in
`decisions.md` **D.23**.

### Posture

Signing is a **capability**, not the identity model. Default OFF in UI,
CLI, and the wizard. Verification of others' signed commits is
independent of whether the local user signs their own commits.

### GPG private-key import

Two entry points in `Settings → Identities → <identity> → Signing
keys`:

1. **File picker** — opens SAF; user picks an ASCII-armored
   `.asc`/`.gpg` file. App parses with BouncyCastle's
   `PGPSecretKeyRingCollection`, prompts for the key's passphrase if
   encrypted, validates the key contains a signing-capable subkey,
   then stores.
2. **Paste armored text** — text field accepts pasted
   `-----BEGIN PGP PRIVATE KEY BLOCK-----` … `-----END PGP PRIVATE KEY
   BLOCK-----`. Same validation pipeline.

After import: app displays key fingerprint (40 hex chars, grouped
4-by-4), user IDs (name + email associations), creation date, expiry
(or "no expiry"). User confirms "yes this is the key I meant to
import" and it's persisted.

### Storage

Imported armored private keys live in `EncryptedSharedPreferences`
under prefs key `signing.gpg.<fingerprint>`. Each entry stores:

```kotlin
data class StoredGpgSigningKey(
    val fingerprint: String,         // 40 hex
    val armoredPrivateKey: String,   // user gave us this; we keep it armored
    val userIds: List<String>,       // for display only
    val createdAt: Long,
    val expiresAt: Long?,
    val passphraseHandle: PassphraseHandle  // either INLINE_ENCRYPTED or PROMPT_EACH_USE
)
```

**Decision (passphrase handling):** two modes per imported key:
- `INLINE_ENCRYPTED`: user types passphrase once at import; app
  decrypts the private key in-memory, re-encrypts the bytes with the
  Android Keystore-backed master key, persists the keystore-encrypted
  blob, and discards the user's passphrase. Sign operations use the
  master-key-decrypted blob. **No user prompt per commit.**
- `PROMPT_EACH_USE`: app keeps the original armored (passphrase-
  protected) blob and prompts the user for the passphrase before every
  signing operation. Suitable for users who treat their GPG key as
  high-value.

Default is `INLINE_ENCRYPTED` (matches "Claude editing schedules via
CLI" use case — no per-commit prompts). User can flip per key in
settings.

### Per-identity + per-repo signing-key picker

`identities/<person-id>.md` frontmatter gains optional
`signing_key_fingerprint = "<40-hex>"` for the identity's default key.

`RepoConfig` gains optional `signingKeyFingerprintOverride: String?`
for a per-repo override (e.g. user signs all personal-repo commits
with key A but work-repo commits with key B).

Resolution at commit time:
1. If `signingEnabled = false` for the repo → no signature.
2. Else if `RepoConfig.signingKeyFingerprintOverride != null` → use
   that key.
3. Else if `identity.signing_key_fingerprint != null` → use that key.
4. Else → no signature, log warning "signing enabled but no key
   selected — commit proceeds unsigned".

### JGit + BouncyCastle wiring

JGit 6.x exposes `GpgSigner.setDefault(signer)`. Our `BcGpgSigner`:

```kotlin
class BcGpgSigner(private val keyStore: SigningKeyStore) : GpgSigner() {
    override fun canLocateSigningKey(...): Boolean = ...
    override fun sign(commit: CommitBuilder, gpgSigningKey: String?,
                       committer: PersonIdent, credentialsProvider: CredentialsProvider?) {
        val key = keyStore.resolveForCommit(commit, gpgSigningKey)
        val secret = key.toBcSecretKey(...)            // decrypt with master key
        val sig = BcPgpSigner.sign(commit.toByteArray(), secret)
        commit.setGpgSignature(GpgSignature(sig))
    }
}
```

`GpgSigner.setDefault(BcGpgSigner(...))` is set in `App.onCreate`
**after** keystore init.

Algorithms supported by BC for PGP signing: RSA-2048+, ECDSA-256+,
Ed25519. We accept all three on import; reject DSA (legacy weak).

### Verified-author chip

When rendering an event/task tile, the UI looks up the authoring
commit's signature status:

- `unsigned` → no chip
- `signed_verified` → small green check badge in the corner, tooltip
  shows fingerprint and signer name
- `signed_unknown_key` → small grey check (signature is valid but we
  haven't imported the public key)
- `signed_bad` → small red X (signature failed verification — could
  indicate tampering or corruption)

The verification result is cached per-commit in Room (keyed by commit
SHA); cache invalidated when the user imports a new public key
(re-verify all `signed_unknown_key` entries against the new keyring).

### Public-key import (verification side)

Separate flow: `Settings → Identities → <other-person-id> → Public
signing keys → + Import public key`. File picker or paste accepts
armored `-----BEGIN PGP PUBLIC KEY BLOCK-----`. Multiple public keys
per identity supported (key rotation).

Public keys stored in `EncryptedSharedPreferences` under
`verify.gpg.<fingerprint>` with the bound `person-id`.

### Key revocation

User can remove an imported private key:
`Settings → Identities → <identity> → Signing keys → tap key →
Remove`. Confirmation modal: "removing this key means new commits
will not be signed with it. Existing signed commits are unaffected —
their signatures remain in the commit objects on the remote."

Removed keys are deleted from `EncryptedSharedPreferences`. No
revocation certificate is generated by the app (out of scope; users
who need a revocation cert do it on their main machine).

### SSH-signed commits (alternative path)

JGit 6.x supports `gpg.format = ssh` for SSH-key-based commit
signatures (the same scheme GitHub recently introduced). Our
`SshSigner` is a parallel `GpgSigner` subclass that:

- Reuses the user's existing on-device ed25519 git SSH key (Phase
  SE-C) when configured.
- Or accepts a separately-imported SSH signing key (so SSH transport
  key and signing key can differ).
- Writes the signature in OpenSSH SSHSIG format.
- Verifies others' SSH signatures via imported SSH public keys
  (`Settings → Identities → <other> → Public signing keys → SSH
  format`).

Per-identity / per-repo: signing-format selector is `none | gpg |
ssh`. **Decision (default):** when the user enables signing for the
first time on an identity that already has a device-stored SSH key,
the default offered is `ssh` (lower friction — no key import). GPG is
offered as an alternative for users with existing GPG keys.

### Default OFF everywhere

- `signingEnabled = false` for every new identity.
- `signingEnabled = false` for every new repo.
- CLI `skb event add` etc. produce unsigned commits by default.
- CLI `skb commit --sign` flag forces signing on a single commit even
  if disabled (for occasional verifiable commits).
- Wizard does not prompt for signing keys (zero new-user friction).
- `Settings → Identities → <identity> → Signing keys` is the only
  surface that mentions it.

### Phase tasks

- [ ] **SE-S.1** Add `bcpg-jdk18on` (BouncyCastle PGP, Apache-2.0
  effectively for our use, MIT-style) to `libs.versions.toml`
- [ ] **SE-S.2** `SigningKeyStore` over `EncryptedSharedPreferences`
- [ ] **SE-S.3** GPG private-key import: file picker + paste UI
- [ ] **SE-S.4** GPG private-key import: BC parse + validation +
  fingerprint display + user confirmation
- [ ] **SE-S.5** Passphrase modes: `INLINE_ENCRYPTED` vs
  `PROMPT_EACH_USE` selector
- [ ] **SE-S.6** `BcGpgSigner` + `GpgSigner.setDefault` wiring
- [ ] **SE-S.7** Per-identity `signing_key_fingerprint` frontmatter
  field (cross-ref `data-model.md` Round 2)
- [ ] **SE-S.8** Per-repo `signingKeyFingerprintOverride` in
  `RepoConfig`
- [ ] **SE-S.9** Resolution chain at commit time (repo-override →
  identity-default → unsigned-with-warning)
- [ ] **SE-S.10** GPG public-key import for verification
- [ ] **SE-S.11** Verified-author chip renderer (4 states: unsigned,
  verified, unknown-key, bad) — cross-ref `ui-spec.md` Round 2
- [ ] **SE-S.12** Per-commit signature verification cache in Room
- [ ] **SE-S.13** Key revocation flow (remove from prefs;
  confirmation modal)
- [ ] **SE-S.14** SSH-signing alternative: `SshSigner` implementation
- [ ] **SE-S.15** SSH-signing default-suggested when device has an
  existing ed25519 SSH key
- [ ] **SE-S.16** `skb commit --sign` CLI override flag
- [ ] **SE-S.17** Test: round-trip sign + verify via BC for RSA, ECDSA,
  Ed25519 (GPG format)
- [ ] **SE-S.18** Test: round-trip sign + verify SSHSIG format
- [ ] **SE-S.19** Test: signature on commit C remains verifiable after
  the private key is removed from the device

---

## Phase SE-T — ssh-agent forwarding (advanced)

Corresponds to `main.md` **GG** (Round 2 ssh-agent) and locks in
`decisions.md` **D.35**.

### Use case

Users on dev machines (Termux, Linux desktop ssh-mosh into the
device, ChromeOS Linux container) have an ssh-agent socket accessible
via `$SSH_AUTH_SOCK`. Letting JGit pull keys from the agent means:

- No need to copy an ed25519 key onto the device.
- Hardware-token-backed keys (YubiKey, etc.) plumbed through ssh-agent
  remain usable for git operations.
- Multiple keys served from one agent — JGit selects via the
  publickey-auth probe.

For pure mobile users (Pixel / phone-only / no agent): the existing
device-stored key (Phase SE-C) is the default. This phase is **purely
additive** for advanced users.

### Toggle

`Settings → Sync → SSH → "Use ssh-agent if available"`. Default OFF.
When ON:

1. App reads `System.getenv("SSH_AUTH_SOCK")`.
2. If unset or socket file does not exist → log warning "ssh-agent
   socket not found; falling back to device-stored key".
3. If set and socket reachable → configure
   `SshdSessionFactory.setAuthenticationKeySource(...)`.

The setting is per-app, not per-repo — ssh-agent either is or isn't
available on this device.

### `AuthenticationKeySource` implementation

apache-sshd ships an `AgentClient` that speaks the SSH-agent
protocol over a Unix domain socket. Wrap:

```kotlin
class SshAgentAuthSource(
    private val socketPath: String,
    private val fallback: DeviceKeyAuthSource
) : AuthenticationKeySource {
    override fun getKeys(session: ClientSession): List<KeyPair> {
        return try {
            val agent = SshAgentClient.connect(socketPath)
            agent.requestIdentities()
        } catch (e: IOException) {
            // socket vanished mid-session, or perms denied
            fallback.getKeys(session)
        }
    }
}
```

### Socket path discovery

- **Linux/macOS dev**: `$SSH_AUTH_SOCK` is the canonical env var. We
  read it at process start; it doesn't change at runtime.
- **Android device sessions**: `$SSH_AUTH_SOCK` is unset.
- **Termux**: ssh-agent runs inside Termux; user enables the toggle
  AND sets `SSH_AUTH_SOCK` in the Termux profile so the app can
  inherit it. (Termux launches the app with its env.)
- **GUI launches (Pixel launcher)**: env-var inheritance is not
  guaranteed. Fallback path is the device-stored key.

**Decision (no socket-path manual override in UI):** Surfacing a path
picker is a footgun (wrong paths cause silent fallback). v1 reads only
`SSH_AUTH_SOCK`. Users who need a non-standard path can set the env
var. v1.1 may add an override if there's demand.

### Fallback semantics

If agent fails mid-session (socket disappears, agent crashes), JGit's
auth retry will call the fallback key source on the next attempt.
User sees "auth failed → retrying with device key → succeeded" if the
device key works, or a clear "all keys exhausted" error if neither
works.

### Security note

The app never reads `id_rsa`-style files from disk — it only speaks
the agent protocol. This means:

- The agent's "confirm each use" prompt (`ssh-add -c`) works as
  intended — the user sees a confirm prompt on every git op when so
  configured.
- We can't accidentally leak a private key to the repo or to logs.

Logging: agent requests are logged at INFO with key fingerprints only,
never key material.

### Phase tasks

- [ ] **SE-T.1** `SshAgentAuthSource` implementation against
  apache-sshd's agent client
- [ ] **SE-T.2** `$SSH_AUTH_SOCK` reader at `App.onCreate`
- [ ] **SE-T.3** Fallback chain: agent → device key on socket-not-
  available or agent-failure
- [ ] **SE-T.4** Setting `Settings → Sync → SSH → Use ssh-agent` with
  default-off and status indicator ("Active: 3 keys served" /
  "Inactive: socket not found")
- [ ] **SE-T.5** Audit log of agent-key fingerprints used per session
- [ ] **SE-T.6** Test: agent serves 2 keys → JGit selects the
  matching one for the remote
- [ ] **SE-T.7** Test: agent absent → fallback to device key without
  error toast (silent fallback, only the indicator shows it)
- [ ] **SE-T.8** Documentation page in `Settings → Help → ssh-agent`
  with examples for Termux + Linux desktop

---

## Phase SE-U — Multi-branch support

Corresponds to `main.md` **JJ** (Round 2 multi-branch) and locks in
`decisions.md` **D.36**.

### Goal

A repo may have N branches. The app exposes the current branch in the
repo switcher, allows switching, allows creating/deleting branches,
and scopes every sync operation to the current branch. PR creation
remains a provider-side concern (deep-link out).

### Data-model surface

`RepoConfig` gains:

```kotlin
data class RepoConfig(
    // ...existing fields...
    val currentBranch: String,                  // e.g. "main", "claude/plan-2026-q3"
    val defaultBranch: String,                  // detected from origin/HEAD on clone
    val knownBranches: Set<String> = emptySet() // local + tracked-remote branch names
)
```

`currentBranch` and `defaultBranch` may differ — `defaultBranch` is
the remote-side default ("main" usually), `currentBranch` is what the
user currently has checked out locally.

### Default branch detection on clone

After `git clone`, JGit reports `origin/HEAD` symbolic ref. We read it
and set `defaultBranch` accordingly:

```kotlin
val origHead = repo.resolve("refs/remotes/origin/HEAD")
val targetRefName = repo.refDatabase.exactRef("refs/remotes/origin/HEAD")?.target?.name
val defaultBranch = targetRefName?.removePrefix("refs/remotes/origin/")
```

`currentBranch` is set to `defaultBranch` on clone. If `origin/HEAD`
is missing (some self-hosted setups), default to `main` then `master`
then the first branch seen, in that order, with a one-time warning.

### Branch switching

`Settings → Repos → <repo> → Branch` opens a picker:

- List shows local branches first (bold), then remote-tracking
  branches that have no local counterpart (italic + "remote only").
- Tap a branch → if uncommitted changes exist → `Stash and switch` /
  `Discard and switch` / `Cancel` modal. Stash is the default option.
- After confirm: `git stash -u` (if needed) → `git checkout <branch>`
  → update `RepoConfig.currentBranch` → invalidate Room index for the
  repo (HEAD changed) → trigger a rescan.

`skb branch switch <branch>` CLI mirrors the same logic with
`--stash` / `--discard` flags.

### Branch creation

`Settings → Repos → <repo> → Branch → + New branch` modal:

- Branch name (validated against `git check-ref-format`)
- "Base from": dropdown of existing branches (defaults to current)
- "Set as current after creation": checkbox (default ON)
- "Push immediately": checkbox (default ON when online)

On confirm: `git branch <name> <base>` → optionally checkout →
optionally push with `--set-upstream`.

CLI: `skb branch create <name> [--from BASE] [--no-checkout]
[--no-push]`.

### Branch deletion

`Settings → Repos → <repo> → Branch → tap branch → Delete`:

- Refuses to delete the current branch (must switch off first).
- Refuses to delete `main` (and `defaultBranch` if different).
- Two-step confirmation: "Delete branch 'X'?" with checkbox "Also
  delete on remote".
- On confirm: `git branch -D <name>` locally; if checkbox set, `git
  push origin --delete <name>`.

Reserved name list: `main`, `master`, the repo's `defaultBranch`. The
delete button is disabled (greyed) for these.

CLI: `skb branch delete <name> [--remote]`.

### Stash + reset path for uncommitted changes

The modal at switch time exposes three options:

1. **Stash (default):** `git stash push -u -m "skb pre-switch from
   <oldBranch> at <epoch>"`. The stash is per-app-managed; on switch
   back to `<oldBranch>`, the app offers "Restore stashed changes
   from <when>?". Stashes accumulate; `Settings → Repos → <repo> →
   Stashes` lists them with restore/drop.
2. **Discard:** `git checkout -- .` + `git clean -fd`. Modal warns
   "you will lose <N> file changes; this cannot be undone".
3. **Cancel:** abort the switch; user keeps the dirty state on the
   old branch.

### PR creation: out-of-app

When the user wants to merge a feature branch into `main`, the app
opens the provider's compare page in the system browser:

- GitHub: `https://github.com/<owner>/<repo>/compare/<base>...<head>`
- Forgejo: `<base-url>/<owner>/<repo>/compare/<base>...<head>`
- Gitea: same as Forgejo
- GitLab: `https://gitlab.com/<owner>/<repo>/-/merge_requests/new?merge_request[source_branch]=<head>&merge_request[target_branch]=<base>`

Provider detection: parsed from the configured remote URL host.

`Settings → Repos → <repo> → Branch → tap non-default branch → Open
PR` button surfaces this deep link.

CLI: `skb branch pr [--branch B] [--target main]` opens the same URL
in the device browser via Android intent.

**Decision (no in-app PR):** per D.40 and D.36, in-app PR creation
requires elevated OAuth scopes and provider-API divergence is
significant. Deep-link is sufficient for v1; v1.1 may revisit.

### Per-branch sync

Each branch maintains its own remote tracking ref. Sync operations
(`SyncService`) work on the **current** branch only:

- Fetch fetches all branches (one network call).
- Rebase rebases the current branch onto its remote tracking ref.
- Push pushes the current branch only.

Per-branch sync intervals are NOT a v1 thing (one interval per repo
remains). Per-branch sync state (`commitsAhead`, `commitsBehind`)
is persisted, so when the user switches branches the status badge
updates from cache without a network call.

### Branch in repo switcher

The top-bar repo switcher shows `<avatar> <repo-name>` on the primary
line and `<branch-name>` as a subdued caption when the current branch
is not the default branch. When on the default branch, the caption is
omitted (no visual noise for the common case).

### Sharing + branches

Per `notifications-sharing-import.md` NS-* (Round 2 extension):
the "shared with me" surfaces respect the user's current branch when
showing what's visible. If a shared collaborator has access to all
branches, the app does not surface this; if access is branch-scoped
(GitLab protected branches with maintainer-only), push failures
surface the normal read-only-repo handling.

### Phase tasks

- [ ] **SE-U.1** `RepoConfig` extension: `currentBranch`,
  `defaultBranch`, `knownBranches`
- [ ] **SE-U.2** Default-branch detection at clone time with
  `main` → `master` → first-branch fallback
- [ ] **SE-U.3** Branch picker UI (`Settings → Repos → <repo> →
  Branch`) — cross-ref `ui-spec.md` Round 2
- [ ] **SE-U.4** Stash / Discard / Cancel modal on dirty switch
- [ ] **SE-U.5** Stash management surface (list + restore + drop)
- [ ] **SE-U.6** Branch create modal (name validation, base selector,
  push-immediately checkbox)
- [ ] **SE-U.7** Branch delete with reserved-name guard + remote-delete
  checkbox
- [ ] **SE-U.8** Per-branch persistence of `commitsAhead`/`commitsBehind`
  in Room
- [ ] **SE-U.9** Branch indicator in top-bar repo switcher
- [ ] **SE-U.10** Deep-link PR creation per provider (GitHub, Forgejo,
  Gitea, GitLab) with URL templates
- [ ] **SE-U.11** CLI: `skb branch list|create|switch|delete|pr`
- [ ] **SE-U.12** Room-index invalidation on `currentBranch` change
- [ ] **SE-U.13** Sync engine: scope fetch/rebase/push to current
  branch
- [ ] **SE-U.14** Test: switch with dirty tree triggers stash modal;
  stash + restore round-trips file contents

---

## Phase SE-V — Updated v2 deferrals (post-Round 2)

This phase exists as a marker — it has no implementation tasks. Its
purpose is to record that the Round 2 expansion in `decisions.md`
D.23–D.40 moved several items from the original v2-deferred pile into
v1 scope. The updated deferral list is below this phase, replacing the
original deferral list verbatim. The "still deferred" entries each
keep a one-line rationale; the "moved to v1" entries point at the
phase that owns the work.

This phase ships when every other Round-2 phase (SE-Q through SE-U) is
green AND the deferral list below has been audited against the
implementation status.

### Phase tasks

- [ ] **SE-V.1** Audit "moved to v1" entries against shipped phases —
  every promoted item must have a green phase in this doc, in
  `data-model.md`, in `ui-spec.md`, in `notifications-sharing-import.md`,
  or in `cli-tooling.md` covering its mechanics
- [ ] **SE-V.2** Audit "still deferred v1.1" entries — each must have
  an issue or label in the project tracker (issue created during the
  v1 ship cycle, not now)
- [ ] **SE-V.3** Tick this phase when the Round 1 deferral list below
  has been replaced + audited

---

## Phase SE-W — Cross-cutting verification

Conflict-UI, sync-orchestration, and status-bar reporting all
generalize across Round 1 and Round 2 sources. This phase ensures the
generalizations actually hold across every sync path.

### Conflict UI across all sync paths

Four sync paths can produce a conflict in v1:

| Path | Conflict shape | Modal reuse |
|---|---|---|
| Git pull/rebase (Phase SE-J) | TOML+body 3-way diff on one entity file | Original modal |
| CalDAV pull (Phase SE-Q) | Same TOML+body 3-way diff; label "server" vs "mine" | Parameterized modal |
| Git LFS pointer (Phase SE-R) | Pointer-file diff; structured rows + blob thumbnails | Specialized variant |
| Signed-commit verification (Phase SE-S) | Not a 3-way diff; a "signature mismatch" banner on the entity | Specialized banner, no modal |

**Decision (single modal component):** the conflict modal is a
parameterized Compose `ConflictModal(state: ConflictModalState)` where
`state` carries the source label, the field rows (auto-merged for TOML
keys with disjoint changes), the body 3-way text, and the resolution
buttons. LFS pointer conflicts use the same modal with
`fieldsOnly = true` (no body editor).

Signature-mismatch is not a conflict — it's an integrity warning
rendered as a banner on the affected entity's detail screen with
"Trust this signature anyway" / "Investigate" actions. Not a modal.

### Sync orchestration: per-repo task queue

`SyncService` keeps:

- One job per repo (existing Phase SE-G).
- N jobs per repo (one per `CalDavMirror`, see Phase SE-Q.15).
- A global semaphore bounding concurrent network operations to
  `min(4, configuredMax)` to avoid hammering radio + battery
  (existing Phase SE-L invariant; reaffirmed here).

Fairness:
- Within a repo, git sync and CalDAV mirrors are queued in arrival
  order with priority `manual > scheduled > opportunistic`.
- Across repos, round-robin: each repo gets one slot per cycle until
  the semaphore is exhausted.

Starvation avoidance:
- A repo whose git sync has been queued > 5 minutes jumps the queue
  on the next slot (max-wait-time priority bump).

### Status reporting in top bar

The top-bar sync indicator combines three streams into one icon:

- Git sync state (existing): `idle | syncing | offline | error |
  ahead | behind | conflict`
- CalDAV mirror state (Round 2): `idle | syncing | offline | error |
  conflict` aggregated across all mirrors of the current repo
- LFS transfer state (Round 2): `idle | transferring | error`

Combined icon precedence (highest → lowest visual priority):

1. `error` on any stream → red sync icon with error count badge
2. `conflict` on any stream → orange sync icon with conflict count
3. `syncing` or `transferring` on any stream → animated rotating sync
   icon
4. `offline` on any stream → grey sync icon with offline badge
5. `behind` or `ahead` on git → blue sync icon with ↓N / ↑N count
6. else → idle (subtle icon)

Tap the indicator → opens a sheet with per-stream breakdown (git
status row + per-mirror row + per-transfer row). Each row has its own
action button (retry, resolve, view error).

### Phase tasks

- [ ] **SE-W.1** `ConflictModal` Compose component parameterized over
  source label + fieldsOnly flag — cross-ref `ui-spec.md` Round 2
- [ ] **SE-W.2** `ConflictModalState` data class covering git, CalDAV,
  and LFS-pointer cases
- [ ] **SE-W.3** Signature-mismatch banner component (NOT a modal)
- [ ] **SE-W.4** Sync semaphore confirmed to bound git + CalDAV + LFS
  combined to `min(4, configuredMax)`
- [ ] **SE-W.5** Round-robin queue across repos + arrival-order within
  repo + max-wait-time priority bump
- [ ] **SE-W.6** Combined top-bar status indicator with precedence
  ordering
- [ ] **SE-W.7** Tap-indicator detail sheet with per-stream rows
- [ ] **SE-W.8** Tests: 3 repos × 2 mirrors each × 1 in-flight LFS
  transfer — semaphore caps at 4; round-robin observed; no starvation
- [ ] **SE-W.9** Tests: error in CalDAV does not change top-bar icon
  to red unless the user's *current* repo has the erroring mirror —
  errors on inactive repos surface as a small unread-count dot, not a
  full-screen-warning state
- [ ] **SE-W.10** Audit: every Round-2 phase's conflict path uses
  `ConflictModal`; no ad-hoc conflict UI elsewhere

---

## Items deferred to v2

Updated for Round 2. Items previously deferred that are now in v1 are
marked **✅ MOVED TO v1** with a phase pointer. Items that remain
deferred are marked **⚠️ STILL DEFERRED v1.1** with rationale.

### ✅ Moved to v1 (Round 2 promotions)

- **✅ MOVED TO v1: Bidirectional CalDAV bridge.** Now in v1 per D.25.
  See **Phase SE-Q** above for full mechanics (libraries, discovery,
  mapping, conflict reuse, per-server matrix). Originally deferred as
  "CalDAV server-side bridge".
- **✅ MOVED TO v1: Git LFS.** Now in v1 per D.26. See **Phase SE-R**
  above (threshold routing, capability probe, auth piggyback, pointer
  conflicts, forward-only migration). Originally deferred as "Git LFS
  v2 ask".
- **✅ MOVED TO v1 (as optional capability): Signed commits.** Now in
  v1 per D.23 — demoted from "deferred required" to "v1 optional,
  default OFF". See **Phase SE-S** above (GPG + SSHSIG, per-identity
  and per-repo keys, verified-author chip, key revocation). Originally
  deferred as "server-side hooks for write attribution → GPG/SSH
  signing flows would be needed".
- **✅ MOVED TO v1: ssh-agent integration.** Now in v1 per D.35. See
  **Phase SE-T** above (advanced setting, default OFF, `SSH_AUTH_SOCK`
  reader, fallback chain). Originally deferred as "Android lacks
  Unix-socket ssh-agent" — Termux + dev-machine sessions DO have one,
  so the support is worth it for that user class.
- **✅ MOVED TO v1: Multi-branch support.** Now in v1 per D.36. See
  **Phase SE-U** above (branch picker, switch with stash, create/
  delete, per-branch sync, PR via deep-link). Originally deferred as
  "v1 hard-codes main".

### ⚠️ Still deferred v1.1 (with rationale)

- **⚠️ STILL DEFERRED v1.1: Semantic TOML auto-merge for
  key-disjoint changes.** The file model is designed so conflicts
  are rare; manual 3-way diff UI is sufficient for the volume v1
  will see. Adding semantic-merge needs a careful UX pass (when does
  the user need to see the auto-merge? what's the audit trail?) that
  isn't worth blocking v1 on. See `decisions.md` D.40 row "Semantic
  TOML auto-merge". (Original SE-I deferral preserved.)
- **⚠️ STILL DEFERRED v1.1: QR code for SSH public-key export.**
  Marginal value over clipboard + share-sheet, which already cover
  desktop handoff via cross-device clipboards (Pixel-to-Mac via
  iCloud/Universal Clipboard equivalents, KDE Connect, etc.). zxing
  dep cost (~600KB) not worth it. (Original SE-C deferral preserved;
  matches `decisions.md` D.40.)
- **⚠️ STILL DEFERRED v1.1: Branch-protection PR creation in-app.**
  Multi-branch support (Phase SE-U) covers everything except in-app
  PR submission. Provider-side compare-and-pull-request page via
  deep-link is adequate. In-app PR creation needs elevated OAuth
  scopes per provider (different across GitHub / GitLab / Forgejo /
  Gitea), each with its own API surface — significant matrix without
  proportional user benefit for v1. (Matches D.40.)
- **⚠️ STILL DEFERRED v1.1: Conscrypt for modernized TLS 1.3.**
  Modern OkHttp + minSdk 26 ships acceptable TLS 1.3. Revisit only
  if field reports show handshake failures against specific git
  hosts. (Original SE-D deferral preserved; matches D.40 "Conscrypt
  for older-Android TLS 1.3" rationale.)
- **⚠️ STILL DEFERRED v1.1: Submodules.** Still not supported. Repos
  with submodules fail to clone with a clear error. JGit's submodule
  story has working-tree-quirks on Android (recursive nested working
  trees + R8 keep rules + permissions); not worth it without demand.
  (Original deferral preserved.)
- **⚠️ STILL DEFERRED v1.1: In-app per-recipient deploy-key
  generation.** **Rejected** per D.40 — wrong trust model. The
  recipient should generate their own keypair on their own device;
  the originating user should never possess a recipient's private
  key.
- **⚠️ STILL DEFERRED v1.1: Custom semantic merge for
  `_local/snoozes.toml`.** Per D.34, snoozes use "latest wins per
  entry" auto-merge — already in v1; this entry is here for
  completeness to confirm we don't go beyond that simple rule.

---

---

## Cross-references back to `main.md`

| `main.md` phase | This doc's owning phases |
|---|---|
| **B.1** Vendor JGit + sshd | SE-A |
| **B.2** `GitRepo` abstraction | SE-B |
| **B.3** `RepoStore` | SE-F |
| **B.4** SSH keypair + storage | SE-C |
| **B.5** GitHub OAuth Device Flow | SE-E |
| **B.6** Forgejo OAuth Device Flow | SE-E |
| **B.7** Manual PAT entry | SE-E, SE-F |
| **B.8** Network state monitor | SE-L |
| **B.9** Robolectric tests | SE-O |
| **D.3** Incremental indexer hooks | SE-N |
| **J.1** `SyncService` | SE-G |
| **J.2** Sync scheduler | SE-H |
| **J.3** Manual sync button | SE-H |
| **J.4** Sync status persistence | SE-I, SE-L |
| **J.5** Conflict detection | SE-J |
| **J.6** Conflict resolution UI (data side) | SE-J |
| **J.7** Read-only handling | SE-K |
| **J.8** Sync result toasts | SE-L |
| **V.2** Sync-small-repo perf budget | SE-O (perf tests) |
| **W.6** R8 keep rules for JGit | SE-A |

---

## Status footer

When every checkbox in every phase above is ticked AND the corresponding
`main.md` phases (B.1–B.9 and J.1–J.8) are all green, change the
top-of-file status to:

`## Status: ✅ DONE`

Until then, every shipped sub-step gets `- [x]` and a `shipped in change
<jj-change-id>` annotation on its phase header at the moment the change
lands. Use `jj log -r @ -T 'change_id.short()'` for the ID; do not use
git commit hashes (this is a `jj` repo per the global CLAUDE.md
convention if `.jj/` exists; otherwise commit hashes are fine but jj is
preferred for stability across rebases).
