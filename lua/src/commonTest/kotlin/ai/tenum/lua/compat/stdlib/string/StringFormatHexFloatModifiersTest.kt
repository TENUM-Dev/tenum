package ai.tenum.lua.compat.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for string.format %a/%A with modifiers (precision, flags, width)
 * Based on strings.lua lines 335-340
 *
 * Reproduces and validates the fix for strings.lua:338 assertion failure
 */
class StringFormatHexFloatModifiersTest : LuaCompatTestBase() {
    @Test
    fun testHexFloatPrecisionSupported() {
        // Test that %.3a is supported (pcall should succeed)
        assertLuaTrue(
            """
            local ok, err = pcall(string.format, "%.3a", 0)
            return ok
        """,
        )
    }

    @Test
    fun testHexFloatWithPlusAndPrecision() {
        // strings.lua line 338: assert(string.find(string.format("%+.2A", 12), "^%+0X%x%.%x0P%+?%d$"))
        // Expected pattern: +0X[hex].[hex]0P[+/-][digit]
        // For 12 (0xC = 1.5 * 2^3): +0X1.80P+3
        assertLuaTrue(
            """
            local result = string.format("%+.2A", 12)
            return string.find(result, "^%+0X%x%.%x0P%+?%d$") ~= nil
        """,
        )
    }

    @Test
    fun testHexFloatWithPrecisionNegative() {
        // strings.lua line 339: assert(string.find(string.format("%.4A", -12), "^%-0X%x%.%x000P%+?%d$"))
        // Expected pattern: -0X[hex].[hex]000P[+/-][digit]
        // For -12: -0X1.8000P+3
        assertLuaTrue(
            """
            local result = string.format("%.4A", -12)
            return string.find(result, "^%-0X%x%.%x000P%+?%d$") ~= nil
        """,
        )
    }

    @Test
    fun testHexFloatPrecision2() {
        // Verify exact output for %.2a format
        assertLuaString("return string.format('%.2a', 12)", "0x1.80p+3")
    }

    @Test
    fun testHexFloatPrecision2Uppercase() {
        // Verify exact output for %.2A format
        assertLuaString("return string.format('%.2A', 12)", "0X1.80P+3")
    }

    @Test
    fun testHexFloatPrecision4() {
        // Verify exact output for %.4a format with negative number
        assertLuaString("return string.format('%.4a', -12)", "-0x1.8000p+3")
    }

    @Test
    fun testHexFloatPrecision0() {
        // Precision 0 should show decimal point with no digits after it
        assertLuaString("return string.format('%.0a', 12)", "0x1.p+3")
    }

    @Test
    fun testHexFloatPlusFlag() {
        // Plus flag should show + sign for positive numbers
        assertLuaString("return string.format('%+a', 12)", "+0x1.8p+3")
    }

    @Test
    fun testHexFloatPlusFlagUppercase() {
        // Plus flag with uppercase
        assertLuaString("return string.format('%+A', 12)", "+0X1.8P+3")
    }

    @Test
    fun testHexFloatPrecisionZero() {
        // Test precision with zero value
        assertLuaString("return string.format('%.3a', 0)", "0x0.000p+0")
    }

    @Test
    fun testHexFloatPlusSignZero() {
        // Test plus flag with zero
        assertLuaString("return string.format('%+a', 0)", "+0x0p+0")
    }

    @Test
    fun testHexFloatWidthPadding() {
        // Test width modifier
        val result = execute("return string.format('%20a', 12)")
        assertLuaNumber("return #string.format('%20a', 12)", 20.0)
    }
}
