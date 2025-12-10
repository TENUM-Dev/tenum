package ai.tenum.lua.runtime

/**
 * Convert a Lua value to a boolean following Lua's truthiness rules:
 * - false and nil are false
 * - Everything else is true (including 0 and empty string)
 */
fun LuaValue<*>.toBoolean(): Boolean =
    when (this) {
        is LuaNil -> false
        is LuaBoolean -> this.value
        else -> true
    }

/**
 * Get the "type name" for error messages
 * Returns the same string as Lua's type() function
 */
fun LuaValue<*>.typeName(): String = type().name.lowercase()

/**
 * Check if a value is "falsy" (nil or false)
 */
fun LuaValue<*>.isFalsy(): Boolean = this is LuaNil || (this is LuaBoolean && !this.value)

/**
 * Check if a value is "truthy" (not nil and not false)
 */
fun LuaValue<*>.isTruthy(): Boolean = !isFalsy()
