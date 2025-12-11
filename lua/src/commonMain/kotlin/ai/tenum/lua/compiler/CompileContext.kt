package ai.tenum.lua.compiler

import ai.tenum.lua.compiler.helper.CompilerHelpers
import ai.tenum.lua.compiler.helper.ConstantPool
import ai.tenum.lua.compiler.helper.InstructionBuilder
import ai.tenum.lua.compiler.helper.RegisterAllocator
import ai.tenum.lua.compiler.helper.ScopeManager
import ai.tenum.lua.compiler.helper.UpvalueResolver
import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.LineEvent
import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.compiler.model.LineInfo
import ai.tenum.lua.compiler.model.LocalLifetime
import ai.tenum.lua.compiler.model.LocalVarInfo
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.compiler.stages.StatementCompiler
import ai.tenum.lua.lexer.Token
import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ParserException
import ai.tenum.lua.parser.ast.Chunk
import ai.tenum.lua.parser.ast.Statement
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.OpCode

/**
 * Holds all mutable state for a single compilation unit.
 */
data class CompileContext(
    val functionName: String,
    val constantPool: ConstantPool,
    val instructionBuilder: InstructionBuilder,
    val scopeManager: ScopeManager,
    val upvalueResolver: UpvalueResolver,
    val registerAllocator: RegisterAllocator,
    val debugEnabled: Boolean = false,
    val source: String = "=(load)",
) {
    init {
        if (upvalueResolver.hasNoParent()) {
            upvalueResolver.define("_ENV", inStack = false, index = 0)
            if (debugEnabled) println("[COMPILER][$functionName] define _ENV upvalue")
        }
    }
    // --- GOTO/LABEL SUPPORT ---

    /**
     * Info about a label site for goto resolution.
     */
    data class LabelInfo(
        val instructionIndex: Int,
        val scopeLevel: Int,
        val localCount: Int,
        val line: Int,
    )

    /**
     * Info about a pending goto site for resolution.
     */
    data class GotoInfo(
        val instructionIndex: Int,
        val labelName: String,
        val scopeLevel: Int,
        val localCount: Int,
        val line: Int,
    )

    /**
     * All labels defined in this function (name -> info).
     */
    val labels: MutableMap<String, LabelInfo> = mutableMapOf()

    /**
     * All gotos not yet resolved (pending label definition).
     */
    val pendingGotos: MutableList<GotoInfo> = mutableListOf()

    /**
     * Remove labels that were defined at the given scope level.
     * Called when exiting a block to implement block-scoped label semantics.
     */
    fun removeLabelsAtScope(scopeLevel: Int) {
        val iterator = labels.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.scopeLevel >= scopeLevel) {
                iterator.remove()
            }
        }
    }

    fun emit(
        code: OpCode,
        i: Int,
        i2: Int,
        i3: Int,
    ) = instructionBuilder.emit(code, i, i2, i3)

    fun debug(message: String) {
        if (debugEnabled) {
            println("[COMPILER] [$functionName] $message")
        }
    }

    // Convenience access to the underlying instruction list
    val instructions: MutableList<Instruction>
        get() = instructionBuilder.instructions

    // ───────────────────────────────────────────────
    //  LINE INFO
    // ───────────────────────────────────────────────

    var currentLine: Int = 0
    val lineInfo: MutableList<LineInfo> = mutableListOf()

    // ───────────────────────────────────────────────
    //  LOCALS / SCOPES (unified via ScopeManager)
    // ───────────────────────────────────────────────

    /**
     * Resolve a local by name using ScopeManager (unified source of truth).
     * Only returns locals that are currently active (haven't gone out of scope yet).
     */
    fun findLocal(name: String): ScopeManager.LocalSymbol? {
        // Search from innermost to outermost scope
        for (i in scopeManager.locals.size - 1 downTo 0) {
            val local = scopeManager.locals[i]
            // Check if local is still active (endPc not set yet, or scopeLevel check)
            // A local is active if it's at or below the current scope level AND hasn't been ended
            if (local.name == name &&
                local.scopeLevel <= scopeManager.currentScopeLevel &&
                local.isActive
            ) {
                return local
            }
        }
        return null
    }

    /**
     * Check if a variable is const or close, looking in both local scope and parent scopes (via upvalues).
     * Returns true if the variable is const/close and cannot be reassigned.
     */
    fun isConstOrCloseVariable(name: String): Boolean {
        // First check if it's a local in the current scope
        val local = findLocal(name)
        if (local != null) {
            return local.isConst || local.isClose
        }

        // If not a local, check if it's a const/close upvalue from a parent scope
        return upvalueResolver.isConstOrClose(name)
    }
    // ───────────────────────────────────────────────
    //  CONSTANTS / UPVALUES
    // ─────────────────────────────────────────────

    fun addConstant(value: LuaValue<*>): Int = constantPool.getIndex(value)

    /**
     * Operand for RK-style operands.
     * RK operands encode constants as (index | 256), but this only works for indices 0-255
     * because only 9 bits are available (bit 8 = constant flag, bits 0-7 = index).
     * If your VM uses BITRK encoding, this is the place to implement it.
     */
    fun operandForConstant(constIndex: Int): Int = constIndex or 256

    /**
     * Get an RK operand for a constant index.
     * Throws an error if the constant index cannot be RK-encoded (> 255).
     * Call canUseRKOperand first to check if this is safe.
     */
    fun getRKOperandForConstant(constIndex: Int): Int {
        require(constIndex <= 255) {
            "Constant index $constIndex cannot be RK-encoded (max 255). " +
                "Large constants must be loaded to registers at expression compilation level."
        }
        return operandForConstant(constIndex)
    }

    /**
     * Check if a constant index can be encoded as an RK operand.
     * RK encoding uses 9 bits total: bit 8 for the constant flag, bits 0-7 for the index.
     * This means only constant indices 0-255 can be directly encoded.
     */
    fun canUseRKOperand(constIndex: Int): Boolean = constIndex <= 255

    fun resolveUpvalue(name: String): Int? = upvalueResolver.resolve(name)

    // ─────────────────────────────────────────────
    //  JUMPS / CLOSE
    // ─────────────────────────────────────────────

    /**
     * Patch a JMP at [jumpIndex] so it jumps to [targetIndex].
     * Assumes standard Lua semantics: offset = target - jump - 1 stored in B.
     */
    fun patchJump(
        jumpIndex: Int,
        targetIndex: Int,
    ) {
        val old = instructions[jumpIndex]
        val offset = targetIndex - jumpIndex - 1
        instructions[jumpIndex] = Instruction(old.opcode, old.a, offset, old.c)
    }

    /**
     * Emit CLOSE for all to-be-closed locals at or above [scopeLevel].
     * This is a simplified implementation.
     */
    fun emitCloseVariables(scopeLevel: Int) {
        for (local in scopeManager.locals) {
            if (local.isClose && local.scopeLevel >= scopeLevel && local.isActive) {
                // mode = 2 → scope-exit close: run __close for this register (if tracked)
                emit(OpCode.CLOSE, local.register, 2, 0)
            }
        }
    }

    /**
     * Scope validation for goto.
     * Right now this is a no-op (accept all gotos).
     * You can add strict checks here if you need exact Lua semantics.
     */
    fun validateGotoScope(
        gotoScopeLevel: Int,
        gotoLocalCount: Int,
        labelScopeLevel: Int,
        labelLocalCount: Int,
        labelName: String,
        isForward: Boolean,
        gotoLine: Int = -1,
    ) {
        // Lua rule: you cannot jump INTO a deeper scope with local variables
        // Key: it's about scope depth, not just local count

        if (isForward) {
            // Forward jump: from goto to label
            // Illegal if label is at a deeper scope level (jumped into an inner scope)
            if (labelScopeLevel > gotoScopeLevel) {
                // Label is inside a nested block - not visible from goto location
                val lineInfo = if (gotoLine > 0) " at line $gotoLine" else ""
                throw ParserException(
                    message = "no visible label '$labelName' for <goto>$lineInfo",
                    token = Token(TokenType.IDENTIFIER, labelName, labelName, if (gotoLine > 0) gotoLine else 1, 1),
                )
            }
            // If at same scope level, jumping forward over local declarations is OK
        } else {
            // Backward jump: from label to goto (label was already seen)
            // Jumping backwards/out of scopes is always allowed
            // We just need to close any <close> variables (already handled by emitCloseVariables)
        }
    }

    // ─────────────────────────────────────────────
    //  FUNCTION COMPILATION (for function literals)
    // ─────────────────────────────────────────────

    /**
     * Compile a nested function (used by function literals / local functions).
     * This builds a fresh child context, compiles [body] into a Proto,
     * and wires up upvalues via the existing UpvalueResolver.
     */
    fun compileFunction(
        params: List<String>,
        hasVararg: Boolean,
        body: List<Statement>,
        name: String = "<function>",
        functionLine: Int = 0,
        endLine: Int = 0,
    ): Proto {
        val childScopeManager = ScopeManager(debugEnabled)
        // IMPORTANT: parent scope for upvalues is the current function's scope
        val childResolver = upvalueResolver.createChild(scopeManager)

        val childCtx =
            CompileContext(
                functionName = name,
                constantPool = ConstantPool(),
                instructionBuilder = InstructionBuilder(),
                scopeManager = childScopeManager,
                upvalueResolver = childResolver,
                registerAllocator = RegisterAllocator(),
                debugEnabled = debugEnabled,
                source = source,
            )

        // parameters
        for (param in params) {
            val reg = childCtx.registerAllocator.allocateLocal()
            childCtx.scopeManager.declareLocal(
                name = param,
                register = reg,
                startPc = 0,
                isConst = false,
                isClose = false,
            )
        }

        val stmtCompiler = StatementCompiler()
        val chunk = Chunk(body)
        for (stmt in chunk.statements) {
            stmtCompiler.compileStatement(stmt, childCtx)
        }

        childCtx.emitCloseVariables(0)

        // Emit lineEvent for lastLineDefined (the 'end' line) before RETURN
        // This matches Lua 5.4.8 behavior where lastLineDefined is always in activelines
        val actualEndLine = if (endLine > 0) endLine else childCtx.currentLine
        if (actualEndLine > 0 && actualEndLine != childCtx.currentLine) {
            childCtx.currentLine = actualEndLine
            childCtx.lineInfo.add(LineEvent(childCtx.instructions.size, actualEndLine, LineEventKind.EXECUTION))
        }

        childCtx.emit(OpCode.RETURN, 0, 1, 0)

        val localVarInfo =
            childCtx.scopeManager.allLocals.map { local ->
                LocalVarInfo(
                    name = local.name,
                    register = local.register,
                    lifetime =
                        if (local.isActive) {
                            // Still active at end of function, set endPc to instruction count
                            LocalLifetime.of(local.startPc, childCtx.instructions.size)
                        } else {
                            local.getLifetime()
                        },
                    isConst = local.isConst,
                )
            }

        // Use shared helper to sort upvalues and create index mapping
        val originalUpvalues = childCtx.upvalueResolver.getUpvalues().toList()
        val (sortedUpvalues, upvalueIndexMap) = CompilerHelpers.sortAndMapUpvalues(originalUpvalues)

        // Use shared helper to remap upvalue indices in instructions and constants
        val (remappedInstructions, remappedConstants) =
            CompilerHelpers.remapUpvalueIndices(
                childCtx.instructions,
                childCtx.constantPool.build(),
                upvalueIndexMap,
            )

        return Proto(
            name = name,
            instructions = remappedInstructions,
            constants = remappedConstants,
            upvalueInfo = sortedUpvalues,
            parameters = params,
            hasVararg = hasVararg,
            maxStackSize = childCtx.registerAllocator.maxStackSize,
            localVars = localVarInfo,
            lineEvents = childCtx.lineInfo.toList(),
            source = childCtx.source,
            lineDefined = functionLine,
            lastLineDefined = if (endLine > 0) endLine else childCtx.currentLine,
        )
    }
}
