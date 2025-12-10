package ai.tenum.lua.lexer

// CPD-OFF: test file with intentional token assertion pattern duplications for readability

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for the Lua lexer
 */
class LexerTest {
    @Test
    fun testEmptySource() =
        runTest {
            val lexer = Lexer("")
            val tokens = lexer.scanTokens()

            assertEquals(1, tokens.size)
            assertEquals(TokenType.EOF, tokens[0].type)
        }

    @Test
    fun testSingleCharacterTokens() =
        runTest {
            val lexer = Lexer("(){}[];,+-*/%^#:")
            val tokens = lexer.scanTokens()

            assertEquals(17, tokens.size) // 16 tokens + EOF
            assertEquals(TokenType.LEFT_PAREN, tokens[0].type)
            assertEquals(TokenType.RIGHT_PAREN, tokens[1].type)
            assertEquals(TokenType.LEFT_BRACE, tokens[2].type)
            assertEquals(TokenType.RIGHT_BRACE, tokens[3].type)
            assertEquals(TokenType.LEFT_BRACKET, tokens[4].type)
            assertEquals(TokenType.RIGHT_BRACKET, tokens[5].type)
            assertEquals(TokenType.SEMICOLON, tokens[6].type)
            assertEquals(TokenType.COMMA, tokens[7].type)
            assertEquals(TokenType.PLUS, tokens[8].type)
            assertEquals(TokenType.MINUS, tokens[9].type)
            assertEquals(TokenType.MULTIPLY, tokens[10].type)
            assertEquals(TokenType.DIVIDE, tokens[11].type)
            assertEquals(TokenType.MODULO, tokens[12].type)
            assertEquals(TokenType.POWER, tokens[13].type)
            assertEquals(TokenType.HASH, tokens[14].type)
            assertEquals(TokenType.COLON, tokens[15].type)
        }

    @Test
    fun testTwoCharacterTokens() =
        runTest {
            val lexer = Lexer("== ~= <= >= .. ...")
            val tokens = lexer.scanTokens()

            assertEquals(7, tokens.size) // 6 tokens + EOF
            assertEquals(TokenType.EQUAL, tokens[0].type)
            assertEquals(TokenType.NOT_EQUAL, tokens[1].type)
            assertEquals(TokenType.LESS_EQUAL, tokens[2].type)
            assertEquals(TokenType.GREATER_EQUAL, tokens[3].type)
            assertEquals(TokenType.CONCAT, tokens[4].type)
            assertEquals(TokenType.VARARG, tokens[5].type)
        }

    @Test
    fun testComparisonOperators() =
        runTest {
            val lexer = Lexer("< > = <= >= ==")
            val tokens = lexer.scanTokens()

            assertEquals(7, tokens.size)
            assertEquals(TokenType.LESS, tokens[0].type)
            assertEquals(TokenType.GREATER, tokens[1].type)
            assertEquals(TokenType.ASSIGN, tokens[2].type)
            assertEquals(TokenType.LESS_EQUAL, tokens[3].type)
            assertEquals(TokenType.GREATER_EQUAL, tokens[4].type)
            assertEquals(TokenType.EQUAL, tokens[5].type)
        }

    @Test
    fun testNumbers() =
        runTest {
            val lexer = Lexer("42 3.14 0.5 100.0 1e10 2.5e-3")
            val tokens = lexer.scanTokens()

            assertEquals(7, tokens.size) // 6 numbers + EOF

            // Integer literal (no decimal point or exponent) -> Long
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(42L, tokens[0].literal)

            // Float literals (has decimal point or exponent) -> Double
            assertEquals(TokenType.NUMBER, tokens[1].type)
            assertEquals(3.14, tokens[1].literal)

            assertEquals(TokenType.NUMBER, tokens[2].type)
            assertEquals(0.5, tokens[2].literal)

            assertEquals(TokenType.NUMBER, tokens[3].type)
            assertEquals(100.0, tokens[3].literal)

            assertEquals(TokenType.NUMBER, tokens[4].type)
            assertEquals(1e10, tokens[4].literal)

            assertEquals(TokenType.NUMBER, tokens[5].type)
            assertEquals(2.5e-3, tokens[5].literal)
        }

