package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * PHASE 6.2: Advanced Features - UTF-8 Support
 *
 * Tests UTF-8 string handling library.
 * Based on: utf8.lua
 *
 * Coverage:
 * - utf8.len
 * - utf8.char, utf8.codes
 * - utf8.codepoint
 * - utf8.offset
 * - UTF-8 validation
 */
class Utf8CompatTest : LuaCompatTestBase() {
    @Test
    fun testUtf8Char() =
        runTest {
            // Basic ASCII character
            assertLuaString("return utf8.char(65)", "A")

            // Multiple characters
            assertLuaString("return utf8.char(72, 101, 108, 108, 111)", "Hello")

            // Empty call returns empty string
            assertLuaString("return utf8.char()", "")
        }

    @Test
    fun testUtf8CharUnicode() =
        runTest {
            // Unicode characters
            execute(
                """
            local result = utf8.char(0x4E2D)  -- Chinese character
            assert(result ~= nil, "utf8.char should work with Unicode")
        """,
            )
        }

    @Test
    fun testUtf8Len() =
        runTest {
            // Basic ASCII string
            assertLuaNumber("return utf8.len('Hello')", 5.0)

            // Empty string
            assertLuaNumber("return utf8.len('')", 0.0)

            // With range
            assertLuaNumber("return utf8.len('Hello', 1, 3)", 3.0)
        }

    @Test
    fun testUtf8LenSubstring() =
        runTest {
            execute(
                """
            local s = "Hello World"
            assert(utf8.len(s, 1, 5) == 5, "Should count 5 chars")
            assert(utf8.len(s, 7) == 5, "Should count from position to end")
        """,
            )
        }

    @Test
    fun testUtf8Codepoint() =
        runTest {
            // Single character
            assertLuaNumber("return utf8.codepoint('A')", 65.0)

            // Specific position (should return 'e' = 101)
            assertLuaNumber("return utf8.codepoint('Hello', 2)", 101.0)

            // Range of characters
            execute(
                """
            local a, b, c = utf8.codepoint('ABC', 1, 3)
            assert(a == 65, "First should be 'A'")
            assert(b == 66, "Second should be 'B'")
            assert(c == 67, "Third should be 'C'")
        """,
            )
        }

    @Test
    fun testUtf8CodepointRange() =
        runTest {
            execute(
                """
            local s = "Hello"
            local h, e, l = utf8.codepoint(s, 1, 3)
            assert(h == 72, "H = 72")
            assert(e == 101, "e = 101")
            assert(l == 108, "l = 108")
        """,
            )
        }

    @Test
    fun testUtf8Offset() =
        runTest {
            // Offset from start (position before first char)
            assertLuaNumber("return utf8.offset('Hello', 0)", 1.0)
            // After first char
            assertLuaNumber("return utf8.offset('Hello', 1)", 2.0)
            // After second char
            assertLuaNumber("return utf8.offset('Hello', 2)", 3.0)

            // Offset from specific position (2 chars from position 2)
            assertLuaNumber("return utf8.offset('Hello', 2, 2)", 4.0)
        }

    @Test
    fun testUtf8OffsetNegative() =
        runTest {
            // Negative offset (from end)
            execute(
                """
            local s = "Hello"
            local offset = utf8.offset(s, -1, #s + 1)
            assert(offset ~= nil, "Should handle negative offset")
        """,
            )
        }

    @Test
    fun testUtf8Charpattern() =
        runTest {
            // utf8.charpattern should exist
            execute(
                """
            assert(utf8.charpattern ~= nil, "utf8.charpattern should exist")
            assert(type(utf8.charpattern) == "string", "utf8.charpattern should be a string")
        """,
            )
        }
}
