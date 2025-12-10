package ai.tenum.lua.vm.metamethods

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.CallFunctionCallback

/**
 * Resolves and invokes metamethods for Lua values.
 *
 * Metamethods allow Lua tables to customize their behavior for operations like
 * arithmetic, comparisons, indexing, and function calls. This class provides
 * the core metamethod resolution and invocation logic.
 */
internal class MetamethodResolver(
    private val callFunction: CallFunctionCallback,
) {
    /**
     * Get a metamethod from a value's metatable.
     *
     * @param value The value to get the metamethod from
     * @param name The name of the metamethod (e.g., "__add", "__index")
     * @return The metamethod value, or null if not found
     */
    fun getMetamethod(
        value: LuaValue<*>,
        name: String,
    ): LuaValue<*>? {
        val mt = value.metatable as? LuaTable ?: return null
        return mt[LuaString(name)]
    }

    /**
     * Binary operation with metamethod support.
     *
     * Tries to invoke the metamethod on either operand first.
     * Falls back to the standard operation if no metamethod is found.
     *
     * @param left Left operand
     * @param right Right operand
     * @param metaName Name of the metamethod (e.g., "__add")
     * @param op Standard operation to fall back to
     * @return Result of metamethod call or standard operation
     */
    fun binaryOpWithMeta(
        left: LuaValue<*>,
        right: LuaValue<*>,
        metaName: String,
        op: (Double, Double) -> Double,
        binaryOp: (LuaValue<*>, LuaValue<*>, (Double, Double) -> Double) -> LuaValue<*>,
    ): LuaValue<*> {
        // Try metamethod first
        val metaMethod = getMetamethod(left, metaName) ?: getMetamethod(right, metaName)
        if (metaMethod != null && metaMethod is LuaFunction) {
            val result = callFunction(metaMethod, listOf(left, right))
            return result.firstOrNull() ?: LuaNil
        }

        // Fall back to standard operation
        return binaryOp(left, right, op)
    }

    /**
     * Unary operation with metamethod support.
     *
     * Tries to invoke the metamethod first.
     * Falls back to the standard operation if no metamethod is found.
     *
     * @param value The operand
     * @param metaName Name of the metamethod (e.g., "__unm")
     * @param op Standard operation to fall back to
     * @return Result of metamethod call or standard operation
     */
    fun unaryOpWithMeta(
        value: LuaValue<*>,
        metaName: String,
        op: (LuaValue<*>) -> LuaValue<*>,
    ): LuaValue<*> {
        // Try metamethod first
        val metaMethod = getMetamethod(value, metaName)
        if (metaMethod != null && metaMethod is LuaFunction) {
            val result = callFunction(metaMethod, listOf(value))
            return result.firstOrNull() ?: LuaNil
        }

        // Fall back to standard operation
        return op(value)
    }
}
