import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

// Capture build-time metadata for the splash / About screen. Mirrors
// the tonearmboy pattern: GIT_SHA + BUILD_DATE emitted as BuildConfig
// constants. Falls back to sentinels if git is unavailable (e.g.
// tarball build).
val gitShortSha: String =
  runCatching {
      val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
      proc.waitFor()
      proc.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
    }
    .getOrDefault("unknown")

val buildDateUtc: String = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC))

android {
    namespace = "com.eight87.strictlykeptboy"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.eight87.strictlykeptboy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDateUtc\"")
    }

    // Phase W.1 — release signing config. Env-var driven: when the three
    // STRICTLYKEPTBOY_RELEASE_* vars are set, the release buildType signs
    // with the user's keystore; otherwise the release build falls back to
    // the debug keystore (same as `assembleDebug`) so personal sideload
    // through Obtainium still works end-to-end without ceremony.
    val releaseKeystorePath: String? = System.getenv("STRICTLYKEPTBOY_RELEASE_KEYSTORE")
    val releaseKeyAlias: String? = System.getenv("STRICTLYKEPTBOY_RELEASE_KEY_ALIAS")
    val releaseKeyPassword: String? = System.getenv("STRICTLYKEPTBOY_RELEASE_KEY_PASSWORD")
    val hasReleaseSigning =
        !releaseKeystorePath.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank() &&
            file(releaseKeystorePath!!).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeyPassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Phase W.6 — R8/minify locked ON for release builds going forward.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // F67 (refactor-solid.md) — kotlinx-coroutines-test's
                // `UncaughtExceptionsBeforeTest` accumulator persists across
                // test classes inside one Gradle worker JVM, producing a
                // migratory canary (whichever runTest-based test runs after
                // an upstream coroutine leak fails with "There were uncaught
                // exceptions before the test started"). Forking a fresh JVM
                // periodically resets the accumulator. forkEvery = 100 is
                // the measured sweet spot: green under repeated runs (~55s)
                // without paying the per-class JVM startup cost (~6m wall
                // clock at forkEvery = 1). If the suite grows past ~250
                // test classes this may need raising to avoid the
                // accumulator filling within a single fork window.
                it.forkEvery = 100
            }
        }
    }

    // Round 2.20 Phase B — the bundled rich-demo repo at
    // `assets/rich-demo-repo/` contains dot-prefixed paths
    // (`.strictlykeptboy/repo.toml`, etc.) that are LOAD-BEARING for the
    // produced-repo schema (per CLAUDE.md §D.3). aapt2's default ignore
    // pattern (`!.*`) drops those silently; relax it to keep VCS-style
    // junk out (`.svn`, `.git`, thumbs.db, …) while preserving our
    // dotfiles. RichDemoManifestCoverageTest fails if anything we ship
    // doesn't make it through aapt2.
    androidResources {
        ignoreAssetsPatterns += listOf(
            "<dir>_*", "<dir>CVS", "<dir>thumbs.db", "<dir>picasa.ini",
            "<file>*.scc",
            // Drop SCM dirs by exact name — NOT the `!.*` blanket.
            "<dir>.svn", "<dir>.git", "<dir>.hg", "<dir>.bzr",
            "<file>.DS_Store", "<file>thumbs.db", "<file>picasa.ini",
            "<file>*~",
        )
    }

    // Phase W.6 — Lint's `Instantiatable` check sees a stale class graph
    // when R8 runs in the same Gradle invocation (KSP-generated classes
    // + multi-module classpath ordering). MainActivity and
    // SkbCarAppService both legitimately subclass the required base
    // classes; the false-positive blocks `assembleRelease`. The keep
    // rules in proguard-rules.pro ensure both survive minification.
    lint {
        disable += "Instantiatable"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // JGit ships OSGi metadata in every module — Android doesn't run an
        // OSGi container so the duplicates are harmless. Pick first wins.
        pickFirsts += "OSGI-INF/l10n/plugin.properties"
        pickFirsts += "OSGI-INF/l10n/plugin.properties.MF"
        pickFirsts += "plugin.properties"
        pickFirsts += "about.html"
        // jgit ships duplicate notice/license files across its modules.
        excludes += "/META-INF/NOTICE*"
        excludes += "/META-INF/LICENSE*"
        excludes += "/META-INF/DEPENDENCIES"
        excludes += "/META-INF/INDEX.LIST"
        excludes += "/META-INF/*.kotlin_module"
        excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  // Compose UI tests on Robolectric for headless unit tests.
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.compose.ui.test.manifest)

  // Phase B — Git layer + transport
  implementation(libs.jgit.core) {
    // No ssh-agent on Android; we generate keys per-remote.
    exclude(group = "org.eclipse.jgit", module = "org.eclipse.jgit.ssh.apache.agent")
    // Bring our own slf4j binding (slf4j-android → logcat).
    exclude(group = "org.slf4j", module = "slf4j-api")
  }
  implementation(libs.jgit.ssh.apache) {
    exclude(group = "org.eclipse.jgit", module = "org.eclipse.jgit.ssh.apache.agent")
    exclude(group = "org.slf4j", module = "slf4j-api")
    // sshd-osgi is an umbrella that re-bundles sshd-core/common — keeping
    // both produces dex duplicate-class errors. sshd-cli pulls Spring transitively
    // (jcl-over-slf4j vs spring-jcl commons-logging dup). Both are useless on
    // Android (no CLI surface, no OSGi container) — strip them.
    exclude(group = "org.apache.sshd", module = "sshd-osgi")
    exclude(group = "org.apache.sshd", module = "sshd-cli")
    exclude(group = "org.apache.sshd", module = "sshd-putty")
    exclude(group = "org.apache.sshd", module = "sshd-mina")
    exclude(group = "org.apache.sshd", module = "sshd-netty")
    exclude(group = "org.springframework", module = "spring-jcl")
    exclude(group = "org.springframework.integration", module = "spring-integration-core")
  }
  implementation(libs.bouncycastle.prov)
  implementation(libs.bouncycastle.pkix)
  implementation(libs.okhttp)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.slf4j.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.datetime)
  // Phase C — ktoml dependency registered per D.12. The store layer currently
  // uses a small purpose-built TOML reader/writer (see store/TomlReader.kt) to
  // avoid ktoml's internal-tree-node coupling; the dep is wired so future
  // schema work (e.g. comment preservation, v2 migrations) can adopt it.
  implementation(libs.ktoml.core)

  // Phase D — Room cache + indexer (D.8 read-through cache)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.androidx.room.testing)
  // Robolectric runs on the host JVM. The default Android variant of
  // androidx.sqlite:sqlite-bundled ships only the Android NDK .so; the
  // -jvm variant ships host-OS shared libs (linux-x64, macos-arm64, …).
  testImplementation(libs.androidx.sqlite.bundled)
  testRuntimeOnly("androidx.sqlite:sqlite-bundled-jvm:2.5.0")

  // Phase E — resolver (RRULE expansion per D.5)
  implementation(libs.lib.recur)

  // Phase Q — Android Auto (CarAppService template surface, D.17 / UI-S).
  implementation(libs.androidx.car.app)
  testImplementation(libs.androidx.car.app.testing)

  // Phase EE — inline-markdown body styling (D.31 / UI-Y).
  // Markwon (Apache-2.0). Bridged into Compose via AndroidView in
  // `ui/components/MarkdownRenderer.kt`.
  implementation(libs.markwon.core)
  implementation(libs.markwon.ext.strikethrough)
  implementation(libs.markwon.ext.tables)
  implementation(libs.markwon.html)
  implementation(libs.markwon.linkify)

  // Phase RR — ZXing core for QR code generation in the share sheet (Apache-2.0).
  implementation(libs.zxing.core)

  // Round 2.17 Phase F — Apache Commons Compress (Apache-2.0).
  // JGit does NOT pull commons-compress in transitively (verified in 2.17.A.1);
  // wired explicitly so Phase F's BackupArchiver has Tar/Gzip streams.
  implementation(libs.commons.compress)

  // Local tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.okhttp.mockwebserver)

  // Instrumented tests
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}

