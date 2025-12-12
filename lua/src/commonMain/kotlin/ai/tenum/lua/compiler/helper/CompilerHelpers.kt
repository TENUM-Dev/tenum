package ai.tenum.lua.compiler.helper

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.LineEvent
import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.compiler.model.UpvalueInfo
import ai.tenum.lua.parser.ast.Expression
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.OpCode

/**
 * Type alias for function call compiler function signature.
 * Used to eliminate duplication of this complex function type across multiple helpers.
 */
internal typealias FunctionCallCompiler = (
    FunctionCall,
    Int,
    CompileContext,
    (Expression, Int, CompileContext) -> Unit,
    Int,
    Boolean,
) -> Unit

/**
 * Shared compiler helper utilities for eliminating code duplication across compilation stages.
 */
object CompilerHelpers {
    /**
     * Process upvalues without sorting to match Lua 5.4's textual reference order.
     *
     * Lua 5.4 maintains upvalues in the order they're first referenced in the function body,
     * NOT in register/declaration order. This is critical for debug.upvalueid() correctness.
     *
     * @param originalUpvalues Upvalue list in order of first reference
     * @return Pair of (upvalues, identity map)
     */
    fun sortAndMapUpvalues(originalUpvalues: List<UpvalueInfo>): Pair<List<UpvalueInfo>, Map<Int, Int>> {
        // Keep upvalues in their original order (order of first reference in source)
        // Create an identity mapping since no reordering is needed
        val identityMap = originalUpvalues.indices.associateWith { it }

        return Pair(originalUpvalues, identityMap)
    }

    /**
     * Remaps upvalue indices after sorting and updates child function constants.
     *
     * This helper consolidates the duplicated upvalue remapping logic found in:
     * - CompileContext.compileChunk (lines ~320-360)
     * - FunctionCompiler.finalize (lines ~65-105)
     *
     * After sorting upvalues (to match Lua 5.4's debug.setupvalue order), we must:
     * 1. Remap GETUPVAL/SETUPVAL instruction indices
     * 2. Update child function upvalueInfo for parent upvalue references
     *
     * @param instructions Original instruction list
     * @param constants Original constant pool
     * @param upvalueIndexMap Mapping from old upvalue index to new index
     * @return Pair of (remapped instructions, remapped constants)
     */
    fun remapUpvalueIndices(
        instructions: List<Instruction>,
        constants: List<LuaValue<*>>,
        upvalueIndexMap: Map<Int, Int>,
    ): Pair<List<Instruction>, List<LuaValue<*>>> {
        // Remap GETUPVAL and SETUPVAL instructions to use new upvalue indices
        val remappedInstructions =
            instructions.map { instruction ->
                when (instruction.opcode) {
                    OpCode.GETUPVAL,
                    OpCode.SETUPVAL,
                    -> {
                        // B field contains the upvalue index
                        val oldUpvalIndex = instruction.b
                        val newUpvalIndex = upvalueIndexMap[oldUpvalIndex] ?: oldUpvalIndex
                        instruction.copy(b = newUpvalIndex)
                    }
                    else -> instruction
                }
            }

        // Remap child function upvalueInfo indices in constants
        // When a child function captures a parent upvalue (inStack=false), it stores the parent's
        // upvalue index. After sorting parent upvalues, we must update these indices.
        val remappedConstants =
            constants.map { constant ->
                if (constant is LuaCompiledFunction) {
                    val proto = constant.proto
                    val remappedUpvalueInfo =
                        proto.upvalueInfo.map { upvalInfo ->
                            if (!upvalInfo.inStack && upvalueIndexMap.containsKey(upvalInfo.index)) {
                                // This upvalue references a parent upvalue - remap the index
                                upvalInfo.copy(index = upvalueIndexMap[upvalInfo.index]!!)
                            } else {
                                upvalInfo
                            }
                        }
                    LuaCompiledFunction(
                        proto.copy(upvalueInfo = remappedUpvalueInfo),
                    )
                } else {
                    constant
                }
            }

        return Pair(remappedInstructions, remappedConstants)
    }

