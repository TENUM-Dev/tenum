package ai.tenum.lua.compat.core

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.LuaVm
import ai.tenum.lua.vm.LuaVmImpl
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Lua 5.4.8 Compatibility Test Suite - Core Language Features
 * Based on: testes/literals.lua
 *
 * Tests literal values and basic types:
 * - Number literals (decimal, hex, scientific notation)
 * - String literals (escape sequences, long strings)
 * - Boolean and nil literals
 */
class LiteralsCompatTest {
    private fun execute(code: String): LuaValue<*> {
        val vm: LuaVm = LuaVmImpl()
        return vm.execute(code)
    }

    // ========== BASIC LITERALS ==========

    @Test
    fun testNilLiteral() {
        val result = execute("return nil")
        assertEquals(LuaNil, result)
    }

    @Test
    fun testBooleanLiterals() {
        val resultTrue = execute("return true")
        assertEquals(LuaBoolean.TRUE, resultTrue)
        assertTrue(resultTrue is LuaBoolean)
        assertEquals(true, (resultTrue as LuaBoolean).value)

        val resultFalse = execute("return false")
        assertEquals(LuaBoolean.FALSE, resultFalse)
        assertTrue(resultFalse is LuaBoolean)
        assertEquals(false, (resultFalse as LuaBoolean).value)
    }

    // ========== NUMBER LITERALS - DECIMAL ==========

    @Test
    fun testDecimalIntegerLiterals() {
        // Basic integers
        assertEquals(0.0, (execute("return 0") as LuaNumber).value)
        assertEquals(1.0, (execute("return 1") as LuaNumber).value)
        assertEquals(42.0, (execute("return 42") as LuaNumber).value)
        assertEquals(123456789.0, (execute("return 123456789") as LuaNumber).value)
    }

    @Test
    fun testDecimalFloatLiterals() {
        // Basic floats
        assertEquals(3.14, (execute("return 3.14") as LuaNumber).value)
        assertEquals(0.5, (execute("return 0.5") as LuaNumber).value)
        assertEquals(0.0, (execute("return 0.0") as LuaNumber).value)
        assertEquals(1.0, (execute("return 1.0") as LuaNumber).value)
    }

    @Test
    fun testDecimalNotation() {
        // Decimal point variations
        assertEquals(0.0, (execute("return .0") as LuaNumber).value)
        assertEquals(0.0, (execute("return 0.") as LuaNumber).value)
        assertEquals(1.0, (execute("return 1.") as LuaNumber).value)
        assertEquals(0.5, (execute("return .5") as LuaNumber).value)
    }

    @Test
    fun testScientificNotation() {
        // Scientific notation
        assertEquals(0.0, (execute("return 0e12") as LuaNumber).value)
        assertEquals(20.0, (execute("return .2e2") as LuaNumber).value)
        assertEquals(0.2, (execute("return 2.E-1") as LuaNumber).value)
        assertEquals(100.0, (execute("return 1e2") as LuaNumber).value)
        assertEquals(0.01, (execute("return 1e-2") as LuaNumber).value)
        assertEquals(1000.0, (execute("return 1E3") as LuaNumber).value)
    }

    @Test
    fun testNegativeNumbers() {
        assertEquals(-1.0, (execute("return -1") as LuaNumber).value)
        assertEquals(-3.14, (execute("return -3.14") as LuaNumber).value)
        assertEquals(-10.0, (execute("return -1e1") as LuaNumber).value)
    }

    // ========== NUMBER LITERALS - HEXADECIMAL ==========

    @Test
    fun testHexadecimalIntegerLiterals() {
        // Hexadecimal integers
        assertEquals(0.0, (execute("return 0x0") as LuaNumber).value)
        assertEquals(15.0, (execute("return 0xF") as LuaNumber).value)
        assertEquals(15.0, (execute("return 0xf") as LuaNumber).value)
        assertEquals(255.0, (execute("return 0xFF") as LuaNumber).value)
        assertEquals(255.0, (execute("return 0xff") as LuaNumber).value)
        assertEquals(4095.0, (execute("return 0xFFF") as LuaNumber).value)
        assertEquals(65535.0, (execute("return 0xFFFF") as LuaNumber).value)
    }

