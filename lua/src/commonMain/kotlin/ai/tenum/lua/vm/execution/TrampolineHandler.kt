package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.errorhandling.LuaException
import ai.tenum.lua.vm.errorhandling.LuaStackFrame

/**
 * Handles trampoline call transitions (both regular and tail calls).
 * Manages call depth checks, stack frame transitions, and state updates.
 */
internal class TrampolineHandler(
    private val buildStackTrace: (List<CallFrame>) -> List<LuaStackFrame>,
    private val maxCallDepth: Int = 1000,
) {
    /**
     * Result of a call depth check.
     */
    sealed class CallDepthCheck {
        data class Ok(
            val newDepth: Int,
        ) : CallDepthCheck()

        data class Overflow(
            val exception: LuaException,
        ) : CallDepthCheck()
    }

    /**
     * Check if call depth allows another call, returning new depth or overflow exception.
     */
    fun checkCallDepth(
        currentDepth: Int,
        currentProto: Proto,
        captureSnapshot: () -> List<CallFrame>,
    ): CallDepthCheck {
        val newDepth = currentDepth + 1
        return if (newDepth > maxCallDepth) {
            val stackFrames = buildStackTrace(captureSnapshot())
            CallDepthCheck.Overflow(
                LuaException(
                    errorMessageOnly = "C stack overflow",
                    line = null,
                    source = currentProto.source,
                    luaStackTrace = stackFrames,
                ),
            )
        } else {
            CallDepthCheck.Ok(newDepth)
        }
    }

    /**
     * Create a CallFrame for a trampolined function.
     */
    fun createTrampolineFrame(
        action: CallTrampolineAction,
        newArgs: List<LuaValue<*>>,
        inferredName: InferredFunctionName?,
    ): CallFrame =
        CallFrame(
            function = action.luaFunc,
            proto = action.newProto,
            pc = 0,
            base = 0,
            registers = action.newFrame.registers,
            isNative = false,
            isTailCall = false,
            inferredFunctionName = inferredName,
            varargs = action.newFrame.varargs,
            ftransfer = if (newArgs.isEmpty()) 0 else 1,
            ntransfer = newArgs.size,
        )

    /**
     * Create a CallFrame for a tail call.
     */
    fun createTailCallFrame(
        action: CallTrampolineAction,
        newArgs: List<LuaValue<*>>,
        inferredName: InferredFunctionName?,
        tailCallDepth: Int,
    ): CallFrame =
        CallFrame(
            function = action.luaFunc,
            proto = action.newProto,
            pc = -1,
            base = 0,
            registers = action.newFrame.registers,
            isNative = false,
            isTailCall = true,
            tailCallDepth = tailCallDepth,
            inferredFunctionName = inferredName,
            varargs = action.newFrame.varargs,
            ftransfer = if (newArgs.isEmpty()) 0 else 1,
            ntransfer = newArgs.size,
        )
}
