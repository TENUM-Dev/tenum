package ai.tenum.lua.runtime

import ai.tenum.lua.runtime.LuaTable

/**
 * Represents a Lua boolean value
 * Uses singleton pattern for true/false values
 */
class LuaBoolean private constructor(
    override val value: Boolean,
) : LuaValue<Boolean>,
    MetaTable by Companion {
    override fun type(): LuaType = LuaType.BOOLEAN

    override fun toString(): String = value.toString()

    override fun equals(other: Any?): Boolean = other is LuaBoolean && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object : MetaTable {
        /**
         * Singleton TRUE value
         */
        val TRUE = LuaBoolean(true)

        /**
         * Singleton FALSE value
         */
        val FALSE = LuaBoolean(false)

        /**
         * Factory method to get boolean instance
         */
        fun of(value: Boolean): LuaBoolean = if (value) TRUE else FALSE

        /**
         * Shared metatable for all boolean values
         */
        override var metatableStore: LuaValue<*>? = null
            set(value) {
                require(value == null || value is LuaTable) { "Metatable must be a table or nil" }
                field = value
            }
    }
}
