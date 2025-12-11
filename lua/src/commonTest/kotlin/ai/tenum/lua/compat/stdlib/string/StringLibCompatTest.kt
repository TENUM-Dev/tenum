package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Compatibility tests for Lua 5.4 String Library
 * Based on strings.lua from the official Lua test suite
 *
 * Coverage:
 * - string.len, string.sub, string.char, string.byte
 * - string.upper, string.lower
 * - string.rep, string.reverse
 * - string.find, string.match, string.gmatch (without patterns)
 * - string.gsub (without patterns)
 * - string.format (basic formatting)
 */
class StringLibCompatTest : LuaCompatTestBase() {
    // ============================================================================
    // string.len - Get string length
    // ============================================================================

    @Test
    fun testStringLen() {
        assertLuaNumber("return string.len('hello')", 5.0)
    }

    @Test
    fun testStringLenEmpty() {
        assertLuaNumber("return string.len('')", 0.0)
    }

    @Test
    fun testStringLenWithSpaces() {
        assertLuaNumber("return string.len('hello world')", 11.0)
    }

    // ============================================================================
    // string.sub - Extract substring
    // ============================================================================

    @Test
    fun testStringSub() {
        assertLuaString("return string.sub('hello', 2, 4)", "ell")
    }

    @Test
    fun testStringSubFromStart() {
        assertLuaString("return string.sub('hello', 1, 3)", "hel")
    }

    @Test
    fun testStringSubToEnd() {
        assertLuaString("return string.sub('hello', 3)", "llo")
    }

    @Test
    fun testStringSubNegativeIndex() {
        assertLuaString("return string.sub('hello', -3, -1)", "llo")
    }

    @Test
    fun testStringSubNegativeStart() {
        assertLuaString("return string.sub('hello', -4, 4)", "ell")
    }

    @Test
    fun testStringSubOutOfBounds() {
        assertLuaString("return string.sub('hello', 10, 20)", "")
    }

    @Test
    fun testStringSubZeroZero() {
        // Regression test: (0,0) should return empty, not "1"
        assertLuaString("return string.sub('123456789', 0, 0)", "")
    }

    @Test
    fun testStringSubNegativeOutOfBounds() {
        // Regression test: negative index beyond string start should clamp
        assertLuaString("return string.sub('123456789', -10, 10)", "123456789")
    }

    @Test
    fun testStringSubZeroToNegativeOne() {
        // i=0 clamped to 1, j=-1 is last char
        assertLuaString("return string.sub('123456789', 0, -1)", "123456789")
    }

    // ============================================================================
    // string.upper / string.lower - Case conversion
    // ============================================================================

    @Test
    fun testStringUpper() {
        assertLuaString("return string.upper('hello')", "HELLO")
    }

    @Test
    fun testStringUpperMixed() {
        assertLuaString("return string.upper('HeLLo WoRLd')", "HELLO WORLD")
    }

    @Test
    fun testStringLower() {
        assertLuaString("return string.lower('HELLO')", "hello")
    }

    @Test
    fun testStringLowerMixed() {
        assertLuaString("return string.lower('HeLLo WoRLd')", "hello world")
    }

    // ============================================================================
    // string.reverse - Reverse string
    // ============================================================================

    @Test
    fun testStringReverse() {
        assertLuaString("return string.reverse('hello')", "olleh")
    }

    @Test
    fun testStringReverseEmpty() {
        assertLuaString("return string.reverse('')", "")
    }

    @Test
    fun testStringReverseSingle() {
        assertLuaString("return string.reverse('a')", "a")
    }

    // ============================================================================
    // string.rep - Repeat string
    // ============================================================================

    @Test
    fun testStringRep() {
        assertLuaString("return string.rep('ab', 3)", "ababab")
    }

    @Test
    fun testStringRepOnce() {
        assertLuaString("return string.rep('hello', 1)", "hello")
    }

    @Test
    fun testStringRepZero() {
        assertLuaString("return string.rep('hello', 0)", "")
    }

    @Test
    fun testStringRepWithSeparator() {
        assertLuaString("return string.rep('x', 3, ',')", "x,x,x")
    }

