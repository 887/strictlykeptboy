# Phase W.6 — narrowed R8/ProGuard keep rules.
#
# Strategy: keep only the packages we actually touch reflectively or
# via ServiceLoader. JGit's broad `org.eclipse.jgit.**` blanket has
# been split into the five sub-packages we depend on (lib / api /
# transport / errors / storage). Modules we explicitly excluded in
# app/build.gradle.kts (sshd-cli, sshd-putty, sshd-osgi, jgit-pgm, …)
# don't need keep rules; their classes aren't on the classpath.
#
# isMinifyEnabled + isShrinkResources are ON in release. If R8 strips
# something at runtime, surface the missing rule + add it here.

# --- JGit core surface we actually call into ---------------------------------
# org.eclipse.jgit.lib.* — Ref, RefDatabase, ObjectId, PersonIdent,
# Repository, Config, BatchRefUpdate, hooks. ServiceLoader entries
# (Hooks, RefStorage) live under META-INF/services/org.eclipse.jgit.*.
-keep class org.eclipse.jgit.lib.** { *; }
-keepclassmembers class org.eclipse.jgit.lib.** { *; }

# org.eclipse.jgit.api.* — Git, CloneCommand, PushCommand, FetchCommand,
# PullCommand, CommitCommand, StatusCommand, DiffCommand.
-keep class org.eclipse.jgit.api.** { *; }
-keepclassmembers class org.eclipse.jgit.api.** { *; }

# org.eclipse.jgit.transport.* — RemoteConfig, RefSpec, URIish, SshTransport,
# CredentialsProvider, SshSessionFactory. JGit picks the transport via
# `ServiceLoader<TransportProtocol>`; the impls live in this package.
-keep class org.eclipse.jgit.transport.** { *; }
-keepclassmembers class org.eclipse.jgit.transport.** { *; }

# org.eclipse.jgit.errors.* — JGit wraps low-level IO/transport failures
# in a stable exception hierarchy. We catch by concrete subtypes (e.g.
# `TransportException`, `MissingObjectException`) and pattern-match in
# `GitRepo`. R8 must not collapse these subtypes into the parent.
-keep class org.eclipse.jgit.errors.** { *; }
-keepclassmembers class org.eclipse.jgit.errors.** { *; }

# org.eclipse.jgit.storage.* — pack / file / dfs storage descriptors.
# JGit instantiates these by classname from its service config.
-keep class org.eclipse.jgit.storage.** { *; }
-keepclassmembers class org.eclipse.jgit.storage.** { *; }

# --- SSH transport ----------------------------------------------------------
# apache-sshd-osgi: reflective service discovery for ciphers, KEX,
# signature factories. Keep the public API + impl packages.
-keep class org.apache.sshd.client.** { *; }
-keep class org.apache.sshd.common.** { *; }
-keep class org.eclipse.jgit.internal.transport.sshd.** { *; }
-keepclassmembers class org.apache.sshd.client.** { *; }
-keepclassmembers class org.apache.sshd.common.** { *; }

# --- BouncyCastle -----------------------------------------------------------
# Service-loader driven JCE provider. R8 can dead-code-elim individual
# ciphers since we don't call them by name from Kotlin — `Security.addProvider`
# wires them via reflection.
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }

# --- dmfs lib-recur ---------------------------------------------------------
# Phase E.2 recurrence materializer uses lib-recur's RRULE parser; the
# parser instantiates iterators reflectively keyed on RFC5545 parts.
-keep class org.dmfs.rfc5545.recur.** { *; }
-keepclassmembers class org.dmfs.rfc5545.recur.** { *; }

# --- ktoml ------------------------------------------------------------------
# Phase C registered ktoml for future v2 schema work. ktoml's parser
# uses reflection over its internal tree node hierarchy.
-keep class com.akuleshov7.ktoml.** { *; }
-keepclassmembers class com.akuleshov7.ktoml.** { *; }

