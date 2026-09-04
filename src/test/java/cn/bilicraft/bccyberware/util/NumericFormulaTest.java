package cn.bilicraft.bccyberware.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumericFormulaTest {
    @Test
    void evaluatesValueAndPrecedence() {
        assertEquals(15.0, NumericFormula.evaluate("value * 0.5 + 5", 20), 0.000_001);
        assertEquals(30.0, NumericFormula.evaluate("(value + 10) * 2", 5), 0.000_001);
        assertEquals(-5.0, NumericFormula.evaluate("-value", 5), 0.000_001);
    }

    @Test
    void rejectsDivisionByZeroAndUnknownNames() {
        assertThrows(IllegalArgumentException.class, () -> NumericFormula.evaluate("value / 0", 3));
        assertThrows(IllegalArgumentException.class, () -> NumericFormula.evaluate("level + 1", 3));
    }
}