    // ============================================================================
    // string.byte / string.char - Character code conversion
    // ============================================================================

    @Test
    fun testStringByte() {
        assertLuaNumber("return string.byte('A')", 65.0)
    }

    @Test
    fun testStringBytePosition() {
        assertLuaNumber("return string.byte('hello', 2)", 101.0) // 'e'
    }

    @Test
    fun testStringByteRange() {
        assertLuaNumber("local a, b, c = string.byte('abc', 1, 3); return a", 97.0) // 'a'
        assertLuaNumber("local a, b, c = string.byte('abc', 1, 3); return b", 98.0) // 'b'
        assertLuaNumber("local a, b, c = string.byte('abc', 1, 3); return c", 99.0) // 'c'
    }

    @Test
    fun testStringChar() {
        assertLuaString("return string.char(65)", "A")
    }

    @Test
    fun testStringCharMultiple() {
        assertLuaString("return string.char(72, 101, 108, 108, 111)", "Hello")
    }

    @Test
    fun testStringCharEmpty() {
        assertLuaString("return string.char()", "")
    }

    @Test
    fun testStringCharOutOfRangePositive() {
        // strings.lua line 101: checkerror("out of range", string.char, 256)
        assertThrowsError("return string.char(256)", "out of range")
    }

    @Test
    fun testStringCharOutOfRangeNegative() {
        // strings.lua line 102: checkerror("out of range", string.char, -1)
        assertThrowsError("return string.char(-1)", "out of range")
    }

    @Test
    fun testStringCharOutOfRangeMaxInt() {
        // strings.lua line 103: checkerror("out of range", string.char, math.maxinteger)
        assertThrowsError("return string.char(math.maxinteger)", "out of range")
    }

    @Test
    fun testStringCharOutOfRangeMinInt() {
        // strings.lua line 104: checkerror("out of range", string.char, math.mininteger)
        assertThrowsError("return string.char(math.mininteger)", "out of range")
    }

    @Test
    fun testStringCharWithStringByteFullString() {
        // strings.lua line 97: assert(string.char(string.byte("\xe4l\0�u", 1, -1)) == "\xe4l\0�u")
        // This tests that string.byte returns multiple values and string.char accepts them all
        assertLuaString(
            """
            return string.char(string.byte("hello", 1, -1))
        """,
            "hello",
        )
    }

    @Test
    fun testStringCharWithStringByteSpecialChars() {
        // strings.lua line 97 with special characters including null byte
        assertLuaString(
            """
            local s = "\xe4l\0\x75"
            return string.char(string.byte(s, 1, -1))
        """,
            "\u00E4l\u0000u",
        )
    }

    @Test
    fun testStringRepTooLarge() {
        // strings.lua line 114: checkerror("too large", string.rep, 'aa', (1 << 30))
        // Test with maxInt repetitions to ensure overflow detection
        assertThrowsError("return string.rep('test', 2147483647)", "too large")
    }

    @Test
    fun testStringRepTooLargeWithSeparator() {
        // strings.lua line 115: checkerror("too large", string.rep, 'a', (1 << 30), ',')
        assertThrowsError("return string.rep('a', 2147483647, ',')", "too large")
    }

    @Test
    fun testStringRepMaxIntegerOverflow() {
        // strings.lua line 123: assert(not pcall(string.rep, "aa", maxi // 2 + 10))
        // maxi = math.maxinteger, so maxi // 2 + 10 is huge
        assertThrowsError("return string.rep('aa', math.maxinteger // 2 + 10)", "too large")
    }

    @Test
    fun testStringRepMaxIntegerOverflowWithSeparator() {
        // strings.lua line 124: assert(not pcall(string.rep, "", maxi // 2 + 10, "aa"))
        assertThrowsError("return string.rep('', math.maxinteger // 2 + 10, 'aa')", "too large")
    }

    @Test
    fun testStringByteEmptyRange() {
        // Regression test: string.byte(str, 1, 0) should return nothing (empty range)
        // strings.lua line 98
        assertLuaNil("local a = string.byte('hello', 1, 0); return a")
    }

