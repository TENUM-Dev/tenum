package ai.tenum.lua.compiler.stages

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.helper.CompilerHelpers
import ai.tenum.lua.compiler.model.LineEvent
import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ast.BinaryOp
import ai.tenum.lua.parser.ast.BooleanLiteral
import ai.tenum.lua.parser.ast.Expression
import ai.tenum.lua.parser.ast.FieldAccess
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionExpression
import ai.tenum.lua.parser.ast.IndexAccess
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.NilLiteral
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.ParenExpression
import ai.tenum.lua.parser.ast.StringLiteral
import ai.tenum.lua.parser.ast.TableConstructor
import ai.tenum.lua.parser.ast.TableField
import ai.tenum.lua.parser.ast.UnaryOp
import ai.tenum.lua.parser.ast.VarargExpression
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.OpCode

class ExpressionCompiler(
    private val callCompiler: CallCompiler,
) {
    fun compileExpression(
        expression: Expression,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        ctx.debug("Compiling expression: ${expression::class.simpleName}")

        // Update currentLine for error reporting
        // Note: Binary and unary operators will update this again to their operator's line
        // before emitting operation instructions
        if (expression.line != ctx.currentLine) {
            // For IndexAccess and FieldAccess in multi-line expressions (e.g., db.lua:207),
            // emit LineEvents BEFORE compiling the expression to match lua54 semantics.
            // lua54 generates separate GETI instructions for each operand reference,
            // each tagged with its source line. Line hooks fire when control visits these lines.
            //
            // Example: "a = b[1] + b[1]" where operands are on lines 12 and 22
            // lua54 bytecode:
            //   [12] GETI 1 0 1   ; first b[1] - fires line hook for 12
            //   [22] GETI 2 0 1   ; second b[1] - fires line hook for 22
            //   [12] ADD  1 1 2   ; addition
            //
            // We emit LineEvent at current instruction PC (before the GETTABLE/GETI)
            // to ensure line hooks fire at the correct source lines.
            //
            // SPECIAL CASE: If currentLine is already set to a different line (e.g., operator line
            // in a multi-line binary expression), and this IndexAccess has a line that's earlier,
            // we should keep the currentLine and NOT emit an event. This handles the case where
            // compileBinaryOp has already set the line to the operator's line.
            val lineDiff = expression.line - ctx.currentLine
            val needsLineEvent =
                when (expression) {
                    // Emit for IndexAccess/FieldAccess when moving forward to a different line
                    // This is needed for db.lua:207 with small line differences (i=1, j=1)
                    is IndexAccess, is FieldAccess -> (lineDiff >= 1)
                    else -> false
                }

            // Only update currentLine if we're actually emitting an event, or if moving forward normally
            if (needsLineEvent || lineDiff > 0) {
                ctx.currentLine = expression.line
            }

            if (needsLineEvent) {
                ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
            }
        }

        when (expression) {
            is NilLiteral -> {
                ctx.emit(OpCode.LOADNIL, targetReg, targetReg, 0)
            }
            is BooleanLiteral -> {
                ctx.emit(
                    OpCode.LOADBOOL,
                    targetReg,
                    if (expression.value) 1 else 0,
                    0,
                )
            }
            is NumberLiteral -> {
                // For small integers, use LOADI to avoid constant pool exhaustion
                // LOADI encodes the value in the instruction's b field (int)
                // Safe range: values that fit in Int without overflow
                // IMPORTANT: Only use LOADI for values that were originally integers (raw is Long),
                // never for values written with decimal point (raw is Double), even if value is 0.0 or 1.0
                val intValue =
                    when (expression.raw) {
                        is Long -> {
                            val lv = expression.raw
                            if (lv >= Int.MIN_VALUE && lv <= Int.MAX_VALUE) lv.toInt() else null
                        }
                        is Double -> {
                            // Never use LOADI for Double - must preserve float type
                            null
                        }
                        else -> {
                            // expression.value is either Long or Double
                            when (val v = expression.value) {
                                is Long -> if (v >= Int.MIN_VALUE && v <= Int.MAX_VALUE) v.toInt() else null
                                is Double -> {
                                    if (v == v.toLong().toDouble()) {
                                        val lv = v.toLong()
                                        if (lv >= Int.MIN_VALUE && lv <= Int.MAX_VALUE) lv.toInt() else null
                                    } else {
                                        null
                                    }
                                }
                                else -> null
                            }
                        }
                    }

                if (intValue != null) {
                    // Use LOADI for integer values in safe range
                    ctx.emit(OpCode.LOADI, targetReg, intValue, 0)
                } else {
                    // Use LOADK for large integers and all floating point values
                    val constIndex =
                        when (expression.raw) {
                            is Long -> ctx.addConstant(LuaLong(expression.raw))
                            is Double -> ctx.addConstant(LuaDouble(expression.raw))
                            else -> {
                                // expression.value is either Long or Double
                                when (val v = expression.value) {
                                    is Long -> ctx.addConstant(LuaLong(v))
                                    is Double -> {
                                        if (v == v.toLong().toDouble()) {
                                            ctx.addConstant(LuaLong(v.toLong()))
                                        } else {
                                            ctx.addConstant(LuaDouble(v))
                                        }
                                    }
                                    else -> ctx.addConstant(LuaDouble(0.0))
                                }
                            }
                        }
                    ctx.emit(OpCode.LOADK, targetReg, constIndex, 0)
                }
            }
            is StringLiteral -> {
                val constIndex = ctx.addConstant(LuaString(expression.value))
                ctx.emit(OpCode.LOADK, targetReg, constIndex, 0)
            }
            is Variable -> compileVariable(expression, targetReg, ctx)
            is BinaryOp -> compileBinaryOp(expression, targetReg, ctx)
            is UnaryOp -> compileUnaryOp(expression, targetReg, ctx)
            is FunctionCall -> callCompiler.compileFunctionCall(expression, targetReg, ctx, ::compileExpression)
            is TableConstructor -> compileTableConstructor(expression, targetReg, ctx)
            is IndexAccess -> compileIndexAccess(expression, targetReg, ctx)
            is FieldAccess -> compileFieldAccess(expression, targetReg, ctx)
            is ParenExpression -> compileExpression(expression.expression, targetReg, ctx)
            is FunctionExpression -> compileFunctionExpression(expression, targetReg, ctx)
            is MethodCall -> callCompiler.compileMethodCall(expression, targetReg, ctx, ::compileExpression)
            is VarargExpression -> {
                ctx.emit(OpCode.VARARG, targetReg, 0, 0)
            }
        }
    }

    private fun compileVariable(
        expression: Variable,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        val name = expression.name

        // 1) Local?
        val local = ctx.findLocal(name)
        if (local != null) {
            if (local.register != targetReg) {
                ctx.emit(OpCode.MOVE, targetReg, local.register, 0)
            }
            return
        }

        // 2) Plain upvalue?
        //
        // This includes capturing _ENV explicitly if user wrote `local _ENV = ...`
        // or if it's a normal upvalue coming from a parent function.
        val upvalueIndex = ctx.resolveUpvalue(name)
        if (upvalueIndex != null) {
            ctx.emit(OpCode.GETUPVAL, targetReg, upvalueIndex, 0)
            return
        }

        // 3) At this point, it's not a local and not a plain upvalue.
        //    For normal names, we treat it as a global resolved via _ENV.
        //    (For Lua 5.2+ semantics: globals always go through _ENV.)
        //
        //    We try _ENV as a local first, then as an upvalue.

        val nameConst = ctx.addConstant(LuaString(name))

        // Handle RK overflow: if constant index > 255, must load to register first
        val canUseRK = ctx.canUseRKOperand(nameConst)
        val nameOperand = if (canUseRK) ctx.getRKOperandForConstant(nameConst) else -1 // -1 = unused

        // 3a) _ENV as a local (e.g., `local _ENV = someTable`)
        val envLocal = ctx.findLocal("_ENV")
        if (envLocal != null) {
            if (canUseRK) {
                ctx.emit(OpCode.GETTABLE, targetReg, envLocal.register, nameOperand)
            } else {
                // Load large constant to temp register
                ctx.registerAllocator.withTempRegister { nameReg ->
                    ctx.emit(OpCode.LOADK, nameReg, nameConst, 0)
                    ctx.emit(OpCode.GETTABLE, targetReg, envLocal.register, nameReg)
                }
            }
            return
        }

        // 3b) _ENV as an upvalue
        val envUpvalue = ctx.resolveUpvalue("_ENV")
        if (envUpvalue != null) {
            CompilerHelpers.emitEnvUpvalueAccess(targetReg, envUpvalue, nameConst, ctx, isRead = true)
            return
        }

        // 4) Fallback: true global via GETGLOBAL
        //
        // This will only happen if there is no _ENV local OR upvalue.
        // If you want pure Lua 5.2+ semantics, you can delete this branch
        // and make _ENV mandatory instead.
        ctx.emit(OpCode.GETGLOBAL, targetReg, nameConst, 0)
    }

    private fun compileBinaryOp(
        expr: BinaryOp,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        when (expr.operator.type) {
            TokenType.AND -> {
                // Optimize register usage: compile left into targetReg, right into a temp
                compileExpression(expr.left, targetReg, ctx)

                // Use helper to emit short-circuit scaffolding
                CompilerHelpers.emitShortCircuit(
                    targetReg = targetReg,
                    isOr = false,
                    operatorLine = expr.line,
                    ctx = ctx,
                ) { rightReg ->
                    compileShortCircuitRight(expr.right, rightReg, ctx)
                }
            }

            TokenType.OR -> {
                // Optimize register usage: compile left into targetReg, right into a temp
                compileExpression(expr.left, targetReg, ctx)

                // Use helper to emit short-circuit scaffolding
                CompilerHelpers.emitShortCircuit(
                    targetReg = targetReg,
                    isOr = true,
                    operatorLine = expr.line,
                    ctx = ctx,
                ) { rightReg ->
                    compileShortCircuitRight(expr.right, rightReg, ctx)
                }
            }

            else -> {
                // For deep expression trees, we need to be more careful with register allocation.
                // Instead of allocating 2 temps and then copying to targetReg, we:
                // 1. Compile left directly into targetReg (saves one temp)
                // 2. Compile right into a single temp
                // 3. Perform operation from targetReg and temp into targetReg
                // 4. Free the temp immediately
                // This prevents register overflow in deeply nested expressions like a1+a2+...+a200

                // For multi-line expressions (db.lua:207), lua548 tags the first operand's instruction
                // with the operator's line, not the operand's line.
                // The sequence for "a = b[1] + b[1]" with + on line 3, first b[1] on line 2, second b[1] on line 4
                // is: line 3 (operator line for first GETI), line 4 (second GETI), line 3 (ADD).
                // This happens even for small line differences (i=1, j=1 in db.lua:207).
                val leftOnDifferentLine = expr.left.line != expr.line
                val rightOnDifferentLine = expr.right.line != expr.line
                val isMultiLine = leftOnDifferentLine || rightOnDifferentLine

                if (isMultiLine) {
                    // Set currentLine to operator line and emit LineEvent BEFORE compiling left operand
                    // This ensures the line hook fires at the operator's line when the first operand is evaluated
                    ctx.currentLine = expr.line
                    ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
                }

                compileExpression(expr.left, targetReg, ctx)

                ctx.registerAllocator.withTempRegister { rightReg ->
                    compileExpression(expr.right, rightReg, ctx)

                    // Always update line to operator's line and add LineEvent for error reporting
                    // Even if ctx.currentLine is already at expr.line (from operand compilation),
                    // we need a LineEvent at the operation instruction's PC for accurate error reporting
                    if (expr.line != ctx.currentLine) {
                        ctx.currentLine = expr.line
                    }
                    ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))

                    val (opcode, regB, regC) =
                        when (expr.operator.type) {
                            TokenType.PLUS -> Triple(OpCode.ADD, targetReg, rightReg)
                            TokenType.MINUS -> Triple(OpCode.SUB, targetReg, rightReg)
                            TokenType.MULTIPLY -> Triple(OpCode.MUL, targetReg, rightReg)
                            TokenType.DIVIDE -> Triple(OpCode.DIV, targetReg, rightReg)
                            TokenType.FLOOR_DIVIDE -> Triple(OpCode.IDIV, targetReg, rightReg)
                            TokenType.MODULO -> Triple(OpCode.MOD, targetReg, rightReg)
                            TokenType.POWER -> Triple(OpCode.POW, targetReg, rightReg)
                            TokenType.BITWISE_AND -> Triple(OpCode.BAND, targetReg, rightReg)
                            TokenType.BITWISE_OR -> Triple(OpCode.BOR, targetReg, rightReg)
                            TokenType.BITWISE_XOR -> Triple(OpCode.BXOR, targetReg, rightReg)
                            TokenType.SHIFT_LEFT -> Triple(OpCode.SHL, targetReg, rightReg)
                            TokenType.SHIFT_RIGHT -> Triple(OpCode.SHR, targetReg, rightReg)
                            TokenType.CONCAT -> Triple(OpCode.CONCAT, targetReg, rightReg)
                            TokenType.EQUAL -> Triple(OpCode.EQ, targetReg, rightReg)

                            TokenType.NOT_EQUAL -> {
                                ctx.emit(OpCode.EQ, targetReg, targetReg, rightReg)
                                ctx.emit(OpCode.NOT, targetReg, targetReg, 0)
                                return@withTempRegister
                            }

                            TokenType.LESS -> Triple(OpCode.LT, targetReg, rightReg)
                            TokenType.LESS_EQUAL -> Triple(OpCode.LE, targetReg, rightReg)
                            TokenType.GREATER -> Triple(OpCode.LT, rightReg, targetReg)
                            TokenType.GREATER_EQUAL -> Triple(OpCode.LE, rightReg, targetReg)

                            else -> error("Unknown binary operator: ${expr.operator.type}")
                        }

                    ctx.emit(opcode, targetReg, regB, regC)
                }
            }
        }
    }

    private fun compileUnaryOp(
        expr: UnaryOp,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        ctx.registerAllocator.withTempRegister { operandReg ->
            compileExpression(expr.operand, operandReg, ctx)

            // Update line number to operator's line for error reporting
            // This ensures runtime errors show the correct line where the operation occurs
            if (expr.line != ctx.currentLine) {
                ctx.currentLine = expr.line
                ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
            }

            val opcode =
                when (expr.operator.type) {
                    TokenType.MINUS -> OpCode.UNM
                    TokenType.NOT -> OpCode.NOT
                    TokenType.HASH -> OpCode.LEN
                    TokenType.BITWISE_XOR -> OpCode.BNOT
                    else -> error("Unknown unary operator: ${expr.operator.type}")
                }
            ctx.emit(opcode, targetReg, operandReg, 0)
        }
    }

    private fun compileTableConstructor(
        expr: TableConstructor,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        ctx.emit(OpCode.NEWTABLE, targetReg, 0, 0)

        // Collect consecutive list fields for SETLIST batching
        val listFieldBatch = mutableListOf<TableField.ListField>()
        var arrayIndex = 1

        fun flushListFieldBatch() {
            if (listFieldBatch.isEmpty()) return

            val startIndex = arrayIndex - listFieldBatch.size
            var offset = 0

            while (offset < listFieldBatch.size) {
                val batchSize = minOf(50, listFieldBatch.size - offset)
                val currentIndex = startIndex + offset
                val batchNumber = (currentIndex - 1) / 50 + 1

                // Compile values into consecutive registers starting at targetReg + 1
                // Save current stack top to restore after SETLIST
                val savedStackTop = ctx.registerAllocator.stackTop
                val baseReg = targetReg + 1

                // Ensure we have enough registers allocated
                while (ctx.registerAllocator.stackTop < baseReg + batchSize) {
                    ctx.registerAllocator.allocateTemp()
                }

                // Compile each value into its designated register
                for (i in 0 until batchSize) {
                    val field = listFieldBatch[offset + i]
                    val targetValueReg = baseReg + i

                    // Track line info for expressions that can error
                    if (field.value.line != ctx.currentLine) {
                        ctx.currentLine = field.value.line
                        ctx.lineInfo.add(LineEvent(ctx.instructions.size, ctx.currentLine, LineEventKind.EXECUTION))
                    }

                    // Check if this is a local variable
                    if (field.value is Variable) {
                        val local = ctx.findLocal(field.value.name)
                        if (local != null) {
                            // Move from local's register to target register
                            ctx.emit(OpCode.MOVE, targetValueReg, local.register, 0)
                            continue
                        }
                    }

                    // Regular expression compilation
                    compileExpression(field.value, targetValueReg, ctx)
                }

                // Emit SETLIST instruction
                ctx.emit(OpCode.SETLIST, targetReg, batchSize, batchNumber)

                // Restore stack top (free temporary registers)
                ctx.registerAllocator.stackTop = savedStackTop

                offset += batchSize
            }

            listFieldBatch.clear()
        }

        for (field in expr.fields) {
            when (field) {
                is TableField.ListField -> {
                    if (field.value is VarargExpression) {
                        // Flush pending batch before varargs
                        flushListFieldBatch()
                        // Load all varargs into registers starting at targetReg + 1
                        // VARARG with b=0 means load all varargs and set top marker
                        ctx.emit(OpCode.VARARG, targetReg + 1, 0, 0)
                        // SETLIST with b=0 uses top marker to know how many values to copy
                        ctx.emit(OpCode.SETLIST, targetReg, 0, arrayIndex)
                        return // Can't continue after varargs
                    } else if (field.value is FunctionCall && field == expr.fields.lastOrNull()) {
                        // CRITICAL: When a function call is the LAST list field,
                        // it should capture ALL return values (not just the first)
                        // This is Lua semantics: {f()} captures all returns, {f(), 1} captures only first
                        flushListFieldBatch()

                        // Use CallCompiler to compile the function call for multiple returns
                        val funcCall = field.value
                        val funcReg = targetReg + 1
                        val callCompiler = CallCompiler()

                        // Compile function call with numResults=0 to capture all returns
                        callCompiler.compileFunctionCallForMultiReturn(
                            funcCall,
                            funcReg,
                            ctx,
                            ::compileExpression,
                            numResults = 0, // 0 means capture all
                        )

                        // Use SETLIST with b=0 to copy all returned values into table
                        ctx.emit(OpCode.SETLIST, targetReg, 0, arrayIndex)
                        return // Can't continue after multi-value function call
                    } else {
                        // Add to batch for SETLIST processing
                        listFieldBatch.add(field)
                        arrayIndex++
                    }
                }

                is TableField.NamedField -> {
                    // Flush pending batch before named field
                    flushListFieldBatch()

                    // Check if value is a local variable to avoid freeing its register
                    val isLocalVar = field.value is Variable && ctx.findLocal(field.value.name) != null
                    val valueReg =
                        if (isLocalVar) {
                            ctx.findLocal(field.value.name)!!.register
                        } else {
                            ctx.registerAllocator.allocateTemp()
                        }

                    val keyConst = ctx.addConstant(LuaString(field.name))

                    // Track line info for expressions that can error
                    CompilerHelpers.updateLineForExpression(field.value.line, ctx)

                    compileExpression(field.value, valueReg, ctx)
                    if (ctx.canUseRKOperand(keyConst)) {
                        val keyOperand = ctx.getRKOperandForConstant(keyConst)
                        ctx.emit(
                            OpCode.SETTABLE,
                            targetReg,
                            keyOperand,
                            valueReg,
                        )
                    } else {
                        ctx.registerAllocator.withTempRegister { keyReg ->
                            ctx.emit(OpCode.LOADK, keyReg, keyConst, 0)
                            ctx.emit(OpCode.SETTABLE, targetReg, keyReg, valueReg)
                        }
                    }

                    if (!isLocalVar) {
                        ctx.registerAllocator.freeTemp(valueReg)
                    }
                }

                is TableField.RecordField -> {
                    // Flush pending batch before record field
                    flushListFieldBatch()

                    ctx.registerAllocator.withTempRegisters(2) { regs ->
                        val keyReg = regs[0]
                        val valueReg = regs[1]

                        // Track line info for key expression
                        CompilerHelpers.updateLineForExpression(field.key.line, ctx)
                        compileExpression(field.key, keyReg, ctx)

                        // Track line info for value expression
                        CompilerHelpers.updateLineForExpression(field.value.line, ctx)
                        compileExpression(field.value, valueReg, ctx)

                        ctx.emit(OpCode.SETTABLE, targetReg, keyReg, valueReg)
                    }
                }
            }
        }

        // Flush any remaining list fields
        flushListFieldBatch()
    }

    private fun compileIndexAccess(
        expr: IndexAccess,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        ctx.registerAllocator.withTempRegisters(2) { regs ->
            val tableReg = regs[0]
            val indexReg = regs[1]
            compileExpression(expr.table, tableReg, ctx)
            compileExpression(expr.index, indexReg, ctx)

            // Line event already emitted by compileExpression() before entering this function
            // for multi-line expressions. In multi-line binary expressions, currentLine may
            // already be set to the operator's line by compileBinaryOp, and we should keep it.
            // Only update if we're moving forward to a later line.
            if (expr.line > ctx.currentLine) {
                ctx.currentLine = expr.line
            }

            ctx.emit(OpCode.GETTABLE, targetReg, tableReg, indexReg)
        }
    }

    private fun compileFieldAccess(
        expr: FieldAccess,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        ctx.registerAllocator.withTempRegister { tableReg ->
            compileExpression(expr.table, tableReg, ctx)
            val fieldConst = ctx.addConstant(LuaString(expr.field))

            // Always add LineEvent before GETTABLE for debug.getinfo accuracy
            // Multiple expressions on same line each need their own LineEvent for inspection
            // But only update ctx.currentLine if line actually changed (for hook tracking)
            if (expr.line != ctx.currentLine) {
                ctx.currentLine = expr.line
            }
            ctx.lineInfo.add(LineEvent(ctx.instructions.size, expr.line, LineEventKind.EXECUTION))

            if (ctx.canUseRKOperand(fieldConst)) {
                ctx.emit(
                    OpCode.GETTABLE,
                    targetReg,
                    tableReg,
                    ctx.getRKOperandForConstant(fieldConst),
                )
            } else {
                ctx.registerAllocator.withTempRegister { fieldReg ->
                    ctx.emit(OpCode.LOADK, fieldReg, fieldConst, 0)
                    ctx.emit(OpCode.GETTABLE, targetReg, tableReg, fieldReg)
                }
            }
        }
    }

    private fun compileFunctionExpression(
        expr: FunctionExpression,
        targetReg: Int,
        ctx: CompileContext,
    ) {
        // DEBUG: print parent locals before compiling function
        ctx.debug("[compileFunctionExpression] parent scopeManager.locals: ${ctx.scopeManager.locals.map { it.name }}")
        val proto = ctx.compileFunction(expr.parameters, expr.hasVararg, expr.body, functionLine = expr.line, endLine = expr.endLine)
        // DEBUG: print upvalues captured by the function
        ctx.debug("[compileFunctionExpression] upvalues captured: ${proto.upvalueInfo.map { it.name }}")
        val protoConst = ctx.addConstant(LuaCompiledFunction(proto))

        // Emit line event for the 'end' line (where CLOSURE instruction is generated)
        // This is critical for debug hooks to fire on function definition completion
        // Matches Lua 5.4 semantics where line hook fires on 'end' line
        if (expr.endLine > 0 && expr.endLine != ctx.currentLine) {
            ctx.currentLine = expr.endLine
            ctx.lineInfo.add(LineEvent(ctx.instructions.size, expr.endLine, LineEventKind.EXECUTION))
        }

        ctx.emit(OpCode.CLOSURE, targetReg, protoConst, 0)
    }

    /**
     * Compiles the right-hand side of a short-circuit operator (AND/OR).
     * Handles FunctionCall expressions specially to ensure only 1 result.
     */
    private fun compileShortCircuitRight(
        rightExpr: Expression,
        rightReg: Int,
        ctx: CompileContext,
    ) {
        // For AND/OR operators, if right is a function call, we must ensure
        // it returns only 1 value, not multiple values (Lua semantics)
        if (rightExpr is FunctionCall) {
            callCompiler.compileFunctionCall(
                rightExpr,
                rightReg,
                ctx,
                ::compileExpression,
                numResults = 1,
                captureAllResults = false,
            )
        } else {
            compileExpression(rightExpr, rightReg, ctx)
        }
    }
}
