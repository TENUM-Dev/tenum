package ai.tenum.lua.vm.execution

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.CallFrame

/**
 * Interface for VM capabilities that ExecutionEnvironment delegates to.
 * This represents the services the VM provides to executing code.
 */
interface VmCapabilities {
    fun getMetamethod(
        value: LuaValue<*>,
        methodName: String,
    ): LuaValue<*>?

    fun getRegisterNameHint(registerIndex: Int): String?

    fun getRegisterNameHint(
        registerIndex: Int,
        pc: Int,
    ): String?

    fun luaError(message: String): Nothing

    fun error(
        message: String,
        pc: Int,
    ): Nothing

    fun callFunction(
        func: LuaValue<*>,
        args: List<LuaValue<*>>,
    ): List<LuaValue<*>>

    fun clearCloseException()

    fun setCloseException(exception: Exception)

    fun getCloseException(): Exception?

    fun setPendingCloseVar(
        register: Int,
        value: LuaValue<*>,
    )

    fun clearPendingCloseVar()

    fun setPendingCloseStartReg(registerIndex: Int)

    fun clearPendingCloseStartReg()

    fun setPendingCloseOwnerTbc(vars: MutableList<Pair<Int, LuaValue<*>>>)

    fun clearPendingCloseOwnerTbc()

    fun setPendingCloseOwnerFrame(frame: ExecutionFrame)

    fun getPendingCloseOwnerFrame(): ExecutionFrame?

    fun setPendingCloseErrorArg(error: LuaValue<*>)

    fun clearPendingCloseErrorArg()

    /**
     * When a function call can yield but its results are ignored (e.g., __close metamethods),
     * we need to control how coroutine resume stores the yielded values.
     * Setting encodedCount=1 makes resume store zero results (Lua CALL c=1 semantics).
     */
    fun setYieldResumeContext(
        targetReg: Int,
        encodedCount: Int,
        stayOnSamePc: Boolean = false,
    )

    fun clearYieldResumeContext()

    fun isTruthy(value: LuaValue<*>): Boolean

    fun luaEquals(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean

    fun luaLessThan(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean?

    fun luaLessOrEqual(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean?

    fun toNumber(value: LuaValue<*>): Double

    fun toString(value: LuaValue<*>): String

    val debug: (String) -> Unit

    val trace: (Int, LuaValue<*>, String) -> Unit

    fun getCallStack(): MutableList<CallFrame>

    /**
     * Replace the last call frame on the stack.
     * Used by opcodes that need to update frame state (e.g., ftransfer/ntransfer for RETURN hooks).
     */
    fun replaceLastCallFrame(newFrame: CallFrame)

    /**
     * Set the metamethod call context for the next function call.
     * This is used to mark metamethod invocations with the appropriate namewhat="metamethod".
     *
     * @param metamethodName The name of the metamethod (e.g., "index", "add", "eq")
     */
    fun setMetamethodCallContext(metamethodName: String)

    /**
     * Set the pending inferred name for the next function call.
     * This is used to provide context about how a function is being called (e.g., "for iterator").
     *
     * @param name The inferred function name with its source
     */
    fun setPendingInferredName(name: InferredFunctionName?)

    /**
     * Mark the next function call as a __close metamethod invocation.
     * This is used to annotate stack traces with "in metamethod 'close'".
     */
    fun setNextCallIsCloseMetamethod()

    /**
     * Preserve the error call stack for later retrieval by debug.traceback.
     * Used when errors occur in __close metamethods during function return.
     */
    fun preserveErrorCallStack(callStack: List<ai.tenum.lua.vm.CallFrame>)

    /**
     * Mark the current frame as returning (invisible to debug.getinfo).
     * Used before executing __close metamethods during return, so the returning
     * function is no longer visible in debug.getinfo (matches Lua 5.4 behavior).
     */
    fun markCurrentFrameAsReturning()
}
