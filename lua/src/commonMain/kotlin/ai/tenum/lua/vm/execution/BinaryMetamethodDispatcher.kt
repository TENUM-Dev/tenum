package ai.tenum.lua.vm.execution

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue

/**
 * Domain object for binary metamethod dispatch pattern.
 *
 * Encapsulates the common pattern of:
 * 1. Check left operand for metamethod
 * 2. Check right operand for metamethod (if left doesn't have it)
 * 3. Call metamethod with both operands
 * 4. Return first result (or nil)
 * 5. Error if metamethod exists but is not callable
 *
 * This pattern is duplicated across 15+ opcodes (arithmetic, bitwise, comparison, string).
 */
object BinaryMetamethodDispatcher {
    /**
     * Try to dispatch a binary operation to a metamethod.
     *
     * @param left Left operand
     * @param right Right operand
     * @param metamethodName Name of metamethod (e.g., "__add", "__lt")
     * @param ctx Opcode context providing VM capabilities
     * @return Result of metamethod call, or null if no metamethod found
     * @throws RuntimeException if metamethod exists but is not callable
     */
    fun tryDispatch(
        left: LuaValue<*>,
        right: LuaValue<*>,
        metamethodName: String,
        env: ExecutionEnvironment,
    ): LuaValue<*>? {
        // Check left operand first, then right
        val meta =
            env.getMetamethod(left, metamethodName)
                ?: env.getMetamethod(right, metamethodName)

        return when {
            meta == null || meta is LuaNil -> null // No metamethod found, caller should use native operation
            meta !is LuaFunction -> {
                // Metamethod exists but is not callable - error
                val typeStr = meta.type().name.lowercase()
                val shortName = metamethodName.removePrefix("__")
                env.luaError("attempt to call a $typeStr value (metamethod '$shortName')")
            }
            else -> {
                // Call metamethod and return first result
                env.setMetamethodCallContext(metamethodName)
                val results = env.callFunction(meta, listOf(left, right))
                results.firstOrNull() ?: LuaNil
            }
        }
    }
}
