package ai.tenum.lua.vm.debug

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.vm.CallFrame

/**
 * Debug hook callback interface.
 * Called by the VM when specified events occur during execution.
 */
interface DebugHook {
    /**
     * The original Lua function (if this hook wraps a Lua function)
     */
    val luaFunction: LuaFunction?

    /**
     * Called when a hook event occurs
     *
     * @param event The type of event
     * @param line The current line number (-1 if not available)
     * @param callStack The current call stack (most recent frame first)
     */
    fun onHook(
        event: HookEvent,
        line: Int,
        callStack: List<CallFrame>,
    )
}
