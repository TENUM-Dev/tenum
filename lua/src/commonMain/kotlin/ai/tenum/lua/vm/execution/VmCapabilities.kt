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
}
