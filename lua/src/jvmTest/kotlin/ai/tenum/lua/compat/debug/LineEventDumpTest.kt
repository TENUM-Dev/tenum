package ai.tenum.lua.compat.debug

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LineEventDumpTest {
    @Test
    fun dumpLineEventsForDbScenario() =
        runTest {
            val source = """
local count = 0
local function hook()
  count = count + 1
end

debug.sethook(hook, "l")
do
  local x <close> = setmetatable({}, {__close = function() end})
  local y = 1
  y = 2
end
debug.sethook()
"""

            val lexer = Lexer(source, "test")
            val tokens = lexer.scanTokens()
            val parser = Parser(tokens)
            val chunk = parser.parse()
            val compiler = Compiler(debugEnabled = true)
            val proto = compiler.compile(chunk)

            println("\n=== Proto LineEvents (sorted by PC) ===")
            proto.lineEvents.sortedBy { it.pc }.forEach { event ->
                println("PC=${event.pc.toString().padEnd(3)} line=${event.line.toString().padEnd(3)} kind=${event.kind}")
            }

            println("\n=== Instructions with Line Info ===")
            proto.instructions.forEachIndexed { pc, instr ->
                val events = proto.lineEvents.filter { it.pc == pc }
                val execLine =
                    events
                        .filter { it.kind.name.contains("EXECUTION") || it.kind.name.contains("ITERATION") }
                        .maxByOrNull { it.line }
                        ?.line
                val allKinds = events.joinToString(", ") { "${it.kind}:${it.line}" }
                println("PC=$pc line=${execLine ?: -1} ${instr.opcode.name.padEnd(15)} events=[$allKinds]")
            }

            println("\n=== Nested Functions ===")
            proto.constants.filterIsInstance<ai.tenum.lua.runtime.LuaCompiledFunction>().forEach { nested ->
                println("Function: ${nested.proto.name}")
                nested.proto.lineEvents.sortedBy { it.pc }.forEach { event ->
                    println("  PC=${event.pc} line=${event.line} kind=${event.kind}")
                }
                nested.proto.instructions.forEachIndexed { pc, instr ->
                    val events = nested.proto.lineEvents.filter { it.pc == pc }
                    val allKinds = events.joinToString(", ") { "${it.kind}:${it.line}" }
                    println("  PC=$pc ${instr.opcode.name.padEnd(15)} events=[$allKinds]")
                }
            }
        }
}
