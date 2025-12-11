package ai.tenum.lua.compiler

import ai.tenum.lua.compiler.helper.ConstantPool
import ai.tenum.lua.compiler.helper.InstructionBuilder
import ai.tenum.lua.compiler.helper.RegisterAllocator
import ai.tenum.lua.compiler.helper.ScopeManager
import ai.tenum.lua.compiler.helper.UpvalueResolver
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.compiler.stages.FunctionCompiler
import ai.tenum.lua.parser.ast.Chunk

/**
 * High-level entry point: takes a parsed Chunk and produces a Proto.
 * All real work is delegated to the new CompileContext + stages pipeline.
 */
class Compiler(
    private val sourceFilename: String = "=(load)",
    private val debugEnabled: Boolean = false,
) {
    private lateinit var lastContext: CompileContext

    /**
     * Compile a top-level chunk into a Proto.
     */
    fun compile(
        chunk: Chunk,
        name: String = "main",
        hasVararg: Boolean = false,
    ): Proto {
        // Per-function state objects
        val constantPool = ConstantPool()
        val instructionBuilder = InstructionBuilder()
        val scopeManager = ScopeManager(debugEnabled)
        val registerAllocator = RegisterAllocator()

        // Top-level has no parent resolver/scope
        val upvalueResolver =
            UpvalueResolver(
                parent = null,
                parentScope = null,
                debugEnabled = debugEnabled,
            )

        // Lua-style: top-level gets _ENV as upvalue[0], provided by the VM
        upvalueResolver.define("_ENV", inStack = false, index = 0)

        val ctx =
            CompileContext(
                functionName = name,
                constantPool = constantPool,
                instructionBuilder = instructionBuilder,
                scopeManager = scopeManager,
                upvalueResolver = upvalueResolver,
                registerAllocator = registerAllocator,
                debugEnabled = debugEnabled,
                source = sourceFilename,
            )

        this.lastContext = ctx

        val fnCompiler = FunctionCompiler()
        val proto = fnCompiler.compile(chunk, name, ctx, hasVararg)

        // Fill in source + line range for the top-level Proto
        return proto.copy(
            source = sourceFilename,
            lineDefined = 0,
            lastLineDefined = ctx.currentLine,
        )
    }

    fun getLastCompileContext(): CompileContext = lastContext
}
