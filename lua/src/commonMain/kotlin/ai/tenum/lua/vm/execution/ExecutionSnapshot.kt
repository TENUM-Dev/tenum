package ai.tenum.lua.vm.execution

import ai.tenum.lua.vm.CallFrame

/**
 * Immutable snapshot of execution state at the moment a hook is triggered.
 *
 * This replaces the pattern of temporarily storing state in mutable VM fields
 * (hookCallStack, pendingInferredName) with an immutable snapshot that can be
 * safely passed around without temporal coupling concerns.
 *
 * Benefits:
 * - No temporal coupling (order of set/clear operations doesn't matter)
 * - Thread-safe by design (immutable)
 * - Clear ownership (snapshot belongs to the hook invocation)
 * - Easier to test (pure data, no side effects)
 */
data class ExecutionSnapshot(
    /**
     * The call stack frames at the moment the hook was triggered (oldest-first).
     */
    val frames: List<CallFrame>,
    /**
     * The inferred function name that should be associated with the hook call.
     */
    val inferredName: InferredFunctionName,
) {
    /**
     * Create a StackView for semantic access to the snapshot's frames.
     */
    fun asStackView(): StackView = StackView(frames)

    companion object {
        /**
         * Capture the current execution state for a hook invocation.
         *
         * @param callStack The current call stack (oldest-first as stored in VM)
         * @return ExecutionSnapshot with frames in oldest-first order
         */
        fun capture(callStack: List<CallFrame>): ExecutionSnapshot =
            ExecutionSnapshot(
                frames = callStack.toList(), // Keep oldest-first order for StackView
                inferredName = InferredFunctionName(null, FunctionNameSource.Hook),
            )
    }
}
