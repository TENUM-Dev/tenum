package ai.tenum.lua.stdlib

// CPD-OFF: test file with intentional pattern matching test duplications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for LuaPattern engine
 * Tests the pattern matching logic independent of the Lua VM
 */
class LuaPatternTest {
    // ========== Basic Literal Matching ==========

    @Test
    fun testLiteralMatch() {
        val pattern = LuaPattern("hello")
        val result = pattern.find("hello world")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(5, result.end)
        assertEquals("hello", "hello world".substring(result.start, result.end))
    }

    @Test
    fun testLiteralNoMatch() {
        val pattern = LuaPattern("xyz")
        val result = pattern.find("hello world")
        assertNull(result)
    }

    @Test
    fun testLiteralMatchMiddle() {
        val pattern = LuaPattern("world")
        val result = pattern.find("hello world")
        assertNotNull(result)
        assertEquals(6, result.start)
        assertEquals(11, result.end)
    }

    // ========== Character Classes ==========

    @Test
    fun testCharacterClassDigit() {
        val pattern = LuaPattern("%d")
        val result = pattern.find("abc3def")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals("3", "abc3def".substring(result.start, result.end))
    }

    @Test
    fun testCharacterClassAlpha() {
        val pattern = LuaPattern("%a")
        val result = pattern.find("123a456")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals("a", "123a456".substring(result.start, result.end))
    }

    @Test
    fun testCharacterClassLower() {
        val pattern = LuaPattern("%l")
        val result = pattern.find("ABC123abc")
        assertNotNull(result)
        assertEquals(6, result.start)
        assertEquals(7, result.end)
        assertEquals("a", "ABC123abc".substring(result.start, result.end))
    }

    @Test
    fun testCharacterClassUpper() {
        val pattern = LuaPattern("%u")
        val result = pattern.find("abc123ABC")
        assertNotNull(result)
        assertEquals(6, result.start)
        assertEquals(7, result.end)
        assertEquals("A", "abc123ABC".substring(result.start, result.end))
    }

    @Test
    fun testCharacterClassSpace() {
        val pattern = LuaPattern("%s")
        val result = pattern.find("abc def")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals(" ", "abc def".substring(result.start, result.end))
    }

    @Test
    fun testCharacterClassAlphaNum() {
        val pattern = LuaPattern("%w")
        val result = pattern.find("!!!a123")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals("a", "!!!a123".substring(result.start, result.end))
    }

    @Test
    fun testCharacterClassNegatedDigit() {
        val pattern = LuaPattern("%D")
        val result = pattern.find("123abc")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals("a", "123abc".substring(result.start, result.end))
    }

    // ========== Repetition with Character Classes ==========

    @Test
    fun testDigitPlusRepetition() {
        val pattern = LuaPattern("%d+")
        val result = pattern.find("abc123def")
        assertNotNull(result, "Pattern %d+ should match digits in 'abc123def'")
        assertEquals(3, result.start, "Match should start at position 3")
        assertEquals(6, result.end, "Match should end at position 6")
        assertEquals("123", "abc123def".substring(result.start, result.end))
    }

    @Test
    fun testAlphaPlusRepetition() {
        val pattern = LuaPattern("%a+")
        val result = pattern.find("123abc456")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(6, result.end)
        assertEquals("abc", "123abc456".substring(result.start, result.end))
    }

    @Test
    fun testAlphaNumPlusRepetition() {
        val pattern = LuaPattern("%w+")
        val result = pattern.find("!!!abc123###")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(9, result.end)
        assertEquals("abc123", "!!!abc123###".substring(result.start, result.end))
    }

    @Test
    fun testSpacePlusRepetition() {
        val pattern = LuaPattern("%s+")
        val result = pattern.find("hello   world")
        assertNotNull(result)
        assertEquals(5, result.start)
        assertEquals(8, result.end)
        assertEquals("   ", "hello   world".substring(result.start, result.end))
    }

    // ========== Wildcard ==========

    @Test
    fun testWildcardDot() {
        val pattern = LuaPattern("a.b")
        val result = pattern.find("axb")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(3, result.end)
    }

    @Test
    fun testWildcardDotPlus() {
        val pattern = LuaPattern("a.+b")
        val result = pattern.find("a123b")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(5, result.end)
    }

    // ========== Sets ==========

    @Test
    fun testSetBasic() {
        val pattern = LuaPattern("[ace]")
        val result = pattern.find("xyz e ijk")
        assertNotNull(result)
        assertEquals(4, result.start)
        assertEquals(5, result.end)
        assertEquals("e", "xyz e ijk".substring(result.start, result.end))
    }

    @Test
    fun testSetRange() {
        val pattern = LuaPattern("[a-z]")
        val result = pattern.find("123x456")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals("x", "123x456".substring(result.start, result.end))
    }

    @Test
    fun testSetNegated() {
        val pattern = LuaPattern("[^0-9]")
        val result = pattern.find("123a456")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(4, result.end)
        assertEquals("a", "123a456".substring(result.start, result.end))
    }