// TR-A.2 — `translations:audit` task. Diffs every
// `values-<bcp47>/strings.xml` against canonical `values/strings.xml`
// and prints (a) keys missing from the locale + (b) orphan keys in the
// locale that are absent from canonical. Output goes to stdout via the
// Gradle logger at lifecycle level. Depends on `:app:preBuild` so the
// resource roots are resolved before the diff runs.
tasks.register("translationsAudit") {
    group = "verification"
    description = "Audit values-<bcp47>/strings.xml against canonical values/strings.xml: missing + orphan keys."
    dependsOn("preBuild")
    val resDir = file("src/main/res")
    doLast {
        val canonical = File(resDir, "values/strings.xml")
        require(canonical.isFile) { "canonical strings.xml not found at $canonical" }

        // Parse `name` attributes for both <string ...> and <plurals ...> entries.
        val keyRegex = Regex("""<(?:string|plurals)\s+[^>]*name="([^"]+)"""")
        fun keysOf(file: File): Set<String> =
            keyRegex.findAll(file.readText()).map { it.groupValues[1] }.toSortedSet()

        val canonicalKeys = keysOf(canonical)
        logger.lifecycle("translations:audit — canonical has ${canonicalKeys.size} keys (values/strings.xml)")

        val localeDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (localeDirs.isEmpty()) {
            logger.lifecycle("  (no locale variant directories found)")
            return@doLast
        }

        for (dir in localeDirs) {
            val bcp47 = dir.name.removePrefix("values-")
            val localeFile = File(dir, "strings.xml")
            if (!localeFile.isFile) {
                logger.lifecycle("  $bcp47: no strings.xml (skipped)")
                continue
            }
            val localeKeys = keysOf(localeFile)
            val missing = (canonicalKeys - localeKeys).toSortedSet()
            val orphan = (localeKeys - canonicalKeys).toSortedSet()
            val translated = localeKeys.size - orphan.size
            val pct = if (canonicalKeys.isEmpty()) 0
                else (translated * 100 / canonicalKeys.size)
            logger.lifecycle("  $bcp47: ${localeKeys.size} keys / $pct% coverage ($translated of ${canonicalKeys.size})")
            if (missing.isNotEmpty()) {
                logger.lifecycle("    missing (fall back to canonical English, ${missing.size}):")
                missing.forEach { logger.lifecycle("      - $it") }
            }
            if (orphan.isNotEmpty()) {
                logger.lifecycle("    orphan / stale (in locale but not canonical, ${orphan.size}):")
                orphan.forEach { logger.lifecycle("      ! $it") }
            }
            if (missing.isEmpty() && orphan.isEmpty()) {
                logger.lifecycle("    clean — 100% coverage, no orphans")
            }
        }
    }
}

// [L] #19 (refactor-solid.md, audit-pass-2026-05-17) — resolver purity guard.
// Walks `resolver/` and fails the build if any .kt file imports impure
// packages (java.io.*, java.nio.*, okhttp3.*, org.eclipse.jgit.*,
// androidx.room.*, android.* except android.util.Log) or calls a wall-clock
// .now() / System.currentTimeMillis(). The resolver layer must be pure:
// clocks + I/O are injected from composition, never reached for in-line.
// Hooked into `check` so `./gradlew check` (and CI) runs it automatically.
tasks.register("resolverPurityCheck") {
    group = "verification"
    description = "Fail the build if resolver/ imports impure packages or calls wall-clock .now()."
    val resolverDir = file("src/main/java/com/eight87/strictlykeptboy/resolver")
    doLast {
        if (!resolverDir.isDirectory) {
            logger.lifecycle("resolverPurityCheck: $resolverDir not found — skipping.")
            return@doLast
        }

        // Forbidden import prefixes. `android.util.Log` is the one allowed
        // android.* import (logging is acceptable; see CLAUDE.md).
        val forbiddenImportRegex = Regex(
            """^\s*import\s+(java\.io\.|java\.nio\.|okhttp3\.|org\.eclipse\.jgit\.|androidx\.room\.|android\.)"""
        )
        val androidLogAllow = Regex("""^\s*import\s+android\.util\.Log(\s*$|\s*;)""")

        // Wall-clock leaks — these MUST be injected, never called inline.
        val clockLeakRegex = Regex(
            """\b(LocalDate|LocalDateTime|ZonedDateTime|Instant)\.now\s*\(|\bSystem\.currentTimeMillis\s*\("""
        )

        data class Hit(val file: File, val line: Int, val text: String, val reason: String)
        val hits = mutableListOf<Hit>()

        resolverDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            f.useLines { seq ->
                seq.forEachIndexed { idx, raw ->
                    val line = raw
                    val lineNo = idx + 1
                    if (forbiddenImportRegex.containsMatchIn(line) && !androidLogAllow.containsMatchIn(line)) {
                        hits += Hit(f, lineNo, line.trim(), "forbidden import")
                    }
                    if (clockLeakRegex.containsMatchIn(line)) {
                        hits += Hit(f, lineNo, line.trim(), "wall-clock leak (inject `now` instead)")
                    }
                }
            }
        }

        if (hits.isNotEmpty()) {
            val msg = buildString {
                appendLine("resolverPurityCheck: found ${hits.size} purity violation(s) in resolver/:")
                hits.forEach { h ->
                    appendLine("  ${h.file.relativeTo(rootDir)}:${h.line}  [${h.reason}]  ${h.text}")
                }
                appendLine()
                appendLine("The resolver layer must be pure: no java.io/nio, no JGit/OkHttp/Room,")
                appendLine("no android.* (except android.util.Log), and no wall-clock .now() calls.")
                appendLine("Inject Path/Clock/now from the composition root instead.")
                appendLine("See docs/plans/refactor-solid.md audit pass 2026-05-17, finding #19.")
            }
            throw GradleException(msg)
        }
        logger.lifecycle("resolverPurityCheck: clean — resolver/ has no impure imports or wall-clock leaks.")
    }
}

