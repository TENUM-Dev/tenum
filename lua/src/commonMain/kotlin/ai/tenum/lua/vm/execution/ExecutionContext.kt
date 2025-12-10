package ai.tenum.lua.vm.execution

import ai.tenum.lua.vm.CallFrame

/**
 * Represents the execution context duality in the VM.
 *
 * The VM operates in two distinct contexts:
 * - Normal execution: call stack reflects current execution state
 * - Hook execution: VM executing in a different context while preserving the original observed state
 *
 * This sealed class makes the duality explicit in the type system, eliminating the need for
 * nullable fields and boolean flags to track hook execution state.
 */
sealed class ExecutionContext {
    /**
     * Normal execution context where the current call stack represents the active execution state.
     */
    data class Normal(
        val frames: List<CallFrame>,
    ) : ExecutionContext()

    /**
     * Hook execution context where the VM is executing a hook function while observing
     * the original execution state that triggered the hook.
     *
     * @param hookFrame The current hook function being executed
     * @param observedFrames The original call stack being observed (most-recent-first)
     */
    data class Hook(
        val hookFrame: CallFrame,
        val observedFrames: List<CallFrame>,
    ) : ExecutionContext()

    /**
     * Get all frames in the correct order for traceback display (most-recent-first).
     */
    fun getFramesForTraceback(): List<CallFrame> =
        when (this) {
            is Normal -> frames
            is Hook -> listOf(hookFrame) + observedFrames
        }

    /**
     * Get all frames in the order they appear on the call stack (oldest-first).
     */
    fun getFramesForStack(): List<CallFrame> =
        when (this) {
            is Normal -> frames.asReversed()
            is Hook -> (observedFrames.asReversed() + hookFrame)
        }
}
