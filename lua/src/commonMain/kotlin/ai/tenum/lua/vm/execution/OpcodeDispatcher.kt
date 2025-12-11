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
 * Dispatches opcodes to their handlers, separating routing logic from execution context management.
 * Handles simple opcodes directly, delegates complex control flow to caller.
 */
internal class OpcodeDispatcher(
    private val debugTracer: ai.tenum.lua.vm.debug.DebugTracer,
) {
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
    ): DispatchResult =
        when (instr.opcode) {
            OpCode.MOVE -> {
                StackOpcodes.executeMove(instr, env)
                DispatchResult.Continue
            }

            OpCode.LOADK -> {
                StackOpcodes.executeLoadK(instr, env)
                DispatchResult.Continue
            }

            OpCode.LOADI -> {
                StackOpcodes.executeLoadI(instr, env)
                DispatchResult.Continue
            }

            OpCode.LOADBOOL -> {
                val skipNext = StackOpcodes.executeLoadBool(instr, env)
                if (skipNext) DispatchResult.SkipNext else DispatchResult.Continue
            }

            OpCode.LOADNIL -> {
                StackOpcodes.executeLoadNil(instr, env)
                DispatchResult.Continue
            }

            OpCode.GETGLOBAL -> {
                StackOpcodes.executeGetGlobal(instr, env)
                DispatchResult.Continue
            }

            OpCode.SETGLOBAL -> {
                StackOpcodes.executeSetGlobal(instr, env)
                DispatchResult.Continue
            }

            OpCode.GETTABLE -> {
                TableOpcodes.executeGetTable(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.SETTABLE -> {
                TableOpcodes.executeSetTable(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.NEWTABLE -> {
                TableOpcodes.executeNewTable(instr, env)
                DispatchResult.Continue
            }

            OpCode.SELF -> {
                TableOpcodes.executeSelf(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.ADD -> {
                ArithmeticOpcodes.executeAdd(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.SUB -> {
                ArithmeticOpcodes.executeSub(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.MUL -> {
                ArithmeticOpcodes.executeMul(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.DIV -> {
                ArithmeticOpcodes.executeDiv(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.MOD -> {
                ArithmeticOpcodes.executeMod(instr, env)
                DispatchResult.Continue
            }

            OpCode.POW -> {
                ArithmeticOpcodes.executePow(instr, env)
                DispatchResult.Continue
            }

            OpCode.IDIV -> {
                ArithmeticOpcodes.executeIdiv(instr, env)
                DispatchResult.Continue
            }

            OpCode.BAND -> {
                BitwiseOpcodes.executeBand(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.BOR -> {
                BitwiseOpcodes.executeBor(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.BXOR -> {
                BitwiseOpcodes.executeBxor(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.SHL -> {
                BitwiseOpcodes.executeShl(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.SHR -> {
                BitwiseOpcodes.executeShr(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.UNM -> {
                ArithmeticOpcodes.executeUnm(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.BNOT -> {
                BitwiseOpcodes.executeBnot(instr, env, pc)
                DispatchResult.Continue
            }

            OpCode.NOT -> {
                LogicalOpcodes.executeNot(instr, env)
                DispatchResult.Continue
            }

            OpCode.LEN -> {
                StringOpcodes.executeLen(instr, env)
                DispatchResult.Continue
            }

            OpCode.CONCAT -> {
                StringOpcodes.executeConcat(instr, env)
                DispatchResult.Continue
            }

            OpCode.AND -> {
                LogicalOpcodes.executeAnd(instr, env)
                DispatchResult.Continue
            }

            OpCode.OR -> {
                LogicalOpcodes.executeOr(instr, env)
                DispatchResult.Continue
            }

            OpCode.JMP -> {
                val newPc = ControlFlowOpcodes.executeJmp(instr, env, pc)
                DispatchResult.Jump(newPc)
            }

            OpCode.EQ -> {
                ComparisonOpcodes.executeEq(instr, env)
                DispatchResult.Continue
            }

            OpCode.LT -> {
                ComparisonOpcodes.executeLt(instr, env)
                DispatchResult.Continue
            }

            OpCode.LE -> {
                ComparisonOpcodes.executeLe(instr, env)
                DispatchResult.Continue
            }

            OpCode.TEST -> {
                val newPc = ControlFlowOpcodes.executeTest(instr, env, pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            }

            OpCode.TESTSET -> {
                val newPc = ControlFlowOpcodes.executeTestSet(instr, env, pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            }

            OpCode.CALL -> {
                when (
                    val result =
                        CallOpcodes.executeCall(
                            instr,
                            registers,
                            execFrame,
                            env,
                            pc,
                            setCallContext,
                            debugTracer.isTrampolineEnabled(),
                        )
                ) {
                    is CallOpcodes.CallResult.Completed -> DispatchResult.Continue
                    is CallOpcodes.CallResult.Trampoline -> {
                        DispatchResult.CallTrampoline(
                            newProto = result.newProto,
                            newArgs = result.newArgs,
                            newUpvalues = result.newUpvalues,
                            savedFunc = result.resolvedFunc,
                            callInstruction = instr,
                        )
                    }
                }
            }

            OpCode.TAILCALL -> {
                // Save function reference before delegating to CallOpcodes
                val savedFunc = registers[instr.a]

                when (
                    val result =
                        CallOpcodes.executeTailCall(
                            instr,
                            registers,
                            execFrame,
                            env,
                            pc,
                            debugTracer.isTrampolineEnabled(),
                        )
                ) {
                    is CallOpcodes.TailCallResult.Return -> {
                        DispatchResult.Return(result.values)
                    }
                    is CallOpcodes.TailCallResult.TailCall -> {
                        DispatchResult.TailCallTrampoline(
                            newProto = result.newProto,
                            newArgs = result.newArgs,
                            newUpvalues = result.newUpvalues,
                            savedFunc = result.resolvedFunc,
                        )
                    }
                }
            }

            OpCode.RETURN -> {
                val results =
                    CallOpcodes.executeReturn(instr, registers, execFrame, env, frame.getCurrentLine()) { event, line ->
                        triggerHook(event, line)
                    }
                DispatchResult.Return(results)
            }

            OpCode.FORPREP -> {
                val newPc = LoopOpcodes.executeForPrep(instr, env, pc)
                DispatchResult.Jump(newPc)
            }

            OpCode.FORLOOP -> {
                val newPc = LoopOpcodes.executeForLoop(instr, env, pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            }

            OpCode.TFORCALL -> {
                LoopOpcodes.executeTForCall(instr, env)
                DispatchResult.Continue
            }

            OpCode.TFORLOOP -> {
                val newPc = LoopOpcodes.executeTForLoop(instr, env, pc)
                if (newPc != null) DispatchResult.Jump(newPc) else DispatchResult.Continue
            }

            OpCode.SETLIST -> {
                val table = registers[instr.a]
                if (table is LuaTable) {
                    val n = instr.b
                    val c = instr.c

                    if (n == 0) {
                        // Special case: b=0 means use all values from stack
                        // This handles both varargs and results from CALL with c=0
                        if (execFrame.top > instr.a) {
                            // Use values left on stack by previous CALL with c=0 or VARARG with b=0
                            // Values are at registers[instr.a + 1] through registers[top - 1]
                            val numValues = execFrame.top - (instr.a + 1)
                            for (i in 0 until numValues) {
                                table.set(LuaDouble((c + i).toDouble()), registers[instr.a + 1 + i])
                            }
                            execFrame.top = 0 // Reset top after using it
                        }
                        // If top <= a, there are no values to add (empty varargs or no results)
                    } else {
                        // Normal SETLIST: copy n values from registers
                        val offset = (c - 1) * 50
                        for (i in 1..n) {
                            table.set(LuaDouble((offset + i).toDouble()), registers[instr.a + i])
                        }
                    }
                }
                DispatchResult.Continue
            }

            OpCode.CLOSURE -> {
                UpvalueOpcodes.executeClosure(instr, registers, openUpvalues, env) { proto, args, upvalues, func ->
                    executeProto(proto, args, upvalues, func)
                }
                DispatchResult.Continue
            }

            OpCode.VARARG -> {
                // Load vararg arguments
                // When used in a table constructor {...}, b=0 means load all varargs
                // Otherwise b-1 is the number of values to load
                val n = instr.b - 1
                if (n < 0) {
                    // Load all varargs
                    if (varargs.isEmpty()) {
                        // When varargs is empty and we're using b=0 (load all):
                        // We need to handle this carefully. If the varargs are used in a context
                        // that expects a value (like comparison or assignment), we should provide nil.
                        // If used in a multi-value context (like return or function call), we should
                        // provide 0 values.
                        // Since we can't distinguish at VM level, we provide 0 values by setting top.
                        // But also clear any stale register values to avoid bugs.
                        registers[instr.a] = LuaNil
                        execFrame.top = instr.a
                    } else {
                        for (i in varargs.indices) {
                            registers[instr.a + i] = varargs[i]
                        }
                        execFrame.top = instr.a + varargs.size
                    }
                } else {
                    // Load specific number of varargs
                    for (i in 0 until n) {
                        val idx = instr.a + i
                        registers[idx] = varargs.getOrElse(i) { LuaNil }
                        traceRegisterWrite(idx, registers[idx], "VARARG")
                    }
                }
                DispatchResult.Continue
            }

            OpCode.CLOSE -> {
                FrameOpcodes.executeClose(instr, execFrame, env) { closeFun, value ->
                    callFunction(closeFun, listOf(value, LuaNil))
                }
                DispatchResult.Continue
            }

            OpCode.YIELD -> {
                CoroutineOpcodes.executeYield(instr, env)
                DispatchResult.Continue
            }

            OpCode.RESUME -> {
                CoroutineOpcodes.executeResume(instr, env)
                DispatchResult.Continue
            }

            OpCode.GETUPVAL -> {
                UpvalueOpcodes.executeGetUpval(instr, env)
                DispatchResult.Continue
            }

            OpCode.SETUPVAL -> {
                UpvalueOpcodes.executeSetUpval(instr, env)
                DispatchResult.Continue
            }
        }
}
