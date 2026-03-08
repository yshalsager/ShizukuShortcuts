# Shizuku Shortcuts

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-2ea44f)](https://developer.android.com/about/versions/android-8.0)
[![Shizuku](https://img.shields.io/badge/powered%20by-Shizuku-4f46e5)](https://github.com/RikkaApps/Shizuku)

Tiny launcher shortcuts for Android system panels through Shizuku.

The app currently exposes two actions:

- open notifications
- open Quick Settings

It can run them directly from the compact home screen with `Try`, or pin them as launcher shortcuts with `Pin`.

> [!CAUTION]
> This project was developed with heavy use of AI assistance, including OpenAI Codex.

## Screenshots

<table>
  <thead>
    <tr>
      <th align="left">English</th>
      <th align="left">Arabic (RTL)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <div><strong>Home</strong> (actions + status)</div>
        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01_home.png" alt="Home (EN)" style="max-width: 100%; height: auto;" />
      </td>
      <td>
        <div><strong>Home</strong> (الصفحة الرئيسية)</div>
        <img src="fastlane/metadata/android/ar/images/phoneScreenshots/01_home.png" alt="Home (AR)" style="max-width: 100%; height: auto;" />
      </td>
    </tr>
  </tbody>
</table>

## What This App Does

- Opens the notification shade with `cmd statusbar expand-notifications`
- Opens Quick Settings with `cmd statusbar expand-settings`
- Falls back to `service call statusbar 1` for notifications on older ROMs when needed
- Exposes static launcher shortcuts and pinned launcher shortcuts
- Shows Shizuku state and permission state as compact status chips
- Lets you `Try` each action from the home screen before pinning it
- Supports Android dynamic colors on Android 12+ with a fixed fallback palette on older versions
- Supports English and Arabic with RTL
- Uses Android app language settings, not an in-app language picker

## Requirements

- Android `minSdk 26`
- Shizuku installed and running
- Shizuku permission granted to this app

## Architecture

Core pieces:

- `MainActivity`: condensed Compose home screen with status chips, inline guidance, and action rows
- `ShortcutDispatchActivity`: transparent trampoline for launcher shortcuts
- `AppShizukuManager`: binder state, permission flow, and user-service binding
- `PrivilegedStatusBarService`: Shizuku user service binder
- `ActionPerformer`: shell command execution and fallback logic

Runtime flow:

1. User taps `Try` in the app or launches a static/pinned shortcut
2. The app checks Shizuku availability and permission
3. The app binds the Shizuku user service
4. The user service runs the status bar shell command
5. The app returns silently or shows a short toast on failure

Implementation details live in [docs/implementation-walkthrough.md](/Users/yshalsager/tmp/research/shizuku-shortcuts/docs/implementation-walkthrough.md).

## Build

This project uses `mise` for tool management.

```bash
# Build debug APK
mise x java -- ./gradlew :app:assembleDebug

# Run unit tests
mise x java -- ./gradlew :app:testDebugUnitTest

# Build Android test APK
mise x java -- ./gradlew :app:assembleDebugAndroidTest
```

## Fastlane And Metadata

This repo includes:

- `fastlane/metadata/android/en-US`
- `fastlane/metadata/android/ar`
- screenshot assets under `fastlane/metadata/android/*/images/phoneScreenshots`
- fastlane lanes for metadata validation, screenshot capture, and Play upload

Useful commands:

```bash
# Install fastlane
mise x ruby -- bundle install

# Validate fastlane metadata
mise x ruby -- bundle exec fastlane android validate_metadata

# Capture screenshots
mise x ruby -- bundle exec fastlane android capture_screenshots
```

## CI

GitHub Actions included in this repo:

- `ci.yml`: unit tests, debug build, release build, metadata validation
- `screenshots.yml`: emulator-based fastlane screenshot capture
- `release.yml`: build release APK and attach it to GitHub releases

## License

GPL-3.0-only. See [LICENSE](/Users/yshalsager/tmp/research/shizuku-shortcuts/LICENSE).
