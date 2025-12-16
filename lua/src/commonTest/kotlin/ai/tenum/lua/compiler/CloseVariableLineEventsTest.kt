package ai.tenum.lua.compiler

import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaCompiledFunction
import kotlin.test.Test

class CloseVariableLineEventsTest {
    @Test
    fun testCloseVariableLineEvents() {
        val content = """
local hookCount = 0
debug.sethook(function() hookCount = hookCount + 1 end, "l")
do
    local x <close> = setmetatable({}, {__close = function() end})
    local y = 1
    y = 2
end
print("Total hooks: " .. hookCount)
""".trim()
        
        val lexer = Lexer(content)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val ast = parser.parse()
        val compiler = Compiler(sourceFilename = "test", debugEnabled = true)
        val proto = compiler.compile(ast)
        
        // Main proto line events
        val output = buildString {
            appendLine("Main proto line events:")
            appendLine("Instructions: ${proto.instructions.size}")
            proto.instructions.forEachIndexed { pc, instr ->
                appendLine("  PC $pc: $instr")
            }
            appendLine("Line events:")
            proto.lineEvents.forEach { appendLine("  PC ${it.pc}: line ${it.line} (${it.kind})") }
            
            // Nested protos (the __close function)
            val nestedProtos = proto.constants.filterIsInstance<LuaCompiledFunction>()
            appendLine("\nNested protos (${nestedProtos.size}):")
            nestedProtos.forEachIndexed { idx, func ->
                appendLine("  Nested proto #$idx (${func.proto.name}):")
                appendLine("    Instructions: ${func.proto.instructions.size}")
                func.proto.instructions.forEachIndexed { pc, instr -> 
                    appendLine("      PC $pc: $instr") 
                }
                appendLine("    Line events:")
                func.proto.lineEvents.forEach { appendLine("      PC ${it.pc}: line ${it.line} (${it.kind})") }
            }
        }

        println(output)
    }
}
