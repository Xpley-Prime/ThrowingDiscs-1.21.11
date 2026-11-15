#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
VERSION=9.2.1
GRADLE=".gradle-distribution/gradle-$VERSION/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  mkdir -p .gradle-distribution
  curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o ".gradle-distribution/gradle-$VERSION-bin.zip"
  unzip -qo ".gradle-distribution/gradle-$VERSION-bin.zip" -d .gradle-distribution
fi
"$GRADLE" build --stacktrace