    @Test
    fun testStringByteNegativeOutOfBounds() {
        // Regression test: negative index beyond bounds clamps to 1
        // strings.lua line 99
        assertLuaString("return string.char(string.byte('hello', -10, 100))", "hello")
    }

    // ============================================================================
    // string.format - Formatted output (basic cases)
    // ============================================================================

    @Test
    fun testStringFormatString() {
        assertLuaString("return string.format('Hello %s', 'World')", "Hello World")
    }

    @Test
    fun testStringFormatNumber() {
        assertLuaString("return string.format('Number: %d', 42)", "Number: 42")
    }

    @Test
    fun testStringFormatFloat() {
        assertLuaString("return string.format('Pi: %.2f', 3.14159)", "Pi: 3.14")
    }

    @Test
    fun testStringFormatMultiple() {
        assertLuaString("return string.format('%s: %d', 'Count', 5)", "Count: 5")
    }

    @Test
    fun testStringFormatPercent() {
        assertLuaString("return string.format('100%%')", "100%")
    }

    // ============================================================================
    // string.find - Find substring (plain search, no patterns)
    // ============================================================================

    @Test
    fun testStringFindBasic() {
        assertLuaNumber("local i, j = string.find('hello world', 'world', 1, true); return i", 7.0)
    }

    @Test
    fun testStringFindEnd() {
        assertLuaNumber("local i, j = string.find('hello world', 'world', 1, true); return j", 11.0)
    }

    @Test
    fun testStringFindNotFound() {
        assertLuaNil("local i = string.find('hello', 'xyz', 1, true); return i")
    }

    @Test
    fun testStringFindFromPosition() {
        assertLuaNumber("local i = string.find('hello hello', 'hello', 2, true); return i", 7.0)
    }

    @Test
    fun testStringFindPlain() {
        // Plain search (no pattern matching)
        assertLuaNumber("local i = string.find('a.b', '.', 1, true); return i", 2.0)
    }

    @Test
    fun testStringFindPatternWildcard() {
        // Pattern search: '.' matches any character
        // In "1234567890123456789", ".45" matches "345" at position 3
        assertLuaNumber("local i = string.find('1234567890123456789', '.45'); return i", 3.0)
    }

    @Test
    fun testStringFindPatternWildcardWithNegativeStart() {
        // Regression test: pattern ".45" with init=-9 should start at position 11, find match at 13
        // String length 19, init=-9 → startPos = 19 + (-9) + 1 = 11
        // Matches "345" at position 13
        assertLuaNumber("local i = string.find('1234567890123456789', '.45', -9); return i", 13.0)
    }

    @Test
    fun testStringFindPatternEscaping() {
        // Pattern search: '%.' matches literal dot character
        assertLuaNumber("local i = string.find('a.b', '%.'); return i", 2.0)
    }

    @Test
    fun testStringFindNullCharNotFound() {
        // Regression test: searching for \0 in plain text that doesn't contain it should return nil
        // Line 65 of strings.lua
        assertLuaNil(
            """
            local i = string.find("abcdefg", "\0", 5, 1)
            return i
        """,
        )
    }

    @Test
    fun testStringFindEmptyPattern() {
        // Empty pattern matches at position 1 with endPos=0
        assertLuaNumber("local i = string.find('', ''); return i", 1.0)
        assertLuaNumber("local i, j = string.find('', ''); return j", 0.0)
    }

    @Test
    fun testStringFindEmptyPatternAtPosition() {
        // Empty pattern matches at start position
        assertLuaNumber("local i = string.find('hello', '', 3); return i", 3.0)
    }

    @Test
    fun testStringFindPatternEscapedParentheses() {
        // Regression test: pattern % escaping for special characters
        // Needed for tpack.lua checkerror patterns
        assertLuaNumber(
            """
            local i = string.find("integral size (17) out of limits [1,16]", "%(17%) out of limits %[1,16%]")
            return i
        """,
            15.0,
        )
    }

    // ============================================================================
    // string.gsub - Global substitution (plain, no patterns)
    // ============================================================================

