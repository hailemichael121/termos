package dev.termos

import org.junit.Assert.*
import org.junit.Test

class CalcTest {
    private fun e(s: String) = Calc.eval(s)

    @Test fun precedence() {
        assertEquals(14.0, e("2+3*4"), 1e-9)
        assertEquals(20.0, e("(2+3)*4"), 1e-9)
        assertEquals(2.5, e("10/4"), 1e-9)
        assertEquals(1.0, e("10%3"), 1e-9)
    }

    @Test fun powerAndUnary() {
        assertEquals(-4.0, e("-2^2"), 1e-9)
        assertEquals(512.0, e("2^3^2"), 1e-9)
        assertEquals(5.0, e("--5"), 1e-9)
    }

    @Test fun functionsAndConstants() {
        assertEquals(4.0, e("sqrt(16)"), 1e-9)
        assertEquals(3.0, e("abs(-3)"), 1e-9)
        assertEquals(2.0, e("log(100)"), 1e-9)
        assertEquals(Math.PI * 2, e("2*pi"), 1e-9)
        assertEquals(1.0, e("sin(pi/2)"), 1e-9)
    }

    @Test fun whitespaceIgnored() = assertEquals(7.0, e(" 3 + 4 "), 1e-9)

    @Test fun errors() {
        listOf("2+", "(1", "1/0", "foo(1)", "abc", "2 $ 3").forEach {
            try { e(it); fail("expected error for '$it'") } catch (_: Exception) {}
        }
    }

    @Test fun formatting() {
        assertEquals("14", Calc.fmt(14.0))
        assertEquals("100", Calc.fmt(100.0))
        assertEquals("2.5", Calc.fmt(2.5))
        assertEquals("0.3333333333", Calc.fmt(1.0 / 3))
    }
}
