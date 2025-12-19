package ai.tenum.lua.vm.errorhandling

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.vm.CallFrame

/**
 * Builds Lua stack traces from call frames and bytecode information.
 */
class StackTraceBuilder {
    /**
     * Get the current line number for a given PC in a proto.
     *
     * @param proto The function prototype
     * @param pc The program counter
     * @return The line number, or -1 if no line info available (Lua 5.4 compatibility)
     */
    fun getCurrentLine(
        proto: Proto,
        pc: Int,
    ): Int? {
        // If no line info available (stripped debug info), return -1 for Lua 5.4 compatibility
        if (proto.lineEvents.isEmpty()) {
            return -1
        }
        // Find the most recent line event entry for this PC
        return proto.lineEvents.findLast { it.pc <= pc }?.line
    }

    /**
     * Build Lua stack trace from call stack frames.
     *
     * @param callStack The call stack (most recent frame at end)
     * @return List of Lua stack frames for error reporting
     */
    fun buildLuaStackTrace(callStack: List<CallFrame>): List<LuaStackFrame> =
        callStack.asReversed().map { frame ->
            val line =
                if (frame.proto != null && !frame.isNative) {
                    getCurrentLine(frame.proto, frame.pc)
                } else {
                    null
                }

            LuaStackFrame(
                functionName = frame.proto?.name?.takeIf { it.isNotEmpty() },
                source = frame.proto?.source,
                line = line,
            )
        }
}
