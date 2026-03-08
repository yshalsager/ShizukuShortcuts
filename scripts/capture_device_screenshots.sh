#!/usr/bin/env bash

set -euo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
package_name='com.yshalsager.shizukushortcuts'
activity_name='com.yshalsager.shizukushortcuts/.MainActivity'

capture_one() {
  local locale="$1"
  local output_path="$2"

  adb shell cmd locale set-app-locales "$package_name" --locales "$locale" >/dev/null
  adb shell am force-stop "$package_name" >/dev/null
  adb shell am start -W -n "$activity_name" >/dev/null

  for _ in $(seq 1 20); do
    if adb shell dumpsys activity activities | rg -q "topResumedActivity=.*$package_name/.MainActivity"; then
      sleep 2
      adb exec-out screencap -p > "$output_path"
      return 0
    fi
    sleep 0.5
  done

  echo "App did not reach foreground for locale: $locale" >&2
  return 1
}

capture_one 'en-US' "$repo_root/fastlane/metadata/android/en-US/images/phoneScreenshots/01_home.png"
capture_one 'ar' "$repo_root/fastlane/metadata/android/ar/images/phoneScreenshots/01_home.png"
adb shell cmd locale set-app-locales "$package_name" --locales '' >/dev/null
