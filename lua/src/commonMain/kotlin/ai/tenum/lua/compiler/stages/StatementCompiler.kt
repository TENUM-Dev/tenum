package ai.tenum.lua.compiler.stages

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.helper.CompilerHelpers
import ai.tenum.lua.compiler.helper.ScopeManager
import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.LineEvent
import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.lexer.Token
import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ParserException
import ai.tenum.lua.parser.ast.Assignment
import ai.tenum.lua.parser.ast.BinaryOp
import ai.tenum.lua.parser.ast.BreakStatement
import ai.tenum.lua.parser.ast.DoStatement
import ai.tenum.lua.parser.ast.Expression
import ai.tenum.lua.parser.ast.ExpressionStatement
import ai.tenum.lua.parser.ast.FieldAccess
import ai.tenum.lua.parser.ast.ForInStatement
import ai.tenum.lua.parser.ast.ForStatement
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionDeclaration
import ai.tenum.lua.parser.ast.GotoStatement
import ai.tenum.lua.parser.ast.IfStatement
import ai.tenum.lua.parser.ast.IndexAccess
import ai.tenum.lua.parser.ast.LabelStatement
import ai.tenum.lua.parser.ast.LocalDeclaration
import ai.tenum.lua.parser.ast.LocalFunctionDeclaration
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.ParenExpression
import ai.tenum.lua.parser.ast.RepeatStatement
import ai.tenum.lua.parser.ast.ReturnStatement
import ai.tenum.lua.parser.ast.Statement
import ai.tenum.lua.parser.ast.UnaryOp
import ai.tenum.lua.parser.ast.VarargExpression
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.parser.ast.WhileStatement
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.OpCode

class StatementCompiler {
    val callCompiler = CallCompiler()
    val expressionCompiler = ExpressionCompiler(callCompiler)

    /**
     * Emit a LineEvent for the given statement if its line differs from the current tracked line.
     * This avoids duplicate line events and ensures accurate error reporting.
     */
    private fun emitLineEventIfNeeded(
        statement: Statement,
        ctx: CompileContext,
    ) {
        if (statement.line != ctx.currentLine) {
            ctx.currentLine = statement.line
            ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
        }
    }

    /**
     * Emit line event for the line after a loop (typically 'end'), but only for multi-line loops.
     * In single-line format, everything is on one line and we don't need an end line event.
     *
     * @param statementLine The line where the loop statement starts
     * @param block The loop body statements
     * @param ctx The compilation context
     */
    private fun emitLoopEndLineEvent(
        statementLine: Int,
        block: List<Statement>,
        ctx: CompileContext,
    ) {
        if (block.isNotEmpty()) {
            val lastLine = block.last().line
            val endLine = lastLine + 1
            // Only emit if the end line is different from the statement line and current line
            if (endLine != statementLine && endLine != ctx.currentLine) {
                ctx.currentLine = endLine
                ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
            }
        }
    }

    /**
     * Checks if an expression contains a multi-line binary operation.
     * Multi-line means operands are on different lines than the operator.
     * Used to detect patterns like db.lua:207 where assignment RHS spans multiple lines.
     */
    private fun hasMultiLineBinaryOp(expr: Expression): Boolean =
        when (expr) {
            is BinaryOp -> {
                val leftOnDifferentLine = expr.left.line != expr.line
                val rightOnDifferentLine = expr.right.line != expr.line
                leftOnDifferentLine || rightOnDifferentLine || hasMultiLineBinaryOp(expr.left) || hasMultiLineBinaryOp(expr.right)
            }
            is UnaryOp -> hasMultiLineBinaryOp(expr.operand)
            is FunctionCall -> expr.arguments.any { hasMultiLineBinaryOp(it) }
            is MethodCall -> expr.arguments.any { hasMultiLineBinaryOp(it) } || hasMultiLineBinaryOp(expr.receiver)
            is IndexAccess -> hasMultiLineBinaryOp(expr.table) || hasMultiLineBinaryOp(expr.index)
            is FieldAccess -> hasMultiLineBinaryOp(expr.table)
            is ParenExpression -> hasMultiLineBinaryOp(expr.expression)
            else -> false
        }

    /**
     * Gets the line number of the rightmost (last evaluated) sub-expression.
     * For multi-line binary expressions, this is the line of the right operand.
     * Used for assignment instructions to match lua548's line tagging behavior.
     */
    private fun getRightmostLine(expr: Expression): Int =
        when (expr) {
            is BinaryOp -> getRightmostLine(expr.right)
            is UnaryOp -> getRightmostLine(expr.operand)
            is FunctionCall -> if (expr.arguments.isNotEmpty()) getRightmostLine(expr.arguments.last()) else expr.line
            is MethodCall -> if (expr.arguments.isNotEmpty()) getRightmostLine(expr.arguments.last()) else expr.line
            is IndexAccess -> expr.line // IndexAccess itself is the rightmost
            is FieldAccess -> expr.line // FieldAccess itself is the rightmost
            is ParenExpression -> getRightmostLine(expr.expression)
            else -> expr.line
        }

    fun compileStatement(
        statement: Statement,
        ctx: CompileContext,
    ) {
        // Track line from AST node for accurate error reporting
        // For control flow statements (if/while/for/repeat), line tracking is handled
        // within the specific compile function to properly track keywords like 'then' and 'end'
        // For multi-line assignments (db.lua:207), skip the initial line event and let
        // the RHS expressions handle line tracking to match lua548 behavior.
        val skipInitialLineEvent =
            statement is Assignment &&
                statement.expressions.any { hasMultiLineBinaryOp(it) }

        if (statement !is IfStatement &&
            statement !is WhileStatement &&
            statement !is RepeatStatement &&
            statement !is ForStatement &&
            statement !is ForInStatement &&
            statement !is DoStatement &&
            !skipInitialLineEvent
        ) {
            // Only add LineEvent if the line has changed
            if (statement.line != ctx.currentLine) {
                ctx.currentLine = statement.line
                ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
            }
        }

        when (statement) {
            is ReturnStatement -> compileReturn(statement, ctx)
            is LocalDeclaration -> compileLocalDeclaration(statement, ctx)
            is Assignment -> compileAssignment(statement, ctx)
            is ExpressionStatement -> compileExpressionStatement(statement, ctx)
            is IfStatement -> compileIfStatement(statement, ctx)
            is WhileStatement -> compileWhileStatement(statement, ctx)
            is RepeatStatement -> compileRepeatStatement(statement, ctx)
            is ForStatement -> compileForStatement(statement, ctx)
            is ForInStatement -> compileForInStatement(statement, ctx)
            is FunctionDeclaration -> compileFunctionDeclaration(statement, ctx)
            is LocalFunctionDeclaration -> compileLocalFunctionDeclaration(statement, ctx)
            is DoStatement -> compileDoStatement(statement, ctx)
            is BreakStatement -> compileBreakStatement(ctx)
            is GotoStatement -> compileGotoStatement(statement, ctx)
            is LabelStatement -> compileLabelStatement(statement, ctx)
        }
    }

