#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

GRADLE_DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-8.11-bin"
WRAPPER_PROPS="${CLAUDE_PROJECT_DIR}/gradle/wrapper/gradle-wrapper.properties"
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-8.11-bin.zip"

# Read the expected Gradle version from wrapper properties
if [ -f "$WRAPPER_PROPS" ]; then
  EXPECTED_URL=$(grep distributionUrl "$WRAPPER_PROPS" | sed 's/.*=//' | sed 's/\\:/:/g')
  if [ -n "$EXPECTED_URL" ]; then
    GRADLE_ZIP_URL="$EXPECTED_URL"
  fi
fi

# Check if Gradle wrapper distribution is already available
GRADLEW="${CLAUDE_PROJECT_DIR}/gradlew"
if "$GRADLEW" --version >/dev/null 2>&1; then
  echo "Gradle wrapper already available"
else
  echo "Downloading Gradle distribution..."
  # Find or create the dist subdirectory
  DIST_SUBDIR=$(find "$GRADLE_DIST_DIR" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | head -1)
  if [ -z "$DIST_SUBDIR" ]; then
    DIST_SUBDIR="$GRADLE_DIST_DIR/downloaded"
    mkdir -p "$DIST_SUBDIR"
  fi

  ZIP_FILE="$DIST_SUBDIR/gradle-8.11-bin.zip"
  if [ ! -f "$ZIP_FILE" ] || [ ! -s "$ZIP_FILE" ]; then
    curl -sL -o "$ZIP_FILE" "$GRADLE_ZIP_URL"
  fi

  # Unzip if not already done
  if [ ! -d "$DIST_SUBDIR/gradle-8.11" ]; then
    cd "$DIST_SUBDIR" && unzip -q gradle-8.11-bin.zip
  fi

  # Clean up lock files from failed downloads
  rm -f "$DIST_SUBDIR/gradle-8.11-bin.zip.lck" "$DIST_SUBDIR/gradle-8.11-bin.zip.part"
fi

# Resolve dependencies (build without tests)
echo "Resolving project dependencies..."
cd "$CLAUDE_PROJECT_DIR"
"$GRADLEW" build -x test --no-daemon 2>&1 || echo "Warning: initial build failed (may need proxy config)"

echo "Session start hook completed"
