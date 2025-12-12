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
     *
     * Scope Identity:
     * - scopeStack: Precise identity using stable scope IDs - use for identity matching
     * - scopeLevel: Depth counter (how nested) - use only for depth comparisons
     */
    data class LabelInfo(
        val instructionIndex: Int,
        val scopeLevel: Int, // Depth counter - for comparisons only, NOT identity
        val scopeStack: List<Int>, // Stable scope ID chain - for identity matching
        val localCount: Int,
        val line: Int,
        var isInRepeatUntilBlock: Boolean = false, // True if label is inside repeat-until block (set at scope exit)
        var scopeEndPc: Int = -1, // PC where the label's scope ends (set after scope ends)
    )

    /**
     * Info about a pending goto site for resolution.
     */
    data class GotoInfo(
        val instructionIndex: Int,
        val labelName: String,
        val scopeLevel: Int,
        val scopeStack: List<Int>, // Lexical scope ancestry at goto site
        val localCount: Int,
        val line: Int,
    )

    /**
     * Info about a resolved goto-label pair for later validation.
     */
    data class ResolvedGotoLabel(
        val gotoInfo: GotoInfo,
        val labelInfo: LabelInfo,
    )

    /**
     * Registry of all labels defined in this function (name -> list of label info).
     * Labels persist until function end (function-scoped, not block-scoped).
     * Multiple labels with same name allowed if in different scopes (shadowing).
     * Visibility controlled by scopeStack ancestry in findLabelVisibleFrom().
     */
    private val labelRegistry: MutableMap<String, MutableList<LabelInfo>> = mutableMapOf()

    /**
     * All gotos not yet resolved (pending label definition).
     */
    val pendingGotos: MutableList<GotoInfo> = mutableListOf()

    /**
     * All resolved goto-label pairs for scope validation at endScope().
     */
    val resolvedGotos: MutableList<ResolvedGotoLabel> = mutableListOf()

    /**
     * Register a label at the current scope.
     * Lua 5.4 allows duplicate label names only if they are in sibling scopes (e.g., different if branches).
     * Duplicate labels in nested scopes (parent-child relationship) are NOT allowed.
     */
    fun registerLabel(
        name: String,
        instructionIndex: Int,
        line: Int,
    ) {
        val labelInfo =
            LabelInfo(
                instructionIndex = instructionIndex,
                scopeLevel = scopeManager.currentScopeLevel,
                scopeStack = scopeManager.getCurrentScopeStack(),
                localCount = scopeManager.activeLocalCount(),
                line = line,
                isInRepeatUntilBlock = scopeManager.isInRepeatUntilBlock(),
            )

        val labelList = labelRegistry.getOrPut(name) { mutableListOf() }

        // Check if any existing label would conflict with the new label
        // Uses stable scope IDs (from scopeStack) not depth-based scopeLevel
        // Conflict occurs when:
        // - Existing label is an ancestor of new label (new is nested inside existing)
        // - Existing label is in the same scope as new label (exact duplicate)
        //
        // Example: do ::l:: do ::l:: end end
        //   First  ::l:: has scopeStack=[0,1], scopeId=1
        //   Second ::l:: has scopeStack=[0,1,2], scopeId=2
        //   Is scopeId 1 in [0,1,2]? YES → ERROR (nested duplicate)
        //
        // Example: if a then ::l:: end ::l::
        //   First  ::l:: has scopeStack=[0,1], scopeId=1
        //   Second ::l:: has scopeStack=[0], scopeId=0
        //   Is scopeId 1 in [0]? NO → OK (sibling scopes after first exited)
        for (existingLabel in labelList) {
            val existingScopeId = existingLabel.scopeStack.lastOrNull() ?: 0

            // Check if existing label's scope is in our ancestry (uses stable scope IDs)
            // This includes same-scope case and correctly handles nested duplicates
            if (existingScopeId in labelInfo.scopeStack) {
                throw ParserException(
                    message = "label '$name' already defined on line ${existingLabel.line}",
                    token = Token(TokenType.IDENTIFIER, name, name, labelInfo.line, 1),
                )
            }

            // NOTE: We do NOT check if new label is in existing label's ancestry
            // because that's allowed - a parent scope can have a label with the same name
            // as a label in a child scope that was already exited. Example:
            //   elseif a == 2 then ::l1:: return "inner" end
            //   ::l1:: return "outer"
            // The outer ::l1:: is allowed even though ::l1:: exists in child scope [0,2]
        }

        // Add the new label to the registry
        labelList.add(labelInfo)
    }

    /**
     * Find the most recent (innermost) visible label with the given name.
     * Returns null if no such label exists.
     */
    fun findLabel(name: String): LabelInfo? {
        val labelList = labelRegistry[name] ?: return null
        // Return the last (most recent/innermost) label
        return labelList.lastOrNull()
    }

    /**
     * Find a label visible from a given scope stack (lexical ancestry).
     * A label is visible from a goto if the label's last scope ID is in the goto's scope stack
     * (i.e., the label is in an ancestor scope or the same scope).
     *
     * This prevents sibling-branch bindings: labels in if-blocks are not visible to gotos in elseif-blocks.
     * Returns the most nested visible label (the one with the longest matching scope stack).
     */
    fun findLabelVisibleFrom(
        name: String,
        fromScopeStack: List<Int>,
    ): LabelInfo? {
        val labelList = labelRegistry[name] ?: return null
        // Find all visible labels (whose scope ID is in the goto's scope stack)
        val visibleLabels =
            labelList.filter { label ->
                val labelScopeId = label.scopeStack.lastOrNull() ?: 0
                labelScopeId in fromScopeStack
            }
        // Return the most nested one (longest scope stack = deepest nesting)
        return visibleLabels.maxByOrNull { it.scopeStack.size }
    }

    /**
     * Set the scopeEndPc for all labels at the given scope.
     * Called when exiting a scope to record where the scope ended.
     * Uses scopeStack for precise scope identity (not scopeLevel which is ambiguous for siblings).
     */
    fun setLabelsScopeEndPc(
        scopeStack: List<Int>,
        endPc: Int,
        isRepeatUntilBlock: Boolean = false,
    ) {
        val scopeId = scopeStack.lastOrNull() ?: 0
        for (labelList in labelRegistry.values) {
            for (label in labelList) {
                // Match by exact scope ID (last element of scopeStack)
                // This correctly handles sibling scopes at same depth
                val labelScopeId = label.scopeStack.lastOrNull() ?: 0
                if (labelScopeId == scopeId && label.scopeEndPc < 0) {
                    label.scopeEndPc = endPc
                    if (isRepeatUntilBlock) {
                        label.isInRepeatUntilBlock = true
                    }
                }
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
    val lineInfo: MutableList<LineEvent> = mutableListOf()

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
     * Checks Lua's goto/label scoping rules.
     */
    fun validateGotoScope(
        gotoScopeLevel: Int,
        gotoLocalCount: Int,
        labelScopeLevel: Int,
        labelLocalCount: Int,
        labelName: String,
        isForward: Boolean,
        gotoLine: Int = -1,
        gotoInstructionIndex: Int = -1,
        labelInstructionIndex: Int = -1,
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

            // At same scope level: Lua allows jumping forward over local declarations
            // within the same block. The "no jumping into scope" rule only applies
            // when jumping INTO a nested block (which is already checked above).
            //
            // However, there's a subtle case we need to handle: jumping from inside
            // a nested scope OUT to a label in an outer scope, when there are locals
            // declared in the outer scope between the inner block and the label.
            // But this is naturally prevented by the scope level check and is handled
            // at function compilation time.
        } else {
            // Backward jump: from label to goto (label was already seen)
            // Jumping backwards/out of scopes is always allowed
            // We just need to close any <close> variables (already handled by emitCloseVariables)
        }
    }

    /**
     * Resolve all pending forward gotos and validate them.
     * This should be called after all statements in a function have been compiled.
     */
    fun resolveForwardGotos() {
        val iterator = pendingGotos.iterator()
        while (iterator.hasNext()) {
            val gotoInfo = iterator.next()
            // Find a label visible from the goto's lexical scope ancestry
            val labelInfo = findLabelVisibleFrom(gotoInfo.labelName, gotoInfo.scopeStack)

            if (labelInfo != null) {
                // Found a visible label - validate and patch
                validateGotoScope(
                    gotoScopeLevel = gotoInfo.scopeLevel,
                    gotoLocalCount = gotoInfo.localCount,
                    labelScopeLevel = labelInfo.scopeLevel,
                    labelLocalCount = labelInfo.localCount,
                    labelName = gotoInfo.labelName,
                    isForward = true,
                    gotoLine = gotoInfo.line,
                    gotoInstructionIndex = gotoInfo.instructionIndex,
                    labelInstructionIndex = labelInfo.instructionIndex,
                )
                patchJump(gotoInfo.instructionIndex, labelInfo.instructionIndex)
                // Record for validation
                resolvedGotos.add(ResolvedGotoLabel(gotoInfo, labelInfo))
                iterator.remove()
            }
        }

        // Validate all resolved goto-label pairs across all scope levels
        val allScopeLevels = resolvedGotos.map { it.labelInfo.scopeLevel }.toSet()
        for (scopeLevel in allScopeLevels.sorted()) {
            validateGotosAtScopeExit(scopeLevel, instructions.size)
        }

        // Check for unresolved gotos (labels that were never defined or not visible)
        if (pendingGotos.isNotEmpty()) {
            val firstUnresolved = pendingGotos.first()
            throw ParserException(
                message = "no visible label '${firstUnresolved.labelName}' for <goto> at line ${firstUnresolved.line}",
                token = Token(TokenType.IDENTIFIER, firstUnresolved.labelName, firstUnresolved.labelName, currentLine, 1),
            )
        }
    }

    /**
     * Validate resolved goto-label pairs at scope exit.
     * This checks Lua's rule: you can't jump over local variable declarations
     * if there are statements after the label that execute in the scope of those locals.
     */
    fun validateGotosAtScopeExit(
        scopeLevel: Int,
        endPc: Int,
        isRepeatUntilBlock: Boolean = false,
    ) {
        val iterator = resolvedGotos.iterator()
        while (iterator.hasNext()) {
            val pair = iterator.next()
            val gotoInfo = pair.gotoInfo
            val labelInfo = pair.labelInfo

            // Only validate gotos/labels at this scope level
            if (labelInfo.scopeLevel != scopeLevel) {
                continue
            }

            // Remove this pair from the list since we're processing it
            iterator.remove()

            // Check if goto jumped over any local variables
            // that were declared between goto and label
            // Use allLocals to include locals that have gone out of scope
            val jumpedOverLocal =
                if (gotoInfo.instructionIndex < labelInfo.instructionIndex) {
                    // Forward jump
                    scopeManager.allLocals.any { local ->
                        local.scopeLevel == scopeLevel &&
                            local.startPc > gotoInfo.instructionIndex &&
                            local.startPc <= labelInfo.instructionIndex
                    }
                } else {
                    // Backward jump - not a problem
                    false
                }

            if (jumpedOverLocal) {
                // Check if there are statements after the label within its scope
                // If label is at end of block (labelIndex == endPc - 1), it's OK
                // EXCEPT for repeat-until blocks where the condition is semantically part of the block
                // Use labelInfo.scopeEndPc if set (when scope ended), otherwise use endPc parameter
                val actualEndPc = if (labelInfo.scopeEndPc > 0) labelInfo.scopeEndPc else endPc
                val hasStatementsAfterLabel = labelInfo.instructionIndex < actualEndPc - 1

                if (hasStatementsAfterLabel || isRepeatUntilBlock || labelInfo.isInRepeatUntilBlock) {
                    // Find the first local that was jumped over (use allLocals to include inactive locals)
                    val firstJumpedLocal =
                        scopeManager.allLocals.find { local ->
                            local.scopeLevel == scopeLevel &&
                                local.startPc > gotoInfo.instructionIndex &&
                                local.startPc <= labelInfo.instructionIndex
                        }

                    val lineInfo = if (gotoInfo.line > 0) " at line ${gotoInfo.line}" else ""
                    throw ParserException(
                        message = "<goto ${gotoInfo.labelName}>$lineInfo jumps into the scope of local '${firstJumpedLocal?.name ?: "?"}'",
                        token =
                            Token(
                                TokenType.IDENTIFIER,
                                gotoInfo.labelName,
                                gotoInfo.labelName,
                                if (gotoInfo.line >
                                    0
                                ) {
                                    gotoInfo.line
                                } else {
                                    1
                                },
                                1,
                            ),
                    )
                }
            }
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

        // Resolve all pending forward gotos now that all labels are known
        childCtx.resolveForwardGotos()

        childCtx.emitCloseVariables(0) // Emit lineEvent for lastLineDefined (the 'end' line) before RETURN
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