    @Test
    fun testStringGsubBasic() {
        assertLuaString("local s = string.gsub('hello world', 'world', 'Lua'); return s", "hello Lua")
    }

    @Test
    fun testStringGsubCount() {
        assertLuaNumber("local s, n = string.gsub('hello hello', 'hello', 'hi'); return n", 2.0)
    }

    @Test
    fun testStringGsubLimit() {
        assertLuaString("local s = string.gsub('aa aa aa', 'aa', 'bb', 2); return s", "bb bb aa")
    }

    @Test
    fun testStringGsubNoMatch() {
        assertLuaString("local s = string.gsub('hello', 'xyz', 'abc'); return s", "hello")
    }

    // ============================================================================
    // Method call syntax (string:method)
    // ============================================================================

    @Test
    fun testStringMethodUpper() {
        assertLuaString("return ('hello'):upper()", "HELLO")
    }

    @Test
    fun testStringMethodSub() {
        assertLuaString("return ('hello'):sub(2, 4)", "ell")
    }

    @Test
    fun testStringMethodLen() {
        assertLuaNumber("return ('hello'):len()", 5.0)
    }

    // ============================================================================
    // Edge cases and error handling
    // ============================================================================

    @Test
    fun testStringSubEmptyString() {
        assertLuaString("return string.sub('', 1, 1)", "")
    }

    @Test
    fun testStringRepNegative() {
        assertLuaString("return string.rep('x', -1)", "")
    }

    @Test
    fun testStringByteOutOfRange() {
        assertLuaNil("return string.byte('hello', 10)")
    }

    @Test
    fun testStringLenNumber() {
        // Lua coerces numbers to strings
        assertLuaNumber("return string.len(123)", 3.0)
    }

    // ============================================================================
    // String comparisons (from strings.lua lines 18-35)
    // ============================================================================

    @Test
    fun testStringComparisonBasic() {
        assertLuaBoolean("return 'alo' < 'alo1'", true)
        assertLuaBoolean("return '' < 'a'", true)
    }

    @Test
    fun testStringComparisonWithNull() {
        assertLuaBoolean("return 'alo\\0alo' < 'alo\\0b'", true)
        assertLuaBoolean("return 'alo\\0alo\\0\\0' > 'alo\\0alo\\0'", true)
        assertLuaBoolean("return 'alo' < 'alo\\0'", true)
        assertLuaBoolean("return 'alo\\0' > 'alo'", true)
    }

    @Test
    fun testStringComparisonNullBytes() {
        assertLuaBoolean("return '\\0' < '\\1'", true)
        assertLuaBoolean("return '\\0\\0' < '\\0\\1'", true)
        assertLuaBoolean("return '\\1\\0a\\0a' <= '\\1\\0a\\0a'", true)
        assertLuaBoolean("return not ('\\1\\0a\\0b' <= '\\1\\0a\\0a')", true)
    }

    @Test
    fun testStringComparisonLengthMismatch() {
        assertLuaBoolean("return '\\0\\0\\0' < '\\0\\0\\0\\0'", true)
        assertLuaBoolean("return not('\\0\\0\\0\\0' < '\\0\\0\\0')", true)
        assertLuaBoolean("return '\\0\\0\\0' <= '\\0\\0\\0\\0'", true)
        assertLuaBoolean("return not('\\0\\0\\0\\0' <= '\\0\\0\\0')", true)
    }

    @Test
    fun testStringComparisonEqual() {
        assertLuaBoolean("return '\\0\\0\\0' <= '\\0\\0\\0'", true)
        assertLuaBoolean("return '\\0\\0\\0' >= '\\0\\0\\0'", true)
        assertLuaBoolean("return not ('\\0\\0b' < '\\0\\0a\\0')", true)
    }

    // ============================================================================
    // Additional string.sub tests from official suite
    // ============================================================================

    @Test
    fun testStringSubWithNullBytes() {
        assertLuaString("return string.sub('\\000123456789', 3, 5)", "234")
    }

    @Test
    fun testStringSubAsMethod() {
        assertLuaString("return ('\\000123456789'):sub(8)", "789")
    }

