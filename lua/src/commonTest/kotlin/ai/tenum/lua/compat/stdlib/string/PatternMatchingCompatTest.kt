package ai.tenum.lua.compat.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 9.2: Pattern Matching Library
 *
 * Implements Lua pattern matching for string.match, string.gmatch, string.gsub
 *
 * Lua Pattern Reference:
 * - Character classes: %a %c %d %g %l %p %s %u %w %x
 * - Magic characters: ( ) . % + - * ? [ ] ^ $
 * - Sets: [abc] [^abc] [a-z]
 * - Captures: (pattern)
 * - Repetition: + * - ?
 * - Anchors: ^ $
 */
class PatternMatchingCompatTest : LuaCompatTestBase() {
    // ========== Character Classes ==========

    @Test
    fun testCharacterClassDigit() {
        assertLuaString(
            """
            return string.match("abc123def", "%d+")
        """,
            "123",
        )
    }

    @Test
    fun testCharacterClassAlpha() {
        assertLuaString(
            """
            return string.match("123abc456", "%a+")
        """,
            "abc",
        )
    }

    @Test
    fun testCharacterClassAlphaNum() {
        assertLuaString(
            """
            return string.match("!!!abc123###", "%w+")
        """,
            "abc123",
        )
    }

    @Test
    fun testCharacterClassSpace() {
        assertLuaString(
            """
            local s = "hello   world"
            return string.match(s, "%s+")
        """,
            "   ",
        )
    }

    @Test
    fun testCharacterClassLower() {
        assertLuaString(
            """
            return string.match("ABCdefGHI", "%l+")
        """,
            "def",
        )
    }

    @Test
    fun testCharacterClassUpper() {
        assertLuaString(
            """
            return string.match("abcDEFghi", "%u+")
        """,
            "DEF",
        )
    }

    @Test
    fun testCharacterClassPunctuation() {
        assertLuaString(
            """
            return string.match("abc!!!def", "%p+")
        """,
            "!!!",
        )
    }

    @Test
    fun testCharacterClassControl() {
        // Control characters (ASCII 0-31)
        assertLuaString(
            """
            local s = "hello\tworld"
            return string.match(s, "%c")
        """,
            "\t",
        )
    }

    @Test
    fun testCharacterClassHex() {
        assertLuaString(
            """
            return string.match("zz1A2Fzz", "%x+")
        """,
            "1A2F",
        )
    }

    @Test
    fun testCharacterClassNegated() {
        // %D = non-digit
        assertLuaString(
            """
            return string.match("123abc456", "%D+")
        """,
            "abc",
        )
    }

    // ========== Sets ==========

    @Test
    fun testSetBasic() {
        assertLuaString(
            """
            return string.match("abcdef", "[ace]+")
        """,
            "a",
        )
    }

    @Test
    fun testSetRange() {
        assertLuaString(
            """
            return string.match("abc123xyz", "[a-z]+")
        """,
            "abc",
        )
    }

    @Test
    fun testSetNegated() {
        assertLuaString(
            """
            return string.match("123abc456", "[^0-9]+")
        """,
            "abc",
        )
    }

    @Test
    fun testSetMultipleRanges() {
        assertLuaString(
            """
            return string.match("abc123XYZ", "[a-zA-Z]+")
        """,
            "abc",
        )
    }

    // ========== Magic Characters ==========

    @Test
    fun testDotWildcard() {
        assertLuaString(
            """
            return string.match("abc123", "a.c")
        """,
            "abc",
        )
    }

    @Test
    fun testEscapedPercent() {
        assertLuaString(
            """
            return string.match("50% off", "%d+%%")
        """,
            "50%",
        )
    }

    @Test
    fun testEscapedMagicChars() {
        assertLuaString(
            """
            return string.match("a.b*c+d", "a%.b%*c%+d")
        """,
            "a.b*c+d",
        )
    }

    // ========== Repetition ==========

    @Test
    fun testZeroOrMore() {
        // * matches 0 or more
        assertLuaString(
            """
            return string.match("aaabbb", "a*b+")
        """,
            "aaabbb",
        )
    }

    @Test
    fun testOneOrMore() {
        // + matches 1 or more
        assertLuaString(
            """
            return string.match("aaabbb", "a+b+")
        """,
            "aaabbb",
        )
    }

    @Test
    fun testZeroOrOne() {
        // - matches 0 or 1 (shortest match)
        assertLuaString(
            """
            return string.match("aaabbb", "a-")
        """,
            "",
        )
    }

    @Test
    fun testOptional() {
        // ? matches 0 or 1
        assertLuaString(
            """
            return string.match("http://", "https?://")
        """,
            "http://",
        )
    }

    // ========== Captures ==========

    @Test
    fun testCaptureBasic() {
        val result =
            execute(
                """
            local a, b = string.match("hello world", "(%a+) (%a+)")
            return a .. "-" .. b
        """,
            )
        assertLuaString(result, "hello-world")
    }

    @Test
    fun testCaptureMultiple() {
        val result =
            execute(
                """
            local y, m, d = string.match("2025-01-15", "(%d+)-(%d+)-(%d+)")
            return y .. "/" .. m .. "/" .. d
        """,
            )
        assertLuaString(result, "2025/01/15")
    }

    @Test
    fun testCaptureNested() {
        assertLuaString(
            """
            return string.match("abc(def)ghi", "%((%a+)%)")
        """,
            "def",
        )
    }

    // ========== Anchors ==========

    @Test
    fun testAnchorStart() {
        assertLuaString(
            """
            return string.match("hello world", "^%a+")
        """,
            "hello",
        )
    }

    @Test
    fun testAnchorEnd() {
        assertLuaString(
            """
            return string.match("hello world", "%a+$")
        """,
            "world",
        )
    }

