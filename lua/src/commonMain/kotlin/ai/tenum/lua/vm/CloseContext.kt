package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.CloseResumeState
import ai.tenum.lua.vm.execution.ExecutionFrame
import ai.tenum.lua.vm.execution.ResumptionState

/**
 * Encapsulates all state related to __close metamethod handling and to-be-closed variables.
 *
 * This groups together the 9 close-related member variables from LuaVmImpl:
 * - Pending close operations (var, start reg, owner TBC list)
 * - Close error handling (error arg, exception)
 * - Close resumption state (continuation, owner frame, active state)
 * - Return value capture
 *
 * Benefits:
 * - Reduces parameter passing (single context object instead of 9 parameters)
 * - Clear lifecycle (clearAll() resets everything at once)
 * - Snapshot/restore for yields in __close
 * - Type-safe hasActiveClose() instead of null checks on multiple fields
 */
class CloseContext {
    /**
     * Currently executing __close variable (register, value) when a close handler yields.
     * Re-queued on resume to ensure the in-progress __close runs again.
     */
    var pendingCloseVar: Pair<Int, LuaValue<*>>? = null
        private set

    /**
     * Start register for CLOSE instruction being processed.
     */
    var pendingCloseStartReg: Int = 0
        private set

    /**
     * TBC list snapshot from owner frame for close processing.
     * Captured when entering __close to prevent concurrent modification.
     */
    var pendingCloseOwnerTbc: MutableList<Pair<Int, LuaValue<*>>>? = null
        private set

    /**
     * Error argument to pass to __close handlers.
     * When a __close handler raises an error, subsequent handlers receive this as their error arg.
     */
    var pendingCloseErrorArg: LuaValue<*> = LuaNil
        private set

    /**
     * Resumption state for __close continuation after yield.
     * Captures where the __close function should resume.
     */
    var pendingCloseContinuation: ResumptionState? = null
        private set

    /**
     * Owner frame that triggered the close operation (e.g., the function returning).
     * Used to restore correct context after __close completes.
     */
    var pendingCloseOwnerFrame: ExecutionFrame? = null
        private set

    /**
     * Active close resume state for nested yields in __close.
     * When a __close handler yields and then another __close yields, this tracks the chain.
     */
    var activeCloseResumeState: CloseResumeState? = null
        private set

    /**
     * Exception that occurred during close processing.
     * Stored to be re-thrown after all __close handlers complete.
     */
    var pendingCloseException: Exception? = null
        private set

    /**
     * Return values captured before __close handlers run.
     * Stored so they survive __close exceptions and can be returned after cleanup.
     */
    var capturedReturnValues: List<LuaValue<*>>? = null
        private set

    /**
     * Sets the pending close variable and start register.
     */
    fun setPendingCloseVar(
        closeVar: Pair<Int, LuaValue<*>>?,
        startReg: Int,
    ) {
        pendingCloseVar = closeVar
        pendingCloseStartReg = startReg
    }

    /**
     * Sets the owner frame and its TBC list.
     */
    fun setOwnerFrame(
        frame: ExecutionFrame,
        tbcList: MutableList<Pair<Int, LuaValue<*>>>,
    ) {
        pendingCloseOwnerFrame = frame
        // Store a snapshot copy; callers may mutate/clear the original list (e.g., executeCloseMetamethods)
        pendingCloseOwnerTbc = tbcList.toMutableList()
    }

    /**
     * Sets the active close resume state.
     */
    fun setActiveCloseResumeState(state: CloseResumeState?) {
        activeCloseResumeState = state
    }

    /**
     * Sets the pending close continuation.
     */
    fun setPendingCloseContinuation(continuation: ResumptionState?) {
        pendingCloseContinuation = continuation
    }

    /**
     * Sets the close error argument.
     */
    fun setCloseErrorArg(errorArg: LuaValue<*>) {
        pendingCloseErrorArg = errorArg
    }

    /**
     * Sets captured return values.
     */
    fun setCapturedReturnValues(values: List<LuaValue<*>>?) {
        capturedReturnValues = values
    }

    /**
     * Sets pending exception from close processing.
     */
    fun setPendingException(exception: Exception?) {
        pendingCloseException = exception
    }

    /**
     * Checks if there is an active close operation.
     */
    fun hasActiveClose(): Boolean =
        pendingCloseVar != null ||
            pendingCloseOwnerFrame != null ||
            activeCloseResumeState != null ||
            pendingCloseContinuation != null

    /**
     * Clears all close-related state.
     * Call after close processing completes or when resetting execution.
     */
    fun clearAll() {
        pendingCloseVar = null
        pendingCloseStartReg = 0
        pendingCloseOwnerTbc = null
        pendingCloseErrorArg = LuaNil
        pendingCloseContinuation = null
        pendingCloseOwnerFrame = null
        activeCloseResumeState = null
        pendingCloseException = null
        capturedReturnValues = null
    }

    /**
     * Creates a snapshot of current close state for yield/resume.
     */
    fun snapshot(): CloseContextSnapshot =
        CloseContextSnapshot(
            pendingCloseVar = pendingCloseVar,
            pendingCloseStartReg = pendingCloseStartReg,
            pendingCloseOwnerTbc = pendingCloseOwnerTbc?.toMutableList(),
            pendingCloseErrorArg = pendingCloseErrorArg,
            pendingCloseContinuation = pendingCloseContinuation,
            pendingCloseOwnerFrame = pendingCloseOwnerFrame,
            activeCloseResumeState = activeCloseResumeState,
            pendingCloseException = pendingCloseException,
            capturedReturnValues = capturedReturnValues,
        )

    /**
     * Restores close state from a snapshot.
     */
    fun restore(snapshot: CloseContextSnapshot) {
        pendingCloseVar = snapshot.pendingCloseVar
        pendingCloseStartReg = snapshot.pendingCloseStartReg
        pendingCloseOwnerTbc = snapshot.pendingCloseOwnerTbc
        pendingCloseErrorArg = snapshot.pendingCloseErrorArg
        pendingCloseContinuation = snapshot.pendingCloseContinuation
        pendingCloseOwnerFrame = snapshot.pendingCloseOwnerFrame
        activeCloseResumeState = snapshot.activeCloseResumeState
        pendingCloseException = snapshot.pendingCloseException
        capturedReturnValues = snapshot.capturedReturnValues
    }
}

/**
 * Immutable snapshot of close context state.
 */
data class CloseContextSnapshot(
    val pendingCloseVar: Pair<Int, LuaValue<*>>?,
    val pendingCloseStartReg: Int,
    val pendingCloseOwnerTbc: MutableList<Pair<Int, LuaValue<*>>>?,
    val pendingCloseErrorArg: LuaValue<*>,
    val pendingCloseContinuation: ResumptionState?,
    val pendingCloseOwnerFrame: ExecutionFrame?,
    val activeCloseResumeState: CloseResumeState?,
    val pendingCloseException: Exception?,
    val capturedReturnValues: List<LuaValue<*>>?,
)
