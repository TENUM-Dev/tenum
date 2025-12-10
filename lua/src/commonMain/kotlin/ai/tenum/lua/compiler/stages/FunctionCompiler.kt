package ai.tenum.lua.compiler.stages

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.helper.CompilerHelpers
import ai.tenum.lua.compiler.model.LocalLifetime
import ai.tenum.lua.compiler.model.LocalVarInfo
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.lexer.Token
import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ParserException
import ai.tenum.lua.parser.ast.Chunk
import ai.tenum.lua.vm.OpCode

class FunctionCompiler {
    val stmtCompiler = StatementCompiler()

    fun compile(
        chunk: Chunk,
        name: String,
        ctx: CompileContext,
        hasVararg: Boolean = false,
    ): Proto {
        ctx.debug("=== FunctionCompiler: compiling '$name' ===")

        // Compile all top-level statements
        for (stmt in chunk.statements) {
            stmtCompiler.compileStatement(stmt, ctx)
        }

        // Check for unresolved gotos (labels that were never defined or not visible)
        if (ctx.pendingGotos.isNotEmpty()) {
            val firstUnresolved = ctx.pendingGotos.first()
            throw ParserException(
                message = "no visible label '${firstUnresolved.labelName}' for <goto> at line ${firstUnresolved.line}",
                token = Token(TokenType.IDENTIFIER, firstUnresolved.labelName, firstUnresolved.labelName, ctx.currentLine, 1),
            )
        }

        // Implicit `return nil`
        ctx.emit(OpCode.RETURN, 0, 1, 0)

        // Build local variable debug info from ScopeManager.allLocals
        val localVarInfo =
            ctx.scopeManager.allLocals.map { local ->
                LocalVarInfo(
                    name = local.name,
                    register = local.register,
                    lifetime =
                        if (local.isActive) {
                            // Still active at end of function, set endPc to instruction count
                            LocalLifetime.of(local.startPc, ctx.instructionBuilder.instructions.size)
                        } else {
                            local.getLifetime()
                        },
                    isConst = local.isConst,
                )
            }

        // Use shared helper to sort upvalues and create index mapping
        val originalUpvalues = ctx.upvalueResolver.getUpvalues().toList()
        val (sortedUpvalues, upvalueIndexMap) = CompilerHelpers.sortAndMapUpvalues(originalUpvalues)

        // Use shared helper to remap upvalue indices in instructions and constants
        val (remappedInstructions, remappedConstants) =
            CompilerHelpers.remapUpvalueIndices(
                ctx.instructionBuilder.instructions,
                ctx.constantPool.build(),
                upvalueIndexMap,
            )

        return Proto(
            name = name,
            instructions = remappedInstructions,
            constants = remappedConstants,
            upvalueInfo = sortedUpvalues,
            parameters = emptyList(),
            hasVararg = hasVararg,
            maxStackSize = ctx.registerAllocator.maxStackSize,
            localVars = localVarInfo,
            lineEvents = ctx.lineInfo.toList(),
            source = "", // filled by outer Compiler
            lineDefined = 0,
            lastLineDefined = ctx.currentLine,
        )
    }
}
