package ai.tenum.lua.runtime

/**
 * Represents a Lua userdata value
 * Userdata allows arbitrary Kotlin objects to be stored in Lua
 *
 * Unlike other types, userdata has individual metatables (not shared)
 * and can store any Kotlin object.
 */
class LuaUserdata<T : Any>(
    override val value: T,
) : LuaValue<T> {
    /**
     * Each userdata has its own metatable
     */
    override var metatableStore: LuaValue<*>? = null

    override fun type(): LuaType = LuaType.USERDATA

    override fun toString(): String = "userdata: ${hashCode().toString(16)}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LuaUserdata<*>) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