    @Test
    fun testSetPlusRepetition() {
        val pattern = LuaPattern("[ace]+")
        val result = pattern.find("bdfacegh")
        assertNotNull(result)
        assertEquals(3, result.start)
        assertEquals(6, result.end)
        assertEquals("ace", "bdfacegh".substring(result.start, result.end))
    }

    // ========== Repetition Operators ==========

    @Test
    fun testStarRepetitionZeroMatches() {
        val pattern = LuaPattern("a*b")
        val result = pattern.find("b")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(1, result.end)
    }

    @Test
    fun testStarRepetitionMultipleMatches() {
        val pattern = LuaPattern("a*b")
        val result = pattern.find("aaab")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(4, result.end)
    }

    @Test
    fun testPlusRepetitionNoMatch() {
        val pattern = LuaPattern("a+b")
        val result = pattern.find("b")
        assertNull(result)
    }

    @Test
    fun testPlusRepetitionMatch() {
        val pattern = LuaPattern("a+b")
        val result = pattern.find("aaab")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(4, result.end)
    }

    @Test
    fun testMinusRepetitionShortest() {
        val pattern = LuaPattern("a-b")
        val result = pattern.find("aaab")
        assertNotNull(result)
        // Non-greedy means it tries shortest first at each position
        // But find() returns FIRST match position, which is position 0
        // At position 0, shortest successful match is all 3 a's + b
        assertEquals(0, result.start)
        assertEquals(4, result.end)
    }

    @Test
    fun testQuestionRepetitionZero() {
        val pattern = LuaPattern("a?b")
        val result = pattern.find("b")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(1, result.end)
    }

    @Test
    fun testQuestionRepetitionOne() {
        val pattern = LuaPattern("a?b")
        val result = pattern.find("ab")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(2, result.end)
    }

    // ========== Complex Repetition ==========

    @Test
    fun testMultipleRepetitions() {
        val pattern = LuaPattern("a*b+")
        val result = pattern.find("aaabbb")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(6, result.end)
    }

    @Test
    fun testDigitStarDigitPlus() {
        val pattern = LuaPattern("%d*%a+")
        val result = pattern.find("123abc")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(6, result.end)
    }

    // ========== Anchors ==========

    @Test
    fun testAnchorStart() {
        val pattern = LuaPattern("^hello")
        val result = pattern.find("hello world")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(5, result.end)
    }

    @Test
    fun testAnchorStartNoMatch() {
        val pattern = LuaPattern("^world")
        val result = pattern.find("hello world")
        assertNull(result)
    }

    @Test
    fun testAnchorEnd() {
        val pattern = LuaPattern("world$")
        val result = pattern.find("hello world")
        assertNotNull(result)
        assertEquals(6, result.start)
        assertEquals(11, result.end)
    }

    @Test
    fun testAnchorEndNoMatch() {
        val pattern = LuaPattern("hello$")
        val result = pattern.find("hello world")
        assertNull(result)
    }

    @Test
    fun testAnchorBoth() {
        val pattern = LuaPattern("^hello$")
        val result = pattern.find("hello")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(5, result.end)
    }

    // ========== Captures ==========

    @Test
    fun testCaptureBasic() {
        val pattern = LuaPattern("(%a+)")
        val result = pattern.find("123abc456")
        assertNotNull(result)
        assertEquals(1, result.captures.size)
        assertEquals("abc", result.captures[0].text)
    }

    @Test
    fun testCaptureMultiple() {
        val pattern = LuaPattern("(%a+) (%a+)")
        val result = pattern.find("hello world")
        assertNotNull(result)
        assertEquals(2, result.captures.size)
        assertEquals("hello", result.captures[0].text)
        assertEquals("world", result.captures[1].text)
    }

    @Test
    fun testCaptureWithRepetition() {
        val pattern = LuaPattern("(%d+)")
        val result = pattern.find("abc123def")
        assertNotNull(result)
        assertEquals(1, result.captures.size)
        assertEquals("123", result.captures[0].text)
    }

    // ========== Escaped Characters ==========

    @Test
    fun testEscapedDot() {
        val pattern = LuaPattern("a%.b")
        val result = pattern.find("a.b")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(3, result.end)
    }

    @Test
    fun testEscapedPlus() {
        val pattern = LuaPattern("a%+b")
        val result = pattern.find("a+b")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(3, result.end)
    }

    @Test
    fun testEscapedStar() {
        val pattern = LuaPattern("a%*b")
        val result = pattern.find("a*b")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(3, result.end)
    }

    // ========== Find All ==========

    @Test
    fun testFindAllMultipleMatches() {
        val pattern = LuaPattern("%d+")
        val results = pattern.findAll("a1b22c333d").toList()
        assertEquals(3, results.size)
        assertEquals("1", "a1b22c333d".substring(results[0].start, results[0].end))
        assertEquals("22", "a1b22c333d".substring(results[1].start, results[1].end))
        assertEquals("333", "a1b22c333d".substring(results[2].start, results[2].end))
    }

