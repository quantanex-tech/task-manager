# Android local foundation

This `android/` Gradle workspace contains the Android-side modules for the Android-first dogfood/alpha slices:

- `android/app`: bounded Slice 4 single-activity Jetpack Compose phone application candidate for the first-launch encrypted local Inbox workflow plus one optional exact local reminder per task.
- `android/domain`: pure Kotlin/JVM repository contract, domain models, and fixture-only in-memory repository tests.
- `android/persistence`: Android library adapter that backs the domain `TaskRepository` with Room 2.8.x and SQLCipher Community Edition, plus Android Keystore-backed local database-key bootstrap and Android backup/data-extraction exclusions.

The app candidate is not deployed, publication-ready, installable-proven on Paul's device, sync/accounts/networking, or a usable/live/final-accepted release. Tests and fixtures must remain synthetic; do not persist real task/reminder data in this workspace until the full accepted release boundary is satisfied.

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

Run the Slice 3 app ViewModel/unit tests and compile the debug app plus Compose instrumentation APK:

```bash
gradle -p android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Compile the SQLCipher/Room instrumentation test APKs for encrypted create/open/reopen, plaintext-absence, and migration paths:

```bash
gradle -p android :persistence:assembleDebugAndroidTest --no-daemon
```

Run instrumentation tests only when an Android device or emulator is connected:

```bash
gradle -p android :persistence:connectedDebugAndroidTest --no-daemon
```

Run app Compose instrumentation tests only when an Android device or emulator is connected:

```bash
gradle -p android :app:connectedDebugAndroidTest --no-daemon
```

In the current Slice 3 worker environment, `adb devices` returned only `List of devices attached`; no emulator or Android device was attached, so connected instrumentation tests were not executed. The app and instrumentation APKs were compiled instead.

## Slice 4 reminder and notification boundaries

- Reminder entry is intentionally local-first: the task detail surface stores one optional exact UTC instant through the encrypted `InboxTaskStore`/`TaskRepository` boundary; blank or unparsable reminder input is rejected before storage mutation.
- `LocalReminderCoordinator` requests scheduling only when notification posting is currently permitted. Permission denial saves the encrypted reminder, avoids exact-alarm scheduling, and leaves a visible disabled-notification state with guidance.
- `AndroidExactReminderScheduler` uses Android exact alarms only when `AlarmManager` reports exact-alarm capability. Unavailable or revoked capability returns an explicit degraded state; there is no inexact or best-effort fallback.
- Alarm, boot, locked-boot, time-set, and time-zone receivers reopen the encrypted store before reading reminder data. Store/key/cipher/database failure is fail-closed; receivers do not log protected content, trigger protected reminders from plaintext fallback state, or overwrite encrypted data to recover.
- The broadcast alarm identity is an opaque local ID derived only for Android alarm bookkeeping. Alarm intents do not carry task titles, reminder text, due timestamps, or notification content.
- Notification rendering decrypts task content only by reopening the local encrypted store on device. The public/lock-screen notification version is generic/private; if the store is unavailable or no due task can be decrypted, only generic text is rendered.

## Slice 3 app boundaries

- `android/app` uses `EncryptedTaskRepositoryFactory` from `android/persistence` for production task state. It does not use `InMemoryTaskRepository`, SharedPreferences, plaintext files, network services, telemetry, accounts, sync, reminder scheduling, or notification permissions.
- First launch renders the Inbox title entry field immediately and requests focus. Draft text remains volatile UI state until encrypted storage opens and a nonblank save succeeds; the draft is cleared only after the encrypted repository reports success.
- Repository calls run through the ViewModel on the IO dispatcher. Store/key/cipher/database failures map to a non-sensitive user-visible unavailable state; raw exceptions, task text, key material, and database details are not logged.
- Editing a task passes through the existing `reminderAt` value; complete and undo only change completion state. Slice 4 owns reminder entry and scheduling.
- Stable Compose test tags are present for the first-title field, add button, loading/empty/error/validation states, task list/rows, detail editor, complete/undo, and protected delete confirmation.

## Persistence boundaries

- Domain code remains free of Room, SQLCipher, Android Keystore, and Android framework types.
- `EncryptedTaskRepositoryFactory` opens the repository only after database key bootstrap succeeds, and maps key/cipher/database failures to typed safe results.
- The normal bootstrap path creates a random local database key, wraps it with Android Keystore AES-GCM, and stores only wrapped key material in no-backup app-private storage.
- Existing wrapped key material is never overwritten when unwrap fails; invalidated, unavailable, corrupt, unsupported-cipher, database-open, and setup-latency failures fail closed without plaintext fallback.
- Room schema JSON is exported under `android/persistence/schemas`; add forward-only migrations and never edit an already-applied migration for live versions.
- Android backup and data-extraction rules exclude the encrypted database, WAL/SHM/journal/schema/FTS sidecars, key-wrapper storage, nonce/key state placeholders, sensitive diagnostics, screenshots, and sensitive preferences.
