package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.callstack.CallStackManager

/**
 * Prepared execution state for a Proto.
 * Groups all the setup logic for starting/resuming Proto execution.
 */
data class ExecutionPreparation(
    val execFrame: ExecutionFrame,
    val currentProto: Proto,
    val registers: MutableList<LuaValue<*>>,
    val constants: List<LuaValue<*>>,
    val instructions: List<ai.tenum.lua.compiler.model.Instruction>,
    val pc: Int,
    val openUpvalues: MutableMap<Int, Upvalue>,
    val toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
    val varargs: List<LuaValue<*>>,
    val currentUpvalues: List<Upvalue>,
    val initialCallStackSize: Int,
    val isCoroutineContext: Boolean,
) {
    companion object {
        /**
         * Prepare execution state for a Proto based on execution mode.
         *
         * @param mode Fresh call or resumption
         * @param proto The proto to execute
         * @param args Arguments for the function
         * @param upvalues Upvalues for the function
         * @param callStackManager For managing debug frames
         * @param isCoroutine Whether executing in coroutine context
         * @return Prepared execution state
         */
        fun prepare(
            mode: ExecutionMode,
            proto: Proto,
            args: List<LuaValue<*>>,
            upvalues: List<Upvalue>,
            callStackManager: CallStackManager,
            isCoroutine: Boolean,
        ): ExecutionPreparation {
            // Handle execution mode - fresh call vs resume continuation
            val initialCallStackSize =
                when (mode) {
                    is ExecutionMode.FreshCall -> {
                        // Fresh call: will add new frame below
                        callStackManager.size
                    }
                    is ExecutionMode.ResumeContinuation -> {
                        // Resume: restore debug frames WITHOUT duplicating current execution frame
                        // Filter out coroutine.yield frame - it's a temporary suspension point
                        val framesToRestore =
                            mode.state.debugCallStack.filter { frame ->
                                // Keep all non-native frames
                                // For native frames, filter out coroutine.yield
                                !frame.isNative || (frame.function as? ai.tenum.lua.runtime.LuaNativeFunction)?.name != "coroutine.yield"
                            }
                        callStackManager.resumeWithDebugFrames(framesToRestore)
                    }
                }

            // Create execution frame with state from mode
            val resumptionState = (mode as? ExecutionMode.ResumeContinuation)?.state
            val currentProto = resumptionState?.proto ?: proto
            val execFrame =
                ExecutionFrame(
                    proto = currentProto,
                    initialArgs = args,
                    upvalues = resumptionState?.upvalues ?: upvalues,
                    initialPc = resumptionState?.pc ?: 0,
                    existingRegisters = resumptionState?.registers,
                    existingVarargs = resumptionState?.varargs,
                    existingToBeClosedVars = resumptionState?.toBeClosedVars,
                )

            // CRITICAL: Restore capturedReturns from resumption state (single source of truth for mid-RETURN frames)
            if (resumptionState != null && resumptionState.capturedReturnValues != null) {
                execFrame.capturedReturns = resumptionState.capturedReturnValues
                execFrame.isMidReturn = true
            }

            return ExecutionPreparation(
                execFrame = execFrame,
                currentProto = currentProto,
                registers = execFrame.registers,
                constants = execFrame.constants,
                instructions = execFrame.instructions,
                pc = execFrame.pc,
                openUpvalues = execFrame.openUpvalues,
                toBeClosedVars = execFrame.toBeClosedVars,
                varargs = execFrame.varargs,
                currentUpvalues = resumptionState?.upvalues ?: upvalues,
                initialCallStackSize = initialCallStackSize,
                isCoroutineContext = isCoroutine,
            )
        }
    }
}
