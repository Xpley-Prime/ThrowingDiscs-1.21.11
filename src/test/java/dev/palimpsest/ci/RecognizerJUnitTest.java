package dev.palimpsest.ci;

import dev.palimpsest.glyph.RecognizerSelfTest;
import org.junit.jupiter.api.Test;

/** Runs the pure-Java handwritten rune and packet tests as part of `gradle build`. */
public final class RecognizerJUnitTest {
    @Test
    void recognizesGlyphsAndRejectsInvalidInputs() {
        RecognizerSelfTest.main(new String[0]);
    }
}
