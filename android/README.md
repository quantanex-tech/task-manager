# Android local foundation

This `android/` Gradle workspace contains the Android-side foundation modules for the Android-first dogfood/alpha slices:

- `android/domain`: pure Kotlin/JVM repository contract, domain models, and fixture-only in-memory repository tests.
- `android/persistence`: Android library adapter that backs the domain `TaskRepository` with Room 2.8.x and SQLCipher Community Edition, plus Android Keystore-backed local database-key bootstrap and Android backup/data-extraction exclusions.

It is still not an Android app, not an installable artifact, not a UI, not reminder scheduling/notifications, not sync/accounts/networking, and not a usable/live release. Tests and fixtures must remain synthetic; do not persist real task/reminder data in this workspace until the full accepted release boundary is satisfied.

## Local toolchain used in this worker environment

Java, Gradle, and Android SDK tools were not installed globally in the worker environment. Focused verification used ignored local tools under `.gradle/tools`:

```bash
export JAVA_HOME=$PWD/.gradle/tools/jdk-21
export ANDROID_HOME=$PWD/.gradle/tools/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$JAVA_HOME/bin:$PWD/.gradle/tools/gradle-8.11.1/bin:$ANDROID_HOME/platform-tools:$PATH
```

## Focused verification commands

From the repository root, run the domain tests:

```bash
gradle -p android :domain:test --no-daemon
```

Run Android persistence JVM/Robolectric tests for key bootstrap and backup-rule inspection:

```bash
gradle -p android :persistence:testDebugUnitTest --no-daemon
```

Compile the SQLCipher/Room instrumentation test APKs for encrypted create/open/reopen, plaintext-absence, and migration paths:

```bash
gradle -p android :persistence:assembleDebugAndroidTest --no-daemon
```

Run instrumentation tests only when an Android device or emulator is connected:

```bash
gradle -p android :persistence:connectedDebugAndroidTest --no-daemon
```

In the current worker environment this command compiled the test APK first, then failed with `No connected devices!` because no emulator or Android device was attached.

## Persistence boundaries

- Domain code remains free of Room, SQLCipher, Android Keystore, and Android framework types.
- `EncryptedTaskRepositoryFactory` opens the repository only after database key bootstrap succeeds, and maps key/cipher/database failures to typed safe results.
- The normal bootstrap path creates a random local database key, wraps it with Android Keystore AES-GCM, and stores only wrapped key material in no-backup app-private storage.
- Existing wrapped key material is never overwritten when unwrap fails; invalidated, unavailable, corrupt, unsupported-cipher, database-open, and setup-latency failures fail closed without plaintext fallback.
- Room schema JSON is exported under `android/persistence/schemas`; add forward-only migrations and never edit an already-applied migration for live versions.
- Android backup and data-extraction rules exclude the encrypted database, WAL/SHM/journal/schema/FTS sidecars, key-wrapper storage, nonce/key state placeholders, sensitive diagnostics, screenshots, and sensitive preferences.