    internal fun compileReturn(
        statement: ReturnStatement,
        ctx: CompileContext,
    ) {
        // NOTE: Do NOT emit CLOSE mode=2 here.
        // The RETURN opcode itself will call __close metamethods AFTER return values are evaluated.
        // This ensures return values are not corrupted by __close mutations (see locals.lua:255).
        // ctx.emitCloseVariables(0)  ← REMOVED

        // Check if there are any to-be-closed variables in the current scope
        // If so, we CANNOT use tail call optimization because RETURN must execute to close them
        val hasToBeClosedVars = ctx.scopeManager.locals.any { it.isClose && it.isActive }

        if (statement.expressions.isEmpty()) {
            ctx.emit(OpCode.RETURN, 0, 1, 0)
        } else if (statement.expressions.size == 1 && statement.expressions[0] is ParenExpression) {
            // Check if parenthesized expression contains a call (method or function)
            // Lua 5.4 does NOT use TAILCALL for parenthesized returns because
            // parentheses force exactly 1 return value
            val parenExpr = statement.expressions[0] as ParenExpression

            when (val innerExpr = parenExpr.expression) {
                is FunctionCall -> {
                    // return (f(...)) - use CALL with fixed return count, not TAILCALL
                    ctx.registerAllocator.withTempRegister { funcReg ->
                        callCompiler.compileFunctionCall(
                            innerExpr,
                            funcReg,
                            ctx,
                            expressionCompiler::compileExpression,
                            numResults = 1,
                        )
                        ctx.emit(OpCode.RETURN, funcReg, 2, 1) // Return 1 value
                    }
                }
                is MethodCall -> {
                    // return (self:method(...)) - use CALL with fixed return count, not TAILCALL
                    ctx.registerAllocator.withTempRegister { resultReg ->
                        callCompiler.compileMethodCall(
                            innerExpr,
                            resultReg,
                            ctx,
                            expressionCompiler::compileExpression,
                            numResults = 1,
                        )
                        ctx.emit(OpCode.RETURN, resultReg, 2, 1) // Return 1 value
                    }
                }
                else -> {
                    // return (expr) where expr is not a call
                    ctx.registerAllocator.withTempRegister { resultReg ->
                        expressionCompiler.compileExpression(innerExpr, resultReg, ctx)
                        ctx.emit(OpCode.RETURN, resultReg, 2, 1) // Return 1 value
                    }
                }
            }
        } else if (statement.expressions.size == 1 && statement.expressions[0] is MethodCall && !hasToBeClosedVars) {
            // Tail call optimization for method calls: return self:method(...) becomes TAILCALL
            // BUT: cannot use TCO if there are to-be-closed variables (they must be closed by RETURN)
            val methodCall = statement.expressions[0] as MethodCall
            val argCount = methodCall.arguments.size

            // Frame: [func/method] [self] [args...]
            val totalSlots = 2 + argCount

            ctx.registerAllocator.withContiguousRegisters(totalSlots) { callRegs ->
                val base = callRegs[0] // A for SELF and TAILCALL
                val selfReg = callRegs[1] // B for SELF

                // Use helper to emit method SELF setup
                CompilerHelpers.emitMethodSelfSetup(
                    receiverExpr = methodCall.receiver,
                    methodName = methodCall.method,
                    base = base,
                    selfReg = selfReg,
                    ctx = ctx,
                    compileExpression = { expr, reg, context -> expressionCompiler.compileExpression(expr, reg, context) },
                )

                // 4) Compile arguments into callRegs[2..]
                for ((i, arg) in methodCall.arguments.withIndex()) {
                    val argReg = callRegs[2 + i]
                    expressionCompiler.compileExpression(arg, argReg, ctx)
                }

                // 5) TAILCALL base, B (B = func+self+args)
                ctx.emit(OpCode.TAILCALL, base, argCount + 2, 0)
            }
        } else if (statement.expressions.size == 1 && statement.expressions[0] is FunctionCall && !hasToBeClosedVars) {
            // Tail call optimization: return f(...) becomes TAILCALL
            // BUT: Lua 5.4 does NOT use TAILCALL if the expression is wrapped in parentheses
            // because parentheses force exactly 1 return value
            // ALSO: cannot use TCO if there are to-be-closed variables (they must be closed by RETURN)
            val funcCall = statement.expressions[0] as FunctionCall

            val lastArg = funcCall.arguments.lastOrNull()
            val lastArgIsVararg = lastArg is VarargExpression
            val lastArgIsFunctionCall = lastArg is FunctionCall
            val lastIsMulti = lastArgIsVararg || lastArgIsFunctionCall

            val fixedArgs =
                if (lastIsMulti) {
                    funcCall.arguments.dropLast(1)
                } else {
                    funcCall.arguments
                }

            // [func] + [fixed args] + [1 extra slot if last is multi]
            val totalSlots = 1 + fixedArgs.size + if (lastIsMulti) 1 else 0

            ctx.registerAllocator.withContiguousRegisters(totalSlots) { callRegs ->
                val funcReg = callRegs[0]

                // 1) Function into funcReg
                expressionCompiler.compileExpression(funcCall.function, funcReg, ctx)

                // 2) Fixed args into callRegs[1..]
                for ((i, arg) in fixedArgs.withIndex()) {
                    val argReg = callRegs[1 + i]
                    expressionCompiler.compileExpression(arg, argReg, ctx)
                }

                // 3) Last multi-value argument, if any
                if (lastArgIsVararg) {
                    val varargReg = callRegs[1 + fixedArgs.size]
                    ctx.emit(OpCode.VARARG, varargReg, 0, 0)
                } else if (lastArgIsFunctionCall) {
                    val nestedCall = lastArg as FunctionCall
                    val nestedTarget = callRegs[1 + fixedArgs.size]
                    callCompiler.compileFunctionCallForMultiReturn(nestedCall, nestedTarget, ctx, expressionCompiler::compileExpression)
                }

                // 4) B for TAILCALL
                val argCount = if (lastIsMulti) 0 else funcCall.arguments.size + 1

                ctx.emit(OpCode.TAILCALL, funcReg, argCount, 0)
            }
        } else {
            val lastExpr = statement.expressions.last()

            if (lastExpr is FunctionCall && statement.expressions.size == 1) {
                // Non-TCO version of "return f()": call f with C=0, then RETURN with B=0
                ctx.registerAllocator.withTempRegister { funcReg ->
                    callCompiler.compileFunctionCallForMultiReturn(
                        lastExpr,
                        funcReg,
                        ctx,
                        expressionCompiler::compileExpression,
                    ) // we'll add this helper
                    ctx.emit(OpCode.RETURN, funcReg, 0, 0)
                }
            } else {
                ctx.registerAllocator.withContiguousRegisters(statement.expressions.size) { resultRegs ->
                    val base = resultRegs[0]

                    for ((i, expr) in statement.expressions.withIndex()) {
                        val targetReg = base + i

                        if (i == statement.expressions.lastIndex && expr is FunctionCall) {
                            // last expression is a call: want all results from it
                            callCompiler.compileFunctionCallForMultiReturn(expr, targetReg, ctx, expressionCompiler::compileExpression)
                        } else {
                            expressionCompiler.compileExpression(expr, targetReg, ctx)
                        }
                    }

                    val returnCount =
                        if (lastExpr is VarargExpression || lastExpr is FunctionCall) {
                            0 // RETURN base, 0 -> all values from base .. top-1
                        } else {
                            statement.expressions.size + 1
                        }

                    ctx.emit(OpCode.RETURN, base, returnCount, 0)
                }
            }
        }
    }

