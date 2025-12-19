package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegerFormatterTest {
    private val formatter = IntegerFormatter()

    // %d and %i tests
    @Test
    fun testDecimalSimple() {
        val spec = FormatSpecifier(formatChar = 'd')
        assertEquals("42", formatter.format(LuaNumber.of(42.0), spec))
        assertEquals("-42", formatter.format(LuaNumber.of(-42.0), spec))
    }

    @Test
    fun testDecimalWithWidth() {
        val spec = FormatSpecifier(formatChar = 'd', width = 5)
        assertEquals("   42", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testDecimalWithWidthLeftJustify() {
        val spec = FormatSpecifier(formatChar = 'd', width = 5, flags = setOf('-'))
        assertEquals("42   ", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testDecimalWithZeroPad() {
        val spec = FormatSpecifier(formatChar = 'd', width = 5, flags = setOf('0'))
        assertEquals("00042", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testDecimalWithZeroPadNegative() {
        val spec = FormatSpecifier(formatChar = 'd', width = 5, flags = setOf('0'))
        assertEquals("-0042", formatter.format(LuaNumber.of(-42.0), spec))
    }

    @Test
    fun testDecimalWithForceSign() {
        val spec = FormatSpecifier(formatChar = 'd', flags = setOf('+'))
        assertEquals("+42", formatter.format(LuaNumber.of(42.0), spec))
        assertEquals("-42", formatter.format(LuaNumber.of(-42.0), spec))
    }

    @Test
    fun testDecimalWithPrecision() {
        val spec = FormatSpecifier(formatChar = 'd', precision = 5)
        assertEquals("00042", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testDecimalPrecisionZeroWithZeroValue() {
        val spec = FormatSpecifier(formatChar = 'd', precision = 0)
        assertEquals("", formatter.format(LuaNumber.of(0.0), spec))
    }

    // %u tests
    @Test
    fun testUnsignedSimple() {
        val spec = FormatSpecifier(formatChar = 'u')
        assertEquals("42", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testUnsignedNegative() {
        val spec = FormatSpecifier(formatChar = 'u')
        val result = formatter.format(LuaNumber.of(-1.0), spec)
        assertEquals("18446744073709551615", result) // 2^64 - 1
    }

    // %o tests
    @Test
    fun testOctalSimple() {
        val spec = FormatSpecifier(formatChar = 'o')
        assertEquals("52", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testOctalWithAlternate() {
        val spec = FormatSpecifier(formatChar = 'o', flags = setOf('#'))
        assertEquals("052", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testOctalAlternateZeroValue() {
        val spec = FormatSpecifier(formatChar = 'o', flags = setOf('#'))
        assertEquals("0", formatter.format(LuaNumber.of(0.0), spec))
    }

    // %x and %X tests
    @Test
    fun testHexLowerSimple() {
        val spec = FormatSpecifier(formatChar = 'x')
        assertEquals("2a", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testHexUpperSimple() {
        val spec = FormatSpecifier(formatChar = 'X')
        assertEquals("2A", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testHexWithAlternateLower() {
        val spec = FormatSpecifier(formatChar = 'x', flags = setOf('#'))
        assertEquals("0x2a", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testHexWithAlternateUpper() {
        val spec = FormatSpecifier(formatChar = 'X', flags = setOf('#'))
        assertEquals("0X2A", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testHexAlternateZeroValue() {
        val spec = FormatSpecifier(formatChar = 'x', flags = setOf('#'))
        assertEquals("0", formatter.format(LuaNumber.of(0.0), spec))
    }

    @Test
    fun testHexWithPrecision() {
        val spec = FormatSpecifier(formatChar = 'x', precision = 5)
        assertEquals("0002a", formatter.format(LuaNumber.of(42.0), spec))
    }

    @Test
    fun testHexWithAlternateAndPrecision() {
        val spec = FormatSpecifier(formatChar = 'x', precision = 5, flags = setOf('#'))
        assertEquals("0x0002a", formatter.format(LuaNumber.of(42.0), spec))
    }

    // handles() tests
    @Test
    fun testHandles() {
        assertEquals(true, formatter.handles('d'))
        assertEquals(true, formatter.handles('i'))
        assertEquals(true, formatter.handles('u'))
        assertEquals(true, formatter.handles('o'))
        assertEquals(true, formatter.handles('x'))
        assertEquals(true, formatter.handles('X'))
        assertEquals(false, formatter.handles('s'))
        assertEquals(false, formatter.handles('f'))
    }
}
