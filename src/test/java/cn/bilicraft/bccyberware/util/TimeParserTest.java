package cn.bilicraft.bccyberware.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeParserTest {
    @Test
    void parsesTicksSecondsAndMinutes() {
        assertEquals(20, TimeParser.parseTicks("20t"));
        assertEquals(100, TimeParser.parseTicks("5s"));
        assertEquals(1_800, TimeParser.parseTicks("1.5m"));
    }

    @Test
    void rejectsAmbiguousValues() {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseTicks("5"));
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseTicks("soon"));
    }
}

