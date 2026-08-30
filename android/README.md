# Android fixture-only domain foundation

This `android/` workspace currently contains only a pure Kotlin/JVM domain module at `android/domain`. It is a fixture-only technical slice for repository-boundary and local task lifecycle tests.

It is not an Android application, not an installable artifact, not encrypted persistence, and not a usable/live release. The in-memory repository is volatile per process/repository instance and must use only deterministic synthetic fixtures; it must not store real user task data.

## Lightweight domain test command

From the repository root, run:

```bash
JAVA_HOME=<path-to-jdk-21> <path-to-gradle-8.10.2>/bin/gradle -p android :domain:test --no-daemon
```

In this worker environment, where Java and Gradle are not installed globally, the verified command used a local untracked toolchain under `.gradle/tools`:

```bash
JAVA_HOME=$PWD/.gradle/tools/jdk-21 .gradle/tools/gradle-8.10.2/bin/gradle -p android :domain:test --no-daemon
```

## Scope guards

This slice intentionally does not include Android framework code, an app/UI module, Room, SQLCipher, Android Keystore, filesystem/database/preferences persistence, network/server APIs, analytics, alarm scheduling, notifications, recurrence, sync, search, accounts, cryptography, release signing, APK/AAB generation, or real user data.
