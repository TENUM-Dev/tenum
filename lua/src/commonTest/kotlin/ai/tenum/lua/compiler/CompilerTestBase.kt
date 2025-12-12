package ai.tenum.lua.compiler

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser

/**
 * Base class for compiler tests providing common helper methods.
 */
abstract class CompilerTestBase {
    protected fun compile(source: String): Proto {
        val lexer = Lexer(source)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val chunk = parser.parse()
        val compiler = Compiler(debugEnabled = true)
        return compiler.compile(chunk)
    }
}
