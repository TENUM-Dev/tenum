package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals

class ExponentialFormatterTest {
    private val formatter = ExponentialFormatter()

    // ============================================================================
    // Basic %e format tests
    // ============================================================================

    @Test
    fun testBasicExponentialFormatting() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("1.234560e+02", result)
    }

    @Test
    fun testExponentialSmallNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(0.00123), spec)
        assertEquals("1.230000e-03", result)
    }

    @Test
    fun testExponentialZero() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(0.0), spec)
        assertEquals("0.000000e+00", result)
    }

    @Test
    fun testExponentialWithZeroPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 0, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("1e+02", result)
    }

    @Test
    fun testExponentialNegativeNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(-123.456), spec)
        assertEquals("-1.234560e+02", result)
    }

    @Test
    fun testExponentialWithHighPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 10, 'e')
        val result = formatter.format(LuaNumber.of(1.23), spec)
        assertEquals("1.2300000000e+00", result)
    }

    @Test
    fun testExponentialLargeNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(1234567890.0), spec)
        assertEquals("1.234568e+09", result)
    }

    @Test
    fun testExponentialVerySmallNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(0.000000123), spec)
        assertEquals("1.230000e-07", result)
    }

    // ============================================================================
    // %E format tests (uppercase)
    // ============================================================================

    @Test
    fun testExponentialUppercase() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'E')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("1.234560E+02", result)
    }

    @Test
    fun testExponentialUppercaseNegative() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'E')
        val result = formatter.format(LuaNumber.of(0.00123), spec)
        assertEquals("1.230000E-03", result)
    }

    // ============================================================================
    // Flag tests
    // ============================================================================

    @Test
    fun testExponentialWithForceSign() {
        val spec = FormatSpecifier(setOf('+'), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("+1.234560e+02", result)
    }

    @Test
    fun testExponentialWithSpaceSign() {
        val spec = FormatSpecifier(setOf(' '), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals(" 1.234560e+02", result)
    }

    @Test
    fun testExponentialWithWidth() {
        val spec = FormatSpecifier(emptySet(), 20, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("        1.234560e+02", result)
    }

    @Test
    fun testExponentialWithLeftJustify() {
        val spec = FormatSpecifier(setOf('-'), 20, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("1.234560e+02        ", result)
    }

    @Test
    fun testExponentialWithZeroPad() {
        val spec = FormatSpecifier(setOf('0'), 20, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("000000001.234560e+02", result)
    }

    @Test
    fun testExponentialWithZeroPadAndSign() {
        val spec = FormatSpecifier(setOf('0', '+'), 20, 6, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("+00000001.234560e+02", result)
    }

    @Test
    fun testExponentialAlternateForm() {
        val spec = FormatSpecifier(setOf('#'), null, 0, 'e')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        // With alternate form and precision 0, decimal point is shown
        assertEquals("1.e+02", result)
    }

    // ============================================================================
    // Special values tests
    // ============================================================================

    @Test
    fun testExponentialNaN() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("nan", result)
    }

    @Test
    fun testExponentialNaNUppercase() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'E')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("NAN", result)
    }

    @Test
    fun testExponentialInfinity() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("inf", result)
    }

    @Test
    fun testExponentialInfinityUppercase() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'E')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("INF", result)
    }

    @Test
    fun testExponentialNegativeInfinity() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'e')
        val result = formatter.format(LuaNumber.of(Double.NEGATIVE_INFINITY), spec)
        assertEquals("-inf", result)
    }

    @Test
    fun testExponentialNegativeInfinityUppercase() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'E')
        val result = formatter.format(LuaNumber.of(Double.NEGATIVE_INFINITY), spec)
        assertEquals("-INF", result)
    }

    // ============================================================================
    // Combined flags tests
    // ============================================================================

    @Test
    fun testExponentialComplexFormatting() {
        val spec = FormatSpecifier(setOf('0', '+'), 25, 10, 'E')
        val result = formatter.format(LuaNumber.of(123.456), spec)
        assertEquals("+000000001.2345600000E+02", result)
    }
}