    /**
     * Emits short-circuit AND/OR scaffolding: line update + TEST + JMP + right-hand expression + patch.
     *
     * This helper consolidates the duplicated short-circuit codegen found in:
     * - ExpressionCompiler.compileBinaryOp (AND branch, lines ~222-250)
     * - ExpressionCompiler.compileBinaryOp (OR branch, lines ~254-282)
     *
     * Short-circuit evaluation:
     * - AND: if left is false, skip right (TEST k=0)
     * - OR: if left is true, skip right (TEST k=1)
     *
     * @param targetReg Register containing left value (also destination for final result)
     * @param isOr True for OR, false for AND
     * @param operatorLine Line number of the operator (for error reporting)
     * @param ctx Compilation context
     * @param compileRight Lambda to compile the right-hand expression into a temp register
     */
    fun emitShortCircuit(
        targetReg: Int,
        isOr: Boolean,
        operatorLine: Int,
        ctx: CompileContext,
        compileRight: (rightReg: Int) -> Unit,
    ) {
        // Update line number to operator's line for error reporting
        if (operatorLine != ctx.currentLine) {
            ctx.currentLine = operatorLine
            ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
        }

        // TEST targetReg, 0, k  where k=0 for AND (skip if false), k=1 for OR (skip if true)
        ctx.emit(OpCode.TEST, targetReg, 0, if (isOr) 1 else 0)
        val skipJump = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0)

        // Compile right expression into temp, then move to targetReg
        ctx.registerAllocator.withTempRegister { rightReg ->
            compileRight(rightReg)
            ctx.emit(OpCode.MOVE, targetReg, rightReg, 0)
        }

