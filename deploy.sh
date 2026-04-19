#!/bin/bash
# Build PathChakra APK, copy to SeenSlide landing page, commit & push both repos.
# Usage: ./deploy.sh [commit message]

set -e

ANDROID_DIR="$(cd "$(dirname "$0")" && pwd)"
SEENSLIDE_DIR="$HOME/code/hobby/SeenSlide"
JAVA_HOME="$HOME/app/android-studio-panda2-linux/android-studio/jbr"
APK_SRC="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
APK_DST="$SEENSLIDE_DIR/modules/cloud/static/seenslide-teacher.apk"

MSG="${1:-Update PathChakra APK}"

echo "==> Building release APK..."
cd "$ANDROID_DIR"
JAVA_HOME="$JAVA_HOME" ./gradlew assembleRelease -q

echo "==> Copying APK to SeenSlide static/ ($(du -h "$APK_SRC" | cut -f1))"
cp "$APK_SRC" "$APK_DST"

echo "==> Committing & pushing PathChakra..."
cd "$ANDROID_DIR"
git add -A
git diff --cached --quiet || git commit -m "$MSG"
git push

echo "==> Committing & pushing SeenSlide..."
cd "$SEENSLIDE_DIR"
git add modules/cloud/static/seenslide-teacher.apk
git diff --cached --quiet || git commit -m "Update PathChakra APK"
git push

echo "==> Done!"
