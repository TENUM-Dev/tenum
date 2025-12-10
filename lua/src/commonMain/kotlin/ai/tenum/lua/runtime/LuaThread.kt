package ai.tenum.lua.runtime

/**
 * Represents a Lua thread (coroutine) value
 * Threads are used for cooperative multitasking in Lua
 */
class LuaThread : LuaValue<Unit> {
    override val value: Unit = Unit

    /**
     * Each thread has its own metatable
     */
    override var metatableStore: LuaValue<*>? = null

    override fun type(): LuaType = LuaType.THREAD

    override fun toString(): String = "thread: ${hashCode().toString(16)}"

    // TODO: Add coroutine state and execution context when implementing coroutines
}
