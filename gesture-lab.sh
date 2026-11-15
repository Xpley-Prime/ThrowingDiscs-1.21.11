#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/gesture-lab
javac -encoding UTF-8 -d build/gesture-lab src/main/java/dev/palimpsest/glyph/*.java src/test/java/dev/palimpsest/glyph/GestureLab.java
java -cp build/gesture-lab dev.palimpsest.glyph.GestureLab
