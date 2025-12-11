package ai.tenum.lua.vm.debug

import ai.tenum.lua.runtime.LuaValue

/**
 * Manages debug tracing and logging for the VM.
 *
 * Handles:
 * - Debug logging with configurable output
 * - Register write tracing for debugging
 */
class DebugTracer {
    /**
     * Debug logging enabled/disabled
     */
    var debugEnabled: Boolean = false

    /**
     * Debug log output handler
     */
    var debugLogger: (String) -> Unit = { message -> println("[LuaVM] $message") }

    /**
     * Register index to trace writes (null = no tracing)
     */
    private var traceRegisterIndex: Int? = null

    /**
     * Enable debug logging with optional custom logger.
     *
     * @param logger Custom log output handler
     */
    fun enableDebug(logger: (String) -> Unit = { message -> println("[LuaVM] $message") }) {
        debugEnabled = true
        debugLogger = logger
    }

    /**
     * Disable debug logging.
     */
    fun disableDebug() {
        debugEnabled = false
    }

    /**
     * Log debug message if debug is enabled.
     *
     * The message builder lambda is only evaluated if debug is enabled,
     * providing zero overhead when disabled.
     *
     * @param messageBuilder Lambda that produces the debug message
     */
    inline fun debug(messageBuilder: () -> String) {
        if (debugEnabled) {
            debugLogger(messageBuilder())
        }
    }

    /**
     * Enable register write tracing for a specific register index.
     *
     * When enabled, all writes to this register will be logged.
     *
     * @param index Register index to trace (null to disable)
     */
    fun enableRegisterTrace(index: Int?) {
        traceRegisterIndex = index
        debug { "Register trace enabled for R[$index]" }
    }

    /**
     * Trace a register write if tracing is enabled for this register.
     *
     * @param regIndex Register being written
     * @param value Value being written
     * @param ctx Context string describing the operation
     */
    fun traceRegisterWrite(
        regIndex: Int,
        value: LuaValue<*>,
        ctx: String,
    ) {
        val traceIdx = traceRegisterIndex
        if (traceIdx != null && traceIdx == regIndex) {
            debug { "[TRACE] Write R[$regIndex] = $value (ctx=$ctx)" }
        }
    }
}
