package com.calmlauncher.domain.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalculatorTest {
    private fun eval(s: String) = Calculator.evaluate(s)

    @Test
    fun `basic arithmetic with precedence`() {
        assertEquals(14.0, eval("2+3*4"))
        assertEquals(20.0, eval("(2+3)*4"))
        assertEquals(2.5, eval("5/2"))
        assertEquals(-1.0, eval("2-3"))
        assertEquals(512.0, eval("2^3^2"))
    }

    @Test
    fun `symbols and percent`() {
        assertEquals(12.0, eval("3×4"))
        assertEquals(3.0, eval("9÷3"))
        assertEquals(1.0, eval("10 % 3"))
        assertEquals(12.0, eval("15% of 80"))
    }

    @Test
    fun `unary minus and decimals`() {
        assertEquals(-6.0, eval("-2*3"))
        assertEquals(0.75, eval("1.5/2"))
        assertEquals(3.5, eval("1,5+2"))
    }

    @Test
    fun `rejects non expressions and errors`() {
        assertNull(eval("youtube"))
        assertNull(eval("12"))
        assertNull(eval("1/0"))
        assertNull(eval("(1+2"))
        assertNull(eval("1++"))
        assertFalse(Calculator.looksLikeExpression("-5"))
        assertTrue(Calculator.looksLikeExpression("5-2"))
    }

    @Test
    fun `format drops trailing zeros`() {
        assertEquals("14", Calculator.format(14.0))
        assertEquals("0.5", Calculator.format(0.5))
        assertEquals("0.333333333333", Calculator.format(1.0 / 3.0))
    }
}