    // ============================================================================
    // Additional string.find tests from official suite
    // ============================================================================

    @Test
    fun testStringFindReturnValues() {
        val code = """
            local a, b = string.find('123456789', '345')
            return string.sub('123456789', a, b)
        """
        assertLuaString(code, "345")
    }

    @Test
    fun testStringFindStartFrom4() {
        assertLuaNumber("return string.find('1234567890123456789', '345', 4)", 13.0)
    }

    @Test
    fun testStringFindPatternNotFound() {
        assertLuaNil("return string.find('1234567890123456789', '346', 4)")
    }

    @Test
    fun testStringFindNegativeStartPos() {
        assertLuaNumber("return string.find('1234567890123456789', '.45', -9)", 13.0)
    }

    @Test
    fun testStringFindNullWithPlain() {
        assertLuaNil("return string.find('abcdefg', '\\0', 5, 1)")
    }

    @Test
    fun testStringFindEmptyPatterns() {
        assertLuaNumber("return string.find('', '')", 1.0)
        assertLuaNumber("return string.find('', '', 1)", 1.0)
        assertLuaNil("return string.find('', '', 2)")
        assertLuaNil("return string.find('', 'aaa', 1)")
    }

    @Test
    fun testStringFindLiteralDot() {
        val result = execute("return ('alo(.)alo'):find('(.)', 1, 1)")
        assertLuaNumber(result, 4.0)
    }

    @Test
    fun testStringFindIdenticalStringAndPattern() {
        // From errors.lua line 40: assert(string.find(msg, msg, 1, true))
        // This tests finding a string within itself with plain=true
        execute("local msg = 'test message'; assert(string.find(msg, msg, 1, true) == 1)")
        execute("local s = '[string \"test\"]:10: error near'; assert(string.find(s, s, 1, true) == 1)")

        // Should work with any string content
        execute("local x = 'hello world'; local i, j = string.find(x, x, 1, true); assert(i == 1 and j == 11)")
        execute("local x = ''; local i, j = string.find(x, x, 1, true); assert(i == 1 and j == 0)")
    }

    @Test
    fun testStringFindPatternAnchor() {
        // From errors.lua line 35: string.find(token, "^<%a")
        // ^ anchors pattern to start of string
        execute("assert(string.find('hello', '^hello') == 1)")
        execute("assert(string.find('hello', '^hell') == 1)")
        execute("assert(not string.find('hello', '^ello'))") // Should not match (not at start)
        execute("assert(not string.find('abcd', '^bcd'))") // Should not match

        // $ anchors to end of string
        execute("assert(string.find('hello', 'lo$') == 4)")
        execute("assert(not string.find('hello', 'hel$'))") // Should not match (not at end)

        // Test pattern with literal < (not character class)
        execute("assert(string.find('<name>', '^<') == 1)")
        execute("assert(not string.find('test', '^<'))")

        // Test escaping brackets and percent
        execute("assert(string.find('[test]', '%[') == 1)")
        execute("assert(string.find('[test]', '%]') == 6)")
        execute("assert(string.find('100%', '%%') == 4)")
    }

    @Test
    fun testStringFindPatternQuantifiers() {
        // From errors.lua line 38-39: pattern uses .* and .-
        // .* matches any sequence (greedy)
        execute("local s = 'abc123xyz'; assert(string.find(s, 'a.*z') == 1)")
        execute("local s = 'test'; local i, j = string.find(s, 't.*t'); assert(i == 1 and j == 4)")

        // .- matches any sequence (non-greedy) - simpler test
        execute("local s = 'axc'; local i, j = string.find(s, 'a.-c'); assert(i == 1 and j == 3)")

        // Test .* with escaped brackets (simpler version of errors.lua pattern)
        execute("local msg = '[string \"test\"]:1:'; assert(string.find(msg, '^%[string \".*\"%]:'))")
    }

    // ============================================================================
    // Character class tests
    // ============================================================================

