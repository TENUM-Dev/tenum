package ai.tenum.lua.compiler

import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaCompiledFunction
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LoadChunkLineNumbersTest {
    @Test
    fun testChunkLineNumbers() {
        // The exact content that load() would receive from test([[...]])
        val content = """local function foo()
end
foo()
A = 1
A = 2
A = 3
"""

        val lexer = Lexer(content, "test")
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val ast = parser.parse()
        val compiler = Compiler(sourceFilename = "test", debugEnabled = true)
        val proto = compiler.compile(ast, hasVararg = false)

        // Check line events
        val lineNumbers =
            proto.lineEvents
                .map { it.line }
                .distinct()
                .sorted()

        // Check nested protos (functions defined in the chunk)
        val nestedProtos = proto.constants.filterIsInstance<LuaCompiledFunction>()
        val nestedProtoInfo =
            if (nestedProtos.isEmpty()) {
                "  (none)"
            } else {
                nestedProtos.joinToString("\n") { nested ->
                    val nestedLines =
                        nested.proto.lineEvents
                            .map { it.line }
                            .distinct()
                            .sorted()
                    "  Proto '${nested.proto.name}': lines $nestedLines"
                }
            }
        // PUC Lua would have lines {2, 3, 4, 5, 6} for this chunk
        // (line 1 is the skipped newline after [[)
        // But our compiler starts at line 1, so we get {1, 2, 3, 4, 5, 6}
        lineNumbers shouldBe listOf(1, 3, 4, 5, 6) // Line 2 is in nested proto
    }
}