    @Test
    fun testHexadecimalFloatLiterals() {
        // Hexadecimal floats with exponents
        assertEquals(0.0625, (execute("return 0x0.1") as LuaNumber).value, 0.0001)
        assertEquals(0.5, (execute("return 0x0.8") as LuaNumber).value, 0.0001)

        // Hex with p-notation (base-2 exponent)
        assertEquals(32.0, (execute("return 0x10p1") as LuaNumber).value)
        assertEquals(8.0, (execute("return 0x10p-1") as LuaNumber).value)
    }

    // ========== STRING LITERALS ==========

    @Test
    fun testBasicStringLiterals() {
        assertEquals("hello", (execute("return 'hello'") as LuaString).value)
        assertEquals("world", (execute("return \"world\"") as LuaString).value)
        assertEquals("", (execute("return ''") as LuaString).value)
        assertEquals("", (execute("return \"\"") as LuaString).value)
    }

    @Test
    fun testStringQuoteVariations() {
        // Single quotes
        assertEquals("test", (execute("return 'test'") as LuaString).value)
        // Double quotes
        assertEquals("test", (execute("return \"test\"") as LuaString).value)
        // Mixed content
        assertEquals("it's", (execute("return \"it's\"") as LuaString).value)
        assertEquals("say \"hi\"", (execute("return 'say \"hi\"'") as LuaString).value)
    }

    // ========== ESCAPE SEQUENCES ==========

