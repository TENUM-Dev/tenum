package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.debug.HookEvent
import ai.tenum.lua.vm.execution.ArgumentCollector
import ai.tenum.lua.vm.execution.ExecutionEnvironment
import ai.tenum.lua.vm.execution.ExecutionFrame
import ai.tenum.lua.vm.execution.FunctionNameInference
import ai.tenum.lua.vm.execution.InferredFunctionName
import ai.tenum.lua.vm.execution.ResultStorage

/**
 * Call-related opcode operations.
 * Handles CALL and TAILCALL instructions for function invocation.
 */
object CallOpcodes {
    /**
     * Prepare a call frame with return values stored in temporary registers for hook access.
     * This allows debug.getlocal to access return values during RETURN hooks.
     *
     * @param currentFrame The current call frame to update
     * @param returnFtransfer The ftransfer value (1-based index where return values start)
     * @param results The return values to store
     * @return Updated call frame with return values accessible via debug.getlocal
     */
    internal inline fun prepareFrameWithReturnValues(
        currentFrame: ai.tenum.lua.vm.CallFrame,
        returnFtransfer: Int,
        results: List<LuaValue<*>>,
    ): ai.tenum.lua.vm.CallFrame {
        // Create a temporary registers array that includes return values for hook access
        // We can't modify the actual registers as that breaks upvalue/closure semantics
        val tempRegisters = currentFrame.registers.toMutableList()
        val baseIndex = currentFrame.base + returnFtransfer - 1 // Convert 1-based to 0-based
        results.forEachIndexed { idx, value ->
            val regIdx = baseIndex + idx
            // Ensure temp registers array is large enough
            while (tempRegisters.size <= regIdx) {
                tempRegisters.add(LuaNil)
            }
            tempRegisters[regIdx] = value
        }

        return currentFrame.copy(
            ftransfer = returnFtransfer,
            ntransfer = results.size,
            registers = tempRegisters, // Use temp registers with return values
            top = returnFtransfer - 1 + results.size, // Update top to include return values
        )
    }

    /**
     * Result of tail call execution.
     */
    sealed class TailCallResult {
        /** Perform tail call optimization - replace current frame */
        data class TailCall(
            val newProto: Proto,
            val newArgs: List<LuaValue<*>>,
            val newUpvalues: List<Upvalue>,
            val resolvedFunc: LuaCompiledFunction,
        ) : TailCallResult()

        /** Return from current function with these values */
        data class Return(
            val values: List<LuaValue<*>>,
        ) : TailCallResult()
    }

    /**
     * Result of regular call execution.
     */
    sealed class CallResult {
        /** Call completed - results stored in registers */
        object Completed : CallResult()

        /** Trampoline call to compiled function */
        data class Trampoline(
            val newProto: Proto,
            val newArgs: List<LuaValue<*>>,
            val newUpvalues: List<Upvalue>,
            val resolvedFunc: LuaCompiledFunction,
        ) : CallResult()
    }

    /**
     * CALL: Call a function with fixed or variable arguments and results.
     * R[A], ... ,R[A+C-2] := R[A](R[A+1], ... ,R[A+B-1])
     *
     * If B=0, arguments are from A+1 to top.
     * If C=0, returns all results and sets top marker.
     * 
     * Returns CallResult indicating whether to trampoline or if call completed.
     */
    fun executeCall(
        instr: Instruction,
        registers: MutableList<LuaValue<*>>,
        frame: ExecutionFrame,
        env: ExecutionEnvironment,
        pc: Int,
        setCallContext: (InferredFunctionName) -> Unit,
        trampolineEnabled: Boolean,
    ): CallResult {
        var func = registers[instr.a]
        val collector = ArgumentCollector(registers, frame)
        val args = collector.collectArgs(instr.a + 1, instr.b)

        // Infer function name from bytecode for debug.getinfo
        val inferred =
            FunctionNameInference.inferFunctionName(
                instructions = frame.proto.instructions,
                callPc = pc,
                funcRegister = instr.a,
                constants = frame.proto.constants,
                localVars = frame.proto.localVars,
            )
        setCallContext(inferred)

        // Capture method call flag and error hint before they might be cleared
        val wasMethodCall = env.isMethodCall
        val methodErrorHint = env.methodCallErrorHint
        env.isMethodCall = false
        env.methodCallErrorHint = null

        // Store method call context for native functions to access
        if (wasMethodCall) {
            MethodCallContext.set(true)
        }

        // Track if we're taking the trampoline path (to avoid clearing context in finally block)
        var isTrampolining = false
        
        try {
            // Check for __call metamethod if func is not a function
            if (func !is LuaFunction) {
                val metaMethod = env.getMetamethod(func, "__call")
                if (metaMethod != null) {
                    env.setMetamethodCallContext("__call")
                    // callFunction will recursively handle chained __call metamethods
                    val results = env.callFunction(metaMethod, listOf(func) + args)
                    env.debug("    Results (via __call): $results")
                    storeCallResults(instr, registers, frame, results, env)
                } else {
                    // Generate error with context
                    // If SELF set methodCallErrorHint, use that instead of the function's hint
                    val nameHint = methodErrorHint ?: env.getRegisterNameHint(instr.a, pc)
                    val typeStr = func.type().name.lowercase()
                    val errorMsg =
                        if (nameHint != null) {
                            "attempt to call a $typeStr value ($nameHint)"
                        } else {
                            "attempt to call a $typeStr value"
                        }
                    env.error(errorMsg, pc)
                }
            } else {
                env.debug("  Call R[${instr.a}] with ${args.size} args")
                
                // Check if we can trampoline for compiled functions
                if (trampolineEnabled && func is LuaCompiledFunction) {
                    env.debug("  Trampolining call to compiled function")
                    // Clear method context before trampolining
                    MethodCallContext.clear()
                    // Mark that we're trampolining so finally block doesn't clear the inferred name
                    isTrampolining = true
                    // NOTE: Do NOT clear call context (setCallContext) here!
                    // The inferred name must be preserved for the trampoline handler to use.
                    // It will be cleared by the handler after creating the CallFrame.
                    return CallResult.Trampoline(
                        newProto = func.proto,
                        newArgs = args,
                        newUpvalues = func.upvalues,
                        resolvedFunc = func,
                    )
                }
                
                // Non-trampoline path: regular recursive call
                val results = env.callFunction(func, args)
                env.debug("    Results: $results")
                storeCallResults(instr, registers, frame, results, env)
            }
        } finally {
            // Always clear method call context after the call completes
            MethodCallContext.clear()
            // Clear call context for next call ONLY if not trampolining
            // (trampoline handler will clear it after using the inferred name)
            if (!isTrampolining) {
                setCallContext(InferredFunctionName.UNKNOWN)
            }
        }
        
        return CallResult.Completed
    }

