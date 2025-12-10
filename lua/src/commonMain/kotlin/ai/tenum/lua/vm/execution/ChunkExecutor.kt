package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Handles compilation and execution of Lua source chunks.
 * Separates the concern of "source text → compiled proto" from proto execution.
 */
class ChunkExecutor(
    private val executeProto: (Proto, List<LuaValue<*>>, List<Upvalue>, LuaCompiledFunction?) -> List<LuaValue<*>>,
    private val globalsTable: LuaTable,
    private val globals: MutableMap<String, LuaValue<*>>,
    private val debugEnabled: () -> Boolean,
    private val debug: (() -> String) -> Unit,
    private val callStackManager: ai.tenum.lua.vm.callstack.CallStackManager,
) {
    /**
     * Execute a Lua chunk from source text.
     * Handles: lexing → parsing → compilation → execution → global sync.
     */
    fun execute(
        chunk: String,
        source: String,
    ): LuaValue<*> {
        debug { "=== Executing Lua chunk ===" }
        debug { "Source: $source" }
        debug { "Source length: ${chunk.length} characters" }

        // Compile the chunk
        val lexer = Lexer(chunk, source)
        val tokens = lexer.scanTokens()
        debug { "Lexer: ${tokens.size} tokens" }

        val parser = Parser(tokens)
        val ast = parser.parse()
        debug { "Parser: AST created (${ast::class.simpleName})" }

        val compiler = Compiler(source, debugEnabled())
        val proto = compiler.compile(ast, hasVararg = true)
        debug { "Compiler: ${proto.instructions.size} instructions, ${proto.constants.size} constants" }

        if (debugEnabled()) {
            debug { "--- Bytecode ---" }
            proto.instructions.forEachIndexed { idx, instr ->
                debug { "  [$idx] ${instr.opcode} a=${instr.a} b=${instr.b} c=${instr.c}" }
            }
            debug { "--- Constants ---" }
            proto.constants.forEachIndexed { idx, const ->
                debug { "  [$idx] $const" }
            }
            debug { "--- LineEvents (${proto.lineEvents.size}) ---" }
            proto.lineEvents.forEach { event ->
                debug { "  PC=${event.pc} Line=${event.line} Kind=${event.kind}" }
            }
        }

        // Top-level chunks need _ENV initialized with globals table
        // Use the shared globals table (created once and reused)

        // Create _ENV upvalue for the chunk
        val envUpvalue = Upvalue(closedValue = globalsTable)
        envUpvalue.isClosed = true

        // Create a LuaCompiledFunction for the main chunk so it's visible to debug.getinfo
        val mainChunkFunction = LuaCompiledFunction(proto, mutableListOf(envUpvalue))

        // Add a native entry frame (like Lua's C pcall frame) before executing main chunk
        // This matches Lua 5.4.8 behavior where there's a C frame at the bottom of the stack
        val cleanup = callStackManager.enterNativeFrame()

        val results =
            try {
                // Execute the compiled proto with _ENV as upvalue[0]
                executeProto(proto, emptyList(), listOf(envUpvalue), mainChunkFunction)
            } finally {
                // Remove the entry frame using the cleanup handle
                cleanup.exit()
            }

        // Sync any changes back to globals map
        for ((key, value) in globalsTable.entries()) {
            if (key is LuaString) {
                globals[key.value] = value
            }
        }

        debug { "=== Execution complete: $results ===" }
        // Return first result for backwards compatibility, or nil if no results
        return results.firstOrNull() ?: LuaNil
    }
}
