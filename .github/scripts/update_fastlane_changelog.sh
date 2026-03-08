#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:?VERSION is required}"
VERSION_CODE="${VERSION_CODE:?VERSION_CODE is required}"
REPO="${GITHUB_REPOSITORY:-yshalsager/shizuku-shortcuts}"

if [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "VERSION_CODE must be numeric"
  exit 1
fi

for locale in en-US ar; do
  dir="fastlane/metadata/android/$locale/changelogs"
  mkdir -p "$dir"
  file="$dir/${VERSION_CODE}.txt"
  text="Release v${VERSION}. Notes: https://github.com/${REPO}/releases/tag/v${VERSION}"

  printf '%s\n' "$text" > "$file"

  size=$(wc -c < "$file")
  if [[ "$size" -gt 500 ]]; then
    echo "Changelog too large ($size > 500): $file"
    exit 1
  fi
done
