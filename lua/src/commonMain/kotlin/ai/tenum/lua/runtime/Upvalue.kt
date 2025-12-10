package ai.tenum.lua.runtime

/**
 * Represents an upvalue in Lua - a captured variable from an outer scope.
 *
 * Upvalues can be in two states:
 * 1. Open: Points to a register in an active stack frame
 * 2. Closed: Contains a copy of the value after the stack frame is gone
 */
class Upvalue(
    /**
     * The register index in the stack (only used when open)
     */
    var registerIndex: Int = -1,
    /**
     * The closed value (only used when closed)
     */
    var closedValue: LuaValue<*>? = null,
    /**
     * Reference to the registers list (only used when open)
     */
    var registers: MutableList<LuaValue<*>>? = null,
) {
    /**
     * Whether this upvalue is closed (has been copied from the stack)
     */
    var isClosed: Boolean = false

    /**
     * Get the current value of this upvalue
     */
    fun get(): LuaValue<*> {
        if (isClosed) {
            return closedValue ?: LuaNil
        } else {
            val regs = registers
            if (regs == null) {
                return LuaNil
            }
            if (registerIndex < 0 || registerIndex >= regs.size) {
                return LuaNil
            }
            val value = regs[registerIndex]
            return value
        }
    }

    /**
     * Set the value of this upvalue
     */
    fun set(value: LuaValue<*>) {
        if (isClosed) {
            closedValue = value
        } else {
            registers?.set(registerIndex, value)
        }
    }

    /**
     * Close this upvalue by copying the value from the stack
     */
    fun close() {
        if (!isClosed) {
            closedValue = registers?.get(registerIndex) ?: LuaNil
            isClosed = true
            registers = null
        }
    }
}