        // Patch jump to skip right expression if short-circuit triggered
        ctx.patchJump(skipJump, ctx.instructions.size)
    }

    /**
     * Emits method SELF setup: receiver compilation + method constant + SELF instruction.
     *
     * This helper consolidates the duplicated method call setup found in:
     * - CallCompiler.compileMethodCall (lines ~329-349)
     * - StatementCompiler.compileFunctionCall (lines ~93-113)
     *
     * Method call setup:
     * 1. Compile receiver into selfReg
     * 2. Create method name constant
     * 3. Emit SELF instruction: R[base+1] = self, R[base] = self[method]
     *    - If method constant fits in RK field, use it directly
     *    - Otherwise, load constant to temp register first
     *
     * @param receiverExpr Receiver expression (e.g., table)
     * @param methodName Method name string
     * @param base Base register for SELF instruction
     * @param selfReg Register to compile receiver into
     * @param ctx Compilation context
     * @param compileExpression Expression compiler function
     */
    fun emitMethodSelfSetup(
        receiverExpr: Expression,
        methodName: String,
        base: Int,
        selfReg: Int,
        ctx: CompileContext,
        compileExpression: (Expression, Int, CompileContext) -> Unit,
    ) {
        // 1) Compile receiver into selfReg
        compileExpression(receiverExpr, selfReg, ctx)

        // 2) Prepare method constant
        val methodConst = ctx.addConstant(LuaString(methodName))

        // 3) SELF base, selfReg, K(method)
        //    -> R[base+1] = self, R[base] = self[method]
        if (ctx.canUseRKOperand(methodConst)) {
            ctx.emit(
                OpCode.SELF,
                base,
                selfReg,
                ctx.getRKOperandForConstant(methodConst),
            )
        } else {
            ctx.registerAllocator.withTempRegister { methodReg ->
                ctx.emit(OpCode.LOADK, methodReg, methodConst, 0)
                ctx.emit(OpCode.SELF, base, selfReg, methodReg)
            }
        }
    }

    /**
     * Updates line info if needed before compiling an expression.
     *
     * This helper consolidates the duplicated line tracking pattern found in:
     * - ExpressionCompiler (multiple locations in table constructor)
     * - Various expression compilation sites
     *
     * @param exprLine Line number of the expression
     * @param ctx Compilation context
     */
    fun updateLineForExpression(
        exprLine: Int,
        ctx: CompileContext,
    ) {
        if (exprLine != ctx.currentLine) {
            ctx.currentLine = exprLine
            ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
        }
    }

    /**
     * Compiles a list of fixed arguments, handling nested FunctionCalls with numResults=1.
     *
     * This helper consolidates the duplicated argument compilation loop found in:
     * - CallCompiler (multiple locations)
     *
     * @param args Argument expressions
     * @param startReg Starting register for arguments
     * @param ctx Compilation context
     * @param compileExpression Expression compiler function
     * @param compileFunctionCall Function call compiler
     */
    fun compileFixedArguments(
        args: List<Expression>,
        startReg: Int,
        ctx: CompileContext,
        compileExpression: (Expression, Int, CompileContext) -> Unit,
        compileFunctionCall: FunctionCallCompiler? = null,
    ) {
        for ((i, arg) in args.withIndex()) {
            val argReg = startReg + i
            if (arg is FunctionCall && compileFunctionCall != null) {
                compileFunctionCall(arg, argReg, ctx, compileExpression, 1, false)
            } else {
                compileExpression(arg, argReg, ctx)
            }
        }
    }

    /**
     * Compiles a simple function call: function + fixed args, then emit CALL with result handling.
     *
     * This helper consolidates the duplicated simple call pattern found in:
     * - CallCompiler lines 56-75 (simple case with no nested calls)
     * - CallCompiler lines 137-159 (middle FunctionCall case)
     *
     * @param functionExpr Function expression to compile
     * @param args Argument expressions
     * @param targetReg Target register for results
     * @param numResults Number of results to capture
     * @param captureAllResults Whether to use c=0 (capture all)
     * @param line Line number for error reporting
     * @param ctx Compilation context
     * @param compileExpression Expression compiler function
     * @param compileFunctionCall Optional function call compiler for nested calls
     * @param updateLineForCall Line update function
     */
    fun compileSimpleFunctionCall(
        functionExpr: Expression,
        args: List<Expression>,
        targetReg: Int,
        numResults: Int,
        captureAllResults: Boolean,
        line: Int,
        ctx: CompileContext,
        compileExpression: (Expression, Int, CompileContext) -> Unit,
        compileFunctionCall: FunctionCallCompiler? = null,
        updateLineForCall: (Int, CompileContext) -> Unit,
    ) {
        val argCount = args.size
        val totalSlots = 1 + argCount

        ctx.registerAllocator.withContiguousRegisters(totalSlots) { regs ->
            val funcReg = regs[0]
            compileExpression(functionExpr, funcReg, ctx)

            // Compile arguments (with optional nested call support)
            compileFixedArguments(args, funcReg + 1, ctx, compileExpression, compileFunctionCall)

            updateLineForCall(line, ctx)
            val cParam = if (captureAllResults) 0 else numResults + 1
            ctx.emit(OpCode.CALL, funcReg, argCount + 1, cParam)

            // Move results if needed
            if (funcReg != targetReg && !captureAllResults) {
                for (i in 0 until numResults) {
                    ctx.emit(OpCode.MOVE, targetReg + i, funcReg + i, 0)
                }
            }
        }
    }

    /**
     * Emits CALL instruction with result handling.
     *
     * This helper consolidates the duplicated CALL emission and result movement pattern.
     *
     * @param funcReg Function register
     * @param targetReg Target register for results
     * @param argCount Number of arguments (or 0 for b=0 mode)
     * @param numResults Number of results to capture
     * @param captureAllResults Whether to use c=0 (capture all)
     * @param ctx Compilation context
     */
    fun emitCallWithResults(
        funcReg: Int,
        targetReg: Int,
        argCount: Int,
        numResults: Int,
        captureAllResults: Boolean,
        ctx: CompileContext,
    ) {
        val cParam = if (captureAllResults) 0 else numResults + 1
        ctx.emit(OpCode.CALL, funcReg, argCount, cParam)

        // Move results if needed
        if (funcReg != targetReg && !captureAllResults) {
            for (i in 0 until numResults) {
                ctx.emit(OpCode.MOVE, targetReg + i, funcReg + i, 0)
            }
        }
    }

    /**
     * Compiles function into funcReg, then all fixed arguments into consecutive registers.
     *
     * This helper consolidates the duplicated function + fixed args pattern found in:
     * - CallCompiler (vararg case and nested call case)
     *
     * @param functionExpr Function expression to compile
     * @param fixedArgs Fixed argument expressions
     * @param funcReg Register for function
     * @param ctx Compilation context
     * @param compileExpression Expression compiler function
     */
    fun compileFunctionAndFixedArgs(
        functionExpr: Expression,
        fixedArgs: List<Expression>,
        funcReg: Int,
        ctx: CompileContext,
        compileExpression: (Expression, Int, CompileContext) -> Unit,
    ) {
        // Compile function
        compileExpression(functionExpr, funcReg, ctx)

        // Compile fixed args
        for ((i, arg) in fixedArgs.withIndex()) {
            val argReg = funcReg + 1 + i
            compileExpression(arg, argReg, ctx)
        }
    }

    /**
     * Emits GETUPVAL + GETTABLE/SETTABLE for _ENV upvalue access with RK operand handling.
     *
     * This helper consolidates the duplicated _ENV upvalue pattern found in:
     * - ExpressionCompiler.compileVariable (GETTABLE for reading)
     * - StatementCompiler.compileAssignment (SETTABLE for writing)
     *
     * @param targetReg Target register (for GETTABLE) or value register (for SETTABLE)
     * @param envUpvalue _ENV upvalue index
     * @param nameConst Name constant index
     * @param ctx Compilation context
     * @param isRead true for GETTABLE (read), false for SETTABLE (write)
     */
    fun emitEnvUpvalueAccess(
        targetReg: Int,
        envUpvalue: Int,
        nameConst: Int,
        ctx: CompileContext,
        isRead: Boolean,
    ) {
        val canUseRK = ctx.canUseRKOperand(nameConst)

        if (canUseRK) {
            ctx.registerAllocator.withTempRegister { envReg ->
                ctx.emit(OpCode.GETUPVAL, envReg, envUpvalue, 0)
                if (isRead) {
                    ctx.emit(OpCode.GETTABLE, targetReg, envReg, ctx.getRKOperandForConstant(nameConst))
                } else {
                    ctx.emit(OpCode.SETTABLE, envReg, ctx.getRKOperandForConstant(nameConst), targetReg)
                }
            }
        } else {
            // Load large constant to temp register
            ctx.registerAllocator.withTempRegister { nameReg ->
                ctx.emit(OpCode.LOADK, nameReg, nameConst, 0)
                ctx.registerAllocator.withTempRegister { envReg ->
                    ctx.emit(OpCode.GETUPVAL, envReg, envUpvalue, 0)
                    if (isRead) {
                        ctx.emit(OpCode.GETTABLE, targetReg, envReg, nameReg)
                    } else {
                        ctx.emit(OpCode.SETTABLE, envReg, nameReg, targetReg)
                    }
                }
            }
        }
    }

    /**
     * Cleans up loop scope: end loop, remove labels, free locals, emit CLOSE if needed, patch breaks.
     *
     * This helper consolidates the duplicated loop cleanup pattern found in:
     * - StatementCompiler.compileRepeatUntil (lines ~604-625)
     * - StatementCompiler.compileGenericFor (lines ~800-820)
     *
     * @param bodySnapshot Scope snapshot from loop body start (returned by beginScope())
     * @param ctx Compilation context
     * @param emitClose Whether to emit CLOSE instruction for captured variables
     */
    fun cleanupLoopScope(
        bodySnapshot: Int,
        ctx: CompileContext,
        emitClose: Boolean = false,
    ) {
        val breaks = ctx.scopeManager.endLoop()

        // Labels persist until function end (function-scoped, not block-scoped)
        // Visibility is controlled by scopeStack ancestry in findLabelVisibleFrom
        val scopeLevel = ctx.scopeManager.currentScopeLevel
        val bodyExit = ctx.scopeManager.endScope(bodySnapshot, ctx.instructions.size)

        // Free registers used by locals in the loop body scope
        for (local in bodyExit.removedLocals) {
            ctx.registerAllocator.freeLocal()
        }

        // Emit CLOSE for captured variables if needed
        if (emitClose && bodyExit.minCapturedRegister != null) {
            ctx.emit(OpCode.CLOSE, bodyExit.minCapturedRegister, 0, 0)
        }

        // Patch break jumps
        for (breakIndex in breaks) {
            ctx.patchJump(breakIndex, ctx.instructions.size)
        }
    }
}