# --- kotlinx.serialization --------------------------------------------------
# Generated $$serializer companions are kept by the compiler plugin's
# default rules, but the JSON format reflectively reads @Serializable
# annotations. Belt + suspenders.
-keepattributes RuntimeVisibleAnnotations
-keep,includedescriptorclasses class com.eight87.strictlykeptboy.**$$serializer { *; }
-keepclassmembers class com.eight87.strictlykeptboy.** {
    *** Companion;
}
-keepclasseswithmembers class com.eight87.strictlykeptboy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Application entry points -----------------------------------------------
# Activities, Services, BroadcastReceivers, ContentProviders and the
# Application class are instantiated by name from the manifest. The
# `proguard-android-optimize.txt` base file keeps these for the Android
# packages, but our own subclasses must be pinned explicitly so R8
# doesn't drop their no-arg constructors.
-keep public class com.eight87.strictlykeptboy.SkbApp { *; }
-keep public class com.eight87.strictlykeptboy.MainActivity { *; }
-keep public class com.eight87.strictlykeptboy.** extends android.app.Activity { *; }
-keep public class com.eight87.strictlykeptboy.** extends android.app.Service { *; }
-keep public class com.eight87.strictlykeptboy.** extends android.content.BroadcastReceiver { *; }
-keep public class com.eight87.strictlykeptboy.** extends android.content.ContentProvider { *; }
-keep public class com.eight87.strictlykeptboy.** extends androidx.car.app.CarAppService { *; }

# --- ServiceLoader providers ------------------------------------------------
# JGit + sshd-osgi + BouncyCastle all use META-INF/services.
-keep class META-INF.services.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# --- Suppress missing-class warnings for excluded transitives ---------------
# We exclude sshd-cli / sshd-osgi umbrella / sshd-mina / sshd-netty /
# sshd-putty / spring-jcl / sshd-sftp / jcl-over-slf4j at the dependency
# level (app/build.gradle.kts). R8 still scans the kept JGit classes
# and finds references to those types in unreachable code paths
# (SFTP subsystem, GSSAPI Kerberos auth, ssh-agent, mina/netty IO).
# None of these execute on Android — we only do JGit-over-SSH client
# auth via apache-sshd-client + apache-sshd-common, and HTTPS via OkHttp.
-dontwarn org.apache.sshd.**
# JVM-only JMX (java.lang.management + javax.management) — JGit's
# Monitoring.registerMBean is unreachable on Android.
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn java.lang.ProcessHandle
# Servlet / Spring / non-Android container hooks JGit references.
-dontwarn javax.servlet.**
-dontwarn jakarta.servlet.**
-dontwarn org.springframework.**
# Kerberos/GSSAPI not present on Android; JGit ssh has unreachable
# refs to `org.ietf.jgss.*` and `javax.security.auth.kerberos.*`.
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.kerberos.**
# JGit references some apache-sshd common/io internals from code paths
# that get tree-shaken once Android transport selectors discard them,
# but R8's reachability analysis still flags the references.
-dontwarn org.apache.sshd.common.io.**
-dontwarn org.apache.sshd.common.kex.**
-dontwarn org.apache.sshd.common.session.**
-dontwarn org.apache.sshd.common.keyprovider.**
-dontwarn org.apache.sshd.common.mac.**
-dontwarn org.apache.sshd.common.signature.**
-dontwarn org.apache.sshd.common.util.**
-dontwarn org.apache.sshd.common.config.**
-dontwarn org.apache.sshd.core.**
-dontwarn org.apache.sshd.client.config.**
-dontwarn org.apache.sshd.client.session.**
-dontwarn org.apache.sshd.client.future.**
-dontwarn org.eclipse.jgit.internal.transport.sshd.agent.**
-dontwarn org.eclipse.jgit.internal.transport.sshd.pkcs11.**
# JGit ships an LFS module we don't ship; refs leak through.
-dontwarn org.eclipse.jgit.lfs.**
# org.slf4j shipped via slf4j-android — third-party log frameworks
# referenced by jgit/bc/sshd internals are unreachable on Android.
-dontwarn org.slf4j.impl.**
# Conscrypt / Tink / other JCE providers may be referenced by sshd security utils.
-dontwarn javax.naming.**
-dontwarn java.beans.**