    fun compileLocalDeclaration(
        statement: LocalDeclaration,
        ctx: CompileContext,
    ) {
        val varCount = statement.variables.size
        val exprCount = statement.expressions.size

        // Check if the last expression can produce multiple returns
        val lastExpr = statement.expressions.lastOrNull()
        val willUseMultiReturn =
            lastExpr != null &&
                (lastExpr is FunctionCall || lastExpr is VarargExpression) &&
                varCount > exprCount

        val firstValueReg = ctx.registerAllocator.usedCount

        // CRITICAL FIX: When we have more variables than expressions AND the last expression
        // is NOT a multi-return expression (e.g., BinaryOp like `3 and f()`), we must
        // pre-allocate ALL variable registers to prevent expression compilation from
        // accidentally using registers that should be reserved for nil-filling.
        //
        // Example: `local a, b = 3 and f()` where f() returns (1,2,3)
        // - varCount=2, exprCount=1, lastExpr is BinaryOp (not FunctionCall)
        // - willUseMultiReturn=false
        // - We pre-allocate R[1] and R[2]
        // - AND operator can't steal R[2] as a temp
        // - R[2] gets properly NILed
        val shouldPreallocate = varCount > exprCount && !willUseMultiReturn
        if (shouldPreallocate) {
            // Pre-allocate all variable registers
            for (i in 0 until varCount) {
                ctx.registerAllocator.allocateTemp()
            }
        }

        // 1. Compile all expressions, pushing their results onto the stack.
        for (i in 0 until exprCount) {
            val expr = statement.expressions[i]
            val targetReg =
                if (shouldPreallocate) {
                    // Use pre-allocated register
                    firstValueReg + i
                } else {
                    // Allocate incrementally (normal case, including multi-return)
                    ctx.registerAllocator.allocateTemp()
                }

            // Special handling for the last expression if it's a multi-return call/vararg
            if (i == exprCount - 1 && willUseMultiReturn) {
                val numResults = varCount - (exprCount - 1)
                if (expr is FunctionCall) {
                    callCompiler.compileFunctionCall(expr, targetReg, ctx, expressionCompiler::compileExpression, numResults)
                } else { // VarargExpression
                    ctx.emit(OpCode.VARARG, targetReg, numResults + 1, 0)
                }
                // Adjust stack top to account for multiple results
                ctx.registerAllocator.adjustStackTop(ctx.registerAllocator.usedCount + numResults - 1)
            } else {
                expressionCompiler.compileExpression(expr, targetReg, ctx)
            }
        }

        // The registers holding expression results are now at the top of the stack.
        // When registers were pre-allocated, actualExprCount should still be exprCount
        val actualExprCount =
            if (shouldPreallocate) {
                exprCount
            } else {
                ctx.registerAllocator.usedCount - firstValueReg
            }

        // 2. Nil-fill any remaining locals that don't have an initializer.
        for (i in actualExprCount until varCount) {
            val nilReg =
                if (shouldPreallocate) {
                    // Use pre-allocated register
                    firstValueReg + i
                } else {
                    ctx.registerAllocator.allocateTemp()
                }
            ctx.emit(OpCode.LOADNIL, nilReg, nilReg, 0)
        }

        // 3. The registers from firstValueReg to stackTop are now our local variables.
        ctx.registerAllocator.promoteTempsToLocals(varCount)

        for (i in 0 until varCount) {
            val varInfo = statement.variables[i]
            val localReg = firstValueReg + i
            ctx.scopeManager.declareLocal(
                name = varInfo.name,
                register = localReg,
                startPc = ctx.instructions.size,
                isConst = varInfo.isConst,
                isClose = varInfo.isClose,
            )

            // Emit CLOSE instruction with mode 1 (declaration) for <close> variables
            if (varInfo.isClose) {
                ctx.emit(OpCode.CLOSE, localReg, 1, 0) // mode 1 = declaration
            }
        }
    }

