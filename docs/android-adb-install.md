# Android and WearOS debug install guide

This project should stay easy to clone, build and install from Paul's laptop.

## Prerequisites

- Git
- Android Studio or Android command-line tools
- Android Platform Tools (`adb`)
- USB debugging or wireless debugging enabled on the Android phone
- ADB debugging enabled on the WearOS watch when testing watch builds

## Clone

```bash
git clone https://github.com/quantanex-tech/task-manager.git
cd task-manager
```

## Future Android phone install flow

The exact module path will be created by the Android foundation task. The intended shape is:

```bash
./gradlew :android:assembleDebug
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Future WearOS install flow

```bash
./gradlew :wear:assembleDebug
adb devices
adb -s SERIAL install -r wear/app/build/outputs/apk/debug/wear-debug.apk
```

Replace `SERIAL` with the watch device ID shown by `adb devices`.

## CI artifact expectation

GitHub Actions should eventually publish debug/internal test artifacts for Android and WearOS so Paul can download APKs from a laptop without building locally when that is more convenient.