    @Test
    fun testAnchorBoth() {
        assertLuaString(
            """
            return string.match("hello", "^hello$")
        """,
            "hello",
        )
    }

    @Test
    fun testAnchorNoMatch() {
        val result =
            execute(
                """
            return string.match("hello world", "^world")
        """,
            )
        assertTrue(result is LuaNil, "Should return nil when anchor doesn't match")
    }

    // ========== string.gmatch (iterator) ==========

    @Test
    fun testGmatchBasic() {
        assertLuaString(
            """
            local words = {}
            for word in string.gmatch("hello world lua", "%a+") do
                table.insert(words, word)
            end
            return table.concat(words, "-")
        """,
            "hello-world-lua",
        )
    }

    @Test
    fun testGmatchDigits() {
        assertLuaString(
            """
            local nums = {}
            for num in string.gmatch("a1b22c333", "%d+") do
                table.insert(nums, num)
            end
            return table.concat(nums, ",")
        """,
            "1,22,333",
        )
    }

    @Test
    fun testGmatchWithCaptures() {
        val result =
            execute(
                """
            local result = {}
            for k, v in string.gmatch("name=John age=30", "(%a+)=(%w+)") do
                table.insert(result, k .. ":" .. v)
            end
            return table.concat(result, ";")
        """,
            )
        assertLuaString(result, "name:John;age:30")
    }

    // ========== string.gsub with patterns ==========

    @Test
    fun testGsubWithPattern() {
        assertLuaString(
            """
            return string.gsub("hello world", "%a+", "X")
        """,
            "X X",
        )
    }

    @Test
    fun testGsubWithCapture() {
        assertLuaString(
            """
            return string.gsub("hello world", "(%a+)", "[%1]")
        """,
            "[hello] [world]",
        )
    }

    @Test
    fun testGsubWithFunction() {
        assertLuaString(
            """
            return string.gsub("hello world", "%a+", string.upper)
        """,
            "HELLO WORLD",
        )
    }

    @Test
    fun testGsubWithTable() {
        assertLuaString(
            """
            local t = {hello = "hi", world = "earth"}
            return string.gsub("hello world", "%a+", t)
        """,
            "hi earth",
        )
    }

    @Test
    fun testGsubCountReplacements() {
        val result =
            execute(
                """
            local s, count = string.gsub("aaa", "a", "b")
            return count
        """,
            )
        assertLuaNumber(result, 3.0)
    }

    // ========== Complex Patterns ==========

    @Test
    fun testEmailPattern() {
        assertLuaString(
            """
            local email = "Contact: user@example.com for info"
            return string.match(email, "%w+@%w+%.%w+")
        """,
            "user@example.com",
        )
    }

    @Test
    fun testUrlPattern() {
        assertLuaString(
            """
            local text = "Visit https://example.com/path"
            return string.match(text, "https?://[%w%./-]+")
        """,
            "https://example.com/path",
        )
    }

    @Test
    fun testNumberExtraction() {
        val result =
            execute(
                """
            local prices = "Item: $19.99, Tax: $2.50"
            local nums = {}
            for num in string.gmatch(prices, "%d+%.%d+") do
                table.insert(nums, num)
            end
            return table.concat(nums, ",")
        """,
            )
        assertLuaString(result, "19.99,2.50")
    }

    @Test
    fun testWordBoundaries() {
        // Using frontier pattern %f for word boundaries
        assertLuaString(
            """
            local text = "The cat in the hat"
            return string.gsub(text, "%f[%a]the%f[%A]", "XXX")
        """,
            "The cat in XXX hat",
        )
    }

    // ========== Edge Cases ==========

    @Test
    fun testEmptyPattern() {
        assertLuaString(
            """
            return string.match("hello", "")
        """,
            "",
        )
    }

    @Test
    fun testNoMatch() {
        val result =
            execute(
                """
            return string.match("hello", "%d+")
        """,
            )
        assertTrue(result is LuaNil)
    }

    @Test
    fun testMatchPosition() {
        assertLuaString(
            """
            return string.match("hello world", "%a+", 7)
        """,
            "world",
        )
    }

    @Test
    fun testGreedyVsNonGreedy() {
        // * is greedy, - is non-greedy
        assertLuaString(
            """
            local s = "<tag>content</tag>"
            return string.match(s, "<(.-)>")
        """,
            "tag",
        )
    }

    // ========== Balanced Patterns ==========

    @Test
    fun testBalancedParentheses() {
        assertLuaString(
            """
            return string.match("text (hello world) more", "%b()")
        """,
            "(hello world)",
        )
    }

    @Test
    fun testBalancedBrackets() {
        assertLuaString(
            """
            return string.match("code: [1,2,3] end", "%b[]")
        """,
            "[1,2,3]",
        )
    }

    @Test
    fun testBalancedNested() {
        assertLuaString(
            """
            return string.match("data {a:{b:1}} rest", "%b{}")
        """,
            "{a:{b:1}}",
        )
    }

    @Test
    fun testBalancedInGsub() {
        assertLuaString(
            """
            return string.gsub("keep (remove this) keep", "%b()", "")
        """,
            "keep  keep",
        )
    }

    // ========== Frontier Patterns ==========

    @Test
    fun testFrontierExtractWords() {
        val result =
            execute(
                """
            local words = {}
            for w in string.gmatch("hello world test", "%f[%a]%a+%f[%A]") do
                table.insert(words, w)
            end
            return table.concat(words, "-")
        """,
            )
        assertLuaString(result, "hello-world-test")
    }

    @Test
    fun testFrontierDigitBoundary() {
        assertLuaString(
            """
            return string.match("abc123def", "%f[%d]%d+")
        """,
            "123",
        )
    }
}
