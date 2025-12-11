package ai.tenum.lua.compiler.stages

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.helper.CompilerHelpers
import ai.tenum.lua.compiler.model.LineEvent
import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.parser.ast.Expression
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.VarargExpression
import ai.tenum.lua.vm.OpCode

/**
 * Handles all function / method call logic, including multi-return calls.
 */
class CallCompiler {
    /**
     * Helper to update line tracking before CALL/TAILCALL operations.
     * This ensures runtime errors during calls report the correct source line.
     *
     * The line parameter should be the line of the function expression being called,
     * NOT the line where '(' appears or where arguments are.
     * This matches PUC-Rio Lua behavior where "attempt to call" errors report
     * the line of the expression being called.
     */
    private fun updateLineForCall(
        line: Int,
        ctx: CompileContext,
    ) {
        // Always update to the function expression's line and add LineEvent
        // This ensures "attempt to call" errors report where the function expression is
        ctx.currentLine = line
        ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
    }

    /**
     * Normal function call producing a specific number of results.
     * @param numResults The number of results expected. 1 for single return, >1 for multi-assign.
     * @param captureAllResults If true, use c=0 to capture all results (needed when call is an argument to another call)
     * @param allowComplexNesting If false, skip the complex nesting branch (used when already in a multi-return frame)
     */
    fun compileFunctionCall(
        expr: FunctionCall,
        targetReg: Int,
        ctx: CompileContext,
        compileExpression: (expr: Expression, targetReg: Int, ctx: CompileContext) -> Unit,
        numResults: Int = 1,
        captureAllResults: Boolean = false,
        allowComplexNesting: Boolean = true,
    ) {
        val args = expr.arguments
        val lastArg = args.lastOrNull()
        val lastIsVararg = lastArg is VarargExpression
        val lastIsFuncCall = lastArg is FunctionCall

        // Check if ANY argument (not just last) is a FunctionCall - they need special handling
        val anyArgIsFuncCall = args.any { it is FunctionCall }

        // ─────────────────────────────────────────────────────────────
        // 1. SIMPLE CASE: no multi-expansion (no ..., no nested calls)
        // ─────────────────────────────────────────────────────────────
        if (!lastIsVararg && !anyArgIsFuncCall) {
            CompilerHelpers.compileSimpleFunctionCall(
                functionExpr = expr.function,
                args = args,
                targetReg = targetReg,
                numResults = numResults,
                captureAllResults = captureAllResults,
                line = expr.line,
                ctx = ctx,
                compileExpression = compileExpression,
                compileFunctionCall = null,
                updateLineForCall = ::updateLineForCall,
            )
            return
        }

        // From here on: we have varargs or nested FunctionCalls.
        // We need to handle these with multi-return semantics.
        // ─────────────────────────────────────────────────────────────

        // Identify which arguments are FunctionCalls (they need all their results captured)
        val funcCallIndices =
            args
                .withIndex()
                .filter { it.value is FunctionCall }
                .map { it.index }
                .toSet()

        // Fixed part: all args except the last one
        val fixedArgs = args.dropLast(1)

        if (lastIsVararg) {
            // ─────────────────────────────────────────────────────────
            // 2. LAST ARG IS "..."  → foo(a, b, ...)
            // ─────────────────────────────────────────────────────────
            val totalSlots = 1 + fixedArgs.size + 1 // func + fixed + vararg-start

            ctx.registerAllocator.withContiguousRegisters(totalSlots) { regs ->
                val funcReg = regs[0]

                // Compile function and fixed args using helper
                CompilerHelpers.compileFunctionAndFixedArgs(expr.function, fixedArgs, funcReg, ctx, compileExpression)

                val varargStart = funcReg + 1 + fixedArgs.size

                // Expand all varargs starting at varargStart
                ctx.emit(OpCode.VARARG, varargStart, 0, 0)

                // Now outer CALL uses B = 0 to read A+1..top-1 as args
                // last is vararg
                updateLineForCall(expr.line, ctx)
                ctx.emit(OpCode.CALL, funcReg, 0, numResults + 1)

                if (funcReg != targetReg) {
                    // Move all results from funcReg..funcReg+numResults-1 to targetReg..targetReg+numResults-1
                    for (i in 0 until numResults) {
                        ctx.emit(OpCode.MOVE, targetReg + i, funcReg + i, 0)
                    }
                }
            }
            return
        }

        // ─────────────────────────────────────────────────────────────
        // 3. LAST ARG IS A FUNCTION CALL → foo(a, b, bar(...))
        //    We want ALL results of bar() to become args to foo.
        // ─────────────────────────────────────────────────────────────
        if (!lastIsFuncCall) {
            // FunctionCall is in middle position, not last - fall back to simple inline compilation
            // This is a rare case: foo(bar(), "literal") or foo(bar(), baz())
            // Compile each arg including FunctionCalls with numResults=1 (take first result only)
            CompilerHelpers.compileSimpleFunctionCall(
                functionExpr = expr.function,
                args = args,
                targetReg = targetReg,
                numResults = numResults,
                captureAllResults = captureAllResults,
                line = expr.line,
                ctx = ctx,
                compileExpression = compileExpression,
                compileFunctionCall = ::compileFunctionCall,
                updateLineForCall = ::updateLineForCall,
            )
            return
        }

        val innerCall = lastArg as FunctionCall
        val innerArgs = innerCall.arguments

        // Check if inner call has FunctionCall args (needs recursive handling)
        // If so, use simpler compilation: compile inner call first, then outer call
        val innerHasFuncCallArgs = innerArgs.any { it is FunctionCall }

        if (allowComplexNesting && (innerHasFuncCallArgs || innerArgs.any { it is VarargExpression })) {
            // Complex nesting - compile inner call first with all results
            // Allocate func reg, compile function there
            val funcReg = ctx.registerAllocator.allocateTemp()
            compileExpression(expr.function, funcReg, ctx)

            // Allocate registers for fixed args
            val fixedArgRegs = List(fixedArgs.size) { ctx.registerAllocator.allocateTemp() }
            for ((i, arg) in fixedArgs.withIndex()) {
                compileExpression(arg, fixedArgRegs[i], ctx)
            }

            // The inner call results must land at funcReg+1+fixedArgs.size for outer call args
            val innerResultsReg = funcReg + 1 + fixedArgs.size

            // Compile inner call recursively with captureAllResults
            // Results will land at innerResultsReg, innerResultsReg+1, ...
            compileFunctionCall(innerCall, innerResultsReg, ctx, compileExpression, numResults = 1, captureAllResults = true)

            // Call outer function with b=0 and handle results
            updateLineForCall(expr.line, ctx)
            CompilerHelpers.emitCallWithResults(funcReg, targetReg, 0, numResults, captureAllResults, ctx)

            // Free registers
            fixedArgRegs.reversed().forEach { ctx.registerAllocator.freeTemp(it) }
            ctx.registerAllocator.freeTemp(funcReg)
            return
        }

        // Simple case: inner call has no nested FunctionCalls or varargs
        val innerLast = innerArgs.lastOrNull()
        val innerLastIsVararg = innerLast is VarargExpression
        val innerFixedArgs =
            if (innerLastIsVararg) {
                innerArgs.dropLast(1)
            } else {
                innerArgs
            }

        // Frame layout for OUTER + INNER (simple inline):
        //
        //   funcReg               -> outer function (foo)
        //   funcReg+1 ..+k-1      -> outer fixed args
        //   innerFuncReg          -> inner function (bar)
        //   innerFuncReg+1..      -> inner fixed args
        //
        val outerFixedCount = fixedArgs.size
        val innerFixedCount = innerFixedArgs.size
        val innerExtraSlot = if (innerLastIsVararg) 1 else 0

        val totalSlots =
            1 + // outer func
                outerFixedCount + // outer fixed args
                1 + // inner func (first result slot)
                innerFixedCount + // inner fixed args
                innerExtraSlot // inner vararg start, if needed

        ctx.registerAllocator.withContiguousRegisters(totalSlots) { regs ->
            val funcReg = regs[0]

            // OUTER function and fixed args using helper
            CompilerHelpers.compileFunctionAndFixedArgs(expr.function, fixedArgs, funcReg, ctx, compileExpression)

            // INNER call base
            val innerFuncReg = funcReg + 1 + outerFixedCount

            // INNER function (bar) into innerFuncReg
            compileExpression(innerCall.function, innerFuncReg, ctx)

            // INNER fixed args into innerFuncReg+1 ..
            for ((i, arg) in innerFixedArgs.withIndex()) {
                val argReg = innerFuncReg + 1 + i
                compileExpression(arg, argReg, ctx)
            }

            if (innerLastIsVararg) {
                val varargStart = innerFuncReg + 1 + innerFixedCount
                ctx.emit(OpCode.VARARG, varargStart, 0, 0)
            }

            val innerArgCount =
                if (innerLastIsVararg) {
                    0 // bar(a, ...) – takes A+1..top-1 (variadic)
                } else {
                    innerArgs.size + 1 // function + N args
                }

            // bar(...) → ALL results (C = 0)
            updateLineForCall(innerCall.line, ctx)
            ctx.emit(OpCode.CALL, innerFuncReg, innerArgCount, 0)

            // Call outer function with b=0 and handle results
            updateLineForCall(expr.line, ctx)
            CompilerHelpers.emitCallWithResults(funcReg, targetReg, 0, numResults, captureAllResults, ctx)
        }
    }

