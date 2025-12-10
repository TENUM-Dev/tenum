package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for large hexadecimal literals that don't fit in 64-bit integers.
 *
 * Lua 5.4 converts integers that overflow to floating-point numbers automatically.
 * This test verifies that hex literals larger than 64 bits are parsed correctly.
 *
 * Example from tpack.lua line 74:
 *   local lnum = 0x13121110090807060504030201
 *
 * This is a 104-bit hex number that must be parsed as a Double.
 */
class LargeHexLiteralTest : LuaCompatTestBase() {
    @Test
    fun testSmallHexLiteral() {
        // Small hex that fits in Long
        val result =
            execute(
                """
            return 0xFF
        """,
            )
        assertLuaNumber(result, 255.0)
    }

    @Test
    fun testLongMaxHex() {
        // Largest positive Long (0x7FFFFFFFFFFFFFFF)
        val result =
            execute(
                """
            return 0x7FFFFFFFFFFFFFFF
        """,
            )
        assertLuaNumber(result, Long.MAX_VALUE.toDouble())
    }

    @Test
    fun testULongMaxHex() {
        // Largest ULong (0xFFFFFFFFFFFFFFFF) wraps to -1 in Lua
        val result =
            execute(
                """
            return 0xFFFFFFFFFFFFFFFF
        """,
            )
        // In Lua, this is parsed as -1 (signed interpretation)
        assertLuaNumber(result, -1.0)
    }

    @Test
    fun testVeryLargeHexLiteral() {
        // 104-bit hex from tpack.lua line 74
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            return lnum
        """,
            )

        // This should parse as a Double (not fail)
        assertTrue(result is LuaNumber, "Expected LuaNumber, got ${result::class.simpleName}")
    }

    @Test
    fun testLargeHexInExpression() {
        // Use large hex in arithmetic
        val result =
            execute(
                """
            local x = 0x13121110090807060504030201
            return x == x
        """,
            )

        assertLuaBoolean(result, true)
    }

    @Test
    fun test65BitHex() {
        // 17 hex digits = 68 bits (just over 64-bit limit)
        val result =
            execute(
                """
            return 0x1FFFFFFFFFFFFFFFF
        """,
            )

        assertTrue(result is LuaNumber, "Expected LuaNumber for 65-bit hex")
    }

    @Test
    fun testHexWithDecimalFallback() {
        // Compare hex parsing with decimal parsing
        val result =
            execute(
                """
            local h = 0x100000000  -- 2^32
            local d = 4294967296   -- 2^32 in decimal
            return h == d
        """,
            )

        assertLuaBoolean(result, true)
    }
}
