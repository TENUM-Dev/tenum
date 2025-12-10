package ai.tenum.lua.vm.debug

import kotlin.text.iterator

/**
 * Debug hook configuration
 */
data class HookConfig(
    val hook: DebugHook?,
    val mask: Set<HookEvent> = emptySet(),
    val count: Int = 0, // For COUNT events - trigger every N instructions
) {
    companion object {
        val NONE = HookConfig(null, emptySet(), 0)

        /**
         * Parse Lua hook mask string:
         * - "c" = call (includes both regular calls and tail calls in Lua 5.4)
         * - "r" = return
         * - "l" = line
         */
        fun fromMask(
            hook: DebugHook?,
            maskString: String,
            count: Int = 0,
        ): HookConfig {
            val events = mutableSetOf<HookEvent>()
            for (c in maskString) {
                when (c) {
                    'c' -> {
                        events.add(HookEvent.CALL)
                        events.add(HookEvent.TAILCALL) // In Lua 5.4, "c" includes tail calls
                    }
                    'r' -> events.add(HookEvent.RETURN)
                    'l' -> events.add(HookEvent.LINE)
                }
            }
            return HookConfig(hook, events, count)
        }
    }
}
