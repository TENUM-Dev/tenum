package ai.tenum.lua.runtime

import ai.tenum.lua.lexer.NumberParser

/**
 * Represents a Lua string value
 *
 * CRITICAL: For %p format to work correctly, we need identity-based hashing.
 * Data class hashCode() is content-based, so we add a separate identityId.
 */
data class LuaString(
    override val value: String,
) : LuaValue<String>,
    MetaTable by Companion {
    override fun type(): LuaType = LuaType.STRING

    override fun toString(): String = value

    /**
     * Attempt to coerce this string to a number following Lua 5.4 semantics
     * Returns LuaLong if it's a valid integer, LuaDouble if it's a valid float, or LuaNil if conversion fails
     */
    fun coerceToNumber(): LuaValue<*> {
        val numberParser = NumberParser()
        val result = numberParser.parseStringToNumber(value)

        return when (result) {
            is Long -> LuaLong(result)
            is Double -> LuaDouble(result)
            else -> LuaNil
        }
    }

    companion object : MetaTable {
        /**
         * Shared metatable for all string values
         */
        override var metatableStore: LuaValue<*>? = null

        fun of(value: String): LuaString = LuaString(value)
    }
}
