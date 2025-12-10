package ai.tenum.lua.runtime

/**
 * Base interface for all Lua values
 * @param T the Kotlin type of the underlying value
 */
sealed interface LuaValue<out T> : MetaTable {
    /**
     * The underlying Kotlin value
     */
    val value: T

    /**
     * Returns the Lua type of this value
     */
    fun type(): LuaType
}