    /**
     * Method calls: receiver:method(args...)  → SELF + CALL.
     */
    fun compileMethodCall(
        expr: MethodCall,
        targetReg: Int,
        ctx: CompileContext,
        compileExpression: (expr: Expression, targetReg: Int, ctx: CompileContext) -> Unit,
        numResults: Int = 1,
    ) {
        val argCount = expr.arguments.size
        // Frame: [func/method] [self] [args...]
        val totalSlots = 2 + argCount

        ctx.registerAllocator.withContiguousRegisters(totalSlots) { callRegs ->
            val base = callRegs[0] // A for SELF and CALL
            val selfReg = callRegs[1] // B for SELF

            // Use helper to emit method SELF setup
            CompilerHelpers.emitMethodSelfSetup(
                receiverExpr = expr.receiver,
                methodName = expr.method,
                base = base,
                selfReg = selfReg,
                ctx = ctx,
                compileExpression = compileExpression,
            )

            // 4) Compile arguments into callRegs[2..]
            for ((i, arg) in expr.arguments.withIndex()) {
                val argReg = callRegs[2 + i]
                compileExpression(arg, argReg, ctx)
            }

            // 5) CALL base, B, C  (B = func+self+args, C = num results)
            updateLineForCall(expr.line, ctx)
            ctx.emit(OpCode.CALL, base, argCount + 2, numResults + 1)

            // 6) Move result to targetReg if needed
            if (base != targetReg) {
                // Move all results from base..base+numResults-1 to targetReg..targetReg+numResults-1
                for (i in 0 until numResults) {
                    ctx.emit(OpCode.MOVE, targetReg + i, base + i, 0)
                }
            }
        }
    }

