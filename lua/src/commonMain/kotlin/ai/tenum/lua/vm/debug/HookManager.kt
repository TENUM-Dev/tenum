package ai.tenum.lua.vm.debug

import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.vm.CallFrame

/**
 * Manages debug hooks and their invocation.
 *
 * Handles:
 * - Hook configuration and lifecycle
 * - Hook event triggering with recursion prevention
 * - Instruction counting for COUNT events
 * - Registry _HOOKKEY initialization
 */
internal class HookManager(
    private val getCallStack: () -> List<CallFrame>,
    private val getRegistry: () -> LuaTable,
) {
    /**
     * Current hook configuration
     */
    private var hookConfig: HookConfig = HookConfig.NONE

    /**
     * Flag to prevent recursive hook calls
     */
    private var inHook = false

    /**
     * Instruction counter for COUNT hooks
     */
    private var instructionCount = 0

    /**
     * Set debug hook configuration.
     *
     * @param hook The hook callback (null to disable)
     * @param mask Event mask string ("c"=call, "r"=return, "l"=line)
     * @param count For COUNT events - trigger every N instructions
     */
    fun setHook(
        hook: DebugHook?,
        mask: String,
        count: Int = 0,
    ) {
        hookConfig = HookConfig.fromMask(hook, mask, count)
        instructionCount = 0

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
     * Get current hook configuration.
     */
    fun getHook(): HookConfig = hookConfig

    /**
     * Trigger hook for CALL, RETURN, or LINE events.
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
        if (inHook) return // Prevent recursive hooks
        val hook = hookConfig.hook
        if (event in hookConfig.mask && hook != null) {
            inHook = true
            try {
                hook.onHook(event, line, getCallStack())
            } finally {
                inHook = false
            }
        }
    }

    /**
     * Check and trigger COUNT hook if configured.
     *
     * Should be called after each instruction execution.
     * Triggers hook every N instructions based on count configuration.
     *
     * @param currentLine The current line number
     */
    fun checkCountHook(currentLine: Int) {
        if (HookEvent.COUNT in hookConfig.mask && hookConfig.count > 0) {
            instructionCount++
            if (instructionCount >= hookConfig.count) {
                instructionCount = 0
                triggerHook(HookEvent.COUNT, currentLine)
            }
        }
    }

    /**
     * Reset instruction counter.
     *
     * Called when entering new function context.
     */
    fun resetInstructionCount() {
        instructionCount = 0
    }
}
