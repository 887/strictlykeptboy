package com.eight87.strictlykeptboy.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase W.6 — assert `app/proguard-rules.pro` keeps the JGit /
 * lib-recur / ktoml / SSHd / BouncyCastle reflection paths that R8
 * would otherwise strip in a minified release build.
 *
 * Scoped to the static rules file rather than the merged APK because
 * the merged R8 trace lives at `app/build/outputs/mapping/release/`
 * and is only produced after `assembleRelease`. That's a Phase-W AVD
 * smoke step, not a unit-test step. This test catches regressions
 * where someone re-broadens or forgets a load-bearing keep rule.
 */
class ProguardKeepRulesTest {

    private val rules: String = run {
        val candidates = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro"),
            File("../app/proguard-rules.pro"),
        )
        val resolved = candidates.firstOrNull { it.exists() }
            ?: error("could not locate proguard-rules.pro from ${File(".").absolutePath}")
        resolved.readText()
    }

    @Test
    fun keeps_jgit_api_package() {
        assertTrue(
            "org.eclipse.jgit.api.** must be kept (Git, CloneCommand, …)",
            rules.contains("-keep class org.eclipse.jgit.api."),
        )
    }

    @Test
    fun keeps_jgit_lib_package() {
        assertTrue(
            "org.eclipse.jgit.lib.** must be kept (Repository, Ref, ObjectId, …)",
            rules.contains("-keep class org.eclipse.jgit.lib."),
        )
    }

    @Test
    fun keeps_jgit_transport_package() {
        assertTrue(
            "org.eclipse.jgit.transport.** must be kept (TransportProtocol ServiceLoader entries)",
            rules.contains("-keep class org.eclipse.jgit.transport."),
        )
    }

    @Test
    fun keeps_jgit_errors_package() {
        // GitRepo pattern-matches on concrete exception subtypes; R8 must
        // not collapse them.
        assertTrue(
            "org.eclipse.jgit.errors.** must be kept",
            rules.contains("-keep class org.eclipse.jgit.errors."),
        )
    }

    @Test
    fun keeps_jgit_storage_package() {
        assertTrue(
            "org.eclipse.jgit.storage.** must be kept",
            rules.contains("-keep class org.eclipse.jgit.storage."),
        )
    }

    @Test
    fun keeps_lib_recur_rrule_engine() {
        // dmfs lib-recur reflects on RFC5545 part classes when parsing.
        assertTrue(
            "org.dmfs.rfc5545.recur.** must be kept for RRULE expansion",
            rules.contains("org.dmfs.rfc5545.recur."),
        )
    }

    @Test
    fun keeps_ktoml_parser() {
        assertTrue(
            "com.akuleshov7.ktoml.** must be kept",
            rules.contains("com.akuleshov7.ktoml."),
        )
    }

    @Test
    fun keeps_bouncycastle_provider() {
        assertTrue(
            "org.bouncycastle.** must be kept (JCE provider, reflective init)",
            rules.contains("org.bouncycastle."),
        )
    }

    @Test
    fun keeps_apache_sshd_transport() {
        assertTrue(
            "org.apache.sshd.client.** must be kept",
            rules.contains("org.apache.sshd.client."),
        )
        assertTrue(
            "org.apache.sshd.common.** must be kept",
            rules.contains("org.apache.sshd.common."),
        )
    }

    @Test
    fun keeps_service_loader_descriptors() {
        assertTrue(
            "META-INF/services entries must survive R8",
            rules.contains("META-INF.services"),
        )
    }

    @Test
    fun does_not_keep_unused_jgit_pgm_package() {
        // pgm is explicitly excluded at the dependency level; keep rules
        // for it would be dead weight. Asserting absence catches the
        // "broaden by reflex" regression.
        assertFalse(
            "org.eclipse.jgit.pgm.** is excluded at dep level; keep rule is dead weight",
            rules.contains("-keep class org.eclipse.jgit.pgm."),
        )
    }

    @Test
    fun keeps_kotlinx_serializer_companions() {
        assertTrue(
            "kotlinx.serialization \$\$serializer companions must be kept",
            rules.contains("\$\$serializer"),
        )
    }
}
