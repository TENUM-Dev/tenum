package ai.tenum.lua.runtime

/**
 * Represents a Lua nil value
 * Singleton object for all nil values
 */
data object LuaNil : LuaValue<Unit?> {
    override val value: Unit? = null

    override var metatableStore: LuaValue<*>? = null

    override fun type(): LuaType = LuaType.NIL

    override fun toString(): String = "nil"
}
