#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/recognizer-tests
javac -encoding UTF-8 -d build/recognizer-tests src/main/java/dev/palimpsest/glyph/*.java src/test/java/dev/palimpsest/glyph/*.java
java -cp build/recognizer-tests dev.palimpsest.glyph.RecognizerSelfTest
