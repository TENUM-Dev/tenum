package ai.tenum.lua.vm.debug

import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.vm.CallFrame

/**
 * Manages debug hooks and their invocation.
 *
 * Handles:
 * - Hook configuration and lifecycle (per-thread in Lua 5.4)
 * - Hook event triggering with recursion prevention
 * - Instruction counting for COUNT events (per-thread)
 * - Registry _HOOKKEY initialization
 *
 * Note: In Lua 5.4, hook state is per-coroutine, not global.
 * Each thread (main or coroutine) has its own hook configuration.
 */
internal class HookManager(
    private val getCallStack: () -> List<CallFrame>,
    private val getRegistry: () -> LuaTable,
    private val getCurrentThread: () -> ThreadHookState,
) {
    /**
     * Set debug hook configuration for a specific thread.
     *
     * @param thread The thread to set hook for (main thread or coroutine thread)
     * @param hook The hook callback (null to disable)
     * @param mask Event mask string ("c"=call, "r"=return, "l"=line)
     * @param count For COUNT events - trigger every N instructions
     */
    fun setHook(
        thread: ThreadHookState,
        hook: DebugHook?,
        mask: String,
        count: Int = 0,
    ) {
        thread.hookConfig = HookConfig.fromMask(hook, mask, count)
        thread.hookInstructionCount = 0

        // Initialize _HOOKKEY in registry with weak keys table if not already present
        // This is required by Lua 5.4 specification (db.lua:328)
        val registry = getRegistry()
        val hookKey = LuaString("_HOOKKEY")
        if (registry[hookKey] !is LuaTable) {
            val hookTable = LuaTable()
            // Set metatable with __mode="k" for weak keys
            val mt = LuaTable()
            mt[LuaString("__mode")] = LuaString("k")
            hookTable.metatable = mt
            registry[hookKey] = hookTable
        }
    }

    /**
     * Get hook configuration for a specific thread.
     */
    fun getHook(thread: ThreadHookState): HookConfig = thread.hookConfig

    /**
     * Trigger hook for CALL, RETURN, or LINE events.
     * Uses the current thread's hook configuration.
     *
     * Prevents recursive hook invocation.
     *
     * @param event The hook event type
     * @param line The current line number
     */
    fun triggerHook(
        event: HookEvent,
        line: Int,
    ) {
        val thread = getCurrentThread()
        if (thread.hookInProgress) return // Prevent recursive hooks
        val hook = thread.hookConfig.hook
        if (event in thread.hookConfig.mask && hook != null) {
            thread.hookInProgress = true
            try {
                hook.onHook(event, line, getCallStack())
            } finally {
                thread.hookInProgress = false
            }
        }
    }

    /**
     * Check and trigger COUNT hook if configured.
     * Uses the current thread's hook configuration.
     *
     * Should be called after each instruction execution.
     * Triggers hook every N instructions based on count configuration.
     *
     * @param currentLine The current line number
     */
    fun checkCountHook(currentLine: Int) {
        val thread = getCurrentThread()
        if (HookEvent.COUNT in thread.hookConfig.mask && thread.hookConfig.count > 0) {
            thread.hookInstructionCount++
            if (thread.hookInstructionCount >= thread.hookConfig.count) {
                thread.hookInstructionCount = 0
                triggerHook(HookEvent.COUNT, currentLine)
            }
        }
    }

    /**
     * Reset instruction counter for current thread.
     *
     * Called when entering new function context.
     */
    fun resetInstructionCount() {
        val thread = getCurrentThread()
        thread.hookInstructionCount = 0
    }
}