    /**
     * Store CALL results according to expected result count.
     * CRITICAL: Properly resets top to prevent stale values from VARARG affecting SETLIST.
     */
    internal fun storeCallResults(
        instr: Instruction,
        registers: MutableList<LuaValue<*>>,
        frame: ExecutionFrame,
        results: List<LuaValue<*>>,
        env: ExecutionEnvironment,
    ) {
        val storage = ResultStorage(env)
        storage.storeResults(
            targetReg = instr.a,
            encodedCount = instr.c,
            results = results,
            opcodeName = "CALL",
        )
    }

    /**
     * TAILCALL: Perform tail call optimization.
     * return R[A](R[A+1], ... ,R[A+B-1])
     *
     * If B=0, arguments are from A+1 to top.
     * Returns TailCallResult indicating whether to perform TCO or fallback to regular return.
     */
    fun executeTailCall(
        instr: Instruction,
        registers: MutableList<LuaValue<*>>,
        frame: ExecutionFrame,
        env: ExecutionEnvironment,
        pc: Int,
        trampolineEnabled: Boolean,
    ): TailCallResult {
        var funcVal = registers[instr.a]
        val collector = ArgumentCollector(registers, frame)
        var args = collector.collectArgs(instr.a + 1, instr.b)

        env.debug("  Tail call R[${instr.a}] with ${args.size} args")
        env.trace(instr.a, registers[instr.a], "TAILCALL-read")

        // Walk the __call chain to find the final callable
        // This preserves TCO through metamethod chains
        while (funcVal !is LuaFunction) {
            val callMeta = env.getMetamethod(funcVal, "__call")
            if (callMeta != null) {
                // Add the table/value with __call as first argument
                args = listOf(funcVal) + args
                funcVal = callMeta
            } else {
                // No __call metamethod, generate error
                val nameHint = env.getRegisterNameHint(instr.a, pc)
                val typeStr = funcVal.type().name.lowercase()
                val errorMsg =
                    if (nameHint != null) {
                        "attempt to call a $typeStr value ($nameHint)"
                    } else {
                        "attempt to call a $typeStr value"
                    }
                env.error(errorMsg, pc)
            }
        }

        // If target is compiled Lua function and trampoline enabled, perform TCO
        if (trampolineEnabled && funcVal is LuaCompiledFunction) {
            env.debug("  TCO: trampoline into closure id=${funcVal.hashCode()}")
            return TailCallResult.TailCall(
                newProto = funcVal.proto,
                newArgs = args,
                newUpvalues = funcVal.upvalues,
                resolvedFunc = funcVal,
            )
        }

        // Fallback: regular return with function call result
        // Use env.callFunction to ensure hooks are triggered
        return TailCallResult.Return(env.callFunction(funcVal, args))
    }

    /**
     * RETURN: Return values from function.
     * return R[A], ... ,R[A+B-2]
     *
     * If B=0, returns all values from A to top (vararg return).
     * If B=1, returns 0 values.
     */
    fun executeReturn(
        instr: Instruction,
        registers: MutableList<LuaValue<*>>,
        frame: ExecutionFrame,
        env: ExecutionEnvironment,
        currentLine: Int,
        triggerHookFn: (HookEvent, Int) -> Unit,
    ): List<LuaValue<*>> {
        val collector = ArgumentCollector(registers, frame)
        val results = collector.collectResults(instr.a, instr.b)

        env.debug("  Return ${results.size} values: $results")

        // Update current CallFrame with return transfer info before triggering RETURN hook
        // This allows debug.getinfo('r') in the hook to access ftransfer and ntransfer
        val callStack = env.getCallStack()
        if (callStack.isNotEmpty()) {
            val currentFrame = callStack.last()

            // Calculate ftransfer: 1 + param_count
            // Return values are indexed right after parameters in debug.getlocal space
            // (locals don't affect this calculation - they're in separate slots)
            val returnFtransfer =
                if (currentFrame.proto != null) {
                    val proto = currentFrame.proto
                    val paramCount = proto.parameters.size
                    1 + paramCount
                } else {
                    // Native function: ftransfer = 1 + param_count (no locals)
                    // For native functions, we don't have precise param count stored
                    // Use a heuristic: assume params are in positions 1..n
                    1 + currentFrame.top
                }

            val updatedFrame = prepareFrameWithReturnValues(currentFrame, returnFtransfer, results)
            env.replaceLastCallFrame(updatedFrame)
        }

        // Trigger RETURN hook
        triggerHookFn(HookEvent.RETURN, currentLine)

        return results
    }
}