    @Test
    fun testStrings() =
        runTest {
            val lexer = Lexer("\"hello\" 'world' \"with\\nnewline\" 'with\\ttab'")
            val tokens = lexer.scanTokens()

            assertEquals(5, tokens.size) // 4 strings + EOF

            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("hello", tokens[0].literal)

            assertEquals(TokenType.STRING, tokens[1].type)
            assertEquals("world", tokens[1].literal)

            assertEquals(TokenType.STRING, tokens[2].type)
            assertEquals("with\nnewline", tokens[2].literal)

            assertEquals(TokenType.STRING, tokens[3].type)
            assertEquals("with\ttab", tokens[3].literal)
        }

    @Test
    fun testKeywords() =
        runTest {
            val lexer = Lexer("and break do else elseif end false for function if")
            val tokens = lexer.scanTokens()

            assertEquals(11, tokens.size) // 10 keywords + EOF
            assertEquals(TokenType.AND, tokens[0].type)
            assertEquals(TokenType.BREAK, tokens[1].type)
            assertEquals(TokenType.DO, tokens[2].type)
            assertEquals(TokenType.ELSE, tokens[3].type)
            assertEquals(TokenType.ELSEIF, tokens[4].type)
            assertEquals(TokenType.END, tokens[5].type)
            assertEquals(TokenType.FALSE, tokens[6].type)
            assertEquals(TokenType.FOR, tokens[7].type)
            assertEquals(TokenType.FUNCTION, tokens[8].type)
            assertEquals(TokenType.IF, tokens[9].type)
        }

    @Test
    fun testMoreKeywords() =
        runTest {
            val lexer = Lexer("in local nil not or repeat return then true until while")
            val tokens = lexer.scanTokens()

            assertEquals(12, tokens.size)
            assertEquals(TokenType.IN, tokens[0].type)
            assertEquals(TokenType.LOCAL, tokens[1].type)
            assertEquals(TokenType.NIL, tokens[2].type)
            assertEquals(TokenType.NOT, tokens[3].type)
            assertEquals(TokenType.OR, tokens[4].type)
            assertEquals(TokenType.REPEAT, tokens[5].type)
            assertEquals(TokenType.RETURN, tokens[6].type)
            assertEquals(TokenType.THEN, tokens[7].type)
            assertEquals(TokenType.TRUE, tokens[8].type)
            assertEquals(TokenType.UNTIL, tokens[9].type)
            assertEquals(TokenType.WHILE, tokens[10].type)
        }

    @Test
    fun testIdentifiers() =
        runTest {
            val lexer = Lexer("foo bar _test myVar123 _123")
            val tokens = lexer.scanTokens()

            assertEquals(6, tokens.size) // 5 identifiers + EOF

            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("foo", tokens[0].lexeme)

            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals("bar", tokens[1].lexeme)

            assertEquals(TokenType.IDENTIFIER, tokens[2].type)
            assertEquals("_test", tokens[2].lexeme)

            assertEquals(TokenType.IDENTIFIER, tokens[3].type)
            assertEquals("myVar123", tokens[3].lexeme)

            assertEquals(TokenType.IDENTIFIER, tokens[4].type)
            assertEquals("_123", tokens[4].lexeme)
        }

