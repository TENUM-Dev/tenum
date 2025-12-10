package ai.tenum.lua.vm.callstack

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.execution.InferredFunctionName

/**
 * Manages the call stack lifecycle for the Lua VM.
 * Separates call stack management from execution logic.
 *
 * Domain Concept: "Call Stack" - tracks the chain of active function calls
 *
 * Responsibilities:
 * - Creating and adding frames for new function calls
 * - Restoring frames when resuming coroutines
 * - Cleaning up frames when functions return
 * - Providing stack snapshots for debugging/error reporting
 *
 * Aggregate Encapsulation:
 * - frames is private - all access goes through domain methods
 * - Stack invariants (cleanup, entry/exit) are enforced here
 * - External code cannot corrupt stack state
 */
class CallStackManager {
    private val frames = mutableListOf<CallFrame>()

    /**
     * Current call stack size
     */
    val size: Int get() = frames.size

    /**
     * Begin a fresh function call - creates and adds a new frame.
     * Returns the initial stack size to track which frames to clean up later.
     *
     * @return The stack size before adding this frame (for cleanup)
     */
    fun beginFunctionCall(
        proto: Proto,
        function: LuaFunction?,
        registers: MutableList<LuaValue<*>>,
        varargs: List<LuaValue<*>>,
        args: List<LuaValue<*>>,
        inferredName: InferredFunctionName?,
        pc: Int = 0,
    ): Int {
        val initialSize = frames.size

        val frame =
            CallFrame(
                function = function,
                proto = proto,
                pc = pc,
                base = 0,
                registers = registers,
                isNative = false,
                inferredFunctionName = inferredName,
                varargs = varargs,
                ftransfer = if (args.isEmpty()) 0 else 1,
                ntransfer = args.size,
            )

        frames.add(frame)
        return initialSize
    }

    /**
     * Resume execution with accumulated debug frames.
     * Restores frames for traceback purposes WITHOUT adding a duplicate frame for current execution.
     *
     * The current execution frame is NOT added here - it's implicit in the continuation.
     *
     * @return The stack size before restoration (for cleanup)
     */
    fun resumeWithDebugFrames(debugFrames: List<CallFrame>): Int {
        val initialSize = frames.size
        frames.addAll(debugFrames)
        return initialSize
    }

    /**
     * Add a single frame to the call stack (used for native calls, etc.)
     */
    fun addFrame(frame: CallFrame) {
        frames.add(frame)
    }

    /**
     * Remove the last frame from the call stack (used for tail calls)
     */
    fun removeLastFrame() {
        if (frames.isNotEmpty()) {
            frames.removeLast()
        }
    }

    /**
     * Clean up frames added during execution.
     * Removes all frames added after the given initial size.
     */
    fun cleanupFrames(initialSize: Int) {
        while (frames.size > initialSize) {
            frames.removeLastOrNull()
        }
    }

    /**
     * Get a snapshot of the current call stack (for error reporting, debug.traceback, etc.)
     */
    fun captureSnapshot(): List<CallFrame> = frames.toList()

    /**
     * Drop the first N frames (e.g., to exclude main thread frames in coroutines)
     */
    fun captureSnapshotFrom(startIndex: Int): List<CallFrame> =
        if (startIndex < frames.size) {
            frames.drop(startIndex)
        } else {
            emptyList()
        }

    /**
     * Get the last frame (current execution frame)
     */
    fun lastFrame(): CallFrame? = frames.lastOrNull()

    /**
     * Update the last frame's PC (for debugging hooks)
     */
    fun updateLastFramePc(pc: Int) {
        frames.lastOrNull()?.let { frame ->
            frames[frames.lastIndex] = frame.copy(pc = pc)
        }
    }

    /**
     * Replace the last frame with an updated version
     */
    fun replaceLastFrame(newFrame: CallFrame) {
        if (frames.isNotEmpty()) {
            frames[frames.lastIndex] = newFrame
        }
    }

    /**
     * Add a native entry frame (e.g., for chunk execution or pcall).
     * Returns a cleanup handle that must be used to remove the frame when done.
     *
     * Usage:
     * ```
     * val cleanup = callStackManager.enterNativeFrame()
     * try {
     *     // ... execution ...
     * } finally {
     *     cleanup.exit()
     * }
     * ```
     */
    fun enterNativeFrame(): FrameCleanup {
        val entryFrame =
            CallFrame(
                function = null,
                proto = null,
                pc = 0,
                base = 0,
                registers = mutableListOf(),
                isNative = true,
                isTailCall = false,
                inferredFunctionName = null,
                varargs = emptyList(),
                ftransfer = 0,
                ntransfer = 0,
            )
        val initialSize = frames.size
        frames.add(entryFrame)
        return FrameCleanup(this, initialSize)
    }

    /**
     * Handle for cleaning up frames.
     * Encapsulates the cleanup logic to ensure frames are always properly removed.
     */
    class FrameCleanup internal constructor(
        private val manager: CallStackManager,
        private val targetSize: Int,
    ) {
        fun exit() {
            manager.cleanupFrames(targetSize)
        }
    }
}
