package ai.tenum.lua.vm.errorhandling

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.vm.CallFrame

/**
 * Generates and throws Lua runtime errors with proper stack traces.
 */
internal class ErrorReporter(
    private val stackTraceBuilder: StackTraceBuilder,
) {
    /**
     * Throw a Lua runtime error with proper stack trace.
     *
     * @param message The error message
     * @param proto The function prototype where the error occurred (optional)
     * @param pc The program counter where the error occurred (optional)
     * @param callStack The current call stack for building stack trace
     * @throws LuaException Always throws
     */
    fun luaError(
        message: String,
        proto: Proto? = null,
        pc: Int? = null,
        callStack: List<CallFrame>,
    ): Nothing {
        val line = if (proto != null && pc != null) stackTraceBuilder.getCurrentLine(proto, pc) else null
        val source = proto?.source
        val stackTrace = stackTraceBuilder.buildLuaStackTrace(callStack)

        throw LuaException(message, line, source, stackTrace)
    }
}
