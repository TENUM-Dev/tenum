package ai.tenum.lua.compat.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for character class ranges in Lua patterns
 * These were broken when string.find used a custom pattern matcher
 * instead of LuaPattern
 */
class StringFindCharacterClassRangeTest : LuaCompatTestBase() {
    @Test
    fun testDigitRange() {
        // [1-9] should match digits 1-9
        execute("assert(string.find('0', '[1-9]') == nil)")
        execute("assert(string.find('1', '[1-9]') == 1)")
        execute("assert(string.find('5', '[1-9]') == 1)")
        execute("assert(string.find('9', '[1-9]') == 1)")
        execute("assert(string.find('a', '[1-9]') == nil)")
    }

    @Test
    fun testLowercaseRange() {
        // [a-z] should match lowercase letters
        execute("assert(string.find('A', '[a-z]') == nil)")
        execute("assert(string.find('a', '[a-z]') == 1)")
        execute("assert(string.find('m', '[a-z]') == 1)")
        execute("assert(string.find('z', '[a-z]') == 1)")
        execute("assert(string.find('1', '[a-z]') == nil)")
    }

    @Test
    fun testUppercaseRange() {
        // [A-Z] should match uppercase letters
        execute("assert(string.find('a', '[A-Z]') == nil)")
        execute("assert(string.find('A', '[A-Z]') == 1)")
        execute("assert(string.find('M', '[A-Z]') == 1)")
        execute("assert(string.find('Z', '[A-Z]') == 1)")
        execute("assert(string.find('1', '[A-Z]') == nil)")
    }

    @Test
    fun testMultipleRanges() {
        // [1-9a-f] should match digits 1-9 or letters a-f
        execute("assert(string.find('0', '[1-9a-f]') == nil)")
        execute("assert(string.find('1', '[1-9a-f]') == 1)")
        execute("assert(string.find('9', '[1-9a-f]') == 1)")
        execute("assert(string.find('a', '[1-9a-f]') == 1)")
        execute("assert(string.find('f', '[1-9a-f]') == 1)")
        execute("assert(string.find('g', '[1-9a-f]') == nil)")
    }

    @Test
    fun testHexadecimalRange() {
        // [0-9a-fA-F] should match hex digits
        execute("assert(string.find('5', '[0-9a-fA-F]') == 1)")
        execute("assert(string.find('a', '[0-9a-fA-F]') == 1)")
        execute("assert(string.find('F', '[0-9a-fA-F]') == 1)")
        execute("assert(string.find('g', '[0-9a-fA-F]') == nil)")
        execute("assert(string.find('G', '[0-9a-fA-F]') == nil)")
    }

    @Test
    fun testRangeInPattern() {
        // Test ranges in more complex patterns
        execute("assert(string.find('test123', '[0-9]') == 5)")
        execute("assert(string.find('123test', '[a-z]') == 4)")
        execute("assert(string.find('ABC123def', '[a-z]+') == 7)")
    }

    @Test
    fun testNegatedRange() {
        // [^0-9] should match anything except digits
        execute("assert(string.find('123a', '[^0-9]') == 4)")
        execute("assert(string.find('abc', '[^0-9]') == 1)")
        execute("assert(string.find('123', '[^0-9]') == nil)")
    }

    @Test
    fun testRangeWithRepetition() {
        // [a-z]+ should match one or more lowercase letters
        execute(
            """
            local s, e = string.find('123abc456', '[a-z]+')
            assert(s == 4, 'start should be 4')
            assert(e == 6, 'end should be 6')
            """,
        )
    }

    @Test
    fun testComplexHexPattern() {
        // This is the pattern from strings.lua:314 that was failing
        execute(
            """
            local s = "0x1.999999999999ap-4"
            assert(string.find(s, "^%-?0x[1-9a-f]%.?[0-9a-f]*p[-+]?%d+$") == 1)
            """,
        )
    }

    @Test
    fun testRangeAtStartOfString() {
        // ^[a-z] should match lowercase letter at start
        execute("assert(string.find('abc', '^[a-z]') == 1)")
        execute("assert(string.find('Abc', '^[a-z]') == nil)")
        execute("assert(string.find('123', '^[a-z]') == nil)")
    }
}
