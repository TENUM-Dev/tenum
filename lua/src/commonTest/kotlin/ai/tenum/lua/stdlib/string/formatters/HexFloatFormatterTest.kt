package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexFloatFormatterTest {
    private val formatter = HexFloatFormatter()

    // ============================================================================
    // Basic %a format tests
    // ============================================================================

    @Test
    fun testBasicHexFloatFormatting() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("0x1p+0", result)
    }

    @Test
    fun testHexFloatTwo() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(2.0), spec)
        assertEquals("0x1p+1", result)
    }

    @Test
    fun testHexFloatFour() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(4.0), spec)
        assertEquals("0x1p+2", result)
    }

    @Test
    fun testHexFloatWithFraction() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(1.5), spec)
        // 1.5 = 1.1 binary = 0x1.8p+0
        assertTrue(result.startsWith("0x1."))
        assertTrue(result.contains("p+0"))
    }

    @Test
    fun testHexFloatNegative() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(-1.0), spec)
        assertEquals("-0x1p+0", result)
    }

    @Test
    fun testHexFloatZero() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(0.0), spec)
        assertEquals("0x0p+0", result)
    }

    @Test
    fun testHexFloatSmallNumber() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(0.5), spec)
        assertEquals("0x1p-1", result)
    }

    // ============================================================================
    // %A format tests (uppercase)
    // ============================================================================

    @Test
    fun testHexFloatUppercase() {
        val spec = FormatSpecifier(emptySet(), null, null, 'A')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("0X1P+0", result)
    }

    @Test
    fun testHexFloatUppercaseWithFraction() {
        val spec = FormatSpecifier(emptySet(), null, null, 'A')
        val result = formatter.format(LuaNumber.of(1.5), spec)
        assertTrue(result.startsWith("0X1."))
        assertTrue(result.contains("P+0"))
    }

    // ============================================================================
    // Precision tests
    // ============================================================================

    @Test
    fun testHexFloatWithZeroPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 0, 'a')
        val result = formatter.format(LuaNumber.of(1.5), spec)
        // With precision 0, no mantissa digits shown but decimal point remains
        assertEquals("0x1.p+0", result)
    }

    @Test
    fun testHexFloatWithPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 4, 'a')
        val result = formatter.format(LuaNumber.of(1.5), spec)
        assertEquals("0x1.8000p+0", result)
    }

    @Test
    fun testHexFloatZeroWithPrecision() {
        val spec = FormatSpecifier(emptySet(), null, 4, 'a')
        val result = formatter.format(LuaNumber.of(0.0), spec)
        assertEquals("0x0.0000p+0", result)
    }

    // ============================================================================
    // Flag tests
    // ============================================================================

    @Test
    fun testHexFloatWithForceSign() {
        val spec = FormatSpecifier(setOf('+'), null, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("+0x1p+0", result)
    }

    @Test
    fun testHexFloatWithSpaceSign() {
        val spec = FormatSpecifier(setOf(' '), null, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals(" 0x1p+0", result)
    }

    @Test
    fun testHexFloatWithWidth() {
        val spec = FormatSpecifier(emptySet(), 15, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("         0x1p+0", result)
    }

    @Test
    fun testHexFloatWithLeftJustify() {
        val spec = FormatSpecifier(setOf('-'), 15, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("0x1p+0         ", result)
    }

    @Test
    fun testHexFloatWithZeroPad() {
        val spec = FormatSpecifier(setOf('0'), 15, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("0000000000x1p+0", result)
    }

    @Test
    fun testHexFloatWithZeroPadAndSign() {
        val spec = FormatSpecifier(setOf('0', '+'), 15, null, 'a')
        val result = formatter.format(LuaNumber.of(1.0), spec)
        assertEquals("+000000000x1p+0", result)
    }

    // ============================================================================
    // Special values tests
    // ============================================================================

    @Test
    fun testHexFloatNaN() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("nan", result)
    }

    @Test
    fun testHexFloatNaNUppercase() {
        val spec = FormatSpecifier(emptySet(), null, null, 'A')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("NAN", result)
    }

    @Test
    fun testHexFloatInfinity() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("inf", result)
    }

    @Test
    fun testHexFloatInfinityUppercase() {
        val spec = FormatSpecifier(emptySet(), null, null, 'A')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("INF", result)
    }

    @Test
    fun testHexFloatNegativeInfinity() {
        val spec = FormatSpecifier(emptySet(), null, null, 'a')
        val result = formatter.format(LuaNumber.of(Double.NEGATIVE_INFINITY), spec)
        assertEquals("-inf", result)
    }
}
