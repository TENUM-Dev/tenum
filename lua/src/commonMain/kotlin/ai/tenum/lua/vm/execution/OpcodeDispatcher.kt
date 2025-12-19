package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.OpCode
import ai.tenum.lua.vm.debug.HookEvent
import ai.tenum.lua.vm.execution.InferredFunctionName
import ai.tenum.lua.vm.execution.opcodes.ArithmeticOpcodes
import ai.tenum.lua.vm.execution.opcodes.BitwiseOpcodes
import ai.tenum.lua.vm.execution.opcodes.CallOpcodes
import ai.tenum.lua.vm.execution.opcodes.ComparisonOpcodes
import ai.tenum.lua.vm.execution.opcodes.ControlFlowOpcodes
import ai.tenum.lua.vm.execution.opcodes.CoroutineOpcodes
import ai.tenum.lua.vm.execution.opcodes.FrameOpcodes
import ai.tenum.lua.vm.execution.opcodes.LogicalOpcodes
import ai.tenum.lua.vm.execution.opcodes.LoopOpcodes
import ai.tenum.lua.vm.execution.opcodes.StackOpcodes
import ai.tenum.lua.vm.execution.opcodes.StringOpcodes
import ai.tenum.lua.vm.execution.opcodes.TableOpcodes
import ai.tenum.lua.vm.execution.opcodes.UpvalueOpcodes

/**
 * Context for opcode execution
 */
internal data class OpcodeContext(
    val instr: Instruction,
    val env: ExecutionEnvironment,
    val registers: MutableList<LuaValue<*>>,
    val execFrame: ExecutionFrame,
    val openUpvalues: MutableMap<Int, Upvalue>,
    val varargs: List<LuaValue<*>>,
    val pc: Int,
    val frame: CallFrame,
    val executeProto: (Proto, List<LuaValue<*>>, List<Upvalue>, ai.tenum.lua.runtime.LuaFunction?) -> List<LuaValue<*>>,
    val callFunction: (ai.tenum.lua.runtime.LuaFunction, List<LuaValue<*>>) -> List<LuaValue<*>>,
    val traceRegisterWrite: (Int, LuaValue<*>, String) -> Unit,
    val triggerHook: (HookEvent, Int) -> Unit,
    val setCallContext: (InferredFunctionName) -> Unit,
)

/**
 * Dispatches opcodes to their handlers, separating routing logic from execution context management.
 * Handles simple opcodes directly, delegates complex control flow to caller.
 */