    @Test
    fun testStringFindCharacterClassAlpha() {
        // %a matches letters (a-z, A-Z)
        execute("assert(string.find('abc', '%a') == 1)")
        execute("assert(string.find('ABC', '%a') == 1)")
        execute("assert(string.find('123abc', '%a') == 4)") // First letter is at position 4
        execute("assert(not string.find('123', '%a'))")

        // %A matches non-letters (uppercase = negation)
        execute("assert(string.find('123', '%A') == 1)")
        execute("assert(not string.find('abc', '%A'))")
    }

    @Test
    fun testStringFindCharacterClassDigit() {
        // %d matches digits (0-9)
        execute("assert(string.find('123', '%d') == 1)")
        execute("assert(string.find('abc123', '%d') == 4)")
        execute("assert(not string.find('abc', '%d'))")

        // %D matches non-digits
        execute("assert(string.find('abc', '%D') == 1)")
        execute("assert(not string.find('123', '%D'))")
    }

    @Test
    fun testStringFindCharacterClassLowerUpper() {
        // %l matches lowercase letters
        execute("assert(string.find('abc', '%l') == 1)")
        execute("assert(not string.find('ABC', '%l'))")

        // %u matches uppercase letters
        execute("assert(string.find('ABC', '%u') == 1)")
        execute("assert(not string.find('abc', '%u'))")

        // %L and %U are negations
        execute("assert(string.find('ABC', '%L') == 1)") // A is not lowercase
        execute("assert(string.find('abc', '%U') == 1)") // a is not uppercase
    }

    @Test
    fun testStringFindCharacterClassAlphanumeric() {
        // %w matches alphanumeric (letters + digits only, NOT underscore)
        execute("assert(string.find('abc', '%w') == 1)")
        execute("assert(string.find('123', '%w') == 1)")
        execute("assert(string.find('_test', '%w') == 2)") // '_' doesn't match, 't' matches at position 2
        execute("assert(not string.find('!@#', '%w'))")

        // %W matches non-alphanumeric
        execute("assert(string.find('!@#', '%W') == 1)")
        execute("assert(not string.find('abc123', '%W'))")
    }

    @Test
    fun testStringFindCharacterClassWhitespace() {
        // %s matches whitespace
        execute("assert(string.find(' ', '%s') == 1)")
        execute("assert(string.find('\\t', '%s') == 1)")
        execute("assert(string.find('\\n', '%s') == 1)")
        execute("assert(not string.find('abc', '%s'))")

        // %S matches non-whitespace
        execute("assert(string.find('abc', '%S') == 1)")
        execute("assert(not string.find(' ', '%S'))")
    }

    @Test
    fun testStringFindCharacterClassPunctuation() {
        // %p matches punctuation (not letter/digit/space)
        execute("assert(string.find('!', '%p') == 1)")
        execute("assert(string.find('.', '%p') == 1)")
        execute("assert(string.find('<', '%p') == 1)")
        execute("assert(string.find('>', '%p') == 1)")
        execute("assert(not string.find('abc', '%p'))")
        execute("assert(not string.find('123', '%p'))")

        // %P matches non-punctuation
        execute("assert(string.find('abc', '%P') == 1)")
        execute("assert(string.find('123', '%P') == 1)")
    }

    @Test
    fun testStringFindCharacterClassControl() {
        // %c matches control characters
        execute("assert(string.find('\\0', '%c') == 1)")
        execute("assert(string.find('\\t', '%c') == 1)")
        execute("assert(not string.find('abc', '%c'))")

        // %C matches non-control
        execute("assert(string.find('abc', '%C') == 1)")
    }

    @Test
    fun testStringFindCharacterClassHex() {
        // %x matches hexadecimal digits (0-9, a-f, A-F)
        execute("assert(string.find('0123456789', '%x') == 1)")
        execute("assert(string.find('abcdef', '%x') == 1)")
        execute("assert(string.find('ABCDEF', '%x') == 1)")
        execute("assert(not string.find('xyz', '%x'))")

        // %X matches non-hex
        execute("assert(string.find('xyz', '%X') == 1)")
    }

