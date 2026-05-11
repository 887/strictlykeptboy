import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  application
}

// Stamp the same GIT_SHA + BUILD_DATE the app uses, so `skb --version`
// matches the APK's About screen line-for-line.
val gitShortSha: String =
  runCatching {
      val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
      proc.waitFor()
      proc.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
    }
    .getOrDefault("unknown")
val buildDateUtc: String = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC))
val cliVersion = "0.1.0"

application {
  mainClass.set("com.eight87.skb.cli.MainKt")
  applicationName = "skb"
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.clikt)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
}

// Generate BuildInfo.kt under build/generated/ so the CLI has the same
// version stamp pattern as the Android BuildConfig.
val generateBuildInfo by tasks.registering {
  val outDir = layout.buildDirectory.dir("generated/source/buildinfo/kotlin")
  outputs.dir(outDir)
  doLast {
    val pkgDir = outDir.get().asFile.resolve("com/eight87/skb/cli")
    pkgDir.mkdirs()
    pkgDir.resolve("BuildInfo.kt").writeText(
      """
      package com.eight87.skb.cli

      object BuildInfo {
        const val VERSION = "$cliVersion"
        const val GIT_SHA = "$gitShortSha"
        const val BUILD_DATE = "$buildDateUtc"
      }
      """.trimIndent() + "\n"
    )
  }
}

kotlin.sourceSets["main"].kotlin.srcDir(generateBuildInfo)

// Fat JAR — single self-contained artifact, no shadow plugin needed.
// Output: cli/build/libs/skb-cli-<version>-<sha>.jar
val fatJar = tasks.register<Jar>("fatJar") {
  dependsOn(tasks.classes)
  archiveBaseName.set("skb-cli")
  archiveVersion.set("$cliVersion-$gitShortSha")
  archiveClassifier.set("")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  manifest {
    attributes(
      "Main-Class" to "com.eight87.skb.cli.MainKt",
      "Implementation-Version" to cliVersion,
      "Implementation-Git-Sha" to gitShortSha,
      "Implementation-Build-Date" to buildDateUtc,
    )
  }
  from(sourceSets.main.get().output)
  from({
    configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
  }) {
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
  }
}

tasks.named("assemble") { dependsOn(fatJar) }
