@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
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
     * Result of call execution - unified for both CALL and TAILCALL.
     */
    sealed class CallResult {
        /** Call completed - results stored in registers (CALL only) */
        object Completed : CallResult()

        /** Trampoline into compiled function */
        data class Trampoline(
            val newProto: Proto,
            val newArgs: List<LuaValue<*>>,
            val newUpvalues: List<Upvalue>,
            val resolvedFunc: LuaCompiledFunction,
        ) : CallResult()

        /** Return from current function with values (TAILCALL to native function) */
        data class Return(
            val values: List<LuaValue<*>>,
        ) : CallResult()
    }

    /**
     * Resolve a value to a callable function, handling __call metamethods.
     * Returns the final callable function and adjusted arguments.
     */
    private inline fun resolveCallable(
        funcVal: LuaValue<*>,
        args: List<LuaValue<*>>,
        env: ExecutionEnvironment,
        pc: Int,
        registerIndex: Int,
    ): Pair<LuaFunction, List<LuaValue<*>>> {
        var func = funcVal
        var finalArgs = args

        // Walk the __call chain to find the final callable
        while (func !is LuaFunction) {
            val callMeta = env.getMetamethod(func, "__call")
            if (callMeta != null) {
                // Add the table/value with __call as first argument
                finalArgs = listOf(func) + finalArgs
                func = callMeta
            } else {
                // No __call metamethod, generate error
                val nameHint = env.getRegisterNameHint(registerIndex, pc)
                val typeStr = func.type().name.lowercase()
                val errorMsg =
                    if (nameHint != null) {
                        "attempt to call a $typeStr value ($nameHint)"
                    } else {
                        "attempt to call a $typeStr value"
                    }
                env.error(errorMsg, pc)
            }
        }

        return func to finalArgs
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
            // Resolve to callable function (handles __call metamethods)
            val (resolvedFunc, resolvedArgs) = resolveCallable(func, args, env, pc, instr.a)

            env.debug("  Call R[${instr.a}] with ${resolvedArgs.size} args")

            // Trampoline for compiled Lua functions
            if (resolvedFunc is LuaCompiledFunction) {
                env.debug("  Trampolining call to compiled function")
                // Clear method context before trampolining
                MethodCallContext.clear()
                // Mark that we're trampolining so finally block doesn't clear the inferred name
                isTrampolining = true
                // NOTE: Do NOT clear call context (setCallContext) here!
                // The inferred name must be preserved for the trampoline handler to use.
                // It will be cleared by the handler after creating the CallFrame.
                return CallResult.Trampoline(
                    newProto = resolvedFunc.proto,
                    newArgs = resolvedArgs,
                    newUpvalues = resolvedFunc.upvalues,
                    resolvedFunc = resolvedFunc,
                )
            }

            // Native functions: direct call
            if (resolvedFunc is LuaNativeFunction && resolvedFunc.name == "pcall") {
                println("[CALL debug] calling pcall; caller TBC size=${frame.toBeClosedVars.size} TBC=${frame.toBeClosedVars}")
            }
            val results = env.callFunction(resolvedFunc, resolvedArgs)
            env.debug("    Results: $results")
            storeCallResults(instr, registers, frame, results, env)
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
     * Returns CallResult indicating whether to perform TCO or return directly.
     */
    fun executeTailCall(
        instr: Instruction,
        registers: MutableList<LuaValue<*>>,
        frame: ExecutionFrame,
        env: ExecutionEnvironment,
        pc: Int,
    ): CallResult {
        val funcVal = registers[instr.a]
        val collector = ArgumentCollector(registers, frame)
        val args = collector.collectArgs(instr.a + 1, instr.b)

        env.debug("  Tail call R[${instr.a}] with ${args.size} args")
        env.trace(instr.a, funcVal, "TAILCALL-read")

        // Resolve to callable function (handles __call metamethods)
        val (resolvedFunc, resolvedArgs) = resolveCallable(funcVal, args, env, pc, instr.a)

        // Perform TCO for compiled Lua functions
        if (resolvedFunc is LuaCompiledFunction) {
            env.debug("  TCO: trampoline into closure id=${resolvedFunc.hashCode()}")
            return CallResult.Trampoline(
                newProto = resolvedFunc.proto,
                newArgs = resolvedArgs,
                newUpvalues = resolvedFunc.upvalues,
                resolvedFunc = resolvedFunc,
            )
        }

        // Native functions: direct return with call result
        // Use env.callFunction to ensure hooks are triggered
        return CallResult.Return(env.callFunction(resolvedFunc, resolvedArgs))
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
        // Step 0: If frame already has captured returns (from previous execution before yield-in-close),
        // use those as the return values. But still process remaining TBC vars if any exist.
        val results =
            if (frame.capturedReturns != null) {
                env.debug("  Return using captured returns: ${frame.capturedReturns}")
                frame.capturedReturns!!
            } else {
                // Collect return values normally
                val collector = ArgumentCollector(registers, frame)
                collector.collectResults(instr.a, instr.b)
            }

        env.debug("  Return ${results.size} values (before __close): $results")
        env.debug("  toBeClosedVars: ${frame.toBeClosedVars}")

        // Step 2: Execute __close metamethods for all to-be-closed variables
        // NOTE: During NORMAL returns (RETURN opcode), the function is STILL ACTIVE
        // when __close executes. debug.getinfo(2) from within __close should see
        // the returning function's name, not the caller's name.
        // This differs from ERROR unwinding, where the function is conceptually
        // "already gone" and debug.getinfo(2) should see the caller.
        // See locals.lua:262 (normal return) vs locals.lua:445 (error path)

        // Step 3: Execute __close metamethods for all to-be-closed variables
        // This must happen AFTER return values are evaluated and frame is popped, but BEFORE returning
        // Note: Error call stack preservation is handled at the VM level (LuaVmImpl.executeProto)
        // where LuaRuntimeError is caught BEFORE conversion to LuaException, ensuring the full
        // call stack with isCloseMetamethod flags is preserved for debug.traceback()
        //
        // IMPORTANT: Store return values and any exception from __close for two-phase return handling
        // This allows xpcall to access return values even when __close fails
        // This matches Lua 5.4 semantics where return values are placed on stack before __close runs
        env.clearCloseException()
        frame.capturedReturns = results
        env.setPendingCloseStartReg(0)
        env.setPendingCloseOwnerFrame(frame) // Save owner frame for yield-in-close
        // Share the TBC list reference so updates remain visible across yield boundaries
        env.setPendingCloseOwnerTbc(frame.toBeClosedVars)
        try {
            frame.executeCloseMetamethods(0) { regIndex, upvalue, capturedValue, errorArg ->
                env.debug("  Calling __close for value: $capturedValue, error: $errorArg")
                val closeFn = upvalue.closedValue as? ai.tenum.lua.runtime.LuaFunction
                if (closeFn != null) {
                    // Call __close - errors should propagate normally
                    env.setMetamethodCallContext("__close")
                    env.setPendingCloseVar(regIndex, capturedValue)
                    env.setPendingCloseErrorArg(errorArg)
                    env.setYieldResumeContext(targetReg = 0, encodedCount = 1, stayOnSamePc = true)
                    env.setNextCallIsCloseMetamethod()
                    env.callFunction(closeFn, listOf(capturedValue, errorArg))
                    env.clearPendingCloseVar()
                    // Clear yield context only after successful return (not after yield)
                    env.clearYieldResumeContext()
                }
            }
        } catch (e: Exception) {
            // Store the __close exception for two-phase handling
            env.setCloseException(e)
            // Re-throw so normal error handling works
            throw e
        } finally {
            env.clearPendingCloseStartReg()
        }

        env.debug("  Return ${results.size} values (after __close): $results")

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