    @Test
    fun testStringFindCharacterClassWithAnchor() {
        // From errors.lua line 35: ^<%a pattern
        execute("assert(string.find('<eof>', '^<%a') == 1)")
        execute("assert(string.find('<name>', '^<%a') == 1)")
        execute("assert(not string.find('<123>', '^<%a'))") // < followed by digit, not letter
        execute("assert(not string.find('test', '^<%a'))") // Doesn't start with <

        // Test with char%( pattern (also from errors.lua)
        execute("assert(string.find('char(', '^char%(') == 1)")
        execute("assert(not string.find('test(', '^char%('))")
    }

    @Test
    fun testStringFindCharacterClassInComplexPattern() {
        // Character classes in more complex patterns
        execute("assert(string.find('test123', '%a%d') == 4)") // 't' followed by '1'
        execute("assert(string.find('abc def', '%s') == 4)") // Space at position 4
        execute("assert(string.find('[test]', '%[%a') == 1)") // '[' followed by letter

        // Test gsub with character classes (from errors.lua line 37)
        execute("local result = string.gsub('<test>', '(%p)', '%%%1'); assert(result == '%<test%>')")
    }

    // ============================================================================
    // Length operator tests
    // ============================================================================

    @Test
    fun testLengthOperatorEmpty() {
        assertLuaNumber("return #''", 0.0)
    }

    @Test
    fun testLengthOperatorWithNullBytes() {
        assertLuaNumber("return #'\\0\\0\\0'", 3.0)
        assertLuaNumber("return #'1234567890'", 10.0)
    }

    // ============================================================================
    // Additional string.byte/char tests from official suite
    // ============================================================================

    @Test
    fun testStringByteChar255() {
        val result = execute("return string.byte(string.char(255))")
        assertLuaNumber(result, 255.0)
    }

    @Test
    fun testStringByteChar0() {
        val result = execute("return string.byte(string.char(0))")
        assertLuaNumber(result, 0.0)
    }

    @Test
    fun testStringByteHighBitCheck() {
        val result = execute("return string.byte('\\xe4') > 127")
        assertLuaBoolean(result, true)
    }

    @Test
    fun testStringByteNullByteSequence() {
        assertLuaNumber("return string.byte('\\0')", 0.0)
        val result = execute("return string.byte('\\0\\0alo\\0x', -1) == string.byte('x')")
        assertLuaBoolean(result, true)
    }

    @Test
    fun testStringByteMultipleValues() {
        assertLuaNumber("return string.byte('\\n\\n', 2, -1)", 10.0)
        assertLuaNumber("return string.byte('\\n\\n', 2, 2)", 10.0)
    }

    @Test
    fun testStringByteInvalidPositions() {
        assertLuaNil("return string.byte('')")
        assertLuaNil("return string.byte('hi', -3)")
        assertLuaNil("return string.byte('hi', 3)")
        assertLuaNil("return string.byte('hi', 9, 10)")
        assertLuaNil("return string.byte('hi', 2, 1)")
    }

    @Test
    fun testStringCharNoArgs() {
        assertLuaString("return string.char()", "")
    }

    @Test
    fun testStringCharWithZeroAnd255() {
        // string.char creates string with null byte and byte 255
        assertLuaNumber("return #string.char(0, 255, 0)", 3.0)
        assertLuaNumber("return string.byte(string.char(0, 255, 0), 1)", 0.0)
        assertLuaNumber("return string.byte(string.char(0, 255, 0), 2)", 255.0)
        assertLuaNumber("return string.byte(string.char(0, 255, 0), 3)", 0.0)
    }

    @Test
    fun testStringCharByteRoundTrip() {
        val result = execute("return string.char(0, string.byte('\\xe4'), 0) == '\\0\\xe4\\0'")
        assertLuaBoolean(result, true)
    }

    @Test
    fun testStringCharByteFullString() {
        // Verify string.byte returns multiple values for range
        assertLuaNumber("local b1,b2,b3,b4,b5 = string.byte('hello', 1, -1); return b1", 104.0)
        assertLuaNumber("local b1,b2,b3,b4,b5 = string.byte('hello', 1, -1); return b5", 111.0)
    }

