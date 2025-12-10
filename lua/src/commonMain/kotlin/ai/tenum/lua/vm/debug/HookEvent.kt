package ai.tenum.lua.vm.debug

/**
 * Debug hook events that can be monitored
 */
enum class HookEvent {
    CALL, // Function call
    RETURN, // Function return
    LINE, // New line of code
    COUNT, // Instruction count
    TAILCALL, // Tail call (Lua 5.4+)
}
