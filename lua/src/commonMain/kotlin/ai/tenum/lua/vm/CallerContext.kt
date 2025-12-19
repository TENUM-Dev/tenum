package ai.tenum.lua.vm

import ai.tenum.lua.vm.execution.ExecutionFrame

/**
 * Manages the stack of execution frames for tracking close ownership across native boundaries.
 * This helper tracks which frames own to-be-closed variables during nested calls and yields.
 *
 * Responsibilities:
 * - Push frame when entering a Lua call (compiled or native)
 * - Pop frame when returning normally
 * - Preserve frames when yielding (no pop)
 * - Provide snapshots for coroutine save/restore
 *
 * This is a focused component that replaces the direct manipulation of closeOwnerFrameStack
 * in LuaVmImpl, making the lifecycle explicit and testable.
 */
class CallerContext {
    private val stack = mutableListOf<ExecutionFrame>()

    /**
     * Get the current size of the stack
     */
    val size: Int
        get() = stack.size

    /**
     * Push a new frame onto the stack.
     * Called at the start of every Lua function call.
     */
    fun push(frame: ExecutionFrame) {
        stack.add(frame)
    }

    /**
     * Pop the top frame from the stack.
     * Called when a function returns normally (not yielding).
     * Throws if the stack is empty.
     */
    fun pop(): ExecutionFrame {
        require(stack.isNotEmpty()) { "Cannot pop from empty caller context stack" }
        return stack.removeAt(stack.size - 1)
    }

    /**
     * Peek at the top frame without removing it.
     * Returns null if the stack is empty.
     */
    fun peek(): ExecutionFrame? = stack.lastOrNull()

    /**
     * Get the frame at a specific index (0 = bottom, size-1 = top).
     * Returns null if index is out of bounds.
     */
    fun getAt(index: Int): ExecutionFrame? = if (index in stack.indices) stack[index] else null

    /**
     * Create an immutable snapshot of the current stack state.
     * Used for capturing state during yields for coroutine save/restore.
     */
    fun snapshot(): List<ExecutionFrame> = stack.toList()

    /**
     * Restore the stack from a snapshot.
     * Clears the current stack and replaces it with the snapshot.
     * Used when resuming a coroutine.
     */
    fun restore(snapshot: List<ExecutionFrame>) {
        stack.clear()
        stack.addAll(snapshot)
    }

    /**
     * Clear the entire stack.
     * Used for cleanup or reset scenarios.
     */
    fun clear() {
        stack.clear()
    }
}