internal class OpcodeDispatcher(
    private val debugTracer: ai.tenum.lua.vm.debug.DebugTracer,
) {
    private val handlers: Map<OpCode, (OpcodeContext) -> DispatchResult> = buildHandlerMap()

    /**
     * Dispatch a single opcode to its handler.
     * Returns control flow directive (continue, jump, return, etc.)
     */
    fun dispatch(
        instr: Instruction,
        env: ExecutionEnvironment,
        registers: MutableList<LuaValue<*>>,
        execFrame: ExecutionFrame,
        openUpvalues: MutableMap<Int, Upvalue>,
        varargs: List<LuaValue<*>>,
        pc: Int,
        frame: CallFrame,
        executeProto: (Proto, List<LuaValue<*>>, List<Upvalue>, ai.tenum.lua.runtime.LuaFunction?) -> List<LuaValue<*>>,
        callFunction: (ai.tenum.lua.runtime.LuaFunction, List<LuaValue<*>>) -> List<LuaValue<*>>,
        traceRegisterWrite: (Int, LuaValue<*>, String) -> Unit,
        triggerHook: (HookEvent, Int) -> Unit,
        setCallContext: (InferredFunctionName) -> Unit,
    ): DispatchResult {
        val context =
            OpcodeContext(
                instr,
                env,
                registers,
                execFrame,
                openUpvalues,
                varargs,
                pc,
                frame,
                executeProto,
                callFunction,
                traceRegisterWrite,
                triggerHook,
                setCallContext,
            )

        val handler = handlers[instr.opcode] ?: error("Unknown opcode: ${instr.opcode}")
        return handler(context)
    }

    private fun buildHandlerMap(): Map<OpCode, (OpcodeContext) -> DispatchResult> =
        mapOf(
            OpCode.MOVE to { ctx ->
                StackOpcodes.executeMove(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.LOADK to { ctx ->
                StackOpcodes.executeLoadK(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.LOADI to { ctx ->
                StackOpcodes.executeLoadI(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.LOADBOOL to { ctx ->
                val skipNext = StackOpcodes.executeLoadBool(ctx.instr, ctx.env)
                if (skipNext) DispatchResult.SkipNext else DispatchResult.Continue
            },
            OpCode.LOADNIL to { ctx ->
                StackOpcodes.executeLoadNil(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.GETGLOBAL to { ctx ->
                StackOpcodes.executeGetGlobal(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.SETGLOBAL to { ctx ->
                StackOpcodes.executeSetGlobal(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.GETTABLE to { ctx ->
                TableOpcodes.executeGetTable(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.SETTABLE to { ctx ->
                TableOpcodes.executeSetTable(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.NEWTABLE to { ctx ->
                TableOpcodes.executeNewTable(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.SELF to { ctx ->
                TableOpcodes.executeSelf(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.ADD to { ctx ->
                ArithmeticOpcodes.executeAdd(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.SUB to { ctx ->
                ArithmeticOpcodes.executeSub(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.MUL to { ctx ->
                ArithmeticOpcodes.executeMul(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.DIV to { ctx ->
                ArithmeticOpcodes.executeDiv(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.MOD to { ctx ->
                ArithmeticOpcodes.executeMod(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.POW to { ctx ->
                ArithmeticOpcodes.executePow(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.IDIV to { ctx ->
                ArithmeticOpcodes.executeIdiv(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.BAND to { ctx ->
                BitwiseOpcodes.executeBand(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.BOR to { ctx ->
                BitwiseOpcodes.executeBor(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.BXOR to { ctx ->
                BitwiseOpcodes.executeBxor(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.SHL to { ctx ->
                BitwiseOpcodes.executeShl(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.SHR to { ctx ->
                BitwiseOpcodes.executeShr(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.UNM to { ctx ->
                ArithmeticOpcodes.executeUnm(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.BNOT to { ctx ->
                BitwiseOpcodes.executeBnot(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Continue
            },
            OpCode.NOT to { ctx ->
                LogicalOpcodes.executeNot(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.LEN to { ctx ->
                StringOpcodes.executeLen(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.CONCAT to { ctx ->
                StringOpcodes.executeConcat(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.AND to { ctx ->
                LogicalOpcodes.executeAnd(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.OR to { ctx ->
                LogicalOpcodes.executeOr(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.JMP to { ctx ->
                val newPc = ControlFlowOpcodes.executeJmp(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Jump(newPc)
            },
            OpCode.EQ to { ctx ->
                ComparisonOpcodes.executeEq(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.LT to { ctx ->
                ComparisonOpcodes.executeLt(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.LE to { ctx ->
                ComparisonOpcodes.executeLe(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.TEST to { ctx ->
                val newPc = ControlFlowOpcodes.executeTest(ctx.instr, ctx.env, ctx.pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            },
            OpCode.TESTSET to { ctx ->
                val newPc = ControlFlowOpcodes.executeTestSet(ctx.instr, ctx.env, ctx.pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            },
            OpCode.CALL to { ctx ->
                when (
                    val result =
                        CallOpcodes.executeCall(
                            ctx.instr,
                            ctx.registers,
                            ctx.execFrame,
                            ctx.env,
                            ctx.pc,
                            ctx.setCallContext,
                        )
                ) {
                    CallOpcodes.CallResult.Completed -> DispatchResult.Continue
                    is CallOpcodes.CallResult.Trampoline -> {
                        DispatchResult.CallTrampoline(
                            newProto = result.newProto,
                            newArgs = result.newArgs,
                            newUpvalues = result.newUpvalues,
                            savedFunc = result.resolvedFunc,
                            callInstruction = ctx.instr,
                        )
                    }
                    is CallOpcodes.CallResult.Return -> {
                        error("CALL should not return Return result")
                    }
                }
            },
            OpCode.TAILCALL to { ctx ->
                when (
                    val result =
                        CallOpcodes.executeTailCall(
                            ctx.instr,
                            ctx.registers,
                            ctx.execFrame,
                            ctx.env,
                            ctx.pc,
                        )
                ) {
                    is CallOpcodes.CallResult.Return -> {
                        DispatchResult.Return(result.values)
                    }
                    is CallOpcodes.CallResult.Trampoline -> {
                        DispatchResult.TailCallTrampoline(
                            newProto = result.newProto,
                            newArgs = result.newArgs,
                            newUpvalues = result.newUpvalues,
                            savedFunc = result.resolvedFunc,
                        )
                    }
                    CallOpcodes.CallResult.Completed -> {
                        error("TAILCALL should not return Completed")
                    }
                }
            },
            OpCode.RETURN to { ctx ->
                val results =
                    CallOpcodes.executeReturn(ctx.instr, ctx.registers, ctx.execFrame, ctx.env, ctx.frame.getCurrentLine()) { event, line ->
                        ctx.triggerHook(event, line)
                    }
                DispatchResult.Return(results)
            },
            OpCode.FORPREP to { ctx ->
                val newPc = LoopOpcodes.executeForPrep(ctx.instr, ctx.env, ctx.pc)
                DispatchResult.Jump(newPc)
            },
            OpCode.FORLOOP to { ctx ->
                val newPc = LoopOpcodes.executeForLoop(ctx.instr, ctx.env, ctx.pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            },
            OpCode.TFORCALL to { ctx ->
                LoopOpcodes.executeTForCall(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.TFORLOOP to { ctx ->
                val newPc = LoopOpcodes.executeTForLoop(ctx.instr, ctx.env, ctx.pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            },
            OpCode.SETLIST to { ctx ->
                val table = ctx.registers[ctx.instr.a]
                if (table is LuaTable) {
                    val n = ctx.instr.b
                    val c = ctx.instr.c

                    if (n == 0) {
                        // Special case: b=0 means use all values from stack
                        // This handles both varargs and results from CALL with c=0
                        if (ctx.execFrame.top > ctx.instr.a) {
                            // Use values left on stack by previous CALL with c=0 or VARARG with b=0
                            // Values are at registers[instr.a + 1] through registers[top - 1]
                            val numValues = ctx.execFrame.top - (ctx.instr.a + 1)
                            for (i in 0 until numValues) {
                                table.set(LuaDouble((c + i).toDouble()), ctx.registers[ctx.instr.a + 1 + i])
                            }
                            ctx.execFrame.top = 0 // Reset top after using it
                        }
                        // If top <= a, there are no values to add (empty varargs or no results)
                    } else {
                        // Normal SETLIST: copy n values from registers
                        val offset = (c - 1) * 50
                        for (i in 1..n) {
                            table.set(LuaDouble((offset + i).toDouble()), ctx.registers[ctx.instr.a + i])
                        }
                    }
                }
                DispatchResult.Continue
            },
            OpCode.CLOSURE to { ctx ->
                UpvalueOpcodes.executeClosure(ctx.instr, ctx.registers, ctx.openUpvalues, ctx.env) { proto, args, upvalues, func ->
                    ctx.executeProto(proto, args, upvalues, func)
                }
                DispatchResult.Continue
            },
            OpCode.VARARG to { ctx ->
                // Load vararg arguments
                // When used in a table constructor {...}, b=0 means load all varargs
                // Otherwise b-1 is the number of values to load
                val n = ctx.instr.b - 1
                if (n < 0) {
                    // Load all varargs
                    if (ctx.varargs.isEmpty()) {
                        // When varargs is empty and we're using b=0 (load all):
                        // We need to handle this carefully. If the varargs are used in a context
                        // that expects a value (like comparison or assignment), we should provide nil.
                        // If used in a multi-value context (like return or function call), we should
                        // provide 0 values.
                        // Since we can't distinguish at VM level, we provide 0 values by setting top.
                        // But also clear any stale register values to avoid bugs.
                        ctx.registers[ctx.instr.a] = LuaNil
                        ctx.execFrame.top = ctx.instr.a
                    } else {
                        for (i in ctx.varargs.indices) {
                            ctx.registers[ctx.instr.a + i] = ctx.varargs[i]
                        }
                        ctx.execFrame.top = ctx.instr.a + ctx.varargs.size
                    }
                } else {
                    // Load specific number of varargs
                    for (i in 0 until n) {
                        val idx = ctx.instr.a + i
                        ctx.registers[idx] = ctx.varargs.getOrElse(i) { LuaNil }
                        ctx.traceRegisterWrite(idx, ctx.registers[idx], "VARARG")
                    }
                }
                DispatchResult.Continue
            },
            OpCode.CLOSE to { ctx ->
                // Set owner frame for CLOSE-mode yields (nested scopes)
                ctx.env.setPendingCloseOwnerFrame(ctx.execFrame)
                // CRITICAL: Snapshot TBC list BEFORE executeClose clears it
                ctx.env.setPendingCloseOwnerTbc(ctx.execFrame.toBeClosedVars)
                FrameOpcodes.executeClose(ctx.instr, ctx.execFrame, ctx.env) { closeFun, value, errorArg, regIndex ->
                    ctx.env.setMetamethodCallContext("__close")
                    ctx.env.setPendingCloseVar(regIndex, value)
                    ctx.env.setYieldResumeContext(targetReg = 0, encodedCount = 1, stayOnSamePc = true) // __close results ignored
                    ctx.env.setNextCallIsCloseMetamethod()
                    ctx.callFunction(closeFun, listOf(value, errorArg)) // Return value is ignored by executeClose
                    ctx.env.clearPendingCloseVar()
                    ctx.env.clearYieldResumeContext()
                    Unit // Explicitly return Unit
                }
                DispatchResult.Continue
            },
            OpCode.YIELD to { ctx ->
                CoroutineOpcodes.executeYield(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.RESUME to { ctx ->
                CoroutineOpcodes.executeResume(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.GETUPVAL to { ctx ->
                UpvalueOpcodes.executeGetUpval(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
            OpCode.SETUPVAL to { ctx ->
                UpvalueOpcodes.executeSetUpval(ctx.instr, ctx.env)
                DispatchResult.Continue
            },
        )
}
