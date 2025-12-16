package ai.tenum.lua.compat.debug

import ai.tenum.lua.lexer.Lexer
import kotlin.test.Test

class LexerLineNumberTest {
    @Test
    fun testLeadingNewline() {
        val source = "\nlocal x = 1"
        val lexer = Lexer(source, "test")
        val tokens = lexer.scanTokens()

        println("Source with leading newline:")
        tokens.forEach { token ->
            println("  ${token.type} '${token.lexeme}' line=${token.line}")
        }

        val localToken = tokens.find { it.lexeme == "local" }
        println("LOCAL token is at line: ${localToken?.line}")
    }
}
