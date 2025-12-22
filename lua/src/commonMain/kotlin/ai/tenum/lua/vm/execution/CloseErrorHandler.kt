package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.callstack.CallStackManager
import ai.tenum.lua.vm.errorhandling.LuaException
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import ai.tenum.lua.vm.errorhandling.StackTraceBuilder

/**
 * Handler for error chaining through __close metamethods.
 *
 * This class encapsulates the logic for handling errors that occur during
 * to-be-closed variable cleanup. It:
 * 1. Extracts error values from various exception types
 * 2. Closes to-be-closed variables (which may modify the error via __close handlers)
 * 3. Throws appropriate exceptions based on whether the error changed
 *
 * This matches Lua 5.4 semantics where errors can be replaced by __close metamethods.
 */
class CloseErrorHandler(
    private val callStackManager: CallStackManager,
    private val stackTraceBuilder: StackTraceBuilder,
    private val markCurrentFrameAsReturning: () -> Unit,
    private val closeToBeClosedVars: (
        toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
        alreadyClosedRegs: MutableSet<Int>,
        errorArg: LuaValue<*>,
    ) -> LuaValue<*>,
) {
    /**
     * Convert a LuaValue error to a displayable string message.
     */
    private fun errorValueToMessage(errorValue: LuaValue<*>): String =
        when (errorValue) {
            is LuaString -> errorValue.value
            is LuaNumber -> errorValue.toDouble().toString()
            else -> errorValue.toString()
        }

    /**
     * Extract the error value from various exception types.
     */
    private fun extractErrorValue(originalException: Throwable): LuaValue<*> =
        when (originalException) {
            is LuaRuntimeError -> originalException.errorValue
            is LuaException -> originalException.errorValue ?: (originalException.message?.let { LuaString(it) } ?: LuaNil)
            else -> originalException.message?.let { LuaString(it) } ?: LuaNil
        }

    /**
     * Create a new exception with the modified error value.
     * The exception type is preserved (LuaRuntimeError stays LuaRuntimeError, etc.)
     */
    private fun createModifiedException(
        originalException: Throwable,
        finalError: LuaValue<*>,
        errorMsg: String,
        currentProto: Proto,
        pc: Int,
    ): Throwable =
        when (originalException) {
            is LuaRuntimeError -> {
                LuaRuntimeError(
                    message = errorMsg,
                    errorValue = finalError,
                    callStack = callStackManager.captureSnapshot(),
                )
            }
            is LuaException -> {
                LuaException(
                    errorMessageOnly = errorMsg,
                    line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                    source = currentProto.source,
                    luaStackTrace = originalException.luaStackTrace,
                    errorValueOverride = finalError,
                )
            }
            else -> {
                // Should not happen, but handle gracefully
                LuaException(
                    errorMessageOnly = errorMsg,
                    line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                    source = currentProto.source,
                    luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                    errorValueOverride = finalError,
                )
            }
        }

    /**
     * Handle error chaining through __close metamethods.
     * Closes to-be-closed variables and throws a new exception if the error changed.
     *
     * @param originalException The exception that triggered cleanup
     * @param toBeClosedVars Mutable list of to-be-closed variables (will be cleared)
     * @param alreadyClosedRegs Set of already-closed register indices
     * @param currentProto Current Proto for error reporting
     * @param pc Current program counter for error reporting
     * @return Never returns (always throws)
     */
    fun handleErrorAndCloseToBeClosedVars(
        originalException: Throwable,
        toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
        alreadyClosedRegs: MutableSet<Int>,
        currentProto: Proto,
        pc: Int,
    ): Nothing {
        // Extract initial error value from the exception
        val initialError = extractErrorValue(originalException)

        // Mark the current frame as returning BEFORE closing to-be-closed variables
        // This makes the frame invisible to debug.getinfo during __close execution
        // (matching Lua 5.4.8 semantics where the function is conceptually already returned)
        if (toBeClosedVars.isNotEmpty()) {
            markCurrentFrameAsReturning()
        }

        // Close to-be-closed variables and get the final error (possibly changed by __close handlers)
        val finalError = closeToBeClosedVars(toBeClosedVars, alreadyClosedRegs, initialError)

        // Clear the TBC list to prevent double-closing if exception propagates to outer frames
        // This is critical: if a __close handler throws, the var is NOT removed from the list
        // (see ExecutionFrame.executeCloseMetamethods line 246), so we must clear it here
        // to prevent re-processing when the exception unwinds through multiple executeProto frames
        toBeClosedVars.clear()

        // If the error changed during cleanup, throw a new exception with the new error
        if (finalError !== initialError) {
            val errorMsg = errorValueToMessage(finalError)
            throw createModifiedException(originalException, finalError, errorMsg, currentProto, pc)
        }

        // Error didn't change, re-throw the original exception
        throw originalException
    }
}
