package ai.tenum.lua.vm.errorhandling

import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.CallFrame

/**
 * Lua runtime error with stack trace information.
 * Used for Phase 7.1 (Error Handling) and Phase 6.4 (Debug Library).
 */
class LuaRuntimeError(
    message: String,
    val errorValue: LuaValue<*> = LuaString(message),
    val callStack: List<CallFrame> = emptyList(),
    val level: Int = 1,
) : RuntimeException(message) {
    /**
     * Generate a formatted stack traceback
     */
    fun getTraceback(messagePrefix: String = ""): String = TracebackFormatter.formatTraceback(callStack, messagePrefix)

    companion object {
        /**
         * Create error from simple message
         */
        fun fromMessage(
            message: String,
            callStack: List<CallFrame> = emptyList(),
        ): LuaRuntimeError = LuaRuntimeError(message, LuaString(message), callStack)

        /**
         * Create error from Lua value
         */
        fun fromValue(
            value: LuaValue<*>,
            callStack: List<CallFrame> = emptyList(),
        ): LuaRuntimeError {
            val message =
                when (value) {
                    is LuaString -> value.value
                    else -> value.toString()
                }
            return LuaRuntimeError(message, value, callStack)
        }
    }
}
