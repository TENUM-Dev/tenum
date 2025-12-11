package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test string.rep overflow handling from strings.lua line 123
 * Ensures we throw error BEFORE attempting allocation
 * Limit is Int.MAX_VALUE / 2 (~1,073,741,823)
 */
class StringRepOverflowTest : LuaCompatTestBase() {
    @Test
    fun testStringRepExceedsPracticalLimit() {
        // Count exceeds Int.MAX_VALUE / 2
        assertThrowsError(
            """
            return string.rep("x", 1073741824)
            """,
            "too large",
        )
    }

    @Test
    fun testStringRepMultiplyOverflow() {
        // 1000 char string * 2M repetitions = 2B chars (exceeds limit)
        assertThrowsError(
            """
            local s = string.rep("a", 1000)
            return string.rep(s, 2000000)
            """,
            "too large",
        )
    }

    @Test
    fun testStringRepWithSeparatorOverflow() {
        // Large repetitions with separator pushing over limit
        assertThrowsError(
            """
            return string.rep("test", 600000000, "xx")
            """,
            "too large",
        )
    }

    @Test
    fun testStringRepSeparatorOnlyOverflow() {
        // Empty string, but separators alone exceed limit
        assertThrowsError(
            """
            return string.rep("", 1073741824, "x")
            """,
            "too large",
        )
    }

    @Test
    fun testStringRepJustUnderLimit() {
        // This should succeed - small string, reasonable count
        assertLuaNumber("return #string.rep('a', 1000)", 1000.0)
    }

    @Test
    fun testStringRepWithSeparatorJustUnderLimit() {
        // Should succeed - 10 'a's + 9 'x's = 19 chars
        assertLuaNumber("return #string.rep('a', 10, 'x')", 19.0)
    }
}