    fun compileAssignment(
        statement: Assignment,
        ctx: CompileContext,
    ) {
        // Check for assignments to const/close locals and upvalues
        // Note: Both <const> and <close> variables cannot be reassigned
        statement.variables.forEach {
            if (it is Variable) {
                if (ctx.isConstOrCloseVariable(it.name)) {
                    // Create a token for the error message with line info from the assignment
                    val errorToken = Token(TokenType.IDENTIFIER, it.name, null, statement.line, 0)
                    throw ParserException("attempt to assign to const variable '${it.name}'", errorToken)
                }
            }
        }

        // Lua semantics: all RHS expressions are evaluated *before* any assignments happen.
        // Additionally, for complex LHS like t[i], the index 'i' must be evaluated before any assignments.
        val rhsCount = statement.expressions.size
        val lhsCount = statement.variables.size

        // Allocate contiguous block for RHS values
        val rhsRegs = ctx.registerAllocator.allocateContiguous(rhsCount.coerceAtLeast(lhsCount))

        // Data class to hold pre-evaluated LHS address information
        data class LhsTarget(
            val variable: Expression,
            val tableReg: Int? = null,
            val indexReg: Int? = null,
        )

        val lhsTargets = mutableListOf<LhsTarget>()
        val lhsAddressRegs = mutableListOf<Int>() // Registers to free later

        try {
            // PHASE 0: Pre-resolve LHS upvalues to ensure textual order (Lua 5.4 semantics)
            // This must happen BEFORE RHS compilation to match Lua's left-to-right upvalue order
            statement.variables.forEach { variable ->
                if (variable is Variable) {
                    // Check if it's an upvalue and resolve it now to register in textual order
                    val local = ctx.findLocal(variable.name)
                    if (local == null) {
                        // Not a local - might be upvalue or global
                        // Resolve upvalue now to ensure textual order
                        ctx.resolveUpvalue(variable.name)
                    }
                }
            }

            // PHASE 1: Compile all RHS expressions into temporary registers
            var hasMultiReturn = false
            for (i in 0 until rhsCount) {
                val expr = statement.expressions[i]
                val targetReg = rhsRegs[i]
                // Handle multi-return for the last expression
                if (i == rhsCount - 1 && (expr is FunctionCall || expr is VarargExpression) && lhsCount > rhsCount) {
                    val numResults = lhsCount - (rhsCount - 1)
                    hasMultiReturn = true
                    if (expr is FunctionCall) {
                        callCompiler.compileFunctionCall(expr, targetReg, ctx, expressionCompiler::compileExpression, numResults)
                    } else { // VarargExpression
                        ctx.emit(OpCode.VARARG, targetReg, numResults + 1, 0)
                    }
                } else {
                    expressionCompiler.compileExpression(expr, targetReg, ctx)
                }
            }

            // Nil-fill any remaining LHS variables (only if we don't have multi-return)
            if (!hasMultiReturn) {
                for (i in rhsCount until lhsCount) {
                    ctx.emit(OpCode.LOADNIL, rhsRegs[i], rhsRegs[i], 0)
                }
            }

            // PHASE 2: Evaluate all LHS addresses (for IndexAccess and FieldAccess)
            // This must happen AFTER RHS evaluation but BEFORE assignments
            for (i in 0 until lhsCount) {
                val variable = statement.variables[i]
                when (variable) {
                    is Variable -> {
                        lhsTargets.add(LhsTarget(variable))
                    }
                    is IndexAccess -> {
                        val tableReg = ctx.registerAllocator.allocateTemp()
                        val indexReg = ctx.registerAllocator.allocateTemp()
                        lhsAddressRegs.add(tableReg)
                        lhsAddressRegs.add(indexReg)
                        expressionCompiler.compileExpression(variable.table, tableReg, ctx)
                        expressionCompiler.compileExpression(variable.index, indexReg, ctx)
                        lhsTargets.add(LhsTarget(variable, tableReg, indexReg))
                    }
                    is FieldAccess -> {
                        val tableReg = ctx.registerAllocator.allocateTemp()
                        lhsAddressRegs.add(tableReg)
                        expressionCompiler.compileExpression(variable.table, tableReg, ctx)
                        lhsTargets.add(LhsTarget(variable, tableReg, null))
                    }
                    else -> error("Invalid assignment target: ${variable::class.simpleName}")
                }
            }

            // PHASE 3: Perform the assignments using pre-evaluated addresses
            for (i in 0 until lhsCount) {
                val target = lhsTargets[i]
                val valueReg = rhsRegs[i]

                // For multi-line assignments (db.lua:207), emit line event before each assignment
                // instruction to match lua548 behavior. The assignment instruction should be tagged
                // with the line of the rightmost (last evaluated) expression in the RHS.
                // For "a = b[1] + b[1]", the rightmost is the second b[1] on line 22.
                if (i == 0 && statement.expressions.isNotEmpty()) {
                    val lastRhsLine = getRightmostLine(statement.expressions.last())
                    if (lastRhsLine != ctx.currentLine) {
                        ctx.currentLine = lastRhsLine
                        ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
                    }
                }

                when (target.variable) {
                    is Variable -> {
                        val variable = target.variable as Variable
                        val local = ctx.findLocal(variable.name)
                        if (local != null) {
                            if (valueReg != local.register) {
                                ctx.emit(OpCode.MOVE, local.register, valueReg, 0)
                            }
                        } else {
                            val upvalueIndex = ctx.resolveUpvalue(variable.name)
                            if (upvalueIndex != null) {
                                ctx.emit(OpCode.SETUPVAL, valueReg, upvalueIndex, 0)
                            } else {
                                val nameConst = ctx.addConstant(LuaString(variable.name))
                                val envLocal = ctx.findLocal("_ENV")
                                if (envLocal != null) {
                                    // _ENV is a local variable
                                    if (ctx.canUseRKOperand(nameConst)) {
                                        ctx.emit(OpCode.SETTABLE, envLocal.register, ctx.getRKOperandForConstant(nameConst), valueReg)
                                    } else {
                                        ctx.registerAllocator.withTempRegister { nameReg ->
                                            ctx.emit(OpCode.LOADK, nameReg, nameConst, 0)
                                            ctx.emit(OpCode.SETTABLE, envLocal.register, nameReg, valueReg)
                                        }
                                    }
                                } else {
                                    // Check if _ENV is an upvalue
                                    val envUpvalue = ctx.resolveUpvalue("_ENV")
                                    if (envUpvalue != null) {
                                        // _ENV is an upvalue - use GETUPVAL + SETTABLE
                                        CompilerHelpers.emitEnvUpvalueAccess(valueReg, envUpvalue, nameConst, ctx, isRead = false)
                                    } else {
                                        // No _ENV local or upvalue - use SETGLOBAL
                                        ctx.emit(OpCode.SETGLOBAL, valueReg, nameConst, 0)
                                    }
                                }
                            }
                        }
                    }
                    is IndexAccess -> {
                        ctx.emit(OpCode.SETTABLE, target.tableReg!!, target.indexReg!!, valueReg)
                    }
                    is FieldAccess -> {
                        val variable = target.variable as FieldAccess
                        val fieldConst = ctx.addConstant(LuaString(variable.field))
                        if (ctx.canUseRKOperand(fieldConst)) {
                            ctx.emit(OpCode.SETTABLE, target.tableReg!!, ctx.getRKOperandForConstant(fieldConst), valueReg)
                        } else {
                            ctx.registerAllocator.withTempRegister { fieldReg ->
                                ctx.emit(OpCode.LOADK, fieldReg, fieldConst, 0)
                                ctx.emit(OpCode.SETTABLE, target.tableReg!!, fieldReg, valueReg)
                            }
                        }
                    }
                    else -> error("Invalid assignment target: ${target.variable::class.simpleName}")
                }
            }
        } finally {
            // Free LHS address registers in reverse order
            for (reg in lhsAddressRegs.reversed()) {
                ctx.registerAllocator.freeTemp(reg)
            }
            // Free RHS registers
            ctx.registerAllocator.freeContiguous(rhsRegs)
        }
    }

    fun compileExpressionStatement(
        statement: ExpressionStatement,
        ctx: CompileContext,
    ) {
        // Result is discarded, so compile into a temporary register that gets freed.
        ctx.registerAllocator.withTempRegister { reg ->
            expressionCompiler.compileExpression(statement.expression, reg, ctx)
        }
    }

    /**
     * Helper: Compile condition expression and emit TEST opcode.
     * Frees the condition register immediately to avoid LIFO violations in block bodies.
     */
    private fun compileConditionTest(
        condition: Expression,
        ctx: CompileContext,
    ) {
        val condReg = ctx.registerAllocator.allocateTemp()
        expressionCompiler.compileExpression(condition, condReg, ctx)
        ctx.emit(OpCode.TEST, condReg, 0, 0)
        ctx.registerAllocator.freeTemp(condReg)
    }

    /**
     * Helper to end a scope with proper label tracking.
     * Records scope end markers for validation at function end.
     * Must be called before endScope() to ensure we capture the correct scope level.
     */
    private fun endScopeWithValidation(
        ctx: CompileContext,
        snapshot: Int,
        isRepeatUntilBlock: Boolean = false,
    ): ScopeManager.ScopeExitInfo {
        ctx.emitCloseVariables(ctx.scopeManager.currentScopeLevel)
        val scopeLevel = ctx.scopeManager.currentScopeLevel
        val scopeStack = ctx.scopeManager.getCurrentScopeStack()
        val endPc = ctx.instructions.size

        // Store the end PC for all labels at this scope using precise scope identity
        // This is needed for proper validation of "jump over local" rules at function end
        ctx.setLabelsScopeEndPc(scopeStack, endPc, isRepeatUntilBlock)

        // NOTE: Labels are NOT removed here - they persist for forward goto resolution at function end.
        // Visibility is controlled by scopeStack matching in findLabelVisibleFrom().
        // All goto validation (both forward and backward) happens at function end in resolveForwardGotos().

        return ctx.scopeManager.endScope(snapshot, endPc)
    }

    private fun compileBlockWithScope(
        block: List<Statement>,
        ctx: CompileContext,
    ) {
        val snapshot = ctx.scopeManager.beginScope()
        for (stmt in block) {
            compileStatement(stmt, ctx)
        }
        val scopeExit = endScopeWithValidation(ctx, snapshot)

        // Close captured variables
        if (scopeExit.minCapturedRegister != null) {
            ctx.emit(OpCode.CLOSE, scopeExit.minCapturedRegister, 0, 0)
        }

        // Free registers used by locals in this scope
        for (local in scopeExit.removedLocals) {
            ctx.registerAllocator.freeLocal()
        }
    }

    fun compileIfStatement(
        statement: IfStatement,
        ctx: CompileContext,
    ) {
        // Emit LineEvent for condition expression line
        // The condition expression will update this when it compiles, but we need
        // an entry at the start of the if statement for the LINE hook
        if (statement.condition.line != ctx.currentLine) {
            ctx.currentLine = statement.condition.line
            ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
        }

        // Compile condition and emit TEST (register freed immediately)
        compileConditionTest(statement.condition, ctx)

        val jumpToElse = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0)

