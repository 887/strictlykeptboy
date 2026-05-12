# Phase B — Git layer keep rules.
# Narrow these in Phase V.5 once we know which symbols R8 actually drops.

# JGit relies heavily on reflection + ServiceLoader for ref-storage, hooks,
# transport providers, etc.
-keep class org.eclipse.jgit.** { *; }
-keepclassmembers class org.eclipse.jgit.** { *; }
-keep class org.apache.sshd.** { *; }
-keepclassmembers class org.apache.sshd.** { *; }
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }

# ServiceLoader providers must survive R8.
-keep class META-INF.services.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
