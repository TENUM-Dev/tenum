package ai.tenum.lua.vm

import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.execution.ExecutionFrame

/**
 * Encapsulates execution flow state for the VM.
 *
 * This groups together the 3 execution-control member variables from LuaVmImpl:
 * - currentEnvUpvalue: Current environment upvalue (_ENV)
 * - activeExecutionFrame: Currently executing frame
 * - nextCallIsCloseMetamethod: Flag to mark next call as __close metamethod
 *
 * Benefits:
 * - Clear lifecycle management (set/clear operations)
 * - Type-safe hasActiveFrame() instead of null checks
 * - Snapshot/restore for nested execution contexts
 * - Reduced parameter passing
 */
class ExecutionFlowState {
    /**
     * Current environment upvalue (_ENV).
     * Note: _ENV is NOT always at upvalue[0] - functions can have other upvalues before _ENV!
     * We must find _ENV by name from proto.upvalueInfo, not assume a fixed position.
     */
    internal var currentEnvUpvalue: Upvalue? = null
        private set

    /**
     * Currently active execution frame.
     * Set at the start of executeProto and used by callFunction to push caller frames.
     * This allows native functions (like pcall) to access their calling frame for TBC tracking.
     */
    internal var activeExecutionFrame: ExecutionFrame? = null
        private set

    /**
     * Flag to mark the next function call as a __close metamethod call.
     * When true, the call frame will be marked with isCloseMetamethod=true.
     * This makes the frame transparent for debug.getinfo level counting.
     */
    internal var nextCallIsCloseMetamethod: Boolean = false
        private set

    /**
     * Sets the current environment upvalue.
     */
    fun setCurrentEnvUpvalue(upvalue: Upvalue?) {
        currentEnvUpvalue = upvalue
    }

    /**
     * Sets the active execution frame.
     */
    fun setActiveExecutionFrame(frame: ExecutionFrame?) {
        activeExecutionFrame = frame
    }

    /**
     * Sets the flag for next call being a close metamethod.
     */
    fun setNextCallIsCloseMetamethod(value: Boolean) {
        nextCallIsCloseMetamethod = value
    }

    /**
     * Checks if there is an active execution frame.
     */
    fun hasActiveFrame(): Boolean {
        return activeExecutionFrame != null
    }

    /**
     * Creates a snapshot of current execution flow state.
     */
    fun snapshot(): ExecutionFlowStateSnapshot {
        return ExecutionFlowStateSnapshot(
            currentEnvUpvalue = currentEnvUpvalue,
            activeExecutionFrame = activeExecutionFrame,
            nextCallIsCloseMetamethod = nextCallIsCloseMetamethod,
        )
    }

    /**
     * Restores execution flow state from a snapshot.
     */
    fun restore(snapshot: ExecutionFlowStateSnapshot) {
        currentEnvUpvalue = snapshot.currentEnvUpvalue
        activeExecutionFrame = snapshot.activeExecutionFrame
        nextCallIsCloseMetamethod = snapshot.nextCallIsCloseMetamethod
    }
}

/**
 * Immutable snapshot of execution flow state.
 */
data class ExecutionFlowStateSnapshot(
    val currentEnvUpvalue: Upvalue?,
    val activeExecutionFrame: ExecutionFrame?,
    val nextCallIsCloseMetamethod: Boolean,
)