    @Test
    fun testBasicEscapeSequences() {
        // Basic escapes
        assertEquals("\n", (execute("""return "\n"""") as LuaString).value)
        assertEquals("\t", (execute("""return "\t"""") as LuaString).value)
        assertEquals("\r", (execute("""return "\r"""") as LuaString).value)
        assertEquals("\\", (execute("""return "\\"""") as LuaString).value)
        assertEquals("\"", (execute("""return "\""""") as LuaString).value)
        assertEquals("'", (execute("""return "\'"""") as LuaString).value)
    }

    @Test
    fun testExtendedEscapeSequences() {
        // Extended escapes (may not all be supported yet)
        // \a = bell (ASCII 7)
        // \b = backspace (ASCII 8)
        // \f = form feed (ASCII 12)
        // \v = vertical tab (ASCII 11)

        // Test what we can
        assertEquals("\u0000", (execute("""return "\0"""") as LuaString).value)
    }

    @Test
    fun testDecimalEscapeSequences() {
        // Decimal escapes \ddd (up to 3 digits)
        assertEquals("c", (execute("""return "\99"""") as LuaString).value) // ASCII 99 = 'c'
        assertEquals("c12", (execute("""return "\09912"""") as LuaString).value) // \099 = 'c', then '12'
        assertEquals("cab", (execute("""return "\99ab"""") as LuaString).value)

        // Null bytes
        assertEquals("\u0000", (execute("""return "\0"""") as LuaString).value)
        assertEquals("\u0000\u0000\u0000alo", (execute("""return "\0\0\0alo"""") as LuaString).value)
    }

    @Test
    fun testHexadecimalEscapeSequences() {
        // Hexadecimal escapes \xHH
        assertEquals("\u0000\u0005\u0010\u001f", (execute("""return "\x00\x05\x10\x1f"""") as LuaString).value)
        assertEquals("\u007F", (execute("""return "\x7F"""") as LuaString).value)
        assertEquals("\u00FF", (execute("""return "\xFF"""") as LuaString).value)
    }

    @Test
    fun testZEscapeSequence() {
        // \z skips subsequent whitespace
        assertEquals(
            "abcdefghi",
            (
                execute(
                    """return "abc\z
        def\z
        ghi\z
       """",
                ) as LuaString
            ).value,
        )
        assertEquals(
            "abc",
            (
                execute(
                    """return "abc\z  

"""",
                ) as LuaString
            ).value,
        )
    }

    @Test
    fun testZEscapeWithDebugGetinfo() {
        // Test from literals.lua:34-41
        // The \z escape works, AND debug.getinfo should report correct line numbers
        val tab = "\t"
        val formFeed = "\u000C"
        val verticalTab = "\u000B"

        execute(
            """
            local function lexstring(x, y, n)
                local code = 'return ' .. x .. ', require"debug".getinfo(1).currentline'
                local f = assert(load(code, ''))
                local s, l = f()
                assert(s == y, "Expected string: '" .. tostring(y) .. "', got: '" .. tostring(s) .. "'")
                assert(l == n, "Expected line: " .. tostring(n) .. ", got: " .. tostring(l))
            end
            
            -- Test 1: \z skips spaces and newline
            lexstring("'abc\\z  \n   efg'", "abcefg", 2)
            
            -- Test 2: \z skips spaces and multiple newlines
            lexstring("'abc\\z  \n\n\n'", "abc", 4)
            
            -- Test 3: \z skips all whitespace including tab, formfeed, verticaltab
            lexstring("'\\z  \n\t\f\v\n'",  "", 3)
            """,
        )
    }

    // ========== UNICODE ESCAPE SEQUENCES ==========

    @Test
    fun testUnicodeEscapeSequences() {
        // Unicode escapes \u{XXX}
        assertEquals("\u0000", (execute("""return "\u{0}"""") as LuaString).value)
        assertEquals("\u0000", (execute("""return "\u{00000000}"""") as LuaString).value)

        // 1-byte sequences (ASCII range)
        assertEquals("\u0000\u007F", (execute("""return "\u{0}\u{7F}"""") as LuaString).value)

        // 2-byte sequences
        // \u{80} to \u{7FF}

        // 3-byte sequences
        // \u{800} to \u{FFFF}

        // Note: Full Unicode support depends on platform string encoding
    }

    @Test
    fun testUnicodeEscapeSequencesBeyondBMP() {
        // 4-byte sequences (beyond BMP - Basic Multilingual Plane)
        // U+10000 = 0xF0 0x90 0x80 0x80 in UTF-8
        val result = execute("""return "\u{10000}"""") as LuaString
        val str = result.value

        // In LuaString, bytes are stored as characters U+0000 to U+00FF (Latin-1 encoding)
        // Verify UTF-8 encoding matches Lua 5.4 spec
        assertEquals(4, str.length)
        assertEquals(0xF0, str[0].code)
        assertEquals(0x90, str[1].code)
        assertEquals(0x80, str[2].code)
        assertEquals(0x80, str[3].code)

        // Test range from literals.lua:69
        // "\u{10000}\u{1FFFFF}" == "\xF0\x90\x80\x80\xF7\xBF\xBF\xBF"
        val result2 = execute("""return "\u{10000}\u{1FFFFF}"""") as LuaString
        val str2 = result2.value
        assertEquals(8, str2.length)
        assertEquals(0xF0, str2[0].code)
        assertEquals(0x90, str2[1].code)
        assertEquals(0x80, str2[2].code)
        assertEquals(0x80, str2[3].code)
        assertEquals(0xF7, str2[4].code)
        assertEquals(0xBF, str2[5].code)
        assertEquals(0xBF, str2[6].code)
        assertEquals(0xBF, str2[7].code)
    }

    @Test
    fun testUnicodeEscapeSequences5And6Byte() {
        // 5-byte sequences (Lua extension, not standard UTF-8)
        // U+200000 = 0xF8 0x88 0x80 0x80 0x80
        val result5byte = execute("""return "\u{200000}"""") as LuaString
        val str5 = result5byte.value
        assertEquals(5, str5.length)
        assertEquals(0xF8, str5[0].code)
        assertEquals(0x88, str5[1].code)
        assertEquals(0x80, str5[2].code)
        assertEquals(0x80, str5[3].code)
        assertEquals(0x80, str5[4].code)

        // 6-byte sequences (Lua extension, not standard UTF-8)
        // U+4000000 = 0xFC 0x84 0x80 0x80 0x80 0x80
        val result6byte = execute("""return "\u{4000000}"""") as LuaString
        val str6 = result6byte.value
        assertEquals(6, str6.length)
        assertEquals(0xFC, str6[0].code)
        assertEquals(0x84, str6[1].code)
        assertEquals(0x80, str6[2].code)
        assertEquals(0x80, str6[3].code)
        assertEquals(0x80, str6[4].code)
        assertEquals(0x80, str6[5].code)
    }

    // ========== LONG STRING LITERALS ==========

    @Test
    fun testLongStringLiterals() {
        // Long strings [[ ... ]]
        assertEquals("hello", (execute("return [[hello]]") as LuaString).value)
        assertEquals("hello\nworld", (execute("return [[hello\nworld]]") as LuaString).value)

        // Long strings ignore first newline
        assertEquals("hello\n", (execute("return [[\nhello\n]]") as LuaString).value)
    }

    @Test
    fun testLongStringWithEquals() {
        // Long strings with = for nesting [=[ ... ]=]
        assertEquals("]=", (execute("return [==[]=]==]") as LuaString).value)
        assertEquals("[[]]", (execute("return [=[[[]]]=]") as LuaString).value)

        // Multi-level nesting
        assertEquals("]]]]]]]]", (execute("return [=[]]]]]]]]]=]") as LuaString).value)
    }

    @Test
    fun testLongStringLineEndings() {
        // Testing different line ending conversions
        // All should normalize to \n
        val code1 = "return [[\nalo\nalo\n\n]]"
        assertEquals("alo\nalo\n\n", (execute(code1) as LuaString).value)

        val code2 = "return [[\nalo\ralo\n\n]]"
        // \r should convert to \n
        val result2 = (execute(code2) as LuaString).value
        assertTrue(result2.contains("alo") && result2.contains("\n"))
    }

    @Test
    fun testLongStringCarriageReturnNormalization() {
        // From literals.lua:47 - tests \r and \r\n normalization
        // Expected behavior (verified with lua54):
        // [[\nalo\ralo\r\n]] produces "alo\nalo\n" (8 bytes)
        //   - First \n is skipped (first newline rule)
        //   - "alo" remains
        //   - \r becomes \n
        //   - "alo" remains
        //   - \r\n becomes \n

        val cr = '\r'
        val lf = '\n'

        // Test case 1: [[\nalo\ralo\r\n]]
        val code1 = "return [[$lf" + "alo" + "$cr" + "alo" + "$cr$lf]]"
        val result1 = (execute(code1) as LuaString).value
        assertEquals(8, result1.length, "Should have 8 characters")
        assertEquals("alo${lf}alo$lf", result1)

        // Test case 2: [[\ralo\n\ralo\r\n]] - first \r is skipped
        val code2 = "return [[$cr" + "alo" + "$lf$cr" + "alo" + "$cr$lf]]"
        val result2 = (execute(code2) as LuaString).value
        assertEquals(8, result2.length, "Should have 8 characters")
        assertEquals("alo${lf}alo$lf", result2)
    }

    // ========== MIXED TESTS ==========

    @Test
    fun testMixedLiterals() {
        // Using different literal types together
        val code =
            """
            local n = 42
            local s = "test"
            local b = true
            return n
            """.trimIndent()

        assertEquals(42.0, (execute(code) as LuaNumber).value)
    }

    // ========== OCTAL LITERALS (Deprecated in Lua 5.2+) ==========

    @Test
    fun testOctalLiterals() {
        // In Lua 5.4, octal literals are written as 0o... not 0...
        // But decimal escape sequences still work: \ddd

        // This is a decimal 10, not octal
        assertEquals(10.0, (execute("return 010") as LuaNumber).value)
    }

    // ========== ERROR CASES ==========

    @Test
    fun testInvalidHexEscapeSequences() {
        // Test from literals.lua:86-95
        // Hex escape \x requires exactly 2 hex digits
        // The error message must match Lua 5.4 format: "hexadecimal digit expected near '<token>'"

        // Helper function matching literals.lua lexerror pattern
        fun checkLexerror(
            code: String,
            expectedNear: String,
        ) {
            val ex =
                assertFailsWith<Exception> {
                    execute("return $code")
                }
            val msg = ex.message ?: ""

            // Must contain "hexadecimal digit expected"
            assertTrue(
                msg.contains("hexadecimal digit expected"),
                "Expected 'hexadecimal digit expected', got: $msg",
            )

            // Must match pattern "near .-<expectedNear>'"
            val pattern = Regex("near .*${Regex.escape(expectedNear)}'")
            assertTrue(
                pattern.containsMatchIn(msg),
                "Expected pattern 'near .-$expectedNear' in: $msg",
            )
        }

        // Test cases from literals.lua:86-95
        // expectedNear values are from literals.lua - they are Lua patterns to match against error message
        checkLexerror(""""abc\x"""", """\x"""") // \x at end, closing quote included
        checkLexerror(""""abc\x""", """\x""") // \x at end, no closing quote
        checkLexerror(""""\x""", """\x""") // \x at end, no closing quote
        checkLexerror(""""\x5"""", """\x5"""") // \x5 at end, closing quote included
        checkLexerror(""""\x5""", """\x5""") // \x5 at end, no closing quote
        checkLexerror(""""\xr"""", """\xr""") // \xr at end, no closing quote (r consumed as first invalid digit)
        checkLexerror(""""\xr""", """\xr""") // \xr at end, no closing quote
        checkLexerror(""""\x.""", """\x.""") // \x. at end, no closing quote
        checkLexerror(""""\x8%"""", """\x8%""") // \x8% at end, no closing quote (% consumed as second invalid digit)
        checkLexerror(""""\xAG""", """\xAG""") // \xAG at end, no closing quote
    }

    @Test
    fun testInvalidEscapeSequences() {
        fun checkLexerror(
            code: String,
            expectedNear: String,
        ) {
            val exception =
                assertFailsWith<Exception> {
                    execute("return $code")
                }
            val msg = exception.message ?: ""

            // Must contain "invalid escape sequence"
            assertTrue(
                msg.contains("invalid escape sequence"),
                "Expected 'invalid escape sequence', got: $msg",
            )

            // Must match pattern "near .-<expectedNear>'"
            val pattern = Regex("near .*${Regex.escape(expectedNear)}'")
            assertTrue(
                pattern.containsMatchIn(msg),
                "Expected pattern 'near .-$expectedNear' in: $msg",
            )
        }

        // Test cases from literals.lua:96-98
        checkLexerror(""""\g"""", """\g""") // Invalid escape \g with closing quote
        checkLexerror(""""\g""", """\g""") // Invalid escape \g without closing quote
        checkLexerror(""""\."""", """\.""") // Invalid escape \. with closing quote
    }

    @Test
    fun testDecimalEscapeOverflow() {
        fun checkLexerror(
            code: String,
            expectedNear: String,
        ) {
            val exception =
                assertFailsWith<Exception> {
                    execute("return $code")
                }
            val msg = exception.message ?: ""

            // Must contain "decimal escape too large"
            assertTrue(
                msg.contains("decimal escape") || msg.contains("too large"),
                "Expected 'decimal escape too large', got: $msg",
            )

            // Must match pattern "near .-<expectedNear>'"
            val pattern = Regex("near .*${Regex.escape(expectedNear)}'")
            assertTrue(
                pattern.containsMatchIn(msg),
                "Expected pattern 'near .-$expectedNear' in: $msg",
            )
        }

        // Test cases from literals.lua:100-102
        checkLexerror(""""\999"""", """"\999"""") // Decimal escape > 255
        checkLexerror(""""xyz\300"""", """"xyz\300"""") // Decimal escape 300 > 255
        checkLexerror(""""   \256"""", """"   \256"""") // Decimal escape 256 > 255
    }

    @Test
    fun testUnicodeEscapeErrors() {
        fun checkLexerror(
            code: String,
            expectedNear: String,
            expectedError: String,
        ) {
            val exception =
                assertFailsWith<Exception> {
                    execute("return $code")
                }
            val msg = exception.message ?: ""

            // Must contain expected error message
            assertTrue(
                msg.contains(expectedError),
                "Expected '$expectedError', got: $msg",
            )

            // Must match pattern "near .-<expectedNear>'"
            val pattern = Regex("near .*${Regex.escape(expectedNear)}'")
            assertTrue(
                pattern.containsMatchIn(msg),
                "Expected pattern 'near .-$expectedNear' in: $msg",
            )
        }

        // Test cases from literals.lua:105-110
        checkLexerror(""""abc\u{100000000}"""", """abc\u{100000000""", "UTF-8 value too large")
        checkLexerror(""""abc\u11r"""", """abc\u1""", "missing '{'")
        checkLexerror(""""abc\u"""", """abc\u"""", "missing '{'")
        checkLexerror(""""abc\u{11r"""", """abc\u{11r""", "missing '}'")
        checkLexerror(""""abc\u{11"""", """abc\u{11"""", "missing '}'")
        checkLexerror(""""abc\u{11""", """abc\u{11""", "missing '}'")
        checkLexerror(""""abc\u{r"""", """abc\u{r""", "hexadecimal digit expected") // literals.lua:114
    }

    companion object {
        // Helper to check if a value is approximately equal (for floating point)
        private fun assertEquals(
            expected: Double,
            actual: Number,
            delta: Double = 0.0001,
        ) {
            val diff = abs(expected - actual.toDouble())
            assertTrue(diff < delta, "Expected $expected but got $actual (diff: $diff)")
        }
    }

    @Test
    fun testVerticalTabAndFormFeedAsWhitespace() {
        // Lua 5.4 treats \v (vertical tab) and \f (form feed) as whitespace
        // outside of string literals. Test literal characters in source code.
        val result = execute("x \u000B\u000C= \t\r 123 \u000B\u000C\u000C; return x") as LuaNumber
        assertEquals(123.0, result.toDouble())
    }

    @Test
    fun testUnicodeEscapeErrorViaLoad() {
        // This test mimics exactly what literals.lua does at line 114
        val code =
            """
            local s = [["abc\u{r"]]
            local err = [[abc\u{r]]
            local st, msg = load('return ' .. s, '')
            if st then error("load should have failed") end
            if err ~= '<eof>' then err = err .. "'" end
            print("Error message: [" .. msg .. "]")
            print("Pattern: [near .-" .. err .. "]")
            local found = string.find(msg, "near .-" .. err)
            if not found then
                error("Pattern not found in error message")
            end
            return true
            """.trimIndent()

        val result = execute(code)
        assertEquals(LuaBoolean.TRUE, result)
    }

    @Test
    fun testUnfinishedStrings() {
        // Test from literals.lua:113-120
        // Mimics the lexerror() function pattern
        val code =
            """
            local function lexerror (s, err)
              local st, msg = load('return ' .. s, '')
              if err ~= '<eof>' then err = err .. "'" end
              assert(not st and string.find(msg, "near .-" .. err))
            end
            
            -- unfinished strings
            lexerror("[=[alo]]", "<eof>")
            lexerror("[=[alo]=", "<eof>")
            lexerror("[=[alo]", "<eof>")
            lexerror("'alo", "<eof>")
            lexerror("'alo \\z  \n\n", "<eof>")
            lexerror("'alo \\z", "<eof>")
            lexerror([['alo \98]], "<eof>")
            
            return true
            """.trimIndent()

        val result = execute(code)
        assertEquals(LuaBoolean.TRUE, result)
    }

    @Test
    fun testInvalidVariableNameStart() {
        // Direct test: # cannot start a variable name
        val code =
            """
            local f, err = load("#=1", "")
            assert(not f, "load('#=1') should fail")
            assert(err ~= nil, "error message should be present")
            assert(string.find(err, "unexpected"), "error should mention 'unexpected'")
            return true
            """.trimIndent()

        val result = execute(code)
        assertEquals(LuaBoolean.TRUE, result)
    }

    @Test
    fun testValidVariableNameCharacters() {
        // Test from literals.lua:123-127
        // Characters that match [a-zA-Z_] should be valid variable name starts
        // Characters that don't match should cause load() to fail
        val code =
            """
            -- Test a few key characters that should fail
            -- # (char 35) does not match [a-zA-Z_], so load("#=1", "") should fail
            local f, err = load("#=1", "")
            assert(not f, "# should not be valid variable name start")
            
            -- @ (char 64) does not match [a-zA-Z_], so load("@=1", "") should fail  
            f, err = load("@=1", "")
            assert(not f, "@ should not be valid variable name start")
            
            -- Test that valid characters work
            f, err = load("a=1", "")
            assert(f, "a should be valid variable name start")
            
            f, err = load("_=1", "")
            assert(f, "_ should be valid variable name start")
            
            return true
            """.trimIndent()

        val result = execute(code)
        assertEquals(LuaBoolean.TRUE, result)
    }

    @Test
    fun testNewlineInQuotedString() {
        // Test from literals.lua:330
        // Actual newlines (not escape sequences) inside quoted strings should fail
        val code =
            """
            assert(not load"a = 'non-ending string")
            assert(not load"a = 'non-ending string\n'")
            assert(not load"a = '\\345'")
            assert(not load"a = [=x]")
            return true
            """.trimIndent()

        val result = execute(code)
        assertEquals(LuaBoolean.TRUE, result)
    }
}
