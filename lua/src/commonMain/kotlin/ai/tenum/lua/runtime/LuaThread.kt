package ai.tenum.lua.runtime

/**
 * Represents a Lua thread (coroutine) value
 * Threads are used for cooperative multitasking in Lua
 */
class LuaThread :
    LuaValue<Unit>,
    ai.tenum.lua.vm.debug.ThreadHookState {
    override val value: Unit = Unit

    /**
     * Each thread has its own metatable
     */
    override var metatableStore: LuaValue<*>? = null

    override fun type(): LuaType = LuaType.THREAD

    override fun toString(): String = "thread: ${hashCode().toString(16)}"

    // Per-thread hook state (Lua 5.4 keeps hooks per coroutine)
    // Main thread also needs its own hook state
    override var hookConfig: ai.tenum.lua.vm.debug.HookConfig = ai.tenum.lua.vm.debug.HookConfig.NONE
    override var hookInstructionCount: Int = 0
    override var hookInProgress: Boolean = false
}
