package ai.tenum.lua.vm.debug

/**
 * Interface for objects that can store per-thread hook state.
 * In Lua 5.4, hook state is per-coroutine, not global.
 * Both main thread (LuaThread) and coroutine threads (CoroutineThread) implement this.
 */
interface ThreadHookState {
    var hookConfig: HookConfig
    var hookInstructionCount: Int
    var hookInProgress: Boolean
}
