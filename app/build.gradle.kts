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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
