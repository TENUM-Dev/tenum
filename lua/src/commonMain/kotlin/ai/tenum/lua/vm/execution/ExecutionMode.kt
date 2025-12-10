package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CallFrame

/**
 * Represents the mode of execution for a Lua function.
 * Makes explicit the distinction between fresh function calls and resuming suspended coroutines.
 *
 * Domain Concept: "How are we executing this function?"
 * - FreshCall: Starting new execution (needs new call frame)
 * - ResumeContinuation: Continuing suspended execution (frames already exist in call stack)
 */
sealed class ExecutionMode {
    /**
     * Fresh function call - starting new execution from the beginning.
     * Requires creating and adding a new call frame to the call stack.
     */
    object FreshCall : ExecutionMode()

    /**
     * Resuming a suspended coroutine - continuing from a saved execution state.
     * Call frames from previous execution already exist and should not be duplicated.
     *
     * @param state The saved execution state to resume from
     */
    data class ResumeContinuation(
        val state: ResumptionState,
    ) : ExecutionMode()
}

/**
 * Snapshot of execution state for coroutine resumption.
 * Separates "execution state" (what we need to continue) from "debug info" (what we show in tracebacks).
 *
 * Domain Concept: "The complete state needed to resume execution exactly where we left off"
 */
data class ResumptionState(
    val proto: Proto,
    val pc: Int,
    val registers: MutableList<LuaValue<*>>,
    val upvalues: List<Upvalue>,
    val varargs: List<LuaValue<*>>,
    val yieldTargetRegister: Int,
    val yieldExpectedResults: Int,
    /**
     * Call stack frames for debug tracebacks.
     * These represent the accumulated call history across all yield/resume cycles.
     * Used ONLY for error reporting and debug.traceback, NOT for frame lifecycle management.
     */
    val debugCallStack: List<CallFrame>,
)
