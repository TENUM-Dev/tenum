package ai.tenum.lua.vm.execution

import ai.tenum.lua.vm.CallFrame

/**
 * Provides semantic access to the call stack, hiding implementation details like
 * stack orientation and index calculations.
 *
 * This abstraction eliminates scattered `size - level` calculations and stack reversals
 * throughout the codebase, centralizing all stack indexing logic in one place.
 *
 * Stack orientation:
 * - Internal representation: oldest-first (append-friendly)
 * - Level indexing: 1-based from bottom (Lua semantics: level 1 = caller)
 * - Traceback display: newest-first (user expectation)
 */
class StackView(
    private val frames: List<CallFrame>,
) {
    /**
     * Get the frame at a specific level (0-based from top).
     * Level 0 = current function, level 1 = caller, level 2 = caller's caller, etc.
     * Native (C) functions are included in level counting (Lua 5.4 semantics).
     *
     * @param level Stack level (0-based from top, where 0 is the current/most recent frame)
     * @return CallFrame at the specified level, or null if out of bounds
     */
    fun atLevel(level: Int): CallFrame? {
        if (level < 0 || level >= frames.size) return null
        return frames[frames.size - 1 - level]
    }

    /**
     * Get frames ordered for traceback display (most-recent-first).
     * This is the order users expect to see in stack traces.
     */
    fun forTraceback(): List<CallFrame> = frames.asReversed()

    /**
     * Get frames ordered for stack walking (oldest-first).
     * This is the natural append order used by the VM.
     */
    fun forStack(): List<CallFrame> = frames

    /**
     * Get the total number of frames in the stack.
     */
    val size: Int get() = frames.size

    /**
     * Check if the stack is empty.
     */
    val isEmpty: Boolean get() = frames.isEmpty()

    /**
     * Get the most recent (top) frame, or null if stack is empty.
     */
    fun mostRecent(): CallFrame? = frames.lastOrNull()

    /**
     * Get the oldest (bottom) frame, or null if stack is empty.
     */
    fun oldest(): CallFrame? = frames.firstOrNull()
}
