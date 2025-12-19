package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.VmDebugSink
import ai.tenum.lua.vm.callstack.CallStackManager
import ai.tenum.lua.vm.coroutine.CoroutineStateManager
import ai.tenum.lua.vm.errorhandling.LuaException
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import ai.tenum.lua.vm.errorhandling.LuaStackFrame
import ai.tenum.lua.vm.errorhandling.StackTraceBuilder

/**
 * Handles error processing and conversion in the VM.
 * Encapsulates logic for:
 * - Converting LuaRuntimeError to LuaException
 * - Preserving error call stacks for coroutines
 * - Building error messages with proper location info
 * - Converting platform errors (StackOverflowError, OutOfMemoryError) to Lua errors
 * - Debug proto dumps for diagnostics
 */
class ErrorHandler(
    private val coroutineStateManager: CoroutineStateManager,
    private val stackTraceBuilder: StackTraceBuilder,
    private val callStackManager: CallStackManager,
    private val debugSink: VmDebugSink,
    private val debugEnabled: Boolean,
    private val preserveErrorCallStack: (List<CallFrame>) -> Unit,
) {
    /**
     * Convert LuaRuntimeError to LuaException with proper error location and stack trace.
     * Handles error level calculation, coroutine error stack preservation, and debug output.
     */
    fun handleRuntimeError(
        error: LuaRuntimeError,
        currentProto: Proto,
        pc: Int,
    ): LuaException {
        // Preserve the full call stack from LuaRuntimeError BEFORE converting to LuaException
        preserveErrorCallStack(error.callStack)

        // If executing in a coroutine, save the call stack for debug.traceback()
        val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
        if (currentCoroutine is LuaCoroutine.LuaFunctionCoroutine) {
            val callStackBase = currentCoroutine.thread.callStackBase
            val coroutineCallStack =
                error.callStack.drop(callStackBase).filter { frame ->
                    !frame.isNative || (frame.function as? LuaNativeFunction)?.name != "coroutine.yield"
                }
            currentCoroutine.thread.savedCallStack = coroutineCallStack
        }

        // When level=0 or errorValue is nil, return raw message with no location info
        if (error.level == 0 || error.errorValue is LuaNil) {
            return LuaException(
                errorMessageOnly = error.message ?: "",
                line = null,
                source = null,
                luaStackTrace = emptyList(),
                errorValueOverride = error.errorValue,
            )
        }

        // Determine which stack frame to report based on error level
        val reversedStack = error.callStack.asReversed()
        val nonNativeFrames = reversedStack.filter { !it.isNative }
        val targetFrameIndex = error.level - 1

        val (errorLine, errorSource) =
            if (targetFrameIndex >= 0 && targetFrameIndex < nonNativeFrames.size) {
                val targetFrame = nonNativeFrames[targetFrameIndex]
                Pair(targetFrame.getCurrentLine(), targetFrame.proto?.source ?: "?")
            } else {
                Pair(stackTraceBuilder.getCurrentLine(currentProto, pc), currentProto.source)
            }

        val errorMsg = error.message ?: "runtime error"

        // Build the stack trace
        val stackTrace =
            nonNativeFrames.map { frame ->
                LuaStackFrame(
                    functionName = frame.proto?.name?.takeIf { it.isNotEmpty() },
                    source = frame.proto?.source ?: "?",
                    line = frame.getCurrentLine(),
                )
            }

        // Dump proto for debugging if needed
        dumpProtoIfNeeded(currentProto, pc, errorMsg)

        // For non-string errors, pass errorValueOverride to preserve the original value
        return if (error.errorValue is LuaString) {
            LuaException(
                errorMessageOnly = errorMsg,
                line = errorLine,
                source = errorSource,
                luaStackTrace = stackTrace,
            )
        } else {
            LuaException(
                errorMessageOnly = errorMsg,
                line = errorLine,
                source = errorSource,
                luaStackTrace = stackTrace,
                errorValueOverride = error.errorValue,
            )
        }
    }

    /**
     * Convert generic exceptions to LuaException with diagnostic information.
     */
    fun handleGenericException(
        exception: Exception,
        currentProto: Proto,
        pc: Int,
        registers: List<LuaValue<*>>,
        constants: List<LuaValue<*>>,
        instructions: List<ai.tenum.lua.compiler.model.Instruction>,
    ): LuaException {
        val extraInfo =
            if (debugEnabled) {
                try {
                    val instr = if (pc in instructions.indices) instructions[pc] else null
                    "pc=$pc, instr=${instr?.opcode ?: "?"} a=${instr?.a ?: -1} b=${instr?.b ?: -1} c=${instr?.c ?: -1}, registers=${registers.size}, constants=${constants.size}, instructions=${instructions.size}"
                } catch (inner: Exception) {
                    "pc=$pc, registers=${registers.size}, constants=${constants.size}, instructions=${instructions.size}"
                }
            } else {
                null
            }

        // Compose a detailed message that includes exception type and optional debug context.
        val excClass = exception::class.simpleName ?: "Exception"
        val baseMessage = exception.message ?: "runtime error"
        val messageWithClass = "$excClass: $baseMessage"
        val composedMessage = if (extraInfo != null) "$messageWithClass ($extraInfo)" else messageWithClass

        // Emit debug information for easier diagnosis when debugEnabled
        debugSink.debug { "[EXCEPTION HANDLER] Converting $excClass to LuaException: ${exception.message}" }
        if (debugEnabled) {
            val traceSummary = "${exception::class.simpleName}:${exception.message}"
            debugSink.debug { "[EXCEPTION HANDLER] Exception summary: $traceSummary" }
        }

        return LuaException(
            errorMessageOnly = composedMessage,
            line = stackTraceBuilder.getCurrentLine(currentProto, pc),
            source = currentProto.source,
            luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
        )
    }

    /**
     * Convert platform errors (StackOverflowError, OutOfMemoryError) to LuaException.
     */
    fun handlePlatformError(
        error: Error,
        currentProto: Proto,
        pc: Int,
    ): LuaException {
        val errorType = error::class.simpleName ?: "Error"

        return when {
            errorType.contains("StackOverflow") -> {
                LuaException(
                    errorMessageOnly = "C stack overflow",
                    line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                    source = currentProto.source,
                    luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                )
            }
            errorType.contains("OutOfMemory") -> {
                LuaException(
                    errorMessageOnly = "not enough memory",
                    line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                    source = currentProto.source,
                    luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                )
            }
            else -> throw error
        }
    }

    /**
     * Dump proto information for debugging when assertions fail.
     */
    private fun dumpProtoIfNeeded(
        proto: Proto,
        pc: Int,
        errorMessage: String,
    ) {
        try {
            if (errorMessage.contains("assertion failed") || debugEnabled) {
                val start = maxOf(0, pc - 8)
                val end = minOf(proto.instructions.size - 1, pc + 8)
                debugSink.debug { "--- Proto dump for ${proto.name} source=${proto.source} pc=$pc ---" }
                for (i in start..end) {
                    val ins = proto.instructions[i]
                    debugSink.debug {
                        "  PC=$i: ${ins.opcode} a=${ins.a} b=${ins.b} c=${ins.c}  # line=${stackTraceBuilder.getCurrentLine(proto, i)}"
                    }
                }
                debugSink.debug { "--- Constants (${proto.constants.size}) ---" }
                for ((ci, c) in proto.constants.withIndex()) {
                    if (ci in (0..199)) debugSink.debug { "  K[$ci] = $c" }
                }
                debugSink.debug { "--- Line events (${proto.lineEvents.size}) ---" }
                for (li in proto.lineEvents) debugSink.debug { "  PC=${li.pc} -> line=${li.line} kind=${li.kind}" }
                debugSink.debug { "--- End proto dump ---" }
            }
        } catch (_: Exception) {
        }
    }
}
