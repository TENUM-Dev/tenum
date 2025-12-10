package ai.tenum.lua.compat.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for string.format %a/%A (hexadecimal float format)
 * Based on strings.lua lines 310-326
 */
class StringFormatHexFloatTest : LuaCompatTestBase() {
    @Test
    fun testHexFloatFormatBasic() {
        // %a produces lowercase hex float
        val result =
            execute(
                """
                return string.format("%a", 0.1)
                """,
            )
        assertLuaString(result, "0x1.999999999999ap-4")
    }

    @Test
    fun testHexFloatFormatUppercase() {
        // %A produces uppercase hex float
        val result =
            execute(
                """
                return string.format("%A", 0.1)
                """,
            )
        assertLuaString(result, "0X1.999999999999AP-4")
    }

    @Test
    fun testHexFloatFormatNegative() {
        val result =
            execute(
                """
                return string.format("%a", -0.1)
                """,
            )
        assertLuaString(result, "-0x1.999999999999ap-4")
    }

    @Test
    fun testHexFloatFormatOne() {
        val result =
            execute(
                """
                return string.format("%a", 1.0)
                """,
            )
        assertLuaString(result, "0x1p+0")
    }

    @Test
    fun testHexFloatFormatTwo() {
        val result =
            execute(
                """
                return string.format("%a", 2.0)
                """,
            )
        assertLuaString(result, "0x1p+1")
    }

    @Test
    fun testHexFloatFormatNegativeTwo() {
        val result =
            execute(
                """
                return string.format("%a", -2.0)
                """,
            )
        assertLuaString(result, "-0x1p+1")
    }

    @Test
    fun testHexFloatFormatZero() {
        // Zero is a special case
        val result =
            execute(
                """
                return string.format("%a", 0.0)
                """,
            )
        assertLuaString(result, "0x0p+0")
    }

    @Test
    fun testHexFloatFormatZeroUppercase() {
        val result =
            execute(
                """
                return string.format("%A", 0.0)
                """,
            )
        assertLuaString(result, "0X0P+0")
    }

    @Test
    fun testHexFloatFormatLargeNumber() {
        val result =
            execute(
                """
                return string.format("%a", 1e30)
                """,
            )
        assertLuaString(result, "0x1.93e5939a08ceap+99")
    }

    @Test
    fun testHexFloatFormatSmallNumber() {
        val result =
            execute(
                """
                return string.format("%a", 3e-20)
                """,
            )
        assertLuaString(result, "0x1.1b578c96db19bp-65")
    }

    @Test
    fun testHexFloatFormatOneThird() {
        val result =
            execute(
                """
                return string.format("%a", 1/3)
                """,
            )
        assertLuaString(result, "0x1.5555555555555p-2")
    }

    @Test
    fun testHexFloatPatternMatching() {
        // Test that the format matches the expected Lua pattern
        execute(
            """
            local s = string.format("%a", 0.1)
            assert(string.find(s, "^%-?0x[1-9a-f]%.?[0-9a-f]*p[-+]?%d+$"))
            """,
        )
    }

    @Test
    fun testHexFloatPatternMatchingUppercase() {
        // Test uppercase format matches uppercase pattern
        execute(
            """
            local s = string.format("%A", 0.1)
            assert(string.find(s, "^%-?0X[1-9A-F]%.?[0-9A-F]*P[-+]?%d+$"))
            """,
        )
    }

    @Test
    fun testHexFloatZeroPattern() {
        // Zero has a special pattern that allows 0 as first digit
        execute(
            """
            local s = string.format("%A", 0.0)
            assert(string.find(s, "^0X0%.?0*P%+?0$"))
            """,
        )
    }
}