tasks.named("check") {
    dependsOn("resolverPurityCheck")
}

// Round 2.20 Phase B.5 — authoring helper that regenerates
// `app/src/main/assets/rich-demo-repo/_manifest.txt`. NOT wired into
// the build graph; invoke manually after editing rich-demo content:
//
//   ./gradlew :app:regenerateRichDemoManifest
//
// The runtime seeder reads this manifest because AssetManager.list()
// is non-recursive. `RichDemoManifestCoverageTest` fails the unit
// suite if the manifest drifts from the asset tree.
val regenerateRichDemoManifest = tasks.register("regenerateRichDemoManifest") {
    group = "build setup"
    description = "Regenerate app/src/main/assets/rich-demo-repo/_manifest.txt by walking the asset tree."
    val assetRoot = file("src/main/assets/rich-demo-repo")
    val manifestOutput = File(assetRoot, "_manifest.txt")
    inputs.files(
        fileTree(assetRoot) { exclude("_manifest.txt") },
    ).withPropertyName("richDemoAssets").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(manifestOutput).withPropertyName("manifest")
    doLast {
        require(assetRoot.isDirectory) { "rich-demo asset dir missing: $assetRoot" }
        val rootPath = assetRoot.toPath()
        val manifest = File(assetRoot, "_manifest.txt")
        // Exclude the manifest itself from the walk so its hash doesn't
        // depend on its previous contents (chicken-and-egg).
        val entries = assetRoot
            .walkTopDown()
            .filter { it.isFile && it != manifest }
            .map { rootPath.relativize(it.toPath()).toString().replace('\\', '/') }
            .toSortedSet()
        // Content hash of (sorted relative path + SHA-256 of file contents) for
        // every entry. Runtime seeder uses this as the idempotency key so any
        // change to the bundled demo auto-invalidates the seeded flag on the
        // next install — no manual KEY_SEEDED bump required.
        val md = MessageDigest.getInstance("SHA-256")
        for (rel in entries) {
            md.update(rel.toByteArray(Charsets.UTF_8))
            md.update(0.toByte())
            md.update(File(assetRoot, rel).readBytes())
            md.update(0.toByte())
        }
        val contentHash = md.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        val lines = buildList {
            add("# content-hash: $contentHash")
            addAll(entries)
        }
        manifest.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        logger.lifecycle("regenerated ${manifest.relativeTo(rootDir)} (${entries.size} entries, hash=${contentHash.take(12)}…)")
    }
}

// Auto-run the manifest regeneration before assets are packaged so the
// bundled `_manifest.txt` (and its content-hash header) always reflects
// the current asset tree. The RichDemoSeeder keys its seeded-flag on
// that hash, so any change to the demo content auto-invalidates the
// on-device seed without a manual KEY_SEEDED bump.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(regenerateRichDemoManifest)
}
tasks.matching { it.name.startsWith("package") && (it.name.endsWith("Resources") || it.name.endsWith("Assets")) }.configureEach {
    dependsOn(regenerateRichDemoManifest)
}
