package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatFormatterTest {
    private val formatter = FloatFormatter()

    // ============================================================================
    // %f format tests
    // ============================================================================

    @Test
    fun testBasicFloatFormatting() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'f')
        val result = formatter.format(LuaNumber.of(3.14159), spec)
        assertEquals("3.141590", result)
    }

    @Test
    fun testFloatWithZeroPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 0, 'f')
        val result = formatter.format(LuaNumber.of(3.7), spec)
        assertEquals("4", result)
    }

    @Test
    fun testFloatWithHighPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 10, 'f')
        val result = formatter.format(LuaNumber.of(1.23), spec)
        assertEquals("1.2300000000", result)
    }

    @Test
    fun testFloatWithWidth() {
        val spec = FormatSpecifier(emptySet(), 10, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("      3.14", result)
    }

    @Test
    fun testFloatWithLeftJustify() {
        val spec = FormatSpecifier(setOf('-'), 10, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("3.14      ", result)
    }

    @Test
    fun testFloatWithZeroPad() {
        val spec = FormatSpecifier(setOf('0'), 10, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("0000003.14", result)
    }

    @Test
    fun testFloatWithForceSign() {
        val spec = FormatSpecifier(setOf('+'), null, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("+3.14", result)
    }

    @Test
    fun testFloatWithSpaceSign() {
        val spec = FormatSpecifier(setOf(' '), null, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals(" 3.14", result)
    }

    @Test
    fun testFloatNegativeNumber() {
        val spec = FormatSpecifier(emptySet(), null, 2, 'f')
        val result = formatter.format(LuaNumber.of(-3.14), spec)
        assertEquals("-3.14", result)
    }

    @Test
    fun testFloatZeroPadWithSign() {
        val spec = FormatSpecifier(setOf('0', '+'), 10, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("+000003.14", result)
    }

    @Test
    fun testFloatAlternateForm() {
        val spec = FormatSpecifier(setOf('#'), null, 0, 'f')
        val result = formatter.format(LuaNumber.of(42.0), spec)
        assertEquals("42.", result)
    }

    // ============================================================================
    // %g format tests
    // ============================================================================

    @Test
    fun testGFormatSmallNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(3.14159), spec)
        assertEquals("3.14159", result)
    }

    @Test
    fun testGFormatLargeNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(1234567.0), spec)
        assertEquals("1.23457e+06", result)
    }

    @Test
    fun testGFormatVerySmallNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(0.000012345), spec)
        assertEquals("1.2345e-05", result)
    }

    @Test
    fun testGFormatTrimsTrailingZeros() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(1.5), spec)
        assertEquals("1.5", result)
    }

    @Test
    fun testGFormatAlternateFormKeepsZeros() {
        val spec = FormatSpecifier(setOf('#'), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(1.5), spec)
        assertEquals("1.50000", result)
    }

    @Test
    fun testGFormatZero() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(0.0), spec)
        assertEquals("0", result)
    }

    @Test
    fun testGFormatExponentialBoundary() {
        // exponent = -4 should still use decimal
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(0.0001), spec)
        assertEquals("0.0001", result)
    }

    @Test
    fun testGFormatExponentialBelowBoundary() {
        // exponent = -5 should use exponential
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(0.00001), spec)
        assertEquals("1e-05", result)
    }

    // ============================================================================
    // %G format tests (uppercase)
    // ============================================================================

    @Test
    fun testGUppercaseFormatLargeNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'G')
        val result = formatter.format(LuaNumber.of(1234567.0), spec)
        assertEquals("1.23457E+06", result)
    }

    @Test
    fun testGUppercaseFormatSmallNumber() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'G')
        val result = formatter.format(LuaNumber.of(0.00001), spec)
        assertEquals("1E-05", result)
    }

    // ============================================================================
    // Special values tests
    // ============================================================================

    @Test
    fun testFloatFormatNaN() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("nan", result)
    }

    @Test
    fun testFloatFormatInfinity() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("inf", result)
    }

    @Test
    fun testFloatFormatNegativeInfinity() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'g')
        val result = formatter.format(LuaNumber.of(Double.NEGATIVE_INFINITY), spec)
        assertEquals("-inf", result)
    }

    @Test
    fun testFloatFormatNaNUppercase() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'G')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("NAN", result)
    }

    @Test
    fun testFloatFormatInfinityUppercase() {
        val spec = FormatSpecifier(emptySet(), null, 6, 'G')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("INF", result)
    }

    // ============================================================================
    // Combined flags tests
    // ============================================================================

    @Test
    fun testFloatWithWidthZeroPadAndSign() {
        val spec = FormatSpecifier(setOf('0', '+'), 12, 2, 'f')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("+00000003.14", result)
    }

    @Test
    fun testGFormatWithWidth() {
        val spec = FormatSpecifier(emptySet(), 10, 6, 'g')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("      3.14", result)
    }

    @Test
    fun testGFormatWithLeftJustifyAndWidth() {
        val spec = FormatSpecifier(setOf('-'), 10, 6, 'g')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        assertEquals("3.14      ", result)
    }
}