    /**
     * Multi-return helper used from statements (return, for-in, locals, etc.).
     * @param numResults The number of results expected. 0 for all results.
     */
    fun compileFunctionCallForMultiReturn(
        expr: FunctionCall,
        baseReg: Int,
        ctx: CompileContext,
        compileExpression: (expr: Expression, targetReg: Int, ctx: CompileContext) -> Unit,
        numResults: Int = 0,
    ) {
        val lastArg = expr.arguments.lastOrNull()
        val lastIsVararg = lastArg is VarargExpression
        val lastIsFuncCall = lastArg is FunctionCall
        val fixedArgs = if (lastIsVararg || lastIsFuncCall) expr.arguments.dropLast(1) else expr.arguments

        // Check if any argument (not just last) is a FunctionCall
        val anyArgIsFuncCall = expr.arguments.any { it is FunctionCall }

        val totalSlots = 1 + fixedArgs.size + if (lastIsVararg || lastIsFuncCall) 1 else 0

        ctx.registerAllocator.withContiguousRegisters(totalSlots) { frame ->
            val funcReg = baseReg

            // function
            compileExpression(expr.function, funcReg, ctx)

            // fixed args - handle nested FunctionCalls properly
            for ((i, arg) in fixedArgs.withIndex()) {
                val argReg = funcReg + 1 + i
                if (arg is FunctionCall) {
                    // Compile nested function call with numResults=1 (take first result only)
                    compileFunctionCall(arg, argReg, ctx, compileExpression, numResults = 1, captureAllResults = false)
                } else {
                    compileExpression(arg, argReg, ctx)
                }
            }

            // Last argument if it's multi-value
            if (lastIsVararg) {
                val varargReg = funcReg + 1 + fixedArgs.size
                ctx.emit(OpCode.VARARG, varargReg, 0, 0)
            } else if (lastIsFuncCall) {
                // Last argument is a function call - we need to inline it here
                // NOT delegate to compileFunctionCall (which would allocate a new frame)
                val nestedCall = lastArg as FunctionCall
                val nestedFuncReg = funcReg + 1 + fixedArgs.size

                // Compile nested function
                compileExpression(nestedCall.function, nestedFuncReg, ctx)

                // Compile nested args
                val nestedArgs = nestedCall.arguments
                val nestedLastArg = nestedArgs.lastOrNull()
                val nestedLastIsVararg = nestedLastArg is VarargExpression
                val nestedLastIsFuncCall = nestedLastArg is FunctionCall
                val nestedFixedArgs =
                    if (nestedLastIsVararg || nestedLastIsFuncCall) {
                        nestedArgs.dropLast(1)
                    } else {
                        nestedArgs
                    }

                // Compile fixed args of nested call
                for ((i, arg) in nestedFixedArgs.withIndex()) {
                    val argReg = nestedFuncReg + 1 + i
                    if (arg is FunctionCall) {
                        // Recursive case: compile with numResults=1
                        compileFunctionCall(arg, argReg, ctx, compileExpression, numResults = 1, captureAllResults = false)
                    } else {
                        compileExpression(arg, argReg, ctx)
                    }
                }

                // Handle last arg of nested call
                if (nestedLastIsVararg) {
                    val varargStart = nestedFuncReg + 1 + nestedFixedArgs.size
                    ctx.emit(OpCode.VARARG, varargStart, 0, 0)
                } else if (nestedLastIsFuncCall) {
                    // Deeply nested: oneless(oneless(oneless(1, 2)))
                    // Recursively use compileFunctionCallForMultiReturn to handle it
                    val innerCall = nestedLastArg as FunctionCall
                    val innerTarget = nestedFuncReg + 1 + nestedFixedArgs.size
                    compileFunctionCallForMultiReturn(innerCall, innerTarget, ctx, compileExpression, numResults = 0)
                }

                // Emit CALL for nested function with b=0 (varargs), c=0 (capture all)
                val nestedBParam = if (nestedLastIsVararg || nestedLastIsFuncCall) 0 else nestedArgs.size + 1
                updateLineForCall(nestedCall.line, ctx)
                ctx.emit(OpCode.CALL, nestedFuncReg, nestedBParam, 0)
            }

            val b = if (lastIsVararg || lastIsFuncCall) 0 else fixedArgs.size + 1
            val c = if (numResults == 0) 0 else numResults + 1

            updateLineForCall(expr.line, ctx)
            ctx.emit(OpCode.CALL, funcReg, b, c)
        }
    }
}