    @Test
    fun testFindAllWithCaptures() {
        val pattern = LuaPattern("(%a)%d")
        val results = pattern.findAll("a1b2c3").toList()
        assertEquals(3, results.size)
        assertEquals("a", results[0].captures[0].text)
        assertEquals("b", results[1].captures[0].text)
        assertEquals("c", results[2].captures[0].text)
    }

    // ========== Non-Greedy with Captures ==========

    @Test
    fun testNonGreedySimple() {
        val pattern = LuaPattern("a.-b")
        val result = pattern.find("axxxb")
        assertNotNull(result, "Pattern a.-b should match axxxb")
        assertEquals(0, result.start)
        assertEquals(5, result.end)
    }

    @Test
    fun testNonGreedyCaptureOnly() {
        val pattern = LuaPattern("(.-)x")
        val result = pattern.find("abcx")
        assertNotNull(result, "Pattern (.-)x should match abcx")
        assertEquals(1, result.captures.size)
        assertEquals("abc", result.captures[0].text)
    }

    @Test
    fun testNonGreedyWithCapture() {
        val pattern = LuaPattern("<(.-)>")
        val result = pattern.find("<tag>content</tag>")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(5, result.end)
        assertEquals(1, result.captures.size)
        assertEquals("tag", result.captures[0].text)
    }

    @Test
    fun testNonGreedyWithLiterals() {
        val pattern = LuaPattern("a.-b")
        val result = pattern.find("axxxbyyyb")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(5, result.end)
        assertEquals("axxxb", "axxxbyyyb".substring(result.start, result.end))
    }

    @Test
    fun testGreedyWithLiterals() {
        val pattern = LuaPattern("a.*b")
        val result = pattern.find("axxxbyyyb")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(9, result.end)
        assertEquals("axxxbyyyb", "axxxbyyyb".substring(result.start, result.end))
    }

    // ========== Balanced Patterns ==========

    @Test
    fun testBalancedParentheses() {
        val pattern = LuaPattern("%b()")
        val result = pattern.find("text (hello world) more")
        assertNotNull(result)
        assertEquals(5, result.start)
        assertEquals(18, result.end)
        assertEquals("(hello world)", "text (hello world) more".substring(result.start, result.end))
    }

    @Test
    fun testBalancedBrackets() {
        val pattern = LuaPattern("%b[]")
        val result = pattern.find("text [abc] more")
        assertNotNull(result)
        assertEquals(5, result.start)
        assertEquals(10, result.end)
        assertEquals("[abc]", "text [abc] more".substring(result.start, result.end))
    }

    @Test
    fun testBalancedNested() {
        val pattern = LuaPattern("%b{}")
        val result = pattern.find("text {outer {inner} more} end")
        assertNotNull(result)
        assertEquals(5, result.start)
        assertEquals(25, result.end)
        assertEquals("{outer {inner} more}", "text {outer {inner} more} end".substring(result.start, result.end))
    }

    @Test
    fun testBalancedUnbalanced() {
        val pattern = LuaPattern("%b()")
        val result = pattern.find("text (unbalanced")
        assertNull(result)
    }

    // ========== Frontier Patterns ==========

    @Test
    fun testFrontierWordBoundary() {
        val pattern = LuaPattern("%f[%a]the%f[%A]")
        val result = pattern.find("The cat in the hat")
        assertNotNull(result)
        assertEquals(11, result.start)
        assertEquals(14, result.end)
        assertEquals("the", "The cat in the hat".substring(result.start, result.end))
    }

    @Test
    fun testFrontierAtStart() {
        val pattern = LuaPattern("%f[%u]The")
        val result = pattern.find("The cat")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(3, result.end)
    }

    @Test
    fun testFrontierAtEnd() {
        val pattern = LuaPattern("abc%f[%z]")
        val result = pattern.find("abc")
        assertNotNull(result)
        assertEquals(0, result.start)
        assertEquals(3, result.end)
    }

    @Test
    fun testFrontierDigits() {
        val pattern = LuaPattern("%f[%d]%d+%f[%D]")
        val result = pattern.find("test123end")
        assertNotNull(result)
        assertEquals(4, result.start)
        assertEquals(7, result.end)
        assertEquals("123", "test123end".substring(result.start, result.end))
    }

    @Test
    fun testFrontierMultipleWords() {
        val pattern = LuaPattern("%f[%a]%a+%f[%A]")
        val results = pattern.findAll("hello world test").toList()
        assertEquals(3, results.size)
        assertEquals("hello", "hello world test".substring(results[0].start, results[0].end))
        assertEquals("world", "hello world test".substring(results[1].start, results[1].end))
        assertEquals("test", "hello world test".substring(results[2].start, results[2].end))
    }
}