    @Test
    fun testComments() =
        runTest {
            val lexer = Lexer("foo -- this is a comment\nbar")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size) // 2 identifiers + EOF
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("foo", tokens[0].lexeme)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals("bar", tokens[1].lexeme)
        }

    @Test
    fun testFirstLineShebangComment() =
        runTest {
            // Lua 5.4: Shebang stripping happens at FILE LOADING level, not in the lexer
            // The lexer should treat # as HASH token
            val lexer = Lexer("# testing special comment\nprint('hello')")
            val tokens = lexer.scanTokens()

            // Should tokenize # as HASH, not skip it
            assertEquals(TokenType.HASH, tokens[0].type)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals("testing", tokens[1].lexeme)
        }

    @Test
    fun testFirstLineShebangWithExclamation() =
        runTest {
            // Shebang stripping is done at file loading level, not in lexer
            // The lexer should tokenize # as HASH and ! as ERROR
            val lexer = Lexer("#!/usr/bin/lua\nlocal x = 5")
            val tokens = lexer.scanTokens()

            // Should tokenize # as HASH
            assertEquals(TokenType.HASH, tokens[0].type)
            assertEquals(TokenType.ERROR, tokens[1].type) // ! is not a valid Lua token
        }

    @Test
    fun testHashNotOnFirstLine() =
        runTest {
            // # should be treated as length operator if not on first line
            val lexer = Lexer("local t = {1,2,3}\nlocal len = #t")
            val tokens = lexer.scanTokens()

            // Find the # token (should be HASH, not skipped)
            val hashToken = tokens.find { it.type == TokenType.HASH }
            assertTrue(hashToken != null, "# should be lexed as HASH token when not on first line")
        }

    @Test
    fun testSimpleExpression() =
        runTest {
            val lexer = Lexer("x = 10 + 5")
            val tokens = lexer.scanTokens()

            assertEquals(6, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("x", tokens[0].lexeme)
            assertEquals(TokenType.ASSIGN, tokens[1].type)
            assertEquals(TokenType.NUMBER, tokens[2].type)
            assertEquals(10L, tokens[2].literal)
            assertEquals(TokenType.PLUS, tokens[3].type)
            assertEquals(TokenType.NUMBER, tokens[4].type)
            assertEquals(5L, tokens[4].literal)
        }

    @Test
    fun testFunctionDeclaration() =
        runTest {
            val lexer = Lexer("function add(a, b) return a + b end")
            val tokens = lexer.scanTokens()

            assertEquals(13, tokens.size)
            assertEquals(TokenType.FUNCTION, tokens[0].type)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals("add", tokens[1].lexeme)
            assertEquals(TokenType.LEFT_PAREN, tokens[2].type)
            assertEquals(TokenType.IDENTIFIER, tokens[3].type)
            assertEquals("a", tokens[3].lexeme)
            assertEquals(TokenType.COMMA, tokens[4].type)
            assertEquals(TokenType.IDENTIFIER, tokens[5].type)
            assertEquals("b", tokens[5].lexeme)
            assertEquals(TokenType.RIGHT_PAREN, tokens[6].type)
            assertEquals(TokenType.RETURN, tokens[7].type)
            assertEquals(TokenType.IDENTIFIER, tokens[8].type)
            assertEquals(TokenType.PLUS, tokens[9].type)
            assertEquals(TokenType.IDENTIFIER, tokens[10].type)
            assertEquals(TokenType.END, tokens[11].type)
        }

    @Test
    fun testIfStatement() =
        runTest {
            val lexer = Lexer("if x > 0 then print(x) end")
            val tokens = lexer.scanTokens()

            assertEquals(11, tokens.size) // 10 tokens + EOF
            assertEquals(TokenType.IF, tokens[0].type)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals(TokenType.GREATER, tokens[2].type)
            assertEquals(TokenType.NUMBER, tokens[3].type)
            assertEquals(TokenType.THEN, tokens[4].type)
            assertEquals(TokenType.IDENTIFIER, tokens[5].type)
            assertEquals(TokenType.LEFT_PAREN, tokens[6].type)
            assertEquals(TokenType.IDENTIFIER, tokens[7].type)
            assertEquals(TokenType.RIGHT_PAREN, tokens[8].type)
            assertEquals(TokenType.END, tokens[9].type)
        }

    @Test
    fun testWhileLoop() =
        runTest {
            val lexer = Lexer("while i < 10 do i = i + 1 end")
            val tokens = lexer.scanTokens()

            assertEquals(12, tokens.size)
            assertEquals(TokenType.WHILE, tokens[0].type)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals(TokenType.LESS, tokens[2].type)
            assertEquals(TokenType.NUMBER, tokens[3].type)
            assertEquals(TokenType.DO, tokens[4].type)
        }

    @Test
    fun testTableAccess() =
        runTest {
            val lexer = Lexer("table[key] table.field")
            val tokens = lexer.scanTokens()

            assertEquals(8, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(TokenType.LEFT_BRACKET, tokens[1].type)
            assertEquals(TokenType.IDENTIFIER, tokens[2].type)
            assertEquals(TokenType.RIGHT_BRACKET, tokens[3].type)
            assertEquals(TokenType.IDENTIFIER, tokens[4].type)
            assertEquals(TokenType.DOT, tokens[5].type)
            assertEquals(TokenType.IDENTIFIER, tokens[6].type)
        }

    @Test
    fun testLineAndColumnTracking() =
        runTest {
            val lexer = Lexer("foo\nbar")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size)
            assertEquals(1, tokens[0].line)
            assertEquals(1, tokens[0].column)
            assertEquals(2, tokens[1].line)
            assertEquals(1, tokens[1].column)
        }

    @Test
    fun testComplexExpression() =
        runTest {
            val lexer = Lexer("result = (a + b) * c - d / e % f ^ g")
            val tokens = lexer.scanTokens()

            assertTrue(tokens.size > 0)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(TokenType.ASSIGN, tokens[1].type)
            assertEquals(TokenType.LEFT_PAREN, tokens[2].type)
        }

    @Test
    fun testLocalVariable() =
        runTest {
            val lexer = Lexer("local x = 42")
            val tokens = lexer.scanTokens()

            assertEquals(5, tokens.size)
            assertEquals(TokenType.LOCAL, tokens[0].type)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals(TokenType.ASSIGN, tokens[2].type)
            assertEquals(TokenType.NUMBER, tokens[3].type)
        }

    @Test
    fun testBooleanLiterals() =
        runTest {
            val lexer = Lexer("true false")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size)
            assertEquals(TokenType.TRUE, tokens[0].type)
            assertEquals(TokenType.FALSE, tokens[1].type)
        }

    @Test
    fun testNilLiteral() =
        runTest {
            val lexer = Lexer("nil")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.NIL, tokens[0].type)
        }

    @Test
    fun testStringConcatenation() =
        runTest {
            val lexer = Lexer("\"hello\" .. \"world\"")
            val tokens = lexer.scanTokens()

            assertEquals(4, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals(TokenType.CONCAT, tokens[1].type)
            assertEquals(TokenType.STRING, tokens[2].type)
        }

    @Test
    fun testVararg() =
        runTest {
            val lexer = Lexer("function test(...) end")
            val tokens = lexer.scanTokens()

            assertTrue(tokens.any { it.type == TokenType.VARARG })
        }

    // ========== NEW LITERAL TESTS ==========

    @Test
    fun testDecimalPointOnly() =
        runTest {
            val lexer = Lexer(".5")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(0.5, tokens[0].literal)
        }

    @Test
    fun testTrailingDecimalPoint() =
        runTest {
            val lexer = Lexer("5.")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(5.0, tokens[0].literal)
        }

    @Test
    fun testTrailingDecimalWithExponent() =
        runTest {
            val lexer = Lexer("2.E-1")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(0.2, tokens[0].literal)
        }

    @Test
    fun testScientificNotationBasic() =
        runTest {
            val lexer = Lexer("1e2")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(100.0, tokens[0].literal)
        }

    @Test
    fun testScientificNotationWithSign() =
        runTest {
            val lexer = Lexer("1e+2 1e-2")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(100.0, tokens[0].literal)
            assertEquals(TokenType.NUMBER, tokens[1].type)
            assertEquals(0.01, tokens[1].literal)
        }

    @Test
    fun testHexadecimalNumbers() =
        runTest {
            val lexer = Lexer("0xFF 0x0 0xf")
            val tokens = lexer.scanTokens()

            assertEquals(4, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(255L, tokens[0].literal) // Hex integers are now Longs
            assertEquals(TokenType.NUMBER, tokens[1].type)
            assertEquals(0L, tokens[1].literal)
            assertEquals(TokenType.NUMBER, tokens[2].type)
            assertEquals(15L, tokens[2].literal)
        }

    @Test
    fun testHexadecimalFloats() =
        runTest {
            val lexer = Lexer("0x0.1 0x0.8")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(0.0625, tokens[0].literal as Double, 0.0001)
            assertEquals(TokenType.NUMBER, tokens[1].type)
            assertEquals(0.5, tokens[1].literal as Double, 0.0001)
        }

    @Test
    fun testHexadecimalWithPNotation() =
        runTest {
            val lexer = Lexer("0x10p1 0x10p-1")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(32.0, tokens[0].literal)
            assertEquals(TokenType.NUMBER, tokens[1].type)
            assertEquals(8.0, tokens[1].literal)
        }

    @Test
    fun testDecimalEscapeSequences() =
        runTest {
            val lexer = Lexer("\"\\99\"")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("c", tokens[0].literal) // ASCII 99 = 'c'
        }

    @Test
    fun testHexadecimalEscapeSequences() =
        runTest {
            val lexer = Lexer("\"\\x41\"")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("A", tokens[0].literal) // \x41 = ASCII 65 = 'A'
        }

    @Test
    fun testUnicodeEscapeSequences() =
        runTest {
            val lexer = Lexer("\"\\u{41}\"")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("A", tokens[0].literal) // \u{41} = 'A'
        }

    @Test
    fun testZEscapeSequence() =
        runTest {
            val lexer = Lexer("\"abc\\z   def\"")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("abcdef", tokens[0].literal) // \z skips whitespace
        }

    @Test
    fun testExtendedEscapeSequences() =
        runTest {
            val lexer = Lexer("\"\\a\\b\\f\\v\"")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("\u0007\b\u000C\u000B", tokens[0].literal)
        }

    @Test
    fun testLongStringBasic() =
        runTest {
            val lexer = Lexer("[[hello]]")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("hello", tokens[0].literal)
        }

    @Test
    fun testLongStringWithNewline() =
        runTest {
            val lexer = Lexer("[[hello\nworld]]")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("hello\nworld", tokens[0].literal)
        }

    @Test
    fun testLongStringSkipsFirstNewline() =
        runTest {
            val lexer = Lexer("[[\nhello\n]]")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("hello\n", tokens[0].literal)
        }

    @Test
    fun testLongStringWithEquals() =
        runTest {
            val lexer = Lexer("[==[]=]==]")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("]=", tokens[0].literal)
        }

    @Test
    fun testLongStringNested() =
        runTest {
            val lexer = Lexer("[=[[[]]]=]")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("[[]]", tokens[0].literal)
        }

    @Test
    fun testLongStringMultipleClosingBrackets() =
        runTest {
            val lexer = Lexer("[=[]]]]]]]]]=]")
            val tokens = lexer.scanTokens()

            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("]]]]]]]]", tokens[0].literal)
        }

    @Test
    fun testFloorDivision() =
        runTest {
            val lexer = Lexer("7 // 3")
            val tokens = lexer.scanTokens()

            assertEquals(4, tokens.size)
            assertEquals(TokenType.NUMBER, tokens[0].type)
            assertEquals(TokenType.FLOOR_DIVIDE, tokens[1].type)
            assertEquals(TokenType.NUMBER, tokens[2].type)
        }

    @Test
    fun testBitwiseOperators() =
        runTest {
            val lexer = Lexer("a & b | c ~ d")
            val tokens = lexer.scanTokens()

            assertEquals(8, tokens.size)
            assertEquals(TokenType.BITWISE_AND, tokens[1].type)
            assertEquals(TokenType.BITWISE_OR, tokens[3].type)
            assertEquals(TokenType.BITWISE_XOR, tokens[5].type)
        }

    @Test
    fun testBitShiftOperators() =
        runTest {
            val lexer = Lexer("a << 3 >> 2")
            val tokens = lexer.scanTokens()

            assertEquals(6, tokens.size)
            assertEquals(TokenType.SHIFT_LEFT, tokens[1].type)
            assertEquals(TokenType.SHIFT_RIGHT, tokens[3].type)
        }

    @Test
    fun testPowerOperator() =
        runTest {
            val lexer = Lexer("2 ^ 3")
            val tokens = lexer.scanTokens()

            assertEquals(4, tokens.size)
            assertEquals(TokenType.POWER, tokens[1].type)
        }

    @Test
    fun testLengthOperator() =
        runTest {
            // Note: # on first line is treated as shebang comment, so we add a newline first
            val lexer = Lexer("\n#\"hello\"")
            val tokens = lexer.scanTokens()

            assertEquals(3, tokens.size)
            assertEquals(TokenType.HASH, tokens[0].type)
            assertEquals(TokenType.STRING, tokens[1].type)
        }

    @Test
    fun testMultiLineStringWithContinuation() =
        runTest {
            // Test that line continuation characters (\) in strings increment line counter
            val source = """local x = "line 1 \
     line 2 \
     line 3"
local y = 123"""
            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()

            // Find the tokens we care about
            val localToken1 = tokens[0] // LOCAL at line 1
            val xToken = tokens[1] // IDENTIFIER "x" at line 1
            val stringToken = tokens[3] // STRING at line 1
            val localToken2 = tokens[4] // LOCAL - should be at line 4, not line 2!
            val yToken = tokens[5] // IDENTIFIER "y" - should be at line 4

            assertEquals(TokenType.LOCAL, localToken1.type)
            assertEquals(1, localToken1.line, "First LOCAL should be on line 1")

            assertEquals(TokenType.IDENTIFIER, xToken.type)
            assertEquals(1, xToken.line, "x should be on line 1")

            assertEquals(TokenType.STRING, stringToken.type)
            assertEquals(1, stringToken.line, "String should start on line 1")

            assertEquals(TokenType.LOCAL, localToken2.type)
            assertEquals(4, localToken2.line, "Second LOCAL should be on line 4 (after 3 lines with \\)")

            assertEquals(TokenType.IDENTIFIER, yToken.type)
            assertEquals(4, yToken.line, "y should be on line 4")
        }

    @Test
    fun testMultiLineStringWithActualNewlines() =
        runTest {
            // Test that escape sequences like \n don't affect line counting
            val source = """local x = "line 1\nline 2\nline 3"
local y = 123"""
            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()

            val localToken1 = tokens[0]
            val localToken2 = tokens[4]

            assertEquals(1, localToken1.line, "First LOCAL should be on line 1")
            assertEquals(2, localToken2.line, "Second LOCAL should be on line 2 (no actual newlines in string)")
        }

    @Test
    fun testCarriageReturnAsLineEnding() =
        runTest {
            // Test that \r (carriage return) is treated as a line ending, not just ignored whitespace
            // This is required by Lua 5.4 spec for cross-platform compatibility
            val source = "local a = 1\rlocal b = 2\rlocal c = 3"
            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()

            val localToken1 = tokens[0]
            val localToken2 = tokens[4]
            val localToken3 = tokens[8]

            assertEquals(TokenType.LOCAL, localToken1.type)
            assertEquals(1, localToken1.line, "First LOCAL should be on line 1")

            assertEquals(TokenType.LOCAL, localToken2.type)
            assertEquals(2, localToken2.line, "Second LOCAL should be on line 2 (after \\r)")

            assertEquals(TokenType.LOCAL, localToken3.type)
            assertEquals(3, localToken3.line, "Third LOCAL should be on line 3 (after second \\r)")
        }

    @Test
    fun testMixedLineEndings() =
        runTest {
            // Test that all Lua line ending forms work correctly: \n, \r, \r\n, \n\r
            val sourceWithCRLF = "local a = 1\r\nlocal b = 2"
            val lexerCRLF = Lexer(sourceWithCRLF)
            val tokensCRLF = lexerCRLF.scanTokens()
            assertEquals(1, tokensCRLF[0].line, "First token on line 1")
            assertEquals(2, tokensCRLF[4].line, "Second LOCAL should be on line 2 after \\r\\n")

            val sourceWithLFCR = "local a = 1\n\rlocal b = 2"
            val lexerLFCR = Lexer(sourceWithLFCR)
            val tokensLFCR = lexerLFCR.scanTokens()
            assertEquals(1, tokensLFCR[0].line, "First token on line 1")
            assertEquals(2, tokensLFCR[4].line, "Second LOCAL should be on line 2 after \\n\\r")
        }

    @Test
    fun testMalformedHexNumberMissingExponent() =
        runTest {
            // "0xep-p" should produce "malformed number near '0xep-p'"
            val lexer = Lexer("0xep-p")
            val tokens = lexer.scanTokens()

            // The lexer should produce an ERROR token
            assertEquals(2, tokens.size) // ERROR + EOF
            assertEquals(TokenType.ERROR, tokens[0].type)
            assertEquals("0xep-p", tokens[0].lexeme)
        }

    @Test
    fun testMalformedDecimalNumberFollowedByIdentifier() =
        runTest {
            // "1print()" should produce "malformed number near '1p'"
            // This happens because the lexer sees '1p' as a number token (trying to parse exponent)
            val lexer = Lexer("1print()")
            val tokens = lexer.scanTokens()

            // Debug: print all tokens
            tokens.forEach { println("Token: ${it.type} '${it.lexeme}'") }

            // The lexer should produce an ERROR token for "1print"
            // Because '1p' looks like it's starting an exponent, and 'rint' continues the alphanumeric sequence
            assertTrue(tokens.size >= 2, "Should have at least ERROR + EOF, got ${tokens.size}")
            assertEquals(TokenType.ERROR, tokens[0].type, "First token should be ERROR")
            assertTrue(tokens[0].lexeme.startsWith("1p"), "Token should start with '1p', got '${tokens[0].lexeme}'")
        }
}