        // Emit CONTROL_FLOW event for 'then' keyword at the start of the then-block
        // This ensures the LINE hook fires when control flow passes through 'then'
        // The event is placed here (after the JMP) because TEST+JMP skips PC when condition is true
        ctx.currentLine = statement.thenLine
        ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.CONTROL_FLOW))

        // Compile then block with scope management
        compileBlockWithScope(statement.thenBlock, ctx)

        val jumpToEnd = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0)
        ctx.patchJump(jumpToElse, ctx.instructions.size)

        // Compile elseif blocks
        for (elseIfBlock in statement.elseIfBlocks) {
            compileConditionTest(elseIfBlock.condition, ctx)

            val jumpToNextElseIf = ctx.instructions.size
            ctx.emit(OpCode.JMP, 0, 0, 0)

            // Emit CONTROL_FLOW event for 'then' keyword in elseif at the start of the elseif block
            // This ensures the LINE hook fires when control flow passes through elseif 'then'
            if (elseIfBlock.thenLine > 0) {
                ctx.currentLine = elseIfBlock.thenLine
                ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.CONTROL_FLOW))
            }

            compileBlockWithScope(elseIfBlock.block, ctx)

            ctx.emit(OpCode.JMP, 0, jumpToEnd - ctx.instructions.size - 1, 0)
            ctx.patchJump(jumpToNextElseIf, ctx.instructions.size)
        }

        // Compile else block if present
        if (statement.elseBlock != null) {
            compileBlockWithScope(statement.elseBlock, ctx)
        }

        ctx.patchJump(jumpToEnd, ctx.instructions.size)

        // Emit MARKER event for 'end' keyword (LINE hook should fire when exiting if statement)
        // ALWAYS emit this event - 'end' marks the exit boundary of the if statement
        ctx.currentLine = statement.endLine
        ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.MARKER))
    }

    fun compileWhileStatement(
        statement: WhileStatement,
        ctx: CompileContext,
    ) {
        // Emit line event at loop start so it fires on each iteration
        // when the JMP instruction jumps back here
        emitLineEventIfNeeded(statement, ctx)

        val loopStart = ctx.instructions.size

        // Compile condition and emit TEST (register freed immediately)
        compileConditionTest(statement.condition, ctx)

        val jumpToEnd = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0)

        ctx.scopeManager.beginLoop()
        compileBlockWithScope(statement.block, ctx)
        ctx.emit(OpCode.JMP, 0, loopStart - ctx.instructions.size - 1, 0)
        val breaks = ctx.scopeManager.endLoop()

        for (breakIndex in breaks) {
            ctx.patchJump(breakIndex, ctx.instructions.size)
        }
        ctx.patchJump(jumpToEnd, ctx.instructions.size)

        // Emit line event for the line after the loop (typically 'end')
        emitLoopEndLineEvent(statement.line, statement.block, ctx)
    }

    fun compileRepeatStatement(
        statement: RepeatStatement,
        ctx: CompileContext,
    ) {
        emitLineEventIfNeeded(statement, ctx)

        val loopStart = ctx.instructions.size
        ctx.scopeManager.beginLoop()
        val bodySnapshot = ctx.scopeManager.beginScope()
        ctx.scopeManager.markCurrentScopeAsRepeatUntil() // Mark for goto validation
        for (stmt in statement.block) {
            compileStatement(stmt, ctx)
        }

        // Compile condition (can see body's locals in repeat-until)
        // Allocate register for condition result
        val condReg = ctx.registerAllocator.allocateLocal()
        expressionCompiler.compileExpression(statement.condition, condReg, ctx)

        // For repeat-until, the condition is semantically part of the block scope
        // So validation must happen after the condition is compiled
        val tempScopeExit = endScopeWithValidation(ctx, bodySnapshot, isRepeatUntilBlock = true)

        // Lua semantics: repeat until <condition> → exit when condition is TRUE
        // TEST with c=1: if value is falsy, skip next instruction
        // Pattern: TEST (condition), JMP (exit when FALSE), CLOSE+JMP (loop-back when TRUE)
        // But we want: exit when TRUE, loop when FALSE
        // So use TEST with c=1 (skip if falsy): if FALSE, skips JMP and hits loop-back
        ctx.emit(OpCode.TEST, condReg, 0, 1)

        // Exit jump (when condition is TRUE - TEST doesn't skip)
        val exitJumpIndex = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0) // Will be patched to jump to exit

        // Loop-back path (when condition is FALSE - TEST skips the exit JMP)
        // Close upvalues before looping back (ensures fresh upvalues for next iteration)
        if (tempScopeExit.minCapturedRegister != null) {
            ctx.emit(OpCode.CLOSE, tempScopeExit.minCapturedRegister, 0, 0)
        }
        val offset = loopStart - ctx.instructions.size - 1
        ctx.emit(OpCode.JMP, 0, offset, 0) // Loop back

        // Exit path (patch the exit jump to here)
        ctx.patchJump(exitJumpIndex, ctx.instructions.size)

        // Close upvalues before exiting loop
        if (tempScopeExit.minCapturedRegister != null) {
            ctx.emit(OpCode.CLOSE, tempScopeExit.minCapturedRegister, 0, 0)
        }

        // Free condition register
        ctx.registerAllocator.freeLocal()

        // Free registers used by locals in the loop body scope
        for (local in tempScopeExit.removedLocals) {
            ctx.registerAllocator.freeLocal()
        }

        // Patch break jumps
        val breaks = ctx.scopeManager.endLoop()
        for (breakIndex in breaks) {
            ctx.patchJump(breakIndex, ctx.instructions.size)
        }
    }

    fun compileForStatement(
        statement: ForStatement,
        ctx: CompileContext,
    ) {
        // Emit line event before FORPREP for error reporting (FORPREP can fail with type errors)
        emitLineEventIfNeeded(statement, ctx)

        // 1. Allocate base, limit, step, loopVar as contiguous block
        // These must persist as hidden locals across the loop body
        val base = ctx.registerAllocator.allocateTemp()
        val limit = ctx.registerAllocator.allocateTemp()
        val step = ctx.registerAllocator.allocateTemp()
        val loopVar = ctx.registerAllocator.allocateTemp()

        // Promote these to locals so they persist across inner scopes
        ctx.registerAllocator.promoteTempsToLocals(4)

        // start -> base
        val r1 = ctx.registerAllocator.allocateTemp()
        expressionCompiler.compileExpression(statement.start, r1, ctx)
        if (r1 != base) ctx.emit(OpCode.MOVE, base, r1, 0)
        ctx.registerAllocator.freeTemp(r1)

        // end -> limit
        val r2 = ctx.registerAllocator.allocateTemp()
        expressionCompiler.compileExpression(statement.end, r2, ctx)
        if (r2 != limit) ctx.emit(OpCode.MOVE, limit, r2, 0)
        ctx.registerAllocator.freeTemp(r2)

        // step -> step OR 1
        if (statement.step != null) {
            val r3 = ctx.registerAllocator.allocateTemp()
            expressionCompiler.compileExpression(statement.step, r3, ctx)
            if (r3 != step) ctx.emit(OpCode.MOVE, step, r3, 0)
            ctx.registerAllocator.freeTemp(r3)
        } else {
            // Use integer 1 by default (LOADI) to preserve integer type in for loops
            // This ensures: for i = 1, 10 do ... end uses integer loop variables
            ctx.emit(OpCode.LOADI, step, 1, 0)
        }

        // FORPREP
        val forPrepPc = ctx.instructions.size
        ctx.emit(OpCode.FORPREP, base, 0, 0)

        // Emit ITERATION event at loop start after FORPREP
        // This ensures the hook fires on EVERY iteration when FORLOOP jumps back here,
        // bypassing the VM's lastLine deduplication for same-line revisits
        val loopStart = ctx.instructions.size
        ctx.lineInfo.add(LineEvent(ctx.instructions.size, statement.line, LineEventKind.ITERATION))

        // Make loopVar the visible "i"
        val bodySnapshot = ctx.scopeManager.beginScope()
        ctx.scopeManager.declareLocal(
            name = statement.variable,
            register = loopVar,
            startPc = ctx.instructions.size,
        )
        ctx.scopeManager.beginLoop()
        statement.block.forEach { compileStatement(it, ctx) }
        val scopeExit = endScopeWithValidation(ctx, bodySnapshot)

        // Close captured variables (mode=0: close upvalues only, no __close)
        if (scopeExit.minCapturedRegister != null) {
            ctx.emit(OpCode.CLOSE, scopeExit.minCapturedRegister, 0, 0)
        }
        // FORLOOP
        val forLoopPc = ctx.instructions.size
        val jump = loopStart - forLoopPc - 1
        ctx.emit(OpCode.FORLOOP, base, jump, 0)
        ctx.patchJump(forPrepPc, forLoopPc)

        // Update ctx.currentLine for subsequent instructions
        // Note: We don't emit a line event here because:
        // 1. Loop iterations fire at loopStart (where FORLOOP jumps back to)
        // 2. Loop exit is handled by the end line event if multi-line
        ctx.currentLine = statement.line

        // breaks
        val breaks = ctx.scopeManager.endLoop()
        for (b in breaks) ctx.patchJump(b, ctx.instructions.size)

        // Emit line event for the 'end' line ONLY for multi-line loops
        // For single-line loops like "for i=1,4 do a=1 end", all content is on one line
        // and we must not emit an end line event
        if (statement.block.isNotEmpty()) {
            val lastLine = statement.block.last().line
            // Only consider emitting end line if the loop body spans multiple lines
            if (lastLine > statement.line) {
                val endLine = lastLine + 1
                // Emit end line event only if it's different from current line
                if (endLine != ctx.currentLine) {
                    ctx.currentLine = endLine
                    ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
                }
            }
        }

        // Free the 4 hidden for-loop control registers
        for (i in 0 until 4) {
            ctx.registerAllocator.freeLocal()
        }
    }

    fun compileForInStatement(
        statement: ForInStatement,
        ctx: CompileContext,
    ) {
        emitLineEventIfNeeded(statement, ctx)

        val exprs = statement.expressions
        val exprCount = exprs.size

        // The structure of a for-in loop requires careful register management.
        // Layout: [f, s, var] [loop vars...] [TBC]
        // Lua 5.4: 4th+ return values from iterator expressions are to-be-closed variables

        // Phase 1: Allocate all registers upfront
        val iteratorStateCount = 3
        val loopStateRegs = ctx.registerAllocator.allocateLocals(iteratorStateCount)
        val loopStateBase = loopStateRegs[0]
        val loopVarRegs = ctx.registerAllocator.allocateLocals(statement.variables.size)
        val toBeClosedReg = ctx.registerAllocator.allocateLocal()

        // Now we have: [loopStateBase, loopStateBase+1, loopStateBase+2] [loopVarRegs...] [toBeClosedReg]
        // We need to evaluate the iterator expression, requesting 4 results
        // The first 3 go into loopStateBase, loopStateBase+1, loopStateBase+2
        // The 4th goes into toBeClosedReg

        // Track the line of the last expression for TFORCALL error reporting
        var lastExprLine = statement.line

        // Evaluate iterator expressions
        // We request 3 + 1 results: f, s, var, TBC
        if (exprs.isNotEmpty() && exprs[0] is FunctionCall) {
            val call = exprs[0] as FunctionCall
            // Request enough results to fill iterator state (3) plus TBC (1)
            // But we need to account for how many expressions follow
            val totalSlotsNeeded = iteratorStateCount + 1 // 3 for iterator state + 1 for TBC
            val remainingExprs = exprCount - 1 // expressions after the function call
            val numResults = totalSlotsNeeded - remainingExprs
            lastExprLine = call.line
            callCompiler.compileFunctionCall(call, loopStateRegs[0], ctx, expressionCompiler::compileExpression, numResults)
            // Compile remaining expressions into iterator state slots (up to 3)
            for (i in 1 until exprCount.coerceAtMost(iteratorStateCount)) {
                lastExprLine = exprs[i].line
                expressionCompiler.compileExpression(exprs[i], loopStateRegs[i], ctx)
            }
            // If there's a 4th expression, it's the to-be-closed variable
            if (exprCount > iteratorStateCount) {
                lastExprLine = exprs[iteratorStateCount].line
                expressionCompiler.compileExpression(exprs[iteratorStateCount], toBeClosedReg, ctx)
            }
            // If function call was supposed to provide the TBC value but there are no explicit expressions,
            // it will be in loopVarRegs[0] and needs to be moved (handled below)
        } else {
            // No function call - evaluate each expression
            // Evaluate up to iteratorStateCount expressions for iterator state (f, s, var)
            val exprsToEvaluate = exprCount.coerceAtMost(iteratorStateCount)
            for (i in 0 until exprsToEvaluate) {
                lastExprLine = exprs[i].line
                expressionCompiler.compileExpression(exprs[i], loopStateRegs[i], ctx)
            }
            // Fill remaining iterator state slots with nil
            for (i in exprsToEvaluate until iteratorStateCount) {
                ctx.emit(OpCode.LOADNIL, loopStateRegs[i], loopStateRegs[i], 0)
            }
            // If there's a 4th expression, it's the to-be-closed variable
            if (exprCount > iteratorStateCount) {
                lastExprLine = exprs[iteratorStateCount].line
                expressionCompiler.compileExpression(exprs[iteratorStateCount], toBeClosedReg, ctx)
            } else {
                // No TBC variable, initialize to nil
                ctx.emit(OpCode.LOADNIL, toBeClosedReg, toBeClosedReg, 0)
            }
        }

        // If the iterator expression was a function call that returned 4+ values,
        // the 4th value ends up at loopStateBase+3, which is loopVarRegs[0].
        // We need to move it to toBeClosedReg (which comes after all loop vars).
        // But only do this if:
        // 1. The first expression was a function call
        // 2. We didn't explicitly compile a 4th expression above
        // 3. The function call was requested to return 4 values (numResults = 4 - remaining)
        val needsMoveTBC =
            exprs.isNotEmpty() &&
                exprs[0] is FunctionCall &&
                exprCount <= iteratorStateCount &&
                loopVarRegs.isNotEmpty()
        if (needsMoveTBC) {
            ctx.emit(OpCode.MOVE, toBeClosedReg, loopVarRegs[0], 0)
            // Initialize first loop var to nil since TFORCALL will overwrite it anyway
            ctx.emit(OpCode.LOADNIL, loopVarRegs[0], loopVarRegs[0], 0)
        }

        // All loop registers are now: [loopStateBase, loopStateBase+1, loopStateBase+2, loopVarRegs..., toBeClosedReg]
        // Note: loopStateBase+3 (tbcTempReg) is unused but will be freed with other iterator state
        val base = loopStateBase
        val state = loopStateBase + 1
        val ctrl = loopStateBase + 2

        // Phase 5: Execute loop body
        val bodySnapshot = ctx.scopeManager.beginScope()

        // Declare loop variables (k, v, etc.)
        for ((i, varName) in statement.variables.withIndex()) {
            ctx.scopeManager.declareLocal(
                name = varName,
                register = loopVarRegs[i],
                startPc = ctx.instructions.size,
            )
        }

        // Declare the to-be-closed variable (4th return value from iterator expression)
        // This is an invisible variable that will be closed when the loop exits
        ctx.scopeManager.declareLocal(
            name = "(for to-be-closed)",
            register = toBeClosedReg,
            startPc = ctx.instructions.size,
            isClose = true,
        )
        // Emit CLOSE instruction with mode 1 (declaration) to mark it as to-be-closed
        ctx.emit(OpCode.CLOSE, toBeClosedReg, 1, 0)

        ctx.scopeManager.beginLoop()

        // The structure is:
        // TFORCALL (prepares iteration with base register)
        // TFORLOOP (checks condition with ctrl register, jumps forward to exit if done)
        // ... loop body ...
        // JMP back to TFORCALL
        // (loop exit)

        // Set line for TFORCALL to the last iterator expression's line
        // This ensures errors during iterator call are reported on the correct line
        ctx.currentLine = lastExprLine
        ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))

        val tforcallPc = ctx.instructions.size
        ctx.emit(OpCode.TFORCALL, base, 0, statement.variables.size)

        // Emit line event here for the 'for ... in' statement line
        // This fires on each iteration when JMP jumps back to TFORCALL
        emitLineEventIfNeeded(statement, ctx)

        val tforloopPc = ctx.instructions.size
        // Emit TFORLOOP with placeholder jump distance (will be patched)
        val tforloopIndex = ctx.instructions.size
        ctx.emit(OpCode.TFORLOOP, ctrl, 0, statement.variables.size) // b=0 is placeholder

        // Loop body starts here
        val bodyStartPc = ctx.instructions.size
        statement.block.forEach { compileStatement(it, ctx) }
        ctx.emitCloseVariables(ctx.scopeManager.currentScopeLevel)

        // Jump back to the TFORCALL instruction to start the next iteration
        ctx.emit(OpCode.JMP, 0, tforcallPc - ctx.instructions.size - 1, 0)

        // Loop exit point - patch TFORLOOP jump distance
        val exitPc = ctx.instructions.size
        val jumpDistance = exitPc - tforloopPc - 1 // Distance from TFORLOOP to exit
        ctx.instructions[tforloopIndex] = Instruction(OpCode.TFORLOOP, ctrl, jumpDistance, statement.variables.size)

        // Loop exit point (TFORLOOP jumps here when done)
        // NOTE: We must emit CLOSE mode=2 AFTER patching breaks so they jump to it
        // Use helper to clean up loop scope (includes CLOSE for upvalues and break patching)
        CompilerHelpers.cleanupLoopScope(bodySnapshot, ctx, emitClose = true)

        // After breaks are patched, emit CLOSE mode=2 to close the to-be-closed variable
        // This ensures both normal exit and break statements close the TBC variable
        ctx.emit(OpCode.CLOSE, toBeClosedReg, 2, 0)

        // Emit line event for the 'end' line
        emitLoopEndLineEvent(statement.line, statement.block, ctx)

        // Phase 6: Free loop state registers (base, state, ctrl)
        // Loop variables and toBeClosedReg were already freed when we freed bodyExit.removedLocals
        for (i in 0 until 3) {
            ctx.registerAllocator.freeLocal()
        }
    }

    fun compileFunctionDeclaration(
        statement: FunctionDeclaration,
        ctx: CompileContext,
    ) {
        val proto =
            ctx.compileFunction(
                statement.parameters,
                statement.hasVararg,
                statement.body,
                statement.name,
                statement.line,
                statement.endLine,
            )
        val protoConst = ctx.addConstant(LuaCompiledFunction(proto))
        ctx.registerAllocator.withTempRegister { reg ->
            ctx.emit(OpCode.CLOSURE, reg, protoConst, 0)
            if (statement.tablePath.isEmpty()) {
                // Check if there's a local variable with the function's name first
                val localVar = ctx.findLocal(statement.name)
                if (localVar != null) {
                    // Check if the local variable is const/close before assigning
                    if (ctx.isConstOrCloseVariable(statement.name)) {
                        val errorToken = Token(TokenType.IDENTIFIER, statement.name, null, statement.line, 0)
                        throw ParserException("attempt to assign to const variable '${statement.name}'", errorToken)
                    }
                    // Assign to existing local variable
                    ctx.emit(OpCode.MOVE, localVar.register, reg, 0)
                } else if (ctx.scopeManager.currentScopeLevel > 0) {
                    // Lua 5.4 semantics: function name() inside a block creates a local variable
                    // This is equivalent to: local function name()
                    val funcReg = ctx.registerAllocator.allocateLocal()
                    ctx.scopeManager.declareLocal(
                        name = statement.name,
                        register = funcReg,
                        startPc = ctx.instructions.size,
                    )
                    ctx.emit(OpCode.MOVE, funcReg, reg, 0)
                } else {
                    val envLocal = ctx.findLocal("_ENV")
                    if (envLocal != null) {
                        val nameConst = ctx.addConstant(LuaString(statement.name))
                        if (ctx.canUseRKOperand(nameConst)) {
                            ctx.emit(OpCode.SETTABLE, envLocal.register, ctx.getRKOperandForConstant(nameConst), reg)
                        } else {
                            ctx.registerAllocator.withTempRegister { nameReg ->
                                ctx.emit(OpCode.LOADK, nameReg, nameConst, 0)
                                ctx.emit(OpCode.SETTABLE, envLocal.register, nameReg, reg)
                            }
                        }
                    } else {
                        val nameConst = ctx.addConstant(LuaString(statement.name))
                        ctx.emit(OpCode.SETGLOBAL, reg, nameConst, 0)
                    }
                }
            } else {
                val firstTableName = statement.tablePath[0]
                var tableReg: Int
                val localVar = ctx.findLocal(firstTableName)
                if (localVar != null) {
                    tableReg = localVar.register
                } else {
                    tableReg =
                        ctx.registerAllocator.withTempRegister { tReg ->
                            val nameConst = ctx.addConstant(LuaString(firstTableName))
                            ctx.emit(OpCode.GETGLOBAL, tReg, nameConst, 0)
                            tReg
                        }
                }

                for (i in 1 until statement.tablePath.size) {
                    val fieldName = statement.tablePath[i]
                    val keyConst = ctx.addConstant(LuaString(fieldName))
                    tableReg =
                        ctx.registerAllocator.withTempRegister { nReg ->
                            if (ctx.canUseRKOperand(keyConst)) {
                                ctx.emit(OpCode.GETTABLE, nReg, tableReg, ctx.getRKOperandForConstant(keyConst))
                            } else {
                                ctx.registerAllocator.withTempRegister { keyReg ->
                                    ctx.emit(OpCode.LOADK, keyReg, keyConst, 0)
                                    ctx.emit(OpCode.GETTABLE, nReg, tableReg, keyReg)
                                }
                            }
                            nReg
                        }
                }
                val nameConst = ctx.addConstant(LuaString(statement.name))
                if (ctx.canUseRKOperand(nameConst)) {
                    ctx.emit(OpCode.SETTABLE, tableReg, ctx.getRKOperandForConstant(nameConst), reg)
                } else {
                    ctx.registerAllocator.withTempRegister { nameReg ->
                        ctx.emit(OpCode.LOADK, nameReg, nameConst, 0)
                        ctx.emit(OpCode.SETTABLE, tableReg, nameReg, reg)
                    }
                }
            }
        }
    }

    fun compileLocalFunctionDeclaration(
        statement: LocalFunctionDeclaration,
        ctx: CompileContext,
    ) {
        val reg = ctx.registerAllocator.allocateLocal()
        ctx.scopeManager.declareLocal(
            name = statement.name,
            register = reg,
            startPc = ctx.instructions.size,
        )
        val proto =
            ctx.compileFunction(
                statement.parameters,
                statement.hasVararg,
                statement.body,
                statement.name,
                statement.line,
                statement.endLine,
            )
        val protoConst = ctx.addConstant(LuaCompiledFunction(proto))
        ctx.emit(OpCode.CLOSURE, reg, protoConst, 0)
    }

    fun compileDoStatement(
        statement: DoStatement,
        ctx: CompileContext,
    ) {
        val bodySnapshot = ctx.scopeManager.beginScope()
        for (stmt in statement.block) {
            compileStatement(stmt, ctx)
        }

        // Use endScopeWithValidation to ensure forward gotos are resolved
        // before labels are removed (fixes "no visible label" errors)
        val bodyExit = endScopeWithValidation(ctx, bodySnapshot, isRepeatUntilBlock = false)

        // Close captured variables
        if (bodyExit.minCapturedRegister != null) {
            ctx.emit(OpCode.CLOSE, bodyExit.minCapturedRegister, 0, 0)
        }
        // Free registers used by locals in this scope
        for (local in bodyExit.removedLocals) {
            ctx.registerAllocator.freeLocal()
        }
    }

    fun compileBreakStatement(ctx: CompileContext) {
        // Close captured upvalues AND <close> variables in the loop scope before breaking
        val loopScopeLevel = ctx.scopeManager.getCurrentLoopScopeLevel()
        if (loopScopeLevel != null) {
            // Find minimum captured or <close> register in the loop scope and inner scopes
            val minCaptured =
                ctx.scopeManager.locals
                    .filter { it.scopeLevel >= loopScopeLevel && (it.isCaptured || it.isClose) && it.isActive }
                    .minOfOrNull { it.register }
            if (minCaptured != null) {
                ctx.emit(OpCode.CLOSE, minCaptured, 0, 0)
            }
        }

        val breakJumpIndex = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0)
        ctx.scopeManager.addBreakJump(breakJumpIndex)
    }

    fun compileGotoStatement(
        statement: GotoStatement,
        ctx: CompileContext,
    ) {
        // Check if label is visible from current scope (respects lexical scoping rules)
        val labelInfo = ctx.findLabelVisibleFrom(statement.label, ctx.scopeManager.getCurrentScopeStack())

        // Close all <close> locals that we're jumping over
        // For backward goto: close from label's scope + 1 (all vars declared after label)
        // For forward goto: close from current scope (all vars in current scope and deeper)
        val closeScopeLevel =
            if (labelInfo != null) {
                // Backward goto: close variables from labelScope + 1 (all variables declared after the label)
                labelInfo.scopeLevel + 1
            } else {
                // Forward goto: close variables from current scope
                ctx.scopeManager.currentScopeLevel
            }
        ctx.emitCloseVariables(closeScopeLevel)

        // For backward goto: close ALL captured variables declared AFTER the label
        // This ensures each iteration creates new upvalue instances
        // Example: ::l1:: local b; ... goto l1  -- must CLOSE b before jumping back
        // Note: <close> variables are already handled by emitCloseVariables above
        if (labelInfo != null) {
            // Backward goto detected: label already exists
            // Get all active locals and filter those declared after the label
            val allActiveLocals = ctx.scopeManager.locals
            // Locals declared after the label are those with index >= labelInfo.localCount
            val localsAfterLabel = allActiveLocals.drop(labelInfo.localCount)
            val minCapturedAfterLabel =
                localsAfterLabel
                    .filter { it.isCaptured && !it.isClose }
                    .minOfOrNull { it.register }
            if (minCapturedAfterLabel != null) {
                ctx.emit(OpCode.CLOSE, minCapturedAfterLabel, 0, 0)
            }
        } else {
            // Forward goto: label not yet seen
            // Close ALL captured variables AND <close> variables in the current scope (and deeper)
            // This is necessary because goto can jump forward out of scope
            val minCaptured =
                ctx.scopeManager.locals
                    .filter { it.scopeLevel >= ctx.scopeManager.currentScopeLevel && (it.isCaptured || it.isClose) && it.isActive }
                    .minOfOrNull { it.register }
            if (minCaptured != null) {
                ctx.emit(OpCode.CLOSE, minCaptured, 0, 0)
            }
        }

        val gotoJumpIndex = ctx.instructions.size
        ctx.emit(OpCode.JMP, 0, 0, 0)

        if (labelInfo != null) {
            val gotoInfo =
                CompileContext.GotoInfo(
                    instructionIndex = gotoJumpIndex,
                    labelName = statement.label,
                    scopeLevel = ctx.scopeManager.currentScopeLevel,
                    scopeStack = ctx.scopeManager.getCurrentScopeStack(),
                    localCount = ctx.scopeManager.activeLocalCount(),
                    line = statement.line,
                )
            ctx.validateGotoScope(
                gotoScopeLevel = ctx.scopeManager.currentScopeLevel,
                gotoLocalCount = ctx.scopeManager.activeLocalCount(),
                labelScopeLevel = labelInfo.scopeLevel,
                labelLocalCount = labelInfo.localCount,
                labelName = statement.label,
                isForward = false,
                gotoLine = statement.line,
                gotoInstructionIndex = gotoJumpIndex,
                labelInstructionIndex = labelInfo.instructionIndex,
            )
            // patchJump knows how to turn target index into an offset
            ctx.patchJump(gotoJumpIndex, labelInfo.instructionIndex)
            // Record for later same-scope validation at endScope()
            ctx.resolvedGotos.add(CompileContext.ResolvedGotoLabel(gotoInfo, labelInfo))
        } else {
            ctx.pendingGotos.add(
                CompileContext.GotoInfo(
                    instructionIndex = gotoJumpIndex,
                    labelName = statement.label,
                    scopeLevel = ctx.scopeManager.currentScopeLevel,
                    scopeStack = ctx.scopeManager.getCurrentScopeStack(),
                    localCount = ctx.scopeManager.activeLocalCount(),
                    line = statement.line,
                ),
            )
        }
    }

    // Helper: emits CLOSE with mode 2 for all <close> locals at or above [scopeLevel]
    private fun emitCloseVariablesMode2(
        scopeLevel: Int,
        ctx: CompileContext,
    ) {
        for (local in ctx.scopeManager.locals) {
            if (local.isClose && local.scopeLevel >= scopeLevel && local.isActive) {
                ctx.emit(OpCode.CLOSE, local.register, 2, 0) // mode 2 = scope exit (for goto)
            }
        }
    }

    fun compileLabelStatement(
        statement: LabelStatement,
        ctx: CompileContext,
    ) {
        val labelIndex = ctx.instructions.size

        // This will throw if a label with the same name exists at the SAME scope level
        ctx.registerLabel(statement.name, labelIndex, statement.line)

        // DO NOT resolve forward gotos here!
        // Pending gotos will be resolved at function end when all labels are known
        // and we can find the correct visible label for each goto.
        // This prevents incorrectly binding gotos to labels in sibling scopes (e.g., elseif branches)
    }
}