    @Test
    fun testStringCharByteEmpty() {
        assertLuaString("return string.char(string.byte('\\xe4l\\0�u', 1, 0))", "")
    }

    @Test
    fun testStringCharByteOutOfRange() {
        // Out of range indices should be clamped
        assertLuaNumber("local b1,b2,b3 = string.byte('abc', -10, 100); return b1", 97.0)
        assertLuaNumber("local b1,b2,b3 = string.byte('abc', -10, 100); return b3", 99.0)
    }

    // ============================================================================
    // string.upper/lower with special characters
    // ============================================================================

    @Test
    fun testStringUpperWithNullByte() {
        // Null byte is preserved but not visible in print
        assertLuaNumber("return #string.upper('ab\\0c')", 4.0)
        assertLuaNumber("return string.byte(string.upper('ab\\0c'), 3)", 0.0)
    }

    @Test
    fun testStringLowerWithSpecialChars() {
        // Null byte at start, verify length and special chars preserved
        assertLuaNumber("return #string.lower('\\0ABCc%$')", 7.0)
        assertLuaNumber("return string.byte(string.lower('\\0ABCc%$'), 1)", 0.0)
    }

    // ============================================================================
    // string.rep edge cases from official suite
    // ============================================================================

    @Test
    fun testStringRepEmptyString() {
        assertLuaString("return string.rep('', 10)", "")
    }

    @Test
    fun testStringRepWithNullBytes() {
        // Original string has null byte, verify doubled length
        assertLuaNumber("local s = 't\\195\\170s\\0t\\195\\170'; return #string.rep(s, 2)", 16.0)
    }

    @Test
    fun testStringRepWithSepEmpty() {
        assertLuaString("return string.rep('teste', 0, 'xuxu')", "")
        assertLuaString("return string.rep('teste', 1, 'xuxu')", "teste")
    }

    @Test
    fun testStringRepWithNullSeparator() {
        // Rep with null byte separator - verify length
        assertLuaNumber("return #string.rep('\\1\\0', 4, '\\0\\1')", 14.0)
    }

    @Test
    fun testStringRepEmptyWithSeparator() {
        val result1 = execute("return string.rep('', 10, '.')")
        val result2 = execute("return string.rep('.', 9)")
        // Both should produce 9 dots
        assertLuaString(result1, ".........")
        assertLuaString(result2, ".........")
    }

    @Test
    fun testStringRepLengthVerification() {
        val code = """
            for i=0,30 do
                if string.len(string.rep('a', i)) ~= i then
                    return false
                end
            end
            return true
        """
        assertLuaBoolean(code, true)
    }

    // ============================================================================
    // string.reverse edge cases
    // ============================================================================

    @Test
    fun testStringReverseNullBytes() {
        // Null bytes are preserved in reverse
        assertLuaNumber("return #string.reverse('\\0\\1\\2\\3')", 4.0)
        assertLuaNumber("return string.byte(string.reverse('\\0\\1\\2\\3'), 1)", 3.0)
        assertLuaNumber("return string.byte(string.reverse('\\0\\1\\2\\3'), 4)", 0.0)
    }

    // ============================================================================
    // tostring tests
    // ============================================================================

    @Test
    fun testTostringTypesAreString() {
        execute("assert(type(tostring(nil)) == 'string')")
        execute("assert(type(tostring(12)) == 'string')")
        execute("assert(#tostring('\\0') == 1)")
    }

    @Test
    fun testTostringBooleanValues() {
        assertLuaString("return tostring(true)", "true")
        assertLuaString("return tostring(false)", "false")
    }

    @Test
    fun testTostringNegativeNumbers() {
        assertLuaString("return tostring(-1203)", "-1203")
        assertLuaString("return tostring(-0.5)", "-0.5")
        assertLuaString("return tostring(-32767)", "-32767")
    }

    // ============================================================================
    // String concatenation operator
    // ============================================================================

    @Test
    fun testStringConcatWithNumber() {
        val result = execute("return '' .. 12")
        assertLuaString(result, "12")
    }
}
